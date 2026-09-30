// Permanent public origin, disposable QA profiles, no credentials in evidence or logs.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
const origin = 'https://play.thefinxperts.com';
const actors = [];
const checks = [];
async function request(path, actor, body) {
  const response = await fetch(origin + path, { method: body === undefined ? 'GET' : 'POST',
    headers: { ...(actor ? { authorization: `Bearer ${actor.token}` } : {}), ...(body === undefined ? {} : { 'content-type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) });
  return { status: response.status, body: await response.json() };
}
async function api(path, actor, body) {
  const result = await request(path, actor, body);
  assert.ok(result.status >= 200 && result.status < 300, `API failure ${result.status}: ${result.body?.code ?? 'unknown'}`);
  return result.body;
}
async function command(actor, code, action) {
  const room = await api(`/v1/bingo/rooms/${code}`, actor);
  return api(`/v1/bingo/rooms/${code}/commands`, actor, { id: randomUUID(), expectedRevision: room.revision, action });
}
try {
  assert.equal((await api('/health/ready')).status, 'ready'); checks.push('public readiness');
  for (const displayName of ['Multi game QA host', 'Multi game QA peer']) actors.push(await api('/v1/guests', null, { displayName }));
  const oldRequest = { id: randomUUID(), tickets: 1, friendTable: true };
  const tambola = await api('/v1/matches', actors[0], oldRequest);
  assert.equal(tambola.snapshot.wallet.balance, 1400);
  assert.deepEqual(await api('/v1/matches', actors[0], oldRequest), tambola);
  const left = await api(`/v1/rooms/${tambola.snapshot.code}/commands`, actors[0], {
    id: randomUUID(), expectedRevision: tambola.snapshot.revision, action: { type: 'leave' } });
  assert.equal(left.snapshot.wallet.balance, 1500); checks.push('legacy Tambola purchase retry and refund');
  const purchase = { id: randomUUID(), cards: 6, friendTable: true, variant: 'BINGO_75' };
  const waiting = await api('/v1/bingo/matches', actors[0], purchase);
  assert.equal(waiting.wallet.balance, 900);
  assert.deepEqual(await api('/v1/bingo/matches', actors[0], purchase), waiting); checks.push('Bingo purchase retry');
  assert.equal((await request('/v1/matches', actors[0], { id: randomUUID(), tickets: 1 })).status, 409);
  await api('/v1/bingo/matches', actors[1], { id: randomUUID(), cards: 1, friendTable: true, friendCode: waiting.code });
  const active = await command(actors[0], waiting.code, { type: 'bingo_start' });
  assert.equal(active.phase, 'ACTIVE'); assert.equal(active.round.ownCards.length, 6);
  assert.ok(active.round.ownCards.every(card => card.playerId === actors[0].playerId));
  assert.equal(active.round.revealedOrder, null);
  const peer = await api(`/v1/bingo/rooms/${waiting.code}`, actors[1]);
  assert.equal(peer.round.ownCards.length, 1);
  assert.ok(peer.round.ownCards.every(card => card.playerId === actors[1].playerId)); checks.push('variant isolation and private six-card snapshots');
  const deadline = Date.now() + 90000;
  let room = active;
  let card;
  while (!(card = room.round.ownCards.find(c => c.cells.some(n => n !== 0 && room.round.called.includes(n))))) {
    assert.ok(Date.now() < deadline, 'No owned called number within deadline');
    await new Promise(resolve => setTimeout(resolve, 1000));
    room = await api(`/v1/bingo/rooms/${waiting.code}`, actors[0]);
  }
  const number = card.cells.find(n => n !== 0 && room.round.called.includes(n));
  const marked = await command(actors[0], waiting.code, { type: 'bingo_mark', roundId: room.round.id, cardId: card.id, number });
  assert.ok(marked.round.ownMarks[card.id].includes(number)); checks.push('live worker draw and authenticated mark');
} finally {
  const failures = [];
  for (const actor of actors) {
    const deletion = { id: randomUUID() };
    let confirmed = false;
    for (let attempt = 0; attempt < 3 && !confirmed; attempt++) {
      try { await api('/v1/guests/me/delete', actor, deletion); confirmed = true; }
      catch { if (attempt < 2) await new Promise(resolve => setTimeout(resolve, 1000)); }
    }
    if (!confirmed) failures.push('QA profile deletion not confirmed');
  }
  assert.equal(failures.length, 0, failures.join('; '));
}
checks.push('temporary profiles deleted');
await mkdir('reviews/release-v44', { recursive: true });
await writeFile('reviews/release-v44/host-verification.json', JSON.stringify({ origin, checkedAt: new Date().toISOString(), checks }, null, 2) + '\n');
console.log(`PASS: ${checks.length} public multi-game checks; temporary profiles deleted.`);
