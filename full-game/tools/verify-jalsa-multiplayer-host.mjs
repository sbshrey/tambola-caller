// Disposable public-host smoke test. Do not log credentials or include them in evidence.
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
async function waitFor(path, actor, active) {
  const deadline = Date.now() + 45000;
  while (Date.now() < deadline) {
    const room = await api(path, actor);
    if (active(room)) return room;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`Timed out waiting for two-human start: ${path}`);
}
try {
  assert.equal((await api('/health/ready')).status, 'ready');
  checks.push('public TLS readiness');
  for (let i = 0; i < 4; i++) actors.push(await api('/v1/guests', null, { displayName: `Jalsa QA ${i + 1}` }));

  const tambolaRequest = { id: randomUUID(), tickets: 1, rulesVersion: 2, largeMatch: true, realPlayersOnly: true };
  const first = await api('/v1/matches', actors[0], tambolaRequest);
  assert.deepEqual(await api('/v1/matches', actors[0], tambolaRequest), first);
  assert.equal(first.snapshot.phase, 'LOBBY');
  assert.ok(first.snapshot.members.length >= 1);
  checks.push('Tambola purchase retry and human-only waiting room');
  await api('/v1/matches', actors[1], { ...tambolaRequest, id: randomUUID() });
  const tambola = await waitFor(`/v1/rooms/${first.snapshot.code}`, actors[0], room => room.snapshot.phase === 'ACTIVE');
  assert.ok(tambola.snapshot.members.length >= 2);
  assert.ok(tambola.snapshot.round.players.every(player => !player.computer));
  assert.equal(tambola.snapshot.round.players.length, tambola.snapshot.members.length);
  checks.push('Tambola quick table starts with two real players and no computer seats');

  const bingoRequest = { id: randomUUID(), cards: 1, realPlayersOnly: true };
  const bingoFirst = await api('/v1/bingo/matches', actors[2], bingoRequest);
  assert.deepEqual(await api('/v1/bingo/matches', actors[2], bingoRequest), bingoFirst);
  assert.equal(bingoFirst.phase, 'LOBBY');
  assert.ok(bingoFirst.members.length >= 1);
  assert.ok(bingoFirst.players.every(player => !player.computer));
  checks.push('Bingo purchase retry and human-only waiting room');
  await api('/v1/bingo/matches', actors[3], { ...bingoRequest, id: randomUUID() });
  const bingo = await waitFor(`/v1/bingo/rooms/${bingoFirst.code}`, actors[2], room => room.phase === 'ACTIVE');
  assert.ok(bingo.members.length >= 2);
  assert.ok(bingo.round.players.every(player => !player.computer));
  assert.equal(bingo.round.players.length, bingo.members.length);
  checks.push('Bingo quick table starts with two real players and no computer seats');
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
checks.push('all temporary QA profiles deleted');
await mkdir('reviews/jalsa-online-redesign-2026-10-01', { recursive: true });
await writeFile('reviews/jalsa-online-redesign-2026-10-01/host-verification.json',
  JSON.stringify({ origin, checkedAt: new Date().toISOString(), checks }, null, 2) + '\n');
console.log(`PASS: ${checks.length} public multiplayer checks; temporary profiles deleted.`);
