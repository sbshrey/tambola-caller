// Invoked by rehearse-windows-recovery.ps1. Installed databases are never mutated.
// Raw archives, player identities, credentials, SQL and server output stay out of evidence.
import assert from 'node:assert/strict';
import https from 'node:https';
import { spawn, spawnSync } from 'node:child_process';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { createServer } from 'node:net';
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { parseRuntimeManifest } from './service-runtime.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const runId = process.env.TAMBOLA_RECOVERY_RUN_ID;
assert.match(runId || '', /^[a-f0-9]{16}$/);
const hostRoot = resolve(process.env.TAMBOLA_RECOVERY_HOST);
const directory = resolve(process.env.TAMBOLA_RECOVERY_DIRECTORY);
assert.equal(directory, resolve(hostRoot, 'recovery-drills', runId));
const evidencePath = resolve(process.env.TAMBOLA_RECOVERY_EVIDENCE);
assert.equal(evidencePath, resolve(root, '.test-workspace', `installed-recovery-${runId}.json`));
const config = JSON.parse(await readFile(resolve(hostRoot, 'host.json'), 'utf8'));
for (const path of [config.java, config.postgresBin, config.serviceLib]) assert.ok(resolve(path).startsWith(hostRoot + sep));
const admin = { user: 'tambola_test', password: process.env.TAMBOLA_TEST_DATABASE_PASSWORD };
assert.ok(admin.password);
const names = Object.fromEntries(['main', 'journal'].map(kind => [kind, `tambola_restore_${kind}_${runId}`]));
const identities = Object.fromEntries(['main_owner', 'journal_owner', 'main_app', 'journal_app'].map(kind =>
  [kind, { user: `tambola_restore_${kind}_${runId}`, password: randomBytes(32).toString('base64url') }]));
const ownedDatabases = new Set(), ownedRoles = new Set();
const hash = value => createHash('sha256').update(value).digest('hex');
function identifier(value) { assert.match(value, /^[a-z][a-z0-9_]{0,62}$/); return `"${value}"`; }
function literal(value) { assert.equal(typeof value, 'string'); return "'" + value.replaceAll("'", "''") + "'"; }
function pg(tool, database, args, identity = admin, input) {
  const result = spawnSync(resolve(config.postgresBin, tool + '.exe'), args, { windowsHide: true,
    encoding: 'utf8', timeout: 30_000, maxBuffer: 8 * 1024 * 1024, input,
    env: { ...process.env, PGHOST: '127.0.0.1', PGPORT: '55432', PGDATABASE: database,
      PGUSER: identity.user, PGPASSWORD: identity.password, PGCONNECT_TIMEOUT: '5' } });
  assert.equal(result.status, 0, 'Isolated PostgreSQL operation failed; private output omitted');
  return result.stdout.trim();
}
const sql = (database, query, identity = admin) => pg('psql', database,
  ['-X', '--no-password', '-At', '--set=ON_ERROR_STOP=1', '--file=-'], identity, query);
function createDatabase(kind) {
  sql('tambola_test', `CREATE DATABASE ${identifier(names[kind])} OWNER ${identifier(identities[kind + '_owner'].user)} TEMPLATE template0`);
  ownedDatabases.add(names[kind]);
}
function dropDatabase(name) {
  assert.ok(ownedDatabases.has(name) && Object.values(names).includes(name));
  sql('tambola_test', `DROP DATABASE ${identifier(name)} WITH (FORCE)`); ownedDatabases.delete(name);
}
function restore(kind, path) {
  pg('pg_restore', names[kind], ['--no-password', '--exit-on-error', '--no-owner', '--no-privileges',
    '--dbname=' + names[kind], path], identities[kind + '_owner']);
}
async function grant(kind) {
  const template = await readFile(resolve(root, 'server/src/main/resources/db/permissions', kind === 'main' ? 'primary.sql' : 'journal.sql'), 'utf8');
  sql(names[kind], template.replaceAll(':"database"', identifier(names[kind]))
    .replaceAll(':"runtime_role"', identifier(identities[kind + '_app'].user)), identities[kind + '_owner']);
}
async function runtime() {
  const entries = (await readdir(config.serviceLib, { withFileTypes: true })).filter(entry => entry.name.endsWith('.jar'));
  assert.ok(entries.length > 0 && entries.every(entry => entry.isFile()));
  return parseRuntimeManifest((await Promise.all(entries.map(async entry =>
    `${hash(await readFile(resolve(config.serviceLib, entry.name)))}  ${entry.name}\n`))).join(''));
}
async function installedHealth() {
  const ca = await readFile(resolve(hostRoot, 'tls-data/caddy/pki/authorities/local/root.crt'));
  return new Promise((done, reject) => {
    const request = https.get(new URL('/health/ready', config.origin), { ca, timeout: 5000 }, response => {
      let body = ''; response.on('data', part => { body += part; if (body.length > 8192) response.destroy(); });
      response.on('error', reject); response.on('end', () => {
        try { assert.equal(response.statusCode, 200); assert.equal(JSON.parse(body).status, 'ready'); done({ status: 200, checkedAt: new Date().toISOString() }); }
        catch { reject(new Error('Installed readiness check failed')); }
      });
    });
    request.on('timeout', () => request.destroy(new Error('Installed readiness timeout'))); request.on('error', reject);
  });
}
const probe = createServer().listen(0, '127.0.0.1'); await once(probe, 'listening');
const port = probe.address().port; await new Promise((done, reject) => probe.close(error => error ? reject(error) : done()));
const base = `http://127.0.0.1:${port}`;
const metricsToken = randomBytes(32).toString('base64url');
function environment(kind) {
  const main = identities['main_' + kind], journal = identities['journal_' + kind];
  const inherited = { ...process.env }; delete inherited.TAMBOLA_TEST_DATABASE_PASSWORD;
  return { ...inherited, PORT: String(port), TAMBOLA_BIND_HOST: '127.0.0.1', TAMBOLA_LOCAL_DEVELOPMENT: 'false',
    TAMBOLA_METRICS_TOKEN: metricsToken,
    TAMBOLA_DATABASE_URL: `jdbc:postgresql://127.0.0.1:55432/${names.main}`, TAMBOLA_DATABASE_USER: main.user, TAMBOLA_DATABASE_PASSWORD: main.password,
    TAMBOLA_DELETION_DATABASE_URL: `jdbc:postgresql://127.0.0.1:55432/${names.journal}`, TAMBOLA_DELETION_DATABASE_USER: journal.user, TAMBOLA_DELETION_DATABASE_PASSWORD: journal.password };
}
const javaArgs = ['-Xms64m', '-Xmx384m', '-cp', resolve(config.serviceLib, '*'), 'io.github.sbshrey.tambola.server.ServerKt'];
let child, exited, stage = 'initialize';
const evidence = { runId, startedAt: new Date().toISOString(), completed: false, cleanupComplete: false, stages: [],
  sourceCommit: config.sourceCommit, postgresRestorePort: 55432, productionRuntimeRoles: true,
  scope: 'Copied installed pre-upgrade primary archive plus a newer installed journal snapshot; isolated owner migration and restricted-role startup. Subsequent logout/deletion restore scenarios mutate only isolated copies. No live restore, reboot, physical phone or public-host acceptance.' };
function mark(value) { stage = value; evidence.stages.push(value); console.log('Recovery: ' + value); }
async function stop() {
  if (child && child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await exited; }
  child = undefined;
}
async function start() {
  assert.ok(!child);
  child = spawn(config.java, javaArgs, { cwd: root, windowsHide: true, env: environment('app'), stdio: 'ignore' });
  exited = once(child, 'exit'); exited.catch(() => {});
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    assert.ok(child.exitCode === null && child.signalCode === null, 'Restricted restored process exited before readiness');
    const ready = await fetch(base + '/health/ready', { signal: AbortSignal.timeout(1000) }).then(r => r.ok).catch(() => false);
    if (ready) { (evidence.processIds ??= []).push(child.pid); return; }
    await delay(200);
  }
  throw new Error('Restored readiness deadline exceeded');
}
async function api(path, token, body, method = 'POST', status = 200) {
  const response = await fetch(base + path, { method, signal: AbortSignal.timeout(8000),
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  assert.equal(response.status, status, 'Unexpected restored API status');
  return status === 204 ? undefined : response.json();
}
function walletRows() {
  return JSON.parse(sql(names.main, `SELECT COALESCE(json_agg(row_to_json(w) ORDER BY w.player_id),'[]') FROM (
    SELECT player_id, refill_after, (SELECT json_agg(row_to_json(l) ORDER BY l.entry_key) FROM coin_ledger l WHERE l.player_id=c.player_id) AS ledger
    FROM coin_wallets c) w`));
}
const fingerprint = rows => hash(JSON.stringify(rows));
function cursor() { return JSON.parse(sql(names.main, 'SELECT row_to_json(d) FROM deletion_recovery d')); }
function position() { return JSON.parse(sql(names.journal, 'SELECT row_to_json(d) FROM deletion_journal_identity d')); }
function caughtUp() { const c = cursor(), p = position(); assert.equal(c.journal_id, p.journal_id); assert.equal(c.applied_sequence, p.head); }
async function buyCancel(actor, count = 3) {
  const before = await api('/v1/wallet', actor.token, null, 'GET');
  const purchase = { id: randomUUID(), tickets: count };
  const purchased = await api('/v1/matches', actor.token, purchase);
  assert.equal(purchased.snapshot.wallet.balance, before.balance - count * 100);
  assert.deepEqual(await api('/v1/matches', actor.token, purchase), purchased);
  const path = '/v1/rooms/' + purchased.snapshot.code;
  const view = await api(path, actor.token, null, 'GET');
  const leave = { id: randomUUID(), expectedRevision: view.snapshot.revision, action: { type: 'leave' } };
  const refunded = await api(path + '/commands', actor.token, leave);
  assert.equal(refunded.snapshot.wallet.balance, before.balance);
  assert.deepEqual(await api(path + '/commands', actor.token, leave), refunded);
  return { purchase, purchased, path, leave, refunded };
}
try {
  mark('verify-installed-runtime-and-copied-archives');
  evidence.installedHealthBefore = await installedHealth();
  Object.assign(evidence, await runtime());
  evidence.driverSha256 = hash(await readFile(fileURLToPath(import.meta.url)));
  evidence.archives = {};
  for (const kind of ['primary', 'journal']) {
    const file = resolve(directory, kind + '.dump'), bytes = await readFile(file);
    evidence.archives[kind] = { sha256: hash(bytes), bytes: bytes.length };
    pg('pg_restore', 'tambola_test', ['--list', file]);
  }
  assert.equal(sql('tambola_test', 'SELECT rolsuper OR (rolcreatedb AND rolcreaterole) FROM pg_roles WHERE rolname=current_user'), 't');
  for (const identity of Object.values(identities)) {
    sql('tambola_test', `CREATE ROLE ${identifier(identity.user)} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD ${literal(identity.password)}`);
    ownedRoles.add(identity.user);
  }
  mark('restore-installed-primary-and-newer-journal-to-isolated-stores');
  createDatabase('main'); createDatabase('journal');
  restore('main', resolve(directory, 'primary.dump')); restore('journal', resolve(directory, 'journal.dump'));
  const oldCursor = cursor(), newPosition = position();
  assert.equal(oldCursor.journal_id, newPosition.journal_id); assert.ok(newPosition.head >= oldCursor.applied_sequence);
  evidence.installedReplay = { primaryCursor: oldCursor.applied_sequence, newerJournalHead: newPosition.head,
    primarySchemaBefore: Number(sql(names.main, 'SELECT max(version) FROM schema_migrations')) };
  const deleted = new Set(JSON.parse(sql(names.journal, "SELECT COALESCE(json_agg(player_id),'[]') FROM profile_deletions")));
  const beforeWallets = walletRows();
  const survivorWallets = beforeWallets.filter(row => !deleted.has(row.player_id));
  const revoked = JSON.parse(sql(names.journal, "SELECT COALESCE(json_agg(row_to_json(r)),'[]') FROM session_revocations r"));
  const migrations = spawnSync(config.java, [...javaArgs, '--migrate'], { cwd: root, windowsHide: true,
    env: environment('owner'), stdio: 'ignore', timeout: 30_000 });
  assert.equal(migrations.status, 0, 'Explicit owner migration failed');
  await grant('main'); await grant('journal');
  mark('restricted-startup-replays-installed-history-and-preserves-wallets');
  await start(); caughtUp();
  assert.equal(fingerprint(walletRows()), fingerprint(survivorWallets));
  for (const playerId of deleted) assert.equal(sql(names.main, `SELECT count(*) FROM guests WHERE id=${literal(playerId)}`), '0');
  for (const intent of revoked) assert.equal(sql(names.main, `SELECT count(*) FROM guests WHERE id=${literal(intent.player_id)}
    AND (expires_at>${Number(intent.revoked_at)} OR revoked_at IS DISTINCT FROM ${Number(intent.revoked_at)} OR device_key_hash IS NOT NULL)`), '0');
  evidence.installedReplay = { ...evidence.installedReplay, primarySchemaAfter: Number(sql(names.main, 'SELECT max(version) FROM schema_migrations')),
    originalWalletCount: beforeWallets.length, survivingWalletCount: survivorWallets.length, preservedWalletSha256: fingerprint(survivorWallets),
    deletionIntentsChecked: deleted.size, revocationIntentsChecked: revoked.length, replayCaughtUp: true };
  mark('prepare-isolated-wallet-receipts-and-enrolled-devices');
  const actors = [];
  for (let i = 0; i < 3; i++) {
    const actor = await api('/v1/guests', null, { displayName: 'Restore QA ' + i }, 'POST', 201);
    actor.deviceKey = randomBytes(32).toString('base64url');
    await api('/v1/guests/me/device', actor.token, { deviceKey: actor.deviceKey });
    actor.receipts = await buyCancel(actor, i + 1); actors.push(actor);
  }
  const walletBefore = walletRows();
  await stop();
  const scenarioArchive = resolve(directory, 'scenario-primary.dump');
  pg('pg_dump', names.main, ['--no-password', '--format=custom', '--no-owner', '--no-privileges', '--file=' + scenarioArchive], identities.main_owner);
  evidence.scenarioPrimarySha256 = hash(await readFile(scenarioArchive));
  await start();
  mark('commit-newer-logout-and-deletion-only-in-isolated-journal');
  await api('/v1/guests/me/logout', actors[1].token, null, 'POST', 204);
  const deletion = { id: randomUUID() };
  const deletedReceipt = await api('/v1/guests/me/delete', actors[2].token, deletion);
  await api('/v1/wallet', actors[1].token, null, 'GET', 401);
  await api('/v1/wallet', actors[2].token, null, 'GET', 401);
  const positionBeforeRestore = position();
  await stop();
  mark('restore-only-older-primary-and-prove-stale-access-is-present-before-replay');
  dropDatabase(names.main); createDatabase('main'); restore('main', scenarioArchive); await grant('main');
  for (const actor of actors.slice(1)) {
    assert.equal(sql(names.main, `SELECT count(*) FROM guests WHERE id=${literal(actor.playerId)} AND token_hash=${literal(hash(actor.token))}
      AND device_key_hash=${literal(hash(actor.deviceKey))} AND expires_at>${Date.now()}`), '1');
  }
  assert.equal(fingerprint(walletRows()), fingerprint(walletBefore));
  assert.deepEqual(position(), positionBeforeRestore);
  assert.equal(positionBeforeRestore.head - cursor().applied_sequence, 2);
  evidence.staleIdentityPositiveControl = true; evidence.journalUntouchedByPrimaryRestore = true;
  mark('restricted-replay-blocks-stale-bearer-and-device-access');
  await start(); caughtUp();
  for (const actor of actors.slice(1)) {
    await api('/v1/wallet', actor.token, null, 'GET', 401);
    await api('/v1/guests/me/session', actor.deviceKey, { expectedRevision: 0, token: randomBytes(32).toString('base64url') }, 'POST', 401);
  }
  assert.equal(sql(names.main, `SELECT count(*) FROM guests WHERE id=${literal(actors[1].playerId)}
    AND revoked_at IS NOT NULL AND expires_at=revoked_at AND device_key_hash IS NULL`), '1');
  assert.deepEqual(await api('/v1/guests/me/delete', actors[2].token, deletion), deletedReceipt);
  assert.equal(sql(names.main, `SELECT count(*) FROM guests WHERE id=${literal(actors[2].playerId)}`), '0');
  assert.equal(fingerprint(walletRows()), fingerprint(walletBefore.filter(row => row.player_id !== actors[2].playerId)));
  const actor = actors[0], receipts = actor.receipts;
  assert.deepEqual(await api('/v1/matches', actor.token, receipts.purchase), receipts.purchased);
  assert.deepEqual(await api(receipts.path + '/commands', actor.token, receipts.leave), receipts.refunded);
  assert.equal((await api('/v1/wallet', actor.token, null, 'GET')).balance, 1500);
  evidence.recoveryChecks = { bearerRejections: 2, deviceRenewalRejections: 2, deletionConfirmationReplay: true,
    deletedWalletRemoved: true, signedOutWalletRetained: true, survivingWalletAndReceiptReplay: true };
  mark('fresh-purchase-refund-and-second-restart-remain-idempotent');
  const fresh = await buyCancel(actor, 6);
  const afterPurchaseWallets = walletRows();
  await stop(); await start(); caughtUp();
  assert.equal(fingerprint(walletRows()), fingerprint(afterPurchaseWallets));
  assert.deepEqual(await api('/v1/matches', actor.token, fresh.purchase), fresh.purchased);
  assert.deepEqual(await api(fresh.path + '/commands', actor.token, fresh.leave), fresh.refunded);
  assert.equal((await api('/v1/wallet', actor.token, null, 'GET')).balance, 1500);
  evidence.recoveryChecks.freshPurchaseRefundAndRestartReplay = true;
  assert.deepEqual(await runtime(), { serviceJarSha256: evidence.serviceJarSha256,
    serviceRuntimeSha256: evidence.serviceRuntimeSha256, serviceRuntimeManifest: evidence.serviceRuntimeManifest });
  evidence.completed = true;
} catch (error) {
  evidence.failedStage = stage; evidence.failureType = error.code || error.name; process.exitCode = 1;
} finally {
  const failures = [];
  try { await stop(); } catch { failures.push('isolated-process'); }
  for (const name of [...ownedDatabases]) { try { dropDatabase(name); } catch { failures.push('isolated-database'); } }
  for (const role of [...ownedRoles]) {
    try {
      assert.ok(Object.values(identities).some(identity => identity.user === role));
      sql('tambola_test', `DROP ROLE ${identifier(role)}`); ownedRoles.delete(role);
    } catch { failures.push('isolated-role'); }
  }
  try { evidence.installedHealthAfter = await installedHealth(); } catch { failures.push('installed-readiness'); }
  evidence.cleanupComplete = failures.length === 0; if (failures.length) { evidence.cleanupErrors = failures; process.exitCode = 1; }
  evidence.finishedAt = new Date().toISOString();
  await writeFile(evidencePath, JSON.stringify(evidence, null, 2) + '\n');
  console.log(JSON.stringify({ completed: evidence.completed, cleanupComplete: evidence.cleanupComplete, failedStage: evidence.failedStage, evidencePath }));
}
