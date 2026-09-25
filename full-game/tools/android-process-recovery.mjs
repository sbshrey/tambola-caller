// Opt-in destructive process tests on a dedicated emulator and explicitly named local test DB.
// Only fixture child processes and this test app are stopped; credentials never enter output/files.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { randomUUID, createHash } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createServer } from 'node:net';
import { once } from 'node:events';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const serial = option('--serial', 'emulator-5582');
const label = option('--label', 'android-process-recovery');
const selectedKind = option('--kind', undefined);
assert.match(serial, /^emulator-\d+$/); assert.match(label, /^[a-z0-9-]+$/);
if (selectedKind) assert.ok(['draw', 'delete'].includes(selectedKind));
const sdk = process.env.ANDROID_SDK_ROOT || resolve(process.env.LOCALAPPDATA, 'Android/Sdk');
const adb = resolve(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const app = 'io.github.sbshrey.tambola.game';
const url = process.env.TAMBOLA_TEST_DATABASE_URL || '';
assert.match(url, /^jdbc:postgresql:\/\/127\.0\.0\.1:\d+\/tambola_test$/);
assert.ok(process.env.TAMBOLA_TEST_DATABASE_USER && process.env.TAMBOLA_TEST_DATABASE_PASSWORD, 'Explicit test database credentials required');
const output = resolve(root, '.test-workspace', label);
await mkdir(output, { recursive: true });
function adbRun(command, optional = false, binary = false) {
  const result = spawnSync(adb, ['-s', serial, ...command], { encoding: binary ? undefined : 'utf8', windowsHide: true, timeout: 30_000, maxBuffer: 80 * 1024 * 1024 });
  if (!optional) assert.equal(result.status, 0, `adb ${command.slice(0, 3).join(' ')} failed`);
  return result.status === 0 ? (binary ? result.stdout : result.stdout.trim()) : null;
}
assert.match(adbRun(['emu', 'avd', 'name']).split(/\r?\n/)[0], /^tambola_full_game_/);
assert.ok(!/tcp:8080|tcp:8082/.test(adbRun(['reverse', '--list'])), 'Test ports already mapped; preserve the existing owner');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const apk = await readFile(resolve(root, 'app/build/outputs/apk/debug/app-debug.apk'));
function installedHash() {
  const path = adbRun(['shell', 'pm', 'path', app]).replace(/^package:/, '');
  assert.match(path, /^\/data\/app\/[A-Za-z0-9_+=.~/-]+\/base\.apk$/);
  return hash(adbRun(['exec-out', 'cat', path], false, true));
}
assert.equal(installedHash(), hash(apk), 'Install the current debug APK before testing');
for (const port of [8080, 8081, 8082]) {
  const probe = createServer(); probe.listen(port, '127.0.0.1'); await once(probe, 'listening');
  await new Promise((done, reject) => probe.close(error => error ? reject(error) : done()));
}
const scales = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'];
const previous = new Map(scales.map(key => [key, adbRun(['shell', 'settings', 'get', 'global', key])]));
for (const value of previous.values()) assert.match(value, /^(null|\d+(?:\.\d+)?)$/);
const owned = []; const mappings = []; let activeTest;
const evidence = { candidateSha256: hash(apk), serial, cases: [], completed: false };
function child(command, commandArgs, env = process.env, capture = false) {
  const handle = spawn(command, commandArgs, { cwd: root, windowsHide: true, env, stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'ignore' });
  const entry = { handle, output: '', closed: null, error: null };
  entry.closed = new Promise((done, reject) => { handle.once('error', error => { entry.error = error.code || 'spawn_failed'; reject(error); }); handle.once('close', code => done(code)); });
  entry.closed.catch(() => {}); // Observe spawn failures immediately; callers still inspect/await the original promise.
  if (capture) { const append = chunk => { entry.output += chunk.toString(); }; handle.stdout.on('data', append); handle.stderr.on('data', append); }
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
async function control(path, method = 'POST') {
  const response = await fetch(`http://127.0.0.1:8082/${path}`, { method, signal: AbortSignal.timeout(3_000) });
  assert.ok(response.ok, 'Fault control failed'); return response.json();
}
function instrument(method, kind, runId) {
  return child(adb, ['-s', serial, 'shell', 'am', 'instrument', '-w', '-e', 'tambolaProcessRecovery', 'true',
    '-e', 'tambolaRunId', runId, '-e', 'tambolaRecoveryKind', kind, '-e', 'class',
    `io.github.sbshrey.tambola.game.ProcessRecoveryTest#${method}`, `${app}.test/androidx.test.runner.AndroidJUnitRunner`], process.env, true);
}
async function captureScreens(kind, phase) {
  const name = `process-${kind}-${phase}.png`;
  const bytes = adbRun(['exec-out', 'run-as', app, 'cat', `files/${name}`], false, true);
  assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  await writeFile(resolve(output, name), bytes);
}
try {
  const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const service = child(java, ['-cp', resolve(root, 'server/build/install/server/lib/*'), 'io.github.sbshrey.tambola.server.ServerKt'], {
    ...process.env, TAMBOLA_DATABASE_URL: url, TAMBOLA_DATABASE_USER: process.env.TAMBOLA_TEST_DATABASE_USER,
    TAMBOLA_DATABASE_PASSWORD: process.env.TAMBOLA_TEST_DATABASE_PASSWORD, TAMBOLA_BIND_HOST: '127.0.0.1', PORT: '8081',
  }); owned.push(service);
  const proxy = child(process.execPath, [resolve(root, 'tools/room-fault-proxy.mjs')]); owned.push(proxy);
  await until(async () => {
    assert.ok(!service.error && !proxy.error, 'Owned fixture could not start');
    assert.equal(service.handle.exitCode, null, 'Owned service exited'); assert.equal(proxy.handle.exitCode, null, 'Owned proxy exited');
    assert.equal(service.handle.signalCode, null, 'Owned service terminated'); assert.equal(proxy.handle.signalCode, null, 'Owned proxy terminated');
    return fetch('http://127.0.0.1:8080/health/ready', { signal: AbortSignal.timeout(1_000) }).then(r => r.ok).catch(() => false);
  }, 30_000, 'Owned fixtures did not become ready');
  assert.equal((await control('status', 'GET')).fixture, 'tambola-delete-drop-v1');
  for (const port of ['tcp:8080', 'tcp:8082']) { adbRun(['reverse', port, port]); mappings.push(port); }
  for (const key of scales) adbRun(['shell', 'settings', 'put', 'global', key, '0']);
  for (const kind of selectedKind ? [selectedKind] : ['draw', 'delete']) {
    const runId = randomUUID(); const before = await control('status', 'GET');
    const seed = activeTest = instrument('seedPendingAndWaitForProcessKill', kind, runId);
    console.log(`Preparing ${kind} with a committed response deliberately dropped.`);
    let ready;
    try {
      await until(() => {
        assert.ok(!seed.error && seed.handle.exitCode === null && seed.handle.signalCode === null, 'Seed ended before its live ready marker; inspect the seed log');
        const raw = adbRun(['exec-out', 'run-as', app, 'cat', 'no_backup/process-recovery-fixture/ready.json'], true);
        if (!raw) return false;
        try { ready = JSON.parse(raw); } catch { return false; }
        return ready.runId === runId && ready.kind === kind;
      }, 120_000, 'Seed did not reach a durable pending action');
      const pid = adbRun(['shell', 'pidof', app]); assert.match(pid, /^\d+$/); assert.equal(Number(pid), ready.pid);
      const status = await control('status', 'GET');
      const counter = kind === 'draw' ? 'commandsDropped' : 'dropped';
      assert.ok(status[counter] > before[counter], 'No committed response was actually dropped');
      await captureScreens(kind, 'pending');
      adbRun(['shell', 'am', 'force-stop', app]);
      await until(() => !adbRun(['shell', 'pidof', app], true), 10_000, 'Original app process is still alive');
      await bounded(seed.closed, 10_000, 'Seed adb did not end after process kill');
      activeTest = undefined;
      assert.ok(!/OK \(1 test\)/.test(seed.output), 'Seed finished normally instead of being killed');
      await control(kind === 'draw' ? 'allow-commands' : 'allow-deletes');
      const verify = activeTest = instrument('verifyAfterColdProcessStart', kind, runId);
      let newPid;
      await until(() => { newPid = adbRun(['shell', 'pidof', app], true); return /^\d+$/.test(newPid || '') && newPid !== pid; }, 15_000, 'No new app process after restart');
      const code = await bounded(verify.closed, 120_000, 'Recovery verification timed out');
      await writeFile(resolve(output, `${kind}-verify.txt`), verify.output);
      assert.equal(code, 0, 'Verification adb process failed');
      assert.match(verify.output, /OK \(1 test\)/); assert.ok(!/FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED/.test(verify.output));
      activeTest = undefined;
      await captureScreens(kind, 'recovered');
      evidence.cases.push({ kind, runId, oldPid: Number(pid), newPid: Number(newPid), originalProcessTerminated: true,
        committedRepliesDropped: status[counter] - before[counter], verifyPassed: true });
      console.log(`${kind}: verified a new process, original pending identity, service reconciliation and preserved offline progress.`);
    } finally { await writeFile(resolve(output, `${kind}-seed-intentionally-killed.txt`), seed.output); }
  }
  assert.equal(installedHash(), evidence.candidateSha256);
  evidence.completed = true;
} catch (error) {
  console.error(error.message); process.exitCode = 1;
} finally {
  const cleanupErrors = [];
  async function cleanupStep(label, action) { try { await action(); } catch { cleanupErrors.push(label); } }
  if (activeTest) {
    await cleanupStep('stop_test_app', async () => {
      adbRun(['shell', 'am', 'force-stop', app], true);
      if (activeTest.handle.exitCode === null && activeTest.handle.signalCode === null) activeTest.handle.kill();
      await bounded(activeTest.closed.catch(() => {}), 10_000, 'Test adb did not stop');
      await writeFile(resolve(output, 'interrupted-test.txt'), activeTest.output);
    });
  }
  for (const [key, value] of previous) {
    await cleanupStep(`restore_${key}`, () => {
      adbRun(value === 'null' ? ['shell', 'settings', 'delete', 'global', key] : ['shell', 'settings', 'put', 'global', key, value]);
      assert.equal(adbRun(['shell', 'settings', 'get', 'global', key]), value, `Restore ${key}`);
    });
  }
  if (!cleanupErrors.some(error => error.startsWith('restore_'))) evidence.restoredAnimationSettings = Object.fromEntries(previous);
  for (const port of mappings) await cleanupStep(`remove_${port}`, () => adbRun(['reverse', '--remove', port]));
  for (const entry of owned.reverse()) await cleanupStep('stop_fixture', async () => {
    if (entry.handle.exitCode === null && entry.handle.signalCode === null) entry.handle.kill('SIGKILL');
    await bounded(entry.closed.catch(() => {}), 10_000, 'Fixture did not stop');
  });
  if (cleanupErrors.length) { evidence.completed = false; evidence.cleanupErrors = cleanupErrors; process.exitCode = 1; console.error(`Cleanup needs attention: ${cleanupErrors.join(', ')}`); }
  await writeFile(resolve(output, 'evidence.json'), JSON.stringify(evidence, null, 2) + '\n');
}
if (evidence.completed) console.log('Selected cold-process cases passed; owned services stopped and device settings/mappings restored.');
