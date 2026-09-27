// Runs only on a dedicated Tambola emulator; restores every device setting it changes.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const args = process.argv.slice(2);
const online = args.includes('--online');
const lan = args.includes('--lan');
const faultProxy = args.includes('--fault-proxy');
const animations = args.includes('--animations');
const idleGuard = args.includes('--idle-guard');
const markGuard = args.includes('--mark-guard');
const benchmark = args.includes('--benchmark');
const endurance = args.includes('--endurance');
function option(name, fallback) {
  const index = args.indexOf(name);
  return index < 0 ? fallback : args[index + 1];
}
const serial = option('--serial', process.env.ANDROID_SERIAL || 'emulator-5582');
const label = option('--label', online ? 'android-online-smoke' : 'android-offline-smoke');
const selectedClass = option('--class', undefined);
assert.match(serial, /^emulator-\d+$/);
assert.match(label, /^[a-z0-9-]+$/);
if (selectedClass) assert.match(selectedClass, /^io\.github\.sbshrey\.tambola\.(game|benchmark)\.[A-Za-z0-9_.#]+$/);
if (benchmark) assert.ok(selectedClass?.startsWith('io.github.sbshrey.tambola.benchmark.') && !online && !faultProxy && !idleGuard && !markGuard);
else assert.ok(!selectedClass?.startsWith('io.github.sbshrey.tambola.benchmark.'), 'Use --benchmark for the separate optimized-app driver');
if (endurance) assert.ok(benchmark && animations && selectedClass === 'io.github.sbshrey.tambola.benchmark.CoinReleaseBenchmark#realCoinEndurance');
if (selectedClass?.endsWith('#realCoinEndurance')) assert.ok(endurance, 'Explicit --endurance opt-in is required');
if (idleGuard) assert.equal(selectedClass, 'io.github.sbshrey.tambola.game.CoinIdleTest');
if (markGuard) assert.ok(selectedClass === 'io.github.sbshrey.tambola.game.CoinMarkTest' && animations && !online && !lan && !idleGuard);
if (lan) assert.ok(!online && selectedClass, '--lan requires an explicit test class and no loopback fixture');
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
  assert.match(run('reverse', '--list'), /\btcp:8080\s+tcp:8080\b/, 'Map this emulator port 8080 to the isolated room service before online tests');
  const health = await fetch('http://127.0.0.1:8080/health/ready', { signal: AbortSignal.timeout(3_000) });
  assert.ok(health.ok, 'Start the isolated loopback room service before online tests');
}
if (faultProxy) {
  assert.ok(online, '--fault-proxy requires --online');
  assert.match(run('reverse', '--list'), /\btcp:8082\s+tcp:8082\b/, 'Map this emulator port 8082 to the fault fixture before online tests');
  const status = await (await fetch('http://127.0.0.1:8082/status', { signal: AbortSignal.timeout(3_000) })).json();
  assert.equal(status.fixture, 'tambola-delete-drop-v1');
}
const scales = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'];
const previous = new Map(scales.map(name => [name, run('shell', 'settings', 'get', 'global', name)]));
for (const value of previous.values()) assert.match(value, /^(null|\d+(?:\.\d+)?)$/);
let output = '';
let exitCode;
try {
  for (const name of scales) run('shell', 'settings', 'put', 'global', name, animations && name === 'animator_duration_scale' ? '1' : '0');
  const command = ['shell', 'am', 'instrument', '-w'];
  if (online) command.push('-e', 'tambolaOnline', 'true');
  if (lan) command.push('-e', 'tambolaLan', 'true');
  if (faultProxy) command.push('-e', 'tambolaFaultProxy', 'true');
  if (idleGuard) command.push('-e', 'tambolaIdleGuard', 'true');
  if (markGuard) command.push('-e', 'tambolaMarkGuard', 'true');
  if (benchmark) command.push('-e', 'tambolaBenchmark', 'true', '-e', 'androidx.benchmark.suppressErrors', 'EMULATOR');
  if (endurance) command.push('-e', 'tambolaEndurance', 'true');
  if (selectedClass) command.push('-e', 'class', selectedClass);
  else command.push('-e', 'notClass', 'io.github.sbshrey.tambola.game.ProcessRecoveryTest,io.github.sbshrey.tambola.game.NativePairTest,io.github.sbshrey.tambola.game.UpgradeAvatarTest,io.github.sbshrey.tambola.game.LocaleProcessTest,io.github.sbshrey.tambola.game.NativeLibraryTest,io.github.sbshrey.tambola.game.StorageLifecycleTest,io.github.sbshrey.tambola.game.LongSessionTest,io.github.sbshrey.tambola.game.CoinIdleTest');
  command.push(`${benchmark ? 'io.github.sbshrey.tambola.benchmark' : 'io.github.sbshrey.tambola.game.test'}/androidx.test.runner.AndroidJUnitRunner`);
  const child = spawn(adb, ['-s', serial, ...command], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  const append = chunk => { output += chunk.toString(); process.stdout.write(chunk); };
  child.stdout.on('data', append); child.stderr.on('data', append);
  const timeout = setTimeout(() => child.kill(), endurance ? 95 * 60_000 : 600_000);
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
  if (benchmark) {
    // Macrobenchmark clears its shared output directory at the next invocation.
    const destination = resolve(root, '.test-workspace', `${label}-benchmark`);
    await mkdir(destination, { recursive: true });
    const copied = spawnSync(adb, ['-s', serial, 'pull', '/sdcard/Android/media/io.github.sbshrey.tambola.benchmark', destination],
      { encoding: 'utf8', windowsHide: true, timeout: 60_000 });
    await writeFile(resolve(destination, 'collection.txt'), `${copied.stdout || ''}${copied.stderr || ''}`);
    assert.equal(copied.status, 0, 'Collect benchmark reports before another invocation');
    if (selectedClass?.endsWith('#realCoinRound') || endurance) {
      for (const name of ['coin-release-journey.json', 'coin-release-results.png', 'coin-release-failure.png', 'coin-release-failure.xml']) {
        // exec-out does not reliably propagate a missing remote file's exit status.
        const exists = spawnSync(adb, ['-s', serial, 'shell', 'run-as', 'io.github.sbshrey.tambola.benchmark', 'test', '-f', `files/${name}`],
          { windowsHide: true, timeout: 30_000 });
        if (exists.status !== 0) {
          assert.notEqual(name, 'coin-release-journey.json', 'The journey must produce a safe cleanup report');
          continue;
        }
        const file = spawnSync(adb, ['-s', serial, 'exec-out', 'run-as', 'io.github.sbshrey.tambola.benchmark', 'cat', `files/${name}`],
          { windowsHide: true, timeout: 30_000, maxBuffer: 8 * 1024 * 1024 });
        assert.equal(file.status, 0, `Collect ${name}`);
        if (name.endsWith('.png')) assert.equal(file.stdout.subarray(0, 8).toString('hex'), '89504e470d0a1a0a', `Validate ${name}`);
        if (name.endsWith('.json')) JSON.parse(file.stdout.toString('utf8'));
        await writeFile(resolve(destination, name), file.stdout);
      }
    }
  }
}
// Android's am instrument may exit zero despite JUnit failures.
assert.equal(exitCode, 0, 'Instrumentation process failed');
assert.ok(/OK \(\d+ tests?\)/.test(output), `JUnit did not report success; see .test-workspace/${label}.txt`);
assert.ok(!/FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(output), `Instrumentation failed; see .test-workspace/${label}.txt`);
console.log('Instrumentation passed; original system animation settings restored.');
