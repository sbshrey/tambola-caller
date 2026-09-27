// Real public HTTPS, temporary QA profiles, no credentials in output or evidence.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const directory = 'https://raw.githubusercontent.com/sbshrey/tambola-caller/codex/public-beta-channel/server.json';
const entry = await (await fetch(`${directory}?minute=${Math.floor(Date.now() / 60000)}`, { redirect: 'error', signal: AbortSignal.timeout(15000) })).json();
assert.equal(entry.service, 'tambola-together-public-beta-v1');
assert.ok(entry.expiresAt > Date.now());
assert.match(entry.origin, /^https:\/\/[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com$/);
const guests = [];
async function request(path, method = 'GET', body, actor) {
  const response = await fetch(entry.origin + path, { method, redirect: 'error', signal: AbortSignal.timeout(15000),
    headers: { ...(body ? { 'content-type': 'application/json' } : {}), ...(actor ? { authorization: `Bearer ${actor.token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined });
  return { status: response.status, body: await response.json() };
}
async function api(...args) {
  const result = await request(...args);
  assert.ok(result.status >= 200 && result.status < 300, `Request rejected: ${result.status}, ${result.body?.code ?? 'unknown'}`);
  return result.body;
}
try {
  assert.equal((await api('/health/ready')).protocolVersion, 5);
  for (const name of ['Expanded beta QA A', 'Expanded beta QA B']) guests.push(await api('/v1/guests', 'POST', { displayName: name }));
  for (const actor of guests) {
    const grants = await Promise.all([1, 2].map(() => api('/v1/wallet/login-rewards', 'POST', undefined, actor)));
    assert.deepEqual(grants[0].wallet, grants[1].wallet);
    assert.equal(grants[0].wallet.balance, 50500);
    assert.equal(grants[0].day, 1);
    assert.equal(grants.filter(it => it.newlyCollected).length, 1);
  }
  const first = await api('/v1/matches', 'POST', { id: randomUUID(), tickets: 6, friendTable: true, rulesVersion: 2, powerUp: 'PRIZE_BOOST' }, guests[0]);
  const secondRequest = { id: randomUUID(), tickets: 2, friendTable: true, friendCode: first.snapshot.code, rulesVersion: 2, powerUp: 'TICKET_INSURANCE' };
  const second = await api('/v1/matches', 'POST', secondRequest, guests[1]);
  assert.deepEqual(second, await api('/v1/matches', 'POST', secondRequest, guests[1]));
  assert.equal(second.snapshot.protocolVersion, 5);
  assert.equal(second.snapshot.options.intervalSeconds, 10);
  assert.equal(second.snapshot.options.capacity, 50);
  assert.equal(second.snapshot.options.game.winnersPerPrize, 2);
  assert.equal(second.snapshot.coins.pool, 800);
  assert.equal(second.snapshot.coins.prizes.length, 6);
  assert.deepEqual(second.snapshot.coins.prizes.map(it => it.coins).sort((a, b) => a - b), [80, 80, 80, 80, 80, 400]);
  assert.equal(second.snapshot.coins.powerUp, 'TICKET_INSURANCE');
  assert.equal(second.snapshot.wallet.balance, 50300);
  const path = `/v1/rooms/${first.snapshot.code}`;
  for (const actor of [...guests].reverse()) {
    const latest = await api(path, 'GET', undefined, actor);
    const leave = { id: randomUUID(), expectedRevision: latest.snapshot.revision, action: { type: 'leave' } };
    const receipt = await api(`${path}/commands`, 'POST', leave, actor);
    assert.deepEqual(receipt, await api(`${path}/commands`, 'POST', leave, actor));
    assert.equal((await api('/v1/wallet', 'GET', undefined, actor)).balance, 50500);
  }
  assert.equal((await request('/v1/wallet/ad-intents', 'POST', undefined, guests[0])).body.code, 'ads_disabled');
} finally {
  for (const actor of guests) await api('/v1/guests/me/delete', 'POST', { id: randomUUID() }, actor);
}
const evidence = { passed: true, observedAt: new Date().toISOString(), origin: entry.origin,
  checks: ['concurrent 50000 starter plus day-one reward exactly once', 'two friends with rules v2 and ten-second calls',
    '50-seat capacity and two winners per category', 'six fixed category pools', 'persisted power-up selections',
    'exact purchase/leave retry and full refund', 'live ads disabled pending activation', 'QA profiles deleted'],
  scope: 'Public HTTPS from the host PC, lobby and economy checks; no physical-phone acceptance or completed round claim' };
await mkdir(resolve('.test-workspace/internet-beta'), { recursive: true });
await writeFile(resolve('.test-workspace/internet-beta/expanded-entry.json'), JSON.stringify(evidence, null, 2) + '\n');
console.log(JSON.stringify(evidence));
