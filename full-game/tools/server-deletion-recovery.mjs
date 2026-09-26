// Destructive operations are restricted to fresh databases created by this invocation on loopback.
// Raw sessions, SQL payloads, server logs and dumps are never included in the safe evidence file.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { serviceRuntime } from './service-runtime.mjs';
import { once } from 'node:events';
import { createServer } from 'node:net';
import { mkdir, readFile, unlink, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const jdbc = process.env.TAMBOLA_TEST_DATABASE_URL || '';
const match = jdbc.match(/^jdbc:postgresql:\/\/127\.0\.0\.1:(\d+)\/tambola_test$/);
assert.ok(match, 'Use the explicit isolated loopback tambola_test database');
const user = process.env.TAMBOLA_TEST_DATABASE_USER, password = process.env.TAMBOLA_TEST_DATABASE_PASSWORD;
assert.ok(user && password && process.env.TAMBOLA_PG_BIN);
const suffix = process.platform === 'win32' ? '.exe' : '';
const pgEnv = { ...process.env, PGHOST: '127.0.0.1', PGPORT: match[1], PGDATABASE: 'tambola_test', PGUSER: user, PGPASSWORD: password, PGCONNECT_TIMEOUT: '5' };
function pg(tool, args) {
  const result = spawnSync(resolve(process.env.TAMBOLA_PG_BIN, tool + suffix), args, { env: pgEnv, windowsHide: true, encoding: 'utf8', timeout: 30_000, maxBuffer: 1024 * 1024 });
  assert.equal(result.status, 0, `${tool} failed; SQL, credentials and tool output omitted`);
  return result.stdout.trim();
}
const sql = (database, query) => pg('psql', ['--no-password', '-At', '--set=ON_ERROR_STOP=1', '--dbname=' + database, '--command=' + query]);
assert.equal(sql('tambola_test', 'SELECT rolsuper OR rolcreatedb FROM pg_roles WHERE rolname = current_user'), 't', 'The isolated fixture role needs CREATEDB');
const runId = randomUUID().replaceAll('-', '').slice(0, 16);
const names = Object.fromEntries(['main', 'journal', 'wrong'].map(kind => [kind, `tambola_recovery_${kind}_${runId}`]));
const owned = new Set();
function ownedName(name) { assert.ok(Object.values(names).includes(name) && /^tambola_recovery_(main|journal|wrong)_[a-f0-9]{16}$/.test(name)); }
function createDatabase(name) { ownedName(name); sql('tambola_test', `CREATE DATABASE ${name} TEMPLATE template0`); owned.add(name); }
function dropDatabase(name) { ownedName(name); assert.ok(owned.has(name)); sql('tambola_test', `DROP DATABASE ${name} WITH (FORCE)`); owned.delete(name); }
const directory = resolve(root, '.test-workspace', `deletion-process-${runId}`);
await mkdir(resolve(root, '.test-workspace'), { recursive: true });
await mkdir(directory, { recursive: false });
const dump = resolve(directory, 'primary.dump');
const probe = createServer().listen(0, '127.0.0.1'); await once(probe, 'listening');
const port = probe.address().port; await new Promise((done, reject) => probe.close(error => error ? reject(error) : done()));
const base = `http://127.0.0.1:${port}`;
let child, exited, childOutput = '', stage = 'initialization';
const evidence = { runId, completed: false, stages: [], cleanupComplete: false, scope: 'Real Java processes and primary-only logical database restore on one isolated PostgreSQL instance; no hosted PITR or independent infrastructure acceptance.' };
const mark = value => { stage = value; evidence.stages.push(value); };
async function stop() {
  if (child && child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await exited; }
  child = undefined;
}
async function start(journalDatabase, expectRejected = false) {
  assert.ok(!child);
  childOutput = '';
  const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin', 'java' + suffix) : 'java';
  child = spawn(java, ['-Xms128m', '-Xmx384m', '-cp', resolve(root, 'server/build/install/server/lib/*'), 'io.github.sbshrey.tambola.server.ServerKt'], {
    cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, PORT: String(port), TAMBOLA_BIND_HOST: '127.0.0.1', TAMBOLA_LOCAL_DEVELOPMENT: 'true',
      TAMBOLA_DATABASE_URL: jdbc.replace('/tambola_test', '/' + names.main), TAMBOLA_DATABASE_USER: user, TAMBOLA_DATABASE_PASSWORD: password,
      TAMBOLA_DELETION_DATABASE_URL: jdbc.replace('/tambola_test', '/' + journalDatabase), TAMBOLA_DELETION_DATABASE_USER: user, TAMBOLA_DELETION_DATABASE_PASSWORD: password },
  });
  const pid = child.pid;
  const capture = data => { if (childOutput.length < 65_536) childOutput += data.toString().slice(0, 65_536 - childOutput.length); };
  child.stdout.on('data', capture); child.stderr.on('data', capture);
  exited = once(child, 'exit'); exited.catch(() => {});
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null || child.signalCode !== null) {
      assert.ok(expectRejected && child.exitCode !== 0 && childOutput.includes('Primary database belongs to another deletion journal'), 'Unexpected server startup failure; output omitted');
      const reachable = await fetch(base + '/health/ready', { signal: AbortSignal.timeout(500) }).then(() => true).catch(() => false);
      assert.equal(reachable, false, 'Rejected recovery must not leave an HTTP listener');
      child = undefined;
      return pid;
    }
    const ready = await fetch(base + '/health/ready', { signal: AbortSignal.timeout(1000) }).then(response => response.ok).catch(() => false);
    if (ready) { assert.equal(expectRejected, false, 'Wrong journal was accepted'); return pid; }
    await delay(150);
  }
  throw new Error('Server startup deadline exceeded');
}
async function api(path, token, body, method = 'POST', status = 200) {
  const response = await fetch(base + path, { method, signal: AbortSignal.timeout(15_000),
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined });
  assert.equal(response.status, status, 'Unexpected HTTP response during ' + stage);
  return response.json();
}
try {
  Object.assign(evidence, await serviceRuntime(root));
  for (const name of Object.values(names)) createDatabase(name);
  mark('start-with-independent-journal'); evidence.seedPid = await start(names.journal);
  const host = await api('/v1/guests', null, { displayName: 'Recovery fixture host', avatar: 6 }, 'POST', 201);
  const peer = await api('/v1/guests', null, { displayName: 'Recovery fixture peer', avatar: 3 }, 'POST', 201);
  const room = await api('/v1/rooms', host.token, { id: randomUUID(), options: { automaticCalling: false } }, 'POST', 201);
  const path = '/v1/rooms/' + room.snapshot.code;
  await api(path + '/join', peer.token);
  async function command(actor, action) {
    const current = await api(path, actor.token, null, 'GET');
    return api(path + '/commands', actor.token, { id: randomUUID(), expectedRevision: current.snapshot.revision, action });
  }
  await command(host, { type: 'ready', value: true }); await command(peer, { type: 'ready', value: true });
  await command(host, { type: 'start' }); await command(host, { type: 'draw' });
  const before = (await api(path, peer.token, null, 'GET')).snapshot;
  mark('snapshot-before-deletion');
  pg('pg_dump', ['--no-password', '--format=custom', '--no-owner', '--no-privileges', '--dbname=' + names.main, '--file=' + dump]);
  evidence.backupBytes = (await readFile(dump)).length;
  mark('confirm-deletion-after-snapshot');
  const request = { id: randomUUID() };
  const receipt = await api('/v1/guests/me/delete', host.token, request);
  await api(path, host.token, null, 'GET', 401);
  assert.equal(sql(names.journal, 'SELECT head FROM deletion_journal_identity'), '1');
  await stop();
  mark('restore-primary-only');
  dropDatabase(names.main); createDatabase(names.main);
  pg('pg_restore', ['--no-password', '--clean', '--if-exists', '--single-transaction', '--no-owner', '--no-privileges', '--dbname=' + names.main, dump]);
  assert.match(host.playerId, /^[a-f0-9-]{36}$/);
  assert.equal(sql(names.main, `SELECT count(*) FROM guests WHERE id = '${host.playerId}'`), '1', 'Positive control must restore the deleted identity');
  evidence.restoredIdentityBeforeReplay = true;
  mark('reject-wrong-journal-before-listening');
  await start(names.wrong, true); evidence.wrongJournalRejectedBeforeListening = true;
  mark('restart-and-replay-current-journal'); evidence.recoveredPid = await start(names.journal);
  assert.notEqual(evidence.seedPid, evidence.recoveredPid);
  await api(path, host.token, null, 'GET', 401);
  const after = (await api(path, peer.token, null, 'GET')).snapshot;
  assert.equal(after.hostId, peer.playerId);
  assert.equal(after.members.some(member => member.playerId === host.playerId), false);
  assert.equal(after.round.players.find(player => player.id === host.playerId).name, 'Deleted player');
  assert.equal(after.round.players.find(player => player.id === host.playerId).avatar, 0);
  for (const key of ['called', 'ownTickets', 'scores', 'drawCommitment']) assert.deepEqual(after.round[key], before.round[key]);
  assert.deepEqual(await api('/v1/guests/me/delete', host.token, request), receipt);
  assert.equal((await command(peer, { type: 'draw' })).snapshot.round.called.length, 2);
  assert.equal(sql(names.main, 'SELECT applied_sequence FROM deletion_recovery'), '1');
  assert.equal(sql(names.main, `SELECT count(*) FROM guests WHERE id = '${host.playerId}'`), '0');
  assert.deepEqual(await serviceRuntime(root), { serviceJarSha256: evidence.serviceJarSha256,
    serviceRuntimeSha256: evidence.serviceRuntimeSha256, serviceRuntimeManifest: evidence.serviceRuntimeManifest });
  evidence.completed = true;
} catch (error) {
  evidence.failedStage = stage; evidence.failureType = error.code || error.name;
  process.exitCode = 1;
} finally {
  const cleanupErrors = [];
  try { await stop(); } catch { cleanupErrors.push('owned-service'); }
  for (const name of [...owned]) { try { dropDatabase(name); } catch { cleanupErrors.push('owned-database'); } }
  try { await unlink(dump); } catch (error) { if (error.code !== 'ENOENT') cleanupErrors.push('fixture-dump'); }
  evidence.cleanupComplete = cleanupErrors.length === 0;
  if (cleanupErrors.length) { evidence.cleanupErrors = cleanupErrors; process.exitCode = 1; }
  await writeFile(resolve(directory, 'evidence.json'), JSON.stringify(evidence, null, 2) + '\n');
  console.log(JSON.stringify({ evidence: resolve(directory, 'evidence.json'), ...evidence, serviceRuntimeManifest: undefined }, null, 2));
}
