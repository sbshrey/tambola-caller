// Public-host quick-fill smoke test with disposable profiles; never log tokens.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';

const origin = 'https://play.thefinxperts.com';
const actors = [];
const checks = [];
async function api(path, actor, body) {
  const response = await fetch(origin + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { ...(actor ? { authorization: `Bearer ${actor.token}` } : {}),
      ...(body === undefined ? {} : { 'content-type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
  const result = await response.json();
  assert.ok(response.ok, `${path}: HTTP ${response.status} ${result.code ?? ''}`);
  return result;
}
async function active(path, actor, view) {
  const deadline = Date.now() + 45000;
  while (Date.now() < deadline) {
    const response = view(await api(path, actor));
    if (response.phase === 'ACTIVE') return response;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`Quick table did not start: ${path}`);
}
try {
  assert.equal((await api('/health/ready')).status, 'ready');
  checks.push('public TLS readiness');
  for (const name of ['Quick Tambola QA', 'Quick Bingo QA']) actors.push(await api('/v1/guests', null, { displayName: name }));

  const tambola = await api('/v1/matches', actors[0], { id: randomUUID(), tickets: 1,
    rulesVersion: 2, largeMatch: true, roundSummary: true, realPlayersOnly: false });
  assert.equal(tambola.snapshot.phase, 'LOBBY');
  assert.ok(tambola.snapshot.coins.startsAt - tambola.snapshot.serverTime <= 10000);
  const tambolaRound = await active(`/v1/rooms/${tambola.snapshot.code}`, actors[0], room => room.snapshot);
  assert.ok(tambolaRound.round.players.some(player => player.id === actors[0].playerId && !player.computer));
  assert.ok(tambolaRound.round.players.some(player => player.computer));
  checks.push('Tambola quick room starts after ten-second window with computer flags');

  const bingo = await api('/v1/bingo/matches', actors[1], { id: randomUUID(), cards: 1, realPlayersOnly: false });
  assert.equal(bingo.phase, 'LOBBY');
  assert.ok(bingo.startsAt - bingo.serverTime <= 10000);
  const bingoRound = await active(`/v1/bingo/rooms/${bingo.code}`, actors[1], room => room);
  assert.ok(bingoRound.players.some(player => player.id === actors[1].playerId && !player.computer));
  assert.ok(bingoRound.players.some(player => player.computer));
  checks.push('Bingo quick room starts after ten-second window with computer flags');
} finally {
  for (const actor of actors) {
    const request = { id: randomUUID() };
    let deleted = false;
    for (let attempt = 0; attempt < 3 && !deleted; attempt++) {
      try { await api('/v1/guests/me/delete', actor, request); deleted = true; }
      catch { if (attempt < 2) await new Promise(resolve => setTimeout(resolve, 1000)); }
    }
    assert.ok(deleted, 'Disposable QA profile deletion failed');
  }
}
checks.push('temporary QA profiles deleted');
await mkdir('reviews/jalsa-quick-fill-v49-2026-10-01', { recursive: true });
await writeFile('reviews/jalsa-quick-fill-v49-2026-10-01/host-verification.json',
  JSON.stringify({ origin, checkedAt: new Date().toISOString(), checks }, null, 2) + '\n');
console.log(`PASS: ${checks.length} public quick-fill checks; temporary profiles deleted.`);
