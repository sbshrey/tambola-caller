// Explicitly opt-in cold-process acceptance on a dedicated Tambola emulator. No app data is cleared.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { randomUUID, createHash } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const serial = option('--serial', 'emulator-5582');
const label = option('--label', 'locale-process-recovery');
const upgradeApkPath = option('--upgrade-apk');
const upgradeTestPath = option('--upgrade-test-apk');
assert.equal(Boolean(upgradeApkPath), Boolean(upgradeTestPath), 'An upgrade requires both app and instrumentation APKs');
assert.match(serial, /^emulator-\d+$/); assert.match(label, /^[a-z0-9-]+$/);
const sdk = process.env.ANDROID_SDK_ROOT || resolve(process.env.LOCALAPPDATA, 'Android/Sdk');
const adb = resolve(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const app = 'io.github.sbshrey.tambola.game';
const output = resolve(root, '.test-workspace', label);
await mkdir(output, { recursive: true });
function run(command, optional = false, binary = false) {
  const result = spawnSync(adb, ['-s', serial, ...command], { encoding: binary ? undefined : 'utf8', windowsHide: true, timeout: command[0] === 'install' ? 120_000 : 30_000, maxBuffer: 80 * 1024 * 1024 });
  if (!optional) assert.equal(result.status, 0, `adb ${command.slice(0, 3).join(' ')} failed`);
  return result.status === 0 ? (binary ? result.stdout : result.stdout.trim()) : null;
}
assert.match(run(['emu', 'avd', 'name']).split(/\r?\n/)[0], /^tambola_full_game_/);
const api = Number(run(['shell', 'getprop', 'ro.build.version.sdk']));
assert.ok(api >= 26);
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const apk = await readFile(resolve(root, option('--apk', 'app/build/outputs/apk/debug/app-debug.apk')));
const upgrade = upgradeApkPath ? {
  appPath: resolve(root, upgradeApkPath), testPath: resolve(root, upgradeTestPath),
  appSha256: sha(await readFile(resolve(root, upgradeApkPath))),
  instrumentationSha256: sha(await readFile(resolve(root, upgradeTestPath))),
} : null;
function installedHash(packageName = app) {
  const path = run(['shell', 'pm', 'path', packageName]).replace(/^package:/, '');
  assert.match(path, /^\/data\/app\/[A-Za-z0-9_+=.~/-]+\/base\.apk$/);
  return sha(run(['exec-out', 'cat', path], false, true));
}
assert.equal(installedHash(), sha(apk), 'Install the exact candidate first');
const runId = randomUUID();
const evidence = { runId, serial, api, fingerprint: run(['shell', 'getprop', 'ro.build.fingerprint']), candidateSha256: sha(apk),
  instrumentationSha256: installedHash(`${app}.test`), cases: [], completed: false };
if (upgrade) evidence.upgrade = { appSha256: upgrade.appSha256, instrumentationSha256: upgrade.instrumentationSha256, installed: false };
const scales = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'];
const previous = new Map(scales.map(key => [key, run(['shell', 'settings', 'get', 'global', key])]));
for (const value of previous.values()) assert.match(value, /^(null|\d+(?:\.\d+)?)$/);
let active;
function instrument(method, locale = 'hi') {
  const handle = spawn(adb, ['-s', serial, 'shell', 'am', 'instrument', '-w', '-e', 'tambolaLocaleProcess', 'true',
    '-e', 'tambolaRunId', runId, '-e', 'tambolaExpectedLocale', locale, '-e', 'class',
    `io.github.sbshrey.tambola.game.LocaleProcessTest#${method}`, `${app}.test/androidx.test.runner.AndroidJUnitRunner`],
    { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  const entry = { handle, text: '', closed: null, error: null };
  entry.closed = new Promise((done, reject) => { handle.once('error', error => { entry.error = error.code || 'spawn_failed'; reject(error); }); handle.once('close', done); });
  entry.closed.catch(() => {});
  const append = chunk => { entry.text += chunk.toString(); };
  handle.stdout.on('data', append); handle.stderr.on('data', append);
  active = entry;
  return entry;
}
async function until(predicate, timeout, message) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) { if (await predicate()) return; await delay(200); }
  throw new Error(message);
}
async function bounded(promise, timeout, message) {
  let timer;
  try { return await Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(message)), timeout); })]); }
  finally { clearTimeout(timer); }
}
function fixture(name, optional = false) {
  const raw = run(['exec-out', 'run-as', app, 'cat', `no_backup/locale-process-fixture/${name}.json`], optional);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}
async function screenshot(name) {
  const bytes = run(['exec-out', 'run-as', app, 'cat', `files/${name}.png`], false, true);
  assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  await writeFile(resolve(output, `${name}.png`), bytes);
}
async function checkedTest(method, locale, name) {
  const entry = instrument(method, locale);
  try {
    const code = await bounded(entry.closed, 120_000, `${name} timed out`);
    assert.equal(code, 0); assert.match(entry.text, /OK \(1 test\)/);
    assert.ok(!/FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED/.test(entry.text));
    active = null;
  } finally { await writeFile(resolve(output, `${name}.txt`), entry.text); }
}
try {
  for (const key of scales) run(['shell', 'settings', 'put', 'global', key, '0']);
  const seed = instrument('seedAndWaitForProcessKill');
  console.log(`API ${api}: preparing a Hindi interface and a durable marked round.`);
  let ready;
  try {
    await until(() => {
      assert.ok(!seed.error && seed.handle.exitCode === null && seed.handle.signalCode === null, 'Seed ended before the live marker');
      ready = fixture('ready', true);
      return ready?.runId === runId;
    }, 120_000, 'Seed did not reach a saved Hindi game');
    const pid = run(['shell', 'pidof', app]); assert.match(pid, /^\d+$/); assert.equal(Number(pid), ready.pid);
    await screenshot('locale-process-seeded');
    run(['shell', 'am', 'force-stop', app]);
    await until(() => !run(['shell', 'pidof', app], true), 10_000, 'Original process is still alive');
    await bounded(seed.closed, 10_000, 'Seed did not stop'); active = null;
    assert.ok(!/OK \(1 test\)/.test(seed.text), 'Seed finished instead of being killed');
    if (upgrade) {
      assert.match(run(['install', '-r', '-t', upgrade.appPath]), /Success/);
      assert.match(run(['install', '-r', '-t', upgrade.testPath]), /Success/);
      assert.equal(installedHash(), upgrade.appSha256);
      assert.equal(installedHash(`${app}.test`), upgrade.instrumentationSha256);
      evidence.upgrade.installed = true;
      console.log('Installed the new app and instrumentation in place, preserving app data.');
    }
    for (const locale of api >= 33 ? ['hi', 'en'] : ['hi']) {
      if (locale === 'en') {
        run(['shell', 'am', 'force-stop', app]);
        // The platform command changes the app language while the app is stopped.
        run(['shell', 'cmd', 'locale', 'set-app-locales', app, '--user', '0', '--locales', 'en']);
      }
      await checkedTest('verifyAfterColdStart', locale, `verify-${locale}`);
      const verified = fixture(`verified-${locale}`);
      assert.equal(verified.runId, runId); assert.notEqual(verified.pid, ready.pid);
      assert.equal(verified.roundSha256, ready.roundSha256);
      await screenshot(`locale-process-recovered-${locale}`);
      evidence.cases.push({ locale, source: locale === 'hi' ? 'in_app_picker' : 'platform_while_stopped', oldPid: ready.pid,
        newPid: verified.pid, originalProcessTerminated: true, identicalRoundSha256: verified.roundSha256 });
      console.log(`${locale}: new process retained the language, caller choice and exact marked round.`);
    }
  } finally { await writeFile(resolve(output, 'seed-intentionally-killed.txt'), seed.text); }
  assert.equal(installedHash(), upgrade?.appSha256 ?? evidence.candidateSha256);
  assert.equal(installedHash(`${app}.test`), upgrade?.instrumentationSha256 ?? evidence.instrumentationSha256);
  evidence.completed = true;
} catch (error) { console.error(error.message); process.exitCode = 1; }
finally {
  const cleanupErrors = [];
  if (active) {
    run(['shell', 'am', 'force-stop', app], true);
    if (active.handle.exitCode === null && active.handle.signalCode === null) active.handle.kill();
    try { await bounded(active.closed.catch(() => {}), 10_000, 'Interrupted test did not stop'); }
    catch { cleanupErrors.push('stop_test'); }
  }
  try { await checkedTest('restoreOriginalLocale', 'hi', 'restore-locale'); evidence.originalLocaleRestored = true; }
  catch {
    cleanupErrors.push('restore_locale');
    if (active && active.handle.exitCode === null && active.handle.signalCode === null) {
      run(['shell', 'am', 'force-stop', app], true); active.handle.kill();
      try { await bounded(active.closed.catch(() => {}), 10_000, 'Locale cleanup test did not stop'); }
      catch { cleanupErrors.push('stop_locale_cleanup_test'); }
    }
  }
  for (const [key, value] of previous) {
    try {
      run(value === 'null' ? ['shell', 'settings', 'delete', 'global', key] : ['shell', 'settings', 'put', 'global', key, value]);
      assert.equal(run(['shell', 'settings', 'get', 'global', key]), value);
    } catch { cleanupErrors.push(`restore_${key}`); }
  }
  if (cleanupErrors.length) { evidence.completed = false; evidence.cleanupErrors = cleanupErrors; process.exitCode = 1; }
  else evidence.restoredAnimationSettings = Object.fromEntries(previous);
  await writeFile(resolve(output, 'evidence.json'), JSON.stringify(evidence, null, 2) + '\n');
}
if (evidence.completed) console.log('Cold-locale acceptance passed; original locale and animation settings restored.');
