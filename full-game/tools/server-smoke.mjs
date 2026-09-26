// Uses only an explicitly named local test database and kills only Java processes it starts.
// Requires :server:installDist and Node 22+. Credentials stay in process memory/environment.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { createServer } from 'node:net';
import { resolve, delimiter } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const url = process.env.TAMBOLA_TEST_DATABASE_URL || '';
assert.match(url, /^jdbc:postgresql:\/\/127\.0\.0\.1:\d+\/tambola_test$/);
assert.ok(process.env.TAMBOLA_TEST_DATABASE_USER && process.env.TAMBOLA_TEST_DATABASE_PASSWORD, 'Explicit test credentials are required');
const portProbe = createServer();
portProbe.listen(0, '127.0.0.1');
await once(portProbe, 'listening');
const port = portProbe.address().port;
await new Promise((done, reject) => portProbe.close(error => error ? reject(error) : done()));
const base = `http://127.0.0.1:${port}`;
let processHandle;
let exitPromise;

async function start() {
  const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const classpath = [resolve(root, 'server/build/install/server/lib/*')].join(delimiter);
  processHandle = spawn(java, ['-cp', classpath, 'io.github.sbshrey.tambola.server.ServerKt'], {
    cwd: root, windowsHide: true, stdio: 'ignore',
    env: { ...process.env, TAMBOLA_DATABASE_URL: url, TAMBOLA_DATABASE_USER: process.env.TAMBOLA_TEST_DATABASE_USER,
      TAMBOLA_DATABASE_PASSWORD: process.env.TAMBOLA_TEST_DATABASE_PASSWORD, TAMBOLA_BIND_HOST: '127.0.0.1', PORT: String(port),
      TAMBOLA_LOCAL_DEVELOPMENT: 'true', TAMBOLA_DELETION_DATABASE_URL: '', TAMBOLA_DELETION_DATABASE_USER: '', TAMBOLA_DELETION_DATABASE_PASSWORD: '' },
  });
  exitPromise = once(processHandle, 'exit');
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    assert.equal(processHandle.exitCode, null, 'Owned server exited before readiness');
    if (await fetch(`${base}/health/ready`, { signal: AbortSignal.timeout(1_000) }).then(r => r.ok).catch(() => false)) return;
    await delay(250);
  }
  throw new Error('Server did not become ready within 30 seconds');
}
async function stop() {
  if (processHandle && processHandle.exitCode === null) {
    processHandle.kill('SIGKILL');
    await exitPromise;
  }
  processHandle = undefined;
}
async function api(path, token, body, method = 'POST') {
  const response = await fetch(`${base}${path}`, {
    method, signal: AbortSignal.timeout(10_000),
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  assert.ok(response.ok, `${method} ${path}: HTTP ${response.status}`);
  return response.status === 204 ? undefined : response.json();
}

try {
  await start();
  const host = await api('/v1/guests', null, { displayName: 'Smoke Asha' });
  const other = await api('/v1/guests', null, { displayName: 'Smoke Bina' });
  let room = await api('/v1/rooms', host.token, { id: randomUUID(), options: { automaticCalling: false } });
  const path = `/v1/rooms/${room.snapshot.code}`;
  await api(`${path}/join`, other.token);
  async function command(actor, action) {
    room = await api(path, actor.token, null, 'GET');
    return api(`${path}/commands`, actor.token, { id: randomUUID(), expectedRevision: room.snapshot.revision, action });
  }
  await command(host, { type: 'ready', value: true });
  await command(other, { type: 'ready', value: true });
  room = await command(host, { type: 'start' });
  const draw = { id: randomUUID(), expectedRevision: room.snapshot.revision, action: { type: 'draw' } };
  const drawn = await api(`${path}/commands`, host.token, draw);
  assert.equal(drawn.snapshot.round.called.length, 1);
  assert.equal(drawn.snapshot.round.revealedOrder, null);
  await stop();
  await start();
  const recovered = await api(`${path}?after=${room.snapshot.revision}`, other.token, null, 'GET');
  assert.deepEqual(recovered.snapshot.round.called, drawn.snapshot.round.called);
  assert.equal(recovered.events[0].type, 'drawn');
  assert.ok(recovered.snapshot.round.ownTickets.every(ticket => ticket.playerId === other.playerId));
  assert.deepEqual(await api(`${path}/commands`, host.token, draw), drawn);
  room = await command(host, { type: 'end' });
  assert.equal(room.snapshot.phase, 'FINISHED');
  console.log(JSON.stringify({ result: 'passed', checks: ['real HTTP server', 'two sessions', 'private tickets', 'process kill/restart', 'durable event replay', 'idempotent retry after restart', 'round ended'] }));
} catch (error) {
  // Server log contents are deliberately excluded from terminal output.
  console.error(error.message);
  process.exitCode = 1;
} finally {
  await stop();
}
