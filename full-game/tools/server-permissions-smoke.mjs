// Real process proof with four fresh login roles and two fresh, owned loopback databases.
// Role passwords are random, stay in memory/environment/stdin, and are never printed or exported.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { randomUUID, randomBytes } from 'node:crypto';
import { once } from 'node:events';
import { createServer } from 'node:net';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { serviceRuntime, parseRuntimeManifest } from './service-runtime.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const jdbc = process.env.TAMBOLA_TEST_DATABASE_URL || '';
const match = jdbc.match(/^jdbc:postgresql:\/\/127\.0\.0\.1:(\d+)\/tambola_test$/);
assert.ok(match && process.env.TAMBOLA_TEST_DATABASE_USER && process.env.TAMBOLA_TEST_DATABASE_PASSWORD && process.env.TAMBOLA_PG_BIN);
const admin = { user: process.env.TAMBOLA_TEST_DATABASE_USER, password: process.env.TAMBOLA_TEST_DATABASE_PASSWORD };
const runId = randomUUID().replaceAll('-', '').slice(0, 16);
const metricsToken = randomBytes(32).toString('base64url');
const serviceImage = process.env.TAMBOLA_TEST_SERVICE_IMAGE;
if (serviceImage) assert.match(serviceImage, /^sha256:[a-f0-9]{64}$/);
const names = { main: `tambola_roles_main_${runId}`, journal: `tambola_roles_journal_${runId}` };
const identities = Object.fromEntries(['main_owner', 'journal_owner', 'main_app', 'journal_app'].map(kind => [kind,
  { user: `tambola_roles_${kind}_${runId}`, password: randomBytes(32).toString('base64url') }]));
const ownedRoles = new Set(), ownedDatabases = new Set();
function identifier(name) { assert.match(name, /^[a-z][a-z0-9_]{0,62}$/); return `"${name}"`; }
const suffix = process.platform === 'win32' ? '.exe' : '';
function sql(database, query, identity = admin, expectError) {
  const result = spawnSync(resolve(process.env.TAMBOLA_PG_BIN, 'psql' + suffix),
    ['--no-password', '-At', '--set=ON_ERROR_STOP=1', '--set=VERBOSITY=sqlstate', '--dbname=' + database, '--file=-'], {
      windowsHide: true, encoding: 'utf8', timeout: 15_000, maxBuffer: 1024 * 1024, input: query,
      env: { ...process.env, PGHOST: '127.0.0.1', PGPORT: match[1], PGUSER: identity.user, PGPASSWORD: identity.password, PGCONNECT_TIMEOUT: '5' },
    });
  if (expectError) { assert.notEqual(result.status, 0); assert.ok(result.stderr.includes(expectError), 'Wrong denial code; output omitted'); }
  else assert.equal(result.status, 0, 'Fixture SQL failed; SQL and output omitted');
  return result.stdout.trim();
}
await mkdir(resolve(root, '.test-workspace'), { recursive: true });
const directory = resolve(root, '.test-workspace', `permissions-${runId}`); await mkdir(directory, { recursive: false });
const probe = createServer().listen(0, '127.0.0.1'); await once(probe, 'listening'); const port = probe.address().port;
await new Promise((done, reject) => probe.close(error => error ? reject(error) : done()));
const base = `http://127.0.0.1:${port}`;
const java = resolve(process.env.JAVA_HOME, 'bin', 'java' + suffix);
const javaArgs = ['-Xms64m', '-Xmx384m', '-cp', resolve(root, 'server/build/install/server/lib') + '/*', 'io.github.sbshrey.tambola.server.ServerKt'];
function environment(kind) {
  const main = identities[`main_${kind}`], journal = identities[`journal_${kind}`];
  return { ...process.env, PORT: String(port), TAMBOLA_BIND_HOST: '127.0.0.1', TAMBOLA_LOCAL_DEVELOPMENT: 'false', TAMBOLA_METRICS_TOKEN: metricsToken,
    TAMBOLA_DATABASE_URL: jdbc.replace('/tambola_test', '/' + names.main), TAMBOLA_DATABASE_USER: main.user, TAMBOLA_DATABASE_PASSWORD: main.password,
    TAMBOLA_DELETION_DATABASE_URL: jdbc.replace('/tambola_test', '/' + names.journal), TAMBOLA_DELETION_DATABASE_USER: journal.user, TAMBOLA_DELETION_DATABASE_PASSWORD: journal.password };
}
let child, exited, childContainer, containerSequence = 0, stage = 'initialize', output = '';
const ownedContainers = new Set();
const evidence = { runId, completed: false, cleanupComplete: false, stages: [], scope: 'Real Java processes with separate non-superuser migration owners and restricted runtime login roles on isolated PostgreSQL; no hosted credential/backup acceptance.' };
function serviceCommand(kind, extra = []) {
  const env = environment(kind);
  if (!serviceImage) return { executable: java, args: [...javaArgs, ...extra], env };
  childContainer = `tambola-roles-${runId}-${++containerSequence}`; ownedContainers.add(childContainer);
  Object.assign(env, { PORT: '8080', TAMBOLA_BIND_HOST: '0.0.0.0', TAMBOLA_TLS_PROXY: 'true',
    TAMBOLA_DATABASE_URL: env.TAMBOLA_DATABASE_URL.replace('127.0.0.1', 'host.docker.internal'),
    TAMBOLA_DELETION_DATABASE_URL: env.TAMBOLA_DELETION_DATABASE_URL.replace('127.0.0.1', 'host.docker.internal') });
  const keys = ['PORT', 'TAMBOLA_BIND_HOST', 'TAMBOLA_TLS_PROXY', 'TAMBOLA_LOCAL_DEVELOPMENT', 'TAMBOLA_METRICS_TOKEN',
    'TAMBOLA_DATABASE_URL', 'TAMBOLA_DATABASE_USER', 'TAMBOLA_DATABASE_PASSWORD',
    'TAMBOLA_DELETION_DATABASE_URL', 'TAMBOLA_DELETION_DATABASE_USER', 'TAMBOLA_DELETION_DATABASE_PASSWORD'];
  return { executable: 'docker', env, args: ['run', '--rm', '--name', childContainer, '--label', `tambola.test.run=${runId}`,
    '--read-only', '--tmpfs', '/tmp:rw,noexec,nosuid,size=32m', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges',
    '--memory', '512m', '--cpus', '2', '-p', `127.0.0.1:${port}:8080`,
    ...(process.platform === 'win32' ? [] : ['--add-host', 'host.docker.internal:host-gateway']),
    ...keys.flatMap(key => ['--env', key]), serviceImage, ...extra] };
}
function docker(args) {
  const result = spawnSync('docker', args, { windowsHide: true, encoding: 'utf8', timeout: 20_000 });
  assert.equal(result.status, 0, 'Container operation failed; diagnostics omitted'); return result.stdout.trim();
}
function mark(value) { stage = value; evidence.stages.push(value); }
async function stop(graceful = false) {
  if (child && child.exitCode === null && child.signalCode === null) {
    if (serviceImage) docker(graceful ? ['stop', '--time', '10', childContainer] : ['kill', '--signal=KILL', childContainer]);
    else child.kill('SIGKILL');
    await exited;
    if (graceful && serviceImage) { assert.ok([0, 143].includes(child.exitCode)); evidence.gracefulStopExitCode = child.exitCode; }
  }
  child = undefined;
}
async function start(kind, rejected) {
  assert.ok(!child); output = '';
  const invocation = serviceCommand(kind);
  child = spawn(invocation.executable, invocation.args, { cwd: root, windowsHide: true, env: invocation.env, stdio: ['ignore', 'pipe', 'pipe'] });
  const pid = child.pid;
  const capture = value => { if (output.length < 65_536) output += value.toString().slice(0, 65_536 - output.length); };
  child.stdout.on('data', capture); child.stderr.on('data', capture); exited = once(child, 'exit'); exited.catch(() => {});
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null || child.signalCode !== null) {
      assert.ok(rejected && child.exitCode !== 0 && output.includes(rejected), 'Unexpected startup failure; output omitted');
      assert.equal(await fetch(base + '/health/live', { signal: AbortSignal.timeout(500) }).then(() => true).catch(() => false), false);
      child = undefined; return pid;
    }
    const ready = await fetch(base + '/health/ready', { signal: AbortSignal.timeout(1000) }).then(response => response.ok).catch(() => false);
    if (ready) {
      assert.ok(!rejected, 'Unsafe credentials were accepted');
      if (serviceImage) {
        const fields = docker(['inspect', '--format', '{{.Id}} {{.Config.User}} {{.HostConfig.ReadonlyRootfs}} {{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}', childContainer]).split(' ');
        assert.deepEqual(fields.slice(1), ['10001:10001', 'true', '536870912', '2000000000']);
        (evidence.containerInstanceIds ??= []).push(fields[0]);
        docker(['exec', childContainer, 'java', '-Xmx32m', '-cp', '/app/lib/*', 'io.github.sbshrey.tambola.server.HealthProbe', 'ready']);
      }
      return pid;
    }
    await delay(150);
  }
  throw new Error('Startup deadline exceeded');
}
async function api(path, token, body, method = 'POST', status = 200) {
  const response = await fetch(base + path, { method, signal: AbortSignal.timeout(15_000),
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  assert.equal(response.status, status, 'Unexpected HTTP status in ' + stage); return status === 204 ? undefined : response.json();
}
try {
  Object.assign(evidence, await serviceRuntime(root));
  if (serviceImage) {
    evidence.serviceImage = serviceImage;
    const imageRuntime = parseRuntimeManifest(docker(['run', '--rm', '--network=none', '--read-only', '--cap-drop=ALL', '--entrypoint', 'sh', serviceImage,
      '-c', 'sha256sum /app/lib/*.jar']));
    assert.equal(imageRuntime.serviceRuntimeSha256, evidence.serviceRuntimeSha256);
    evidence.imageJarSha256 = imageRuntime.serviceJarSha256;
    evidence.imageRuntimeSha256 = imageRuntime.serviceRuntimeSha256;
  }
  for (const identity of Object.values(identities)) {
    sql('tambola_test', `CREATE ROLE ${identifier(identity.user)} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '${identity.password}';`);
    ownedRoles.add(identity.user);
  }
  for (const kind of ['main', 'journal']) {
    sql('tambola_test', `CREATE DATABASE ${identifier(names[kind])} OWNER ${identifier(identities[`${kind}_owner`].user)} TEMPLATE template0;`);
    ownedDatabases.add(names[kind]);
  }
  mark('unmigrated-runtime-rejected-before-listening'); await start('app', 'journal_migrations');
  assert.equal(sql(names.journal, "SELECT count(*) FROM pg_tables WHERE schemaname='public'"), '0');
  mark('explicit-migration-job-with-owner-credentials');
  const migration = serviceCommand('owner', ['--migrate']);
  const migrated = spawnSync(migration.executable, migration.args, { cwd: root, windowsHide: true, env: migration.env, stdio: 'ignore', timeout: 30_000 });
  assert.equal(migrated.status, 0, 'Migration command failed');
  assert.equal(await fetch(base + '/health/live', { signal: AbortSignal.timeout(500) }).then(() => true).catch(() => false), false);
  for (const kind of ['main', 'journal']) {
    const template = await readFile(resolve(root, 'server/src/main/resources/db/permissions', kind === 'main' ? 'primary.sql' : 'journal.sql'), 'utf8');
    sql(names[kind], template.replaceAll(':"database"', identifier(names[kind])).replaceAll(':"runtime_role"', identifier(identities[`${kind}_app`].user)), identities[`${kind}_owner`]);
  }
  mark('owner-credentials-rejected-for-serving'); await start('owner', 'Runtime database credentials');
  mark('excessive-column-grant-rejected');
  sql(names.journal, `GRANT UPDATE (player_id) ON profile_deletions TO ${identifier(identities.journal_app.user)}`, identities.journal_owner);
  await start('app', 'Runtime credentials');
  sql(names.journal, `REVOKE UPDATE (player_id) ON profile_deletions FROM ${identifier(identities.journal_app.user)}`, identities.journal_owner);
  mark('restricted-runtime-gameplay'); evidence.firstPid = await start('app');
  const host = await api('/v1/guests', null, { displayName: 'Permissions host', avatar: 5 }, 'POST', 201);
  const peer = await api('/v1/guests', null, { displayName: 'Permissions peer', avatar: 2 }, 'POST', 201);
  const room = await api('/v1/rooms', host.token, { id: randomUUID(), options: { automaticCalling: false } }, 'POST', 201);
  const path = '/v1/rooms/' + room.snapshot.code;
  await api(path + '/join', peer.token);
  async function command(player, action) { const view = await api(path, player.token, null, 'GET'); return api(path + '/commands', player.token, { id: randomUUID(), expectedRevision: view.snapshot.revision, action }); }
  await command(host, { type: 'ready', value: true }); await command(peer, { type: 'ready', value: true });
  await command(host, { type: 'start' }); await command(host, { type: 'draw' });
  const before = (await api(path, peer.token, null, 'GET')).snapshot;
  mark('restricted-runtime-restart-and-deletion'); await stop(); evidence.restartedPid = await start('app');
  assert.notEqual(evidence.firstPid, evidence.restartedPid);
  const deletion = { id: randomUUID() }; const receipt = await api('/v1/guests/me/delete', host.token, deletion);
  assert.deepEqual(await api('/v1/guests/me/delete', host.token, deletion), receipt);
  await api(path, host.token, null, 'GET', 401);
  const after = (await api(path, peer.token, null, 'GET')).snapshot;
  assert.equal(after.hostId, peer.playerId); assert.deepEqual(after.round.called, before.round.called); assert.deepEqual(after.round.ownTickets, before.round.ownTickets);
  assert.equal(after.round.players.find(player => player.id === host.playerId).name, 'Deleted player');
  assert.equal((await command(peer, { type: 'draw' })).snapshot.round.called.length, 2);
  await command(peer, { type: 'end' });
  mark('restricted-runtime-durable-logout');
  const signedOut = await api('/v1/guests', null, { displayName: 'Logout QA' }, 'POST', 201);
  const deviceKey = randomBytes(32).toString('base64url');
  await api('/v1/guests/me/device', signedOut.token, { deviceKey });
  await api('/v1/guests/me/logout', signedOut.token, null, 'POST', 204);
  await api('/v1/wallet', signedOut.token, null, 'GET', 401);
  await api('/v1/guests/me/session', deviceKey, { expectedRevision: 0, token: randomBytes(32).toString('base64url') }, 'POST', 401);
  assert.equal(sql(names.journal, 'SELECT count(*) FROM session_revocations'), '1');
  evidence.logoutJournaled = true;
  mark('denied-destructive-database-operations');
  for (const query of ['DELETE FROM profile_deletions', 'UPDATE profile_deletions SET player_id=player_id', 'TRUNCATE profile_deletions', 'ALTER TABLE profile_deletions ADD COLUMN forbidden text'])
    sql(names.journal, query, identities.journal_app, '42501');
  for (const query of ['DELETE FROM session_revocations', 'UPDATE session_revocations SET revoked_at=0', 'TRUNCATE session_revocations', 'ALTER TABLE session_revocations ADD COLUMN forbidden text'])
    sql(names.journal, query, identities.journal_app, '42501');
  sql(names.journal, 'UPDATE deletion_journal_identity SET head=0', identities.journal_app, '23514');
  for (const query of ['CREATE TEMP TABLE guests(id text)', 'UPDATE schema_migrations SET checksum=checksum', 'TRUNCATE rooms'])
    sql(names.main, query, identities.main_app, '42501');
  assert.equal(sql(names.journal, 'SELECT count(*) FROM profile_deletions'), '1');
  mark('protected-metrics-and-worker-failure-recovery');
  async function scrape(token = metricsToken) {
    const response = await fetch(base + '/internal/metrics', { headers: token ? { Authorization: `Bearer ${token}` } : {}, signal: AbortSignal.timeout(5000) });
    return { response, body: await response.text() };
  }
  assert.equal((await scrape(null)).response.status, 401);
  assert.equal((await scrape(host.token)).response.status, 401);
  async function snapshot() {
    const { response, body } = await scrape(); assert.equal(response.status, 200);
    assert.ok(response.headers.get('content-type').includes('version=0.0.4'));
    for (const value of [metricsToken, host.token, peer.token, host.playerId, peer.playerId, signedOut.token, signedOut.playerId, deviceKey, room.snapshot.code, 'Permissions host', 'Permissions peer'])
      assert.ok(!body.includes(value), 'Metrics exposed fixture identity; content omitted');
    return body;
  }
  const healthyMetrics = await snapshot(); assert.ok(healthyMetrics.includes('tambola_worker_ready 1'));
  // Revoke after startup to model a worker-only fault while SELECT 1 still succeeds.
  sql(names.main, `REVOKE SELECT ON rooms FROM ${identifier(identities.main_app.user)}`, identities.main_owner);
  async function awaitReady(status) {
    const deadline = Date.now() + 15_000;
    while (Date.now() < deadline) {
      if ((await fetch(base + '/health/ready', { signal: AbortSignal.timeout(2000) })).status === status) return;
      await delay(150);
    }
    throw new Error('Readiness transition deadline exceeded');
  }
  await awaitReady(503);
  assert.equal((await fetch(base + '/health/live')).status, 200);
  const failedMetrics = await snapshot(); assert.ok(failedMetrics.includes('tambola_worker_ready 0'));
  assert.match(failedMetrics, /tambola_worker_step_duration_seconds_count\{step="tick",outcome="failure"\} [1-9]/);
  sql(names.main, `GRANT SELECT ON rooms TO ${identifier(identities.main_app.user)}`, identities.main_owner);
  await awaitReady(200);
  const recoveredMetrics = await snapshot(); assert.ok(recoveredMetrics.includes('tambola_worker_ready 1'));
  await writeFile(resolve(directory, 'metrics.prom'), recoveredMetrics);
  evidence.metricsProtected = true; evidence.workerFailureRecovered = true;
  if (serviceImage) {
    mark('container-graceful-shutdown'); await stop(true);
    assert.equal(new Set(evidence.containerInstanceIds).size, 2);
  }
  assert.deepEqual(await serviceRuntime(root), { serviceJarSha256: evidence.serviceJarSha256,
    serviceRuntimeSha256: evidence.serviceRuntimeSha256, serviceRuntimeManifest: evidence.serviceRuntimeManifest });
  evidence.completed = true;
} catch (error) {
  evidence.failedStage = stage; evidence.failureType = error.code || error.name; process.exitCode = 1;
} finally {
  const failures = [];
  try { await stop(); } catch { failures.push('owned-server'); }
  if (serviceImage) for (const name of ownedContainers) {
    try {
      const live = docker(['ps', '-aq', '--filter', `name=^/${name}$`]);
      if (live) { assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "tambola.test.run"}}', name]), runId); docker(['rm', '-f', name]); }
    } catch { failures.push('owned-container'); }
  }
  for (const name of [...ownedDatabases]) { try { assert.ok(Object.values(names).includes(name)); sql('tambola_test', `DROP DATABASE ${identifier(name)} WITH (FORCE)`); ownedDatabases.delete(name); } catch { failures.push('owned-database'); } }
  for (const role of [...ownedRoles]) { try { assert.ok(Object.values(identities).some(identity => identity.user === role)); sql('tambola_test', `DROP ROLE ${identifier(role)}`); ownedRoles.delete(role); } catch { failures.push('owned-role'); } }
  evidence.cleanupComplete = failures.length === 0; if (failures.length) { evidence.cleanupErrors = failures; process.exitCode = 1; }
  await writeFile(resolve(directory, 'evidence.json'), JSON.stringify(evidence, null, 2) + '\n');
  console.log(JSON.stringify({ evidence: resolve(directory, 'evidence.json'), ...evidence, serviceRuntimeManifest: undefined }, null, 2));
}
