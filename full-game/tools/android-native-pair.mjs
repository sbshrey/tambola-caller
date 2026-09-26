// Two independent native Android UIs against an owned, isolated loopback service.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import { once } from 'node:events';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { createByteMeter } from './tcp-byte-meter.mjs';
import { serviceRuntime } from './service-runtime.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const args = process.argv.slice(2);
const option = (key, fallback) => args.includes(key) ? args[args.indexOf(key) + 1] : fallback;
const label = option('--label', 'android-native-pair'); assert.match(label, /^[a-z0-9-]+$/);
const measureNetwork = args.includes('--measure-network');
const devices = [{ role: 'host', serial: option('--host', 'emulator-5582') }, { role: 'guest', serial: option('--guest', 'emulator-5584') }];
assert.notEqual(devices[0].serial, devices[1].serial);
const sdk = process.env.ANDROID_SDK_ROOT || resolve(process.env.LOCALAPPDATA, 'Android/Sdk');
const adb = resolve(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const app = 'io.github.sbshrey.tambola.game';
const db = process.env.TAMBOLA_TEST_DATABASE_URL || '';
assert.match(db, /^jdbc:postgresql:\/\/127\.0\.0\.1:\d+\/tambola_test$/);
assert.ok(process.env.TAMBOLA_TEST_DATABASE_USER && process.env.TAMBOLA_TEST_DATABASE_PASSWORD);
const output = resolve(root, '.test-workspace', label); await mkdir(output, { recursive: false });
const runId = randomUUID(); const scales = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'];
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const candidateSha256 = hash(await readFile(resolve(root, option('--apk', 'app/build/outputs/apk/debug/app-debug.apk'))));
const instrumentationSha256 = hash(await readFile(resolve(root, option('--test-apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))));
const evidence = { runId, candidateSha256, instrumentationSha256, devices: [], completed: false };
Object.assign(evidence, await serviceRuntime(root));
evidence.fixtureSha256 = hash(await readFile(fileURLToPath(import.meta.url)));
if (measureNetwork) evidence.byteMeterSha256 = hash(await readFile(fileURLToPath(new URL('./tcp-byte-meter.mjs', import.meta.url))));
let service;
function run(device, command, optional = false, binary = false) {
  const result = spawnSync(adb, ['-s', device.serial, ...command], { encoding: binary ? undefined : 'utf8', windowsHide: true, timeout: 30_000, maxBuffer: 80 * 1024 * 1024 });
  if (!optional) assert.equal(result.status, 0, `adb ${device.role} ${command.slice(0, 3).join(' ')} failed`);
  return result.status === 0 ? (binary ? result.stdout : result.stdout.trim()) : null;
}
function verifyInstalled(device) {
  for (const [packageId, expected] of [[app, candidateSha256], [`${app}.test`, instrumentationSha256]]) {
    const path = run(device, ['shell', 'pm', 'path', packageId]).replace(/^package:/, '');
    assert.match(path, /^\/data\/app\/[A-Za-z0-9_+=.~/-]+\/base\.apk$/);
    assert.equal(hash(run(device, ['exec-out', 'cat', path], false, true)), expected, `Install matching ${packageId} APK on ${device.role}`);
  }
}
function child(command, commandArgs, env, capture = false) {
  const handle = spawn(command, commandArgs, { cwd: root, env, windowsHide: true, stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'ignore' });
  const result = { handle, output: '', error: null, done: false, code: null };
  result.closed = new Promise((done, reject) => {
    handle.once('error', error => { result.error = error.code || 'spawn_failed'; reject(error); });
    handle.once('close', code => { result.done = true; result.code = code; done(code); });
  }); result.closed.catch(() => {});
  if (capture) { const append = chunk => { result.output += chunk.toString(); }; handle.stdout.on('data', append); handle.stderr.on('data', append); }
  return result;
}
async function until(predicate, timeout, message) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) { if (await predicate()) return; await delay(200); }
  throw new Error(message);
}
function startTest(device, roomCode) {
  const options = ['-s', device.serial, 'shell', 'am', 'instrument', '-w', '-e', 'tambolaNativePair', 'true', '-e', 'tambolaRunId', runId, '-e', 'tambolaPairRole', device.role];
  if (roomCode) options.push('-e', 'tambolaRoomCode', roomCode);
  options.push('-e', 'class', 'io.github.sbshrey.tambola.game.NativePairTest', `${app}.test/androidx.test.runner.AndroidJUnitRunner`);
  device.test = child(adb, options, process.env, true);
}
function readState(device) {
  if (device.test?.error) throw new Error(`${device.role} instrumentation could not start`);
  if (device.test?.done && !/OK \(1 test\)/.test(device.test.output)) throw new Error(`${device.role} instrumentation failed; inspect its saved log`);
  const raw = run(device, ['exec-out', 'run-as', app, 'cat', 'files/native-pair.json'], true);
  if (!raw) return null;
  try { const state = JSON.parse(raw); return state.runId === runId && state.role === device.role ? state : null; } catch { return null; }
}
async function bothPhase(phase, timeout) {
  let states;
  await until(() => { states = devices.map(readState); return states.every(state => state?.phase === phase); }, timeout, `Both native clients did not reach ${phase}`);
  assert.notEqual(states[0].playerId, states[1].playerId);
  assert.equal(states[0].room.roomId, states[1].room.roomId);
  assert.equal(states[0].room.round.id, states[1].room.round.id);
  for (const state of states) {
    assert.equal(state.room.round.ownTickets.length, 2);
    assert.ok(state.room.round.ownTickets.every(ticket => ticket.playerId === state.playerId));
  }
  assert.ok(states[0].room.round.ownTickets.every(ticket => !states[1].room.round.ownTickets.some(other => other.id === ticket.id)));
  for (const field of ['called', 'awards', 'customAwards', 'scores', 'revealedOrder', 'revealedNonce']) assert.deepEqual(states[0].room.round[field], states[1].room.round[field], `Different ${field} across native clients`);
  return states;
}
function gate(name) { for (const device of devices) run(device, ['shell', 'run-as', app, 'touch', `files/native-pair-${runId}-${name}`]); }
function networkSnapshot() {
  return Object.fromEntries(devices.map(device => [device.role, device.meter.snapshot()]));
}
for (const device of devices) {
  assert.match(device.serial, /^emulator-\d+$/);
  device.avd = run(device, ['emu', 'avd', 'name']).split(/\r?\n/)[0].trim(); assert.match(device.avd, /^tambola_full_game_/);
  assert.ok(!/tcp:8080/.test(run(device, ['reverse', '--list'])), 'Preserve the existing reverse-port owner');
  verifyInstalled(device);
  device.scales = new Map(scales.map(key => [key, run(device, ['shell', 'settings', 'get', 'global', key])]));
  for (const value of device.scales.values()) assert.match(value, /^(null|\d+(?:\.\d+)?)$/);
}
assert.notEqual(devices[0].avd, devices[1].avd);
const probe = createServer(); probe.listen(8080, '127.0.0.1'); await once(probe, 'listening');
await new Promise((done, reject) => probe.close(error => error ? reject(error) : done()));
try {
  const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  service = child(java, ['-cp', resolve(root, 'server/build/install/server/lib/*'), 'io.github.sbshrey.tambola.server.ServerKt'], {
    ...process.env, TAMBOLA_DATABASE_URL: db, TAMBOLA_DATABASE_USER: process.env.TAMBOLA_TEST_DATABASE_USER,
    TAMBOLA_DATABASE_PASSWORD: process.env.TAMBOLA_TEST_DATABASE_PASSWORD, TAMBOLA_BIND_HOST: '127.0.0.1', PORT: '8080',
    TAMBOLA_LOCAL_DEVELOPMENT: 'true', TAMBOLA_DELETION_DATABASE_URL: '', TAMBOLA_DELETION_DATABASE_USER: '', TAMBOLA_DELETION_DATABASE_PASSWORD: '',
  });
  await until(async () => {
    assert.ok(!service.error && !service.done, 'Owned service did not stay running');
    return fetch('http://127.0.0.1:8080/health/ready', { signal: AbortSignal.timeout(1_000) }).then(r => r.ok).catch(() => false);
  }, 30_000, 'Room service readiness timed out');
  if (measureNetwork) {
    evidence.network = { scope: 'Per-device loopback TCP payload bytes, including HTTP headers and WebSocket framing/acks/pings. No TLS/IP/radio overhead; no payload contents retained. Not a physical-network measurement.' };
    for (const device of devices) device.meter = await createByteMeter(8080);
  }
  for (const device of devices) {
    run(device, ['reverse', 'tcp:8080', `tcp:${device.meter?.port || 8080}`]); device.mapped = true;
    for (const key of scales) run(device, ['shell', 'settings', 'put', 'global', key, '0']);
  }
  startTest(devices[0]); let lobby;
  await until(() => { lobby = readState(devices[0]); return lobby?.phase === 'lobby'; }, 90_000, 'Native host did not create its lobby');
  assert.match(lobby.room.code, /^[A-HJ-NP-Z2-9]{8}$/);
  startTest(devices[1], lobby.room.code);
  console.log('Native host created a room; independent guest UI is joining and readying.');
  const finished = await bothPhase('finished', 360_000);
  assert.equal(finished[0].room.round.called.length, 90); assert.equal(new Set(finished[0].room.round.called).size, 90);
  assert.ok(finished.every(state => state.room.round.status === 'COMPLETED' && state.historySize === 1));
  evidence.firstRound = { id: finished[0].room.round.id, calls: 90, matchingCallsScoresAwardsAndAudit: true, privateTicketsPerDevice: 2 };
  if (measureNetwork) evidence.network.setupAndFirstRound = networkSnapshot();
  console.log('Both native UIs finished 90 calls with matching scores, awards and draw audit.');
  gate('rematch');
  const rematched = await bothPhase('rematched', 90_000);
  assert.notEqual(rematched[0].room.round.id, finished[0].room.round.id);
  assert.deepEqual(rematched[0].room.options, finished[0].room.options);
  assert.equal(rematched[0].room.round.called.length, 0);
  rematched.forEach((state, index) => assert.ok(state.room.round.ownTickets.some((ticket, ordinal) =>
    JSON.stringify(ticket.cells) !== JSON.stringify(finished[index].room.round.ownTickets[ordinal].cells)), 'Rematch reused every number grid'));
  gate('end');
  const complete = await bothPhase('complete', 90_000);
  assert.ok(complete.every(state => state.room.round.status === 'CANCELLED' && state.historySize === 2));
  await until(() => devices.every(device => device.test.done), 15_000, 'Native tests did not finish');
  for (const device of devices) {
    assert.equal(device.test.code, 0); assert.match(device.test.output, /OK \(1 test\)/);
    assert.ok(!/FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(device.test.output)); verifyInstalled(device);
    for (const phase of ['playing', 'finished', 'rematch-result']) {
      const name = `native-pair-${device.role}-${phase}.png`;
      const bytes = run(device, ['exec-out', 'run-as', app, 'cat', `files/${name}`], false, true);
      assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a'); await writeFile(resolve(output, name), bytes);
    }
    evidence.devices.push({ role: device.role, serial: device.serial, avd: device.avd, pid: complete.find(state => state.role === device.role).pid, passed: true });
  }
  evidence.rematch = { id: complete[0].room.round.id, newNumberGrids: true, retainedRules: true, nativeCancellation: true, historyPerDevice: 2 };
  if (measureNetwork) {
    evidence.network.throughRematchAndNativeCancellation = networkSnapshot();
    for (const counts of Object.values(evidence.network.throughRematchAndNativeCancellation)) {
      assert.ok(counts.clientToServiceBytes > 0 && counts.serviceToClientBytes > 0);
      assert.equal(counts.rejectedConnections, 0, 'Byte meter rejected a connection');
      assert.equal(counts.transportErrors, 0, 'Byte meter encountered a transport error');
    }
  }
  assert.equal((await serviceRuntime(root)).serviceRuntimeSha256, evidence.serviceRuntimeSha256, 'Service runtime changed during native acceptance');
  evidence.completed = true;
} catch (error) { console.error(error.message); process.exitCode = 1; }
finally {
  const cleanupErrors = [];
  async function clean(label, action) { try { await action(); } catch { cleanupErrors.push(label); } }
  for (const device of devices) {
    if (device.test && !device.test.done) await clean(`stop_${device.role}`, async () => {
      run(device, ['shell', 'am', 'force-stop', app], true); device.test.handle.kill();
      await until(() => device.test.done, 10_000, 'Test adb did not stop');
    });
    if (device.test) await writeFile(resolve(output, `${device.role}-instrumentation.txt`), device.test.output);
    for (const [key, value] of device.scales) await clean(`${device.role}_${key}`, () => {
      run(device, value === 'null' ? ['shell', 'settings', 'delete', 'global', key] : ['shell', 'settings', 'put', 'global', key, value]);
      assert.equal(run(device, ['shell', 'settings', 'get', 'global', key]), value);
    });
    if (device.mapped) await clean(`unmap_${device.role}`, () => run(device, ['reverse', '--remove', 'tcp:8080']));
    if (device.meter) {
      if (!evidence.network.finalObserved) evidence.network.finalObserved = {};
      evidence.network.finalObserved[device.role] = device.meter.snapshot();
      await clean(`close_meter_${device.role}`, () => device.meter.close());
    }
  }
  if (service) await clean('stop_service', async () => {
    if (!service.done) service.handle.kill('SIGKILL'); await until(() => service.done, 10_000, 'Owned service did not stop');
  });
  if (cleanupErrors.length) { evidence.completed = false; evidence.cleanupErrors = cleanupErrors; process.exitCode = 1; }
  else evidence.deviceSettingsAndMappingsRestored = true;
  await writeFile(resolve(output, 'evidence.json'), JSON.stringify(evidence, null, 2) + '\n');
}
if (evidence.completed) console.log('Independent native pair passed; original settings restored and owned service stopped.');
