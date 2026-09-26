// Dedicated emulator only. Real app timers, fixed-size frame counters and repeated rematches.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const serial = option('--serial', 'emulator-5582');
const label = option('--label', 'android-long-session');
const rounds = Number(option('--rounds', '9')), draws = Number(option('--draws', '90'));
assert.match(serial, /^emulator-\d+$/); assert.match(label, /^[a-z0-9-]+$/);
assert.ok(Number.isInteger(rounds) && rounds >= 3 && rounds <= 9);
assert.ok(Number.isInteger(draws) && draws >= 2 && draws <= 90);
const sdk = process.env.ANDROID_SDK_ROOT || resolve(process.env.LOCALAPPDATA, 'Android/Sdk');
const adb = resolve(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const app = 'io.github.sbshrey.tambola.game';
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const apkSha256 = sha(await readFile(resolve(root, option('--apk', 'app/build/outputs/apk/debug/app-debug.apk'))));
const testApkSha256 = sha(await readFile(resolve(root, option('--test-apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))));
function run(command, { binary = false, optional = false } = {}) {
  const result = spawnSync(adb, ['-s', serial, ...command], {
    encoding: binary ? undefined : 'utf8', windowsHide: true, timeout: 30_000, maxBuffer: 80 * 1024 * 1024,
  });
  if (!optional) assert.equal(result.status, 0, `adb ${command.slice(0, 3).join(' ')} failed`);
  return result.status === 0 ? (binary ? result.stdout : result.stdout.trim()) : null;
}
function verifyInstalled() {
  for (const [packageId, expected] of [[app, apkSha256], [`${app}.test`, testApkSha256]]) {
    const path = run(['shell', 'pm', 'path', packageId]).replace(/^package:/, '');
    assert.match(path, /^\/data\/app\/[A-Za-z0-9_+=.~/-]+\/base\.apk$/);
    assert.equal(sha(run(['exec-out', 'cat', path], { binary: true })), expected, `Install matching ${packageId} APK`);
  }
}
const avd = run(['emu', 'avd', 'name']).split(/\r?\n/)[0].trim(); assert.match(avd, /^tambola_full_game_/);
verifyInstalled();
const scales = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'];
const previous = new Map(scales.map(key => [key, run(['shell', 'settings', 'get', 'global', key])]));
for (const value of previous.values()) assert.match(value, /^(null|\d+(?:\.\d+)?)$/);
await mkdir(resolve(root, '.test-workspace'), { recursive: true });
const output = resolve(root, '.test-workspace', label); await mkdir(output, { recursive: false });
const runId = randomUUID();
const evidence = { runId, serial, avd, apkSha256, testApkSha256, rounds, draws,
  driverSha256: sha(await readFile(fileURLToPath(import.meta.url))), completed: false,
  fixtureSourceSha256: sha(await readFile(resolve(root, 'app/src/androidTest/java/io/github/sbshrey/tambola/game/LongSessionTest.kt'))),
  fingerprint: run(['shell', 'getprop', 'ro.build.fingerprint']), displaySize: run(['shell', 'wm', 'size']),
  displayDensity: run(['shell', 'wm', 'density']), fontScale: run(['shell', 'settings', 'get', 'system', 'font_scale']),
  rendererProperty: run(['shell', 'getprop', 'debug.hwui.renderer']),
  animationsBefore: Object.fromEntries(previous), scope: 'Debug emulator long-session diagnostics; not physical release performance acceptance.' };
let child, done = false, code, transcript = '', latest;
function readReport() {
  const raw = run(['exec-out', 'run-as', app, 'cat', 'files/long-session.json'], { optional: true });
  if (!raw) return;
  const value = JSON.parse(raw);
  if (value.runId === runId) return value;
}
async function save() {
  await writeFile(resolve(output, 'evidence.json'), JSON.stringify(evidence, null, 2) + '\n');
  if (latest) await writeFile(resolve(output, 'device-report.json'), JSON.stringify(latest, null, 2) + '\n');
}
try {
  for (const key of scales) run(['shell', 'settings', 'put', 'global', key, '1']);
  run(['shell', 'input', 'keyevent', 'KEYCODE_WAKEUP']);
  run(['shell', 'wm', 'dismiss-keyguard']);
  child = spawn(adb, ['-s', serial, 'shell', 'am', 'instrument', '-w',
    '-e', 'tambolaLongSession', 'true', '-e', 'tambolaRunId', runId,
    '-e', 'tambolaRounds', String(rounds), '-e', 'tambolaDraws', String(draws),
    '-e', 'class', 'io.github.sbshrey.tambola.game.LongSessionTest',
    `${app}.test/androidx.test.runner.AndroidJUnitRunner`], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  evidence.adbPid = child.pid;
  child.on('error', error => { evidence.spawnError = error.code || 'spawn_failed'; done = true; });
  child.on('close', status => { done = true; code = status; });
  for (const stream of [child.stdout, child.stderr]) stream.on('data', bytes => { transcript += bytes.toString(); });
  await save();
  const deadline = Date.now() + Math.min(90 * 60_000, rounds * (draws * 10_000 + 90_000));
  let stage;
  while (!done) {
    assert.ok(Date.now() < deadline, 'Long-session fixture exceeded its watchdog');
    const report = readReport();
    if (report) {
      latest = report;
      if (report.stage !== stage) { stage = report.stage; evidence.stage = stage; console.log(`session[${runId}]: ${stage}`); await save(); }
    }
    await delay(5_000);
  }
  latest = readReport() || latest;
  assert.equal(code, 0); assert.match(transcript, /OK \(1 test\)/);
  assert.ok(!/FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(transcript));
  assert.equal(latest?.completed, true); assert.equal(latest?.cleanupComplete, true);
  assert.equal(latest.configuredRounds, rounds); assert.equal(latest.configuredDraws, draws);
  assert.equal(latest.rounds.length, rounds);
  assert.equal(latest.managedHeapSpanWithin16MiB, true);
  if (rounds === 9 && draws === 90) assert.equal(latest.fullSessionAtLeast60Minutes, true);
  else assert.equal(latest.fullSessionAtLeast60Minutes, false);
  verifyInstalled(); evidence.completed = true;
} catch (error) {
  evidence.failureType = error.constructor.name;
  console.error(error.message); process.exitCode = 1;
} finally {
  const cleanupErrors = [];
  if (child && !done) {
    run(['shell', 'am', 'force-stop', app], { optional: true }); child.kill();
    const limit = Date.now() + 10_000;
    while (!done && Date.now() < limit) await delay(100);
    if (!done) cleanupErrors.push('instrumentation_process');
  }
  for (const [key, value] of previous) {
    try {
      run(value === 'null' ? ['shell', 'settings', 'delete', 'global', key] : ['shell', 'settings', 'put', 'global', key, value]);
      assert.equal(run(['shell', 'settings', 'get', 'global', key]), value);
    } catch { cleanupErrors.push(key); }
  }
  try { latest = readReport() || latest; } catch { cleanupErrors.push('read_final_report'); }
  if (child && latest?.cleanupComplete !== true) cleanupErrors.push('app_preference_restore_unconfirmed');
  evidence.deviceSettingsRestored = cleanupErrors.length === 0;
  if (cleanupErrors.length) { evidence.cleanupErrors = cleanupErrors; evidence.completed = false; process.exitCode = 1; }
  await writeFile(resolve(output, 'instrumentation.txt'), transcript); await save();
}
if (evidence.completed) console.log(latest.fullSessionAtLeast60Minutes ? 'Full native session completed; review frame and memory diagnostics.' : 'Short fixture probe completed; it does not satisfy the full-hour gate.');
