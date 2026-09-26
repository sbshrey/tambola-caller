// Installed private host, verified TLS, temporary QA profiles only. No tokens enter evidence.
import assert from 'node:assert/strict';
import https from 'node:https';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
const root = join(process.env.LOCALAPPDATA, 'TambolaTogetherHost');
const config = JSON.parse(await readFile(join(root, 'host.json'), 'utf8'));
assert.match(config.origin, /^https:\/\/192\.168\.\d+\.\d+:8443$/);
const ca = await readFile(join(root, 'tls-data/caddy/pki/authorities/local/root.crt'));
const guests = [];
const checks = [];
const timings = [];
async function request(path, method = 'GET', body, actor, trust = true) {
  const started = performance.now();
  return new Promise((done, reject) => {
    const payload = body === undefined ? undefined : JSON.stringify(body);
    const req = https.request(new URL(path, config.origin), { method, ca: trust ? ca : undefined, timeout: 8000,
      headers: { ...(payload ? { 'content-type': 'application/json', 'content-length': Buffer.byteLength(payload) } : {}),
        ...(actor ? { authorization: `Bearer ${actor.token}` } : {}) } }, response => {
      let text = ''; response.on('data', part => { text += part; if (text.length > 524288) response.destroy(); });
      response.on('error', reject);
      response.on('end', () => {
        timings.push({ operation: path.replace(/[A-HJ-NP-Z2-9]{8}/g, 'ROOM'), milliseconds: Math.round(performance.now() - started), status: response.statusCode });
        try { done({ status: response.statusCode, body: text ? JSON.parse(text) : null }); } catch (error) { reject(error); }
      });
    });
    req.on('timeout', () => req.destroy(new Error('request_timeout'))); req.on('error', reject);
    if (payload) req.write(payload); req.end();
  });
}
async function api(path, method, body, actor) {
  const result = await request(path, method, body, actor);
  assert.ok(result.status >= 200 && result.status < 300, `API ${result.status}: ${result.body?.code ?? 'unknown'}`);
  return result.body;
}
try {
  assert.equal((await request('/health/ready')).status, 200);
  await assert.rejects(() => request('/health/ready', 'GET', undefined, undefined, false));
  assert.equal((await request('/internal/metrics')).status, 404);
  assert.equal((await request('/v1/matches', 'POST', { id: randomUUID(), tickets: 1 })).status, 401);
  checks.push('verified TLS; untrusted certificate rejected; internal routes hidden; unauthenticated purchase refused');
  for (const name of ['Coin WiFi QA One', 'Coin WiFi QA Two']) guests.push(await api('/v1/guests', 'POST', { displayName: name }));
  for (const actor of guests) assert.equal((await api('/v1/wallet', 'GET', undefined, actor)).balance, 1500);
  const purchase = { id: randomUUID(), tickets: 6 };
  const first = await api('/v1/matches', 'POST', purchase, guests[0]);
  assert.equal(first.snapshot.protocolVersion, 4);
  assert.equal(first.snapshot.wallet.balance, 900);
  assert.equal(first.snapshot.coins.ownTickets, 6);
  assert.deepEqual(await api('/v1/matches', 'POST', purchase, guests[0]), first);
  const second = await api('/v1/matches', 'POST', { id: randomUUID(), tickets: 2 }, guests[1]);
  assert.equal(second.snapshot.roomId, first.snapshot.roomId);
  assert.equal(second.snapshot.coins.pool, 1400);
  assert.equal(second.snapshot.coins.prizes.length, 7);
  assert.equal(second.snapshot.wallet.balance, 1300);
  assert.equal(second.snapshot.options.computerPlayers, 2);
  checks.push('two authenticated purchases; one shared lobby; 6 and 2 tickets; 1400 pool; seven prizes; computer seats explicit');
  const path = `/v1/rooms/${first.snapshot.code}`;
  for (const actor of [...guests].reverse()) {
    const latest = await api(path, 'GET', undefined, actor);
    const leave = { id: randomUUID(), expectedRevision: latest.snapshot.revision, action: { type: 'leave' } };
    const returned = await api(`${path}/commands`, 'POST', leave, actor);
    assert.equal(returned.snapshot.wallet.balance, 1500);
    assert.deepEqual(await api(`${path}/commands`, 'POST', leave, actor), returned);
    assert.equal((await api('/v1/wallet', 'GET', undefined, actor)).balance, 1500);
  }
  checks.push('both cancellations refunded once; exact receipt retries including the closed final lobby');
} finally {
  for (const actor of guests) await api('/v1/guests/me/delete', 'POST', { id: randomUUID() }, actor);
}
checks.push('temporary QA profiles deleted');
const evidence = { result: 'passed', observedAt: new Date().toISOString(), sourceCommit: config.sourceCommit, origin: config.origin,
  checks, timings, scope: 'PC HTTPS transactions against installed restricted-role service; not physical phone Wi-Fi' };
await mkdir(resolve('.test-workspace'), { recursive: true });
await writeFile(resolve('.test-workspace/coin-host-smoke.json'), JSON.stringify(evidence, null, 2) + '\n');
console.log(JSON.stringify(evidence));
