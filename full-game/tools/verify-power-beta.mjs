// Public HTTPS acceptance using owned temporary QA profiles. Never logs credentials.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { setTimeout as pause } from 'node:timers/promises';
const directory = 'https://raw.githubusercontent.com/sbshrey/tambola-caller/codex/public-beta-channel/server.json';
const entry = await (await fetch(`${directory}?minute=${Math.floor(Date.now()/60000)}`, { redirect: 'error', signal: AbortSignal.timeout(15000) })).json();
assert.equal(entry.service, 'tambola-together-public-beta-v1');
assert.ok(entry.expiresAt > Date.now());
assert.match(entry.origin, /^https:\/\/[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com$/);
const guests = [];
async function api(path, method = 'GET', body, actor, expected = 200) {
  const response = await fetch(entry.origin + path, { method, redirect: 'error', signal: AbortSignal.timeout(15000),
    headers: { ...(body ? { 'content-type': 'application/json' } : {}), ...(actor ? { authorization: `Bearer ${actor.token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined });
  const result = await response.json();
  assert.ok(expected === 200 ? response.ok : response.status === expected, `HTTP ${response.status}: ${result.code ?? 'unexpected'}`);
  return result;
}
let power;
try {
  assert.equal((await api('/health/ready')).protocolVersion, 9);
  for (const name of ['Power room QA A', 'Power room QA B']) guests.push(await api('/v1/guests', 'POST', { displayName: name }));
  const [a,b] = guests;
  let first = (await api('/v1/matches', 'POST', { id: randomUUID(), tickets: 6, friendTable: true, rulesVersion: 2, powersEnabled: true, previewPowers: true, roundSummary: true }, a)).snapshot;
  await api('/v1/matches', 'POST', { id: randomUUID(), tickets: 1, friendTable: true, friendCode: first.code, rulesVersion: 2, powersEnabled: true, previewPowers: true, roundSummary: true }, b);
  const path = `/v1/rooms/${first.code}`;
  const read = async actor => (await api(path, 'GET', undefined, actor)).snapshot;
  async function command(actor, action, fixed) {
    const request = fixed ?? { id: randomUUID(), expectedRevision: (await read(actor)).revision, action };
    return api(`${path}/commands`, 'POST', request, actor);
  }
  first = (await command(a, { type: 'start' })).snapshot;
  assert.equal(first.protocolVersion, 9);
  assert.equal(first.options.computerPlayers, 0);
  assert.equal(first.options.intervalSeconds, 8);
  const preview = first.round.powers.nextPower;
  assert.ok(['SHIELD', 'AUTO_DAB', 'PRIZE_BONUS'].includes(preview));
  assert.equal(first.round.ownTickets.flatMap(it => it.cells.filter(Boolean)).length, 90);
  const until = Date.now() + 100000;
  const marked = new Set();
  let current = first;
  while (marked.size < 5 && Date.now() < until) {
    current = await read(a);
    for (const number of current.round.called) {
      if (marked.has(number) || marked.size === 5) continue;
      const ticket = current.round.ownTickets.find(it => it.cells.includes(number));
      const action = { type: 'mark', roundId: current.round.id, ticketId: ticket.id, number };
      const request = { id: randomUUID(), expectedRevision: current.revision, action };
      const result = await command(a, action, request);
      assert.deepEqual(result, await command(a, action, request));
      marked.add(number); current = result.snapshot;
    }
    if (marked.size < 5) await pause(1500);
  }
  assert.equal(marked.size, 5);
  assert.equal(current.round.powers.correctMarks, 5);
  assert.equal(current.round.powers.inventory.length, 1);
  power = current.round.powers.inventory[0];
  assert.equal(power, preview);
  const peer = await read(b);
  assert.deepEqual(peer.round.powers.marks, {});
  assert.equal(peer.round.powers.correctMarks, 0);
  assert.equal(peer.round.ownTickets.length, 1);
  assert.equal(current.round.revealedOrder, null);
  const ticket = current.round.ownTickets[0];
  const number = ticket.cells.find(n => n && !current.round.called.includes(n));
  assert.equal((await api(`${path}/commands`, 'POST', { id: randomUUID(), expectedRevision: current.revision,
    action: { type: 'mark', roundId: current.round.id, ticketId: ticket.id, number } }, a, 422)).code, 'number_not_called');
  {
    current = (await command(a, { type: 'use_power', roundId: current.round.id, ticketId: ticket.id, power })).snapshot;
    assert.equal(current.round.powers.inventory.length, 0);
    assert.ok(current.round.powers.used[ticket.id].includes(power));
    if (power === 'AUTO_DAB') assert.equal(current.round.powers.autoUntil[ticket.id] - current.serverTime, 15000);
  }
  // A full house cannot be complete after five calls; retrying its receipt is harmless.
  current = await read(a);
  const action = { type: 'claim', roundId: current.round.id, drawIndex: current.round.called.length,
    markedNumbers: [], selection: { ticketId: ticket.id, prizeId: 'FULL_HOUSE' } };
  const request = { id: randomUUID(), expectedRevision: current.revision, action };
  const penalty = await command(a, action, request);
  assert.deepEqual(penalty, await command(a, action, request));
  assert.equal(penalty.snapshot.round.powers.notice, power === 'SHIELD' ? 'SHIELD_SAVED' : 'TICKET_DISCARDED');
  if (power === 'SHIELD') {
    current = await read(a);
    const discarded = await command(a, { ...action, drawIndex: current.round.called.length });
    assert.deepEqual(discarded.snapshot.round.powers.discarded, [ticket.id]);
  } else assert.deepEqual(penalty.snapshot.round.powers.discarded, [ticket.id]);
} finally {
  for (const actor of guests) await api('/v1/guests/me/delete', 'POST', { id: randomUUID() }, actor);
}
const evidence = { passed: true, observedAt: new Date().toISOString(), origin: entry.origin, sampledPower: power,
  checks: ['six private tickets', 'visible preview matches power granted at five authoritative dabs', 'identical mark receipts on retry',
    'peer power state stays private', 'no future draw order', 'wrong marks rejected', 'single activation including explicit shield arming',
    'false-claim receipts cannot punish twice', 'selected ticket discarded', 'QA profiles deleted'],
  scope: 'Real public HTTPS API and eight-second calls; UI and full settlement covered separately' };
await mkdir('.test-workspace/power-beta', { recursive: true });
await writeFile('.test-workspace/power-beta/public-powers.json', JSON.stringify(evidence, null, 2) + '\n');
console.log(JSON.stringify(evidence));
