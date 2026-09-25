// Runs only on a dedicated Tambola emulator; restores every device setting it changes.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const args = process.argv.slice(2);
const online = args.includes('--online');
const faultProxy = args.includes('--fault-proxy');
function option(name, fallback) {
  const index = args.indexOf(name);
  return index < 0 ? fallback : args[index + 1];
}
const serial = option('--serial', process.env.ANDROID_SERIAL || 'emulator-5582');
const label = option('--label', online ? 'android-online-smoke' : 'android-offline-smoke');
const selectedClass = option('--class', undefined);
assert.match(serial, /^emulator-\d+$/);
assert.match(label, /^[a-z0-9-]+$/);
if (selectedClass) assert.match(selectedClass, /^io\.github\.sbshrey\.tambola\.game\.[A-Za-z0-9_.#]+$/);
const sdk = process.env.ANDROID_SDK_ROOT || (process.env.LOCALAPPDATA && resolve(process.env.LOCALAPPDATA, 'Android/Sdk'));
assert.ok(sdk, 'Set ANDROID_SDK_ROOT');
const adb = resolve(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
function run(...command) {
  const result = spawnSync(adb, ['-s', serial, ...command], { encoding: 'utf8', windowsHide: true, timeout: 30_000 });
  assert.equal(result.status, 0, `adb ${command.slice(0, 3).join(' ')} failed`);
  return result.stdout.trim();
}
assert.match(run('emu', 'avd', 'name').split(/\r?\n/)[0], /^tambola_full_game_/);
if (online) {
  const health = await fetch('http://127.0.0.1:8080/health/ready', { signal: AbortSignal.timeout(3_000) });
  assert.ok(health.ok, 'Start the isolated loopback room service before online tests');
}
if (faultProxy) {
  assert.ok(online, '--fault-proxy requires --online');
  const status = await (await fetch('http://127.0.0.1:8082/status', { signal: AbortSignal.timeout(3_000) })).json();
  assert.equal(status.fixture, 'tambola-delete-drop-v1');
}
const scales = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'];
const previous = new Map(scales.map(name => [name, run('shell', 'settings', 'get', 'global', name)]));
for (const value of previous.values()) assert.match(value, /^(null|\d+(?:\.\d+)?)$/);
let output = '';
let exitCode;
try {
  for (const name of scales) run('shell', 'settings', 'put', 'global', name, '0');
  const command = ['shell', 'am', 'instrument', '-w'];
  if (online) command.push('-e', 'tambolaOnline', 'true');
  if (faultProxy) command.push('-e', 'tambolaFaultProxy', 'true');
  if (selectedClass) command.push('-e', 'class', selectedClass);
  else command.push('-e', 'notClass', 'io.github.sbshrey.tambola.game.ProcessRecoveryTest,io.github.sbshrey.tambola.game.NativePairTest');
  command.push('io.github.sbshrey.tambola.game.test/androidx.test.runner.AndroidJUnitRunner');
  const child = spawn(adb, ['-s', serial, ...command], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  const append = chunk => { output += chunk.toString(); process.stdout.write(chunk); };
  child.stdout.on('data', append); child.stderr.on('data', append);
  const timeout = setTimeout(() => child.kill(), 600_000);
  try {
    exitCode = await new Promise((done, reject) => { child.once('error', reject); child.once('close', done); });
  } finally { clearTimeout(timeout); }
} finally {
  for (const [name, value] of previous) {
    if (value === 'null') run('shell', 'settings', 'delete', 'global', name);
    else run('shell', 'settings', 'put', 'global', name, value);
  }
  for (const [name, value] of previous) assert.equal(run('shell', 'settings', 'get', 'global', name), value, `Restore ${name}`);
  await mkdir(resolve(root, '.test-workspace'), { recursive: true });
  await writeFile(resolve(root, '.test-workspace', `${label}.txt`), output);
}
// Android's am instrument may exit zero despite JUnit failures.
assert.equal(exitCode, 0, 'Instrumentation process failed');
assert.ok(/OK \(\d+ tests?\)/.test(output), `JUnit did not report success; see .test-workspace/${label}.txt`);
assert.ok(!/FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(output), `Instrumentation failed; see .test-workspace/${label}.txt`);
console.log('Instrumentation passed; original system animation settings restored.');
