import test from 'node:test';
import assert from 'node:assert/strict';
import { probeReady, healthDecision } from './public-health.mjs';

test('readiness accepts current and legacy servers, rejects failed or unknown responses', async () => {
  const response = (body, status = 200) => async () => new Response(JSON.stringify(body), { status });
  for (const protocolVersion of [4, 5, 6, 7, 8, 9]) {
    assert.equal(await probeReady('https://game.example', response({ status: 'ready', protocolVersion })), true);
  }
  for (const body of [{ status: 'starting', protocolVersion: 9 }, { status: 'ready', protocolVersion: 10 },
    { status: 'ready', protocolVersion: '9' }, {}]) {
    assert.equal(await probeReady('https://game.example', response(body)), false);
  }
  assert.equal(await probeReady('https://game.example', response({ status: 'ready', protocolVersion: 9 }, 503)), false);
  assert.equal(await probeReady('https://game.example', async () => { throw Error('offline'); }), false);
  assert.equal(await probeReady('https://game.example', async () => new Response('bad json')), false);
});

test('three consecutive public failures rotate a tunnel only while the backend is ready', () => {
  let health = healthDecision(0, true, false);
  assert.equal(health.restart, false);
  health = healthDecision(health.failures, true, false);
  assert.equal(health.restart, false);
  health = healthDecision(health.failures, true, false);
  assert.equal(health.restart, true);
  assert.equal(health.status, 'public_unavailable');
  health = healthDecision(2, true, true);
  assert.equal(health.failures, 0);
  assert.equal(health.status, 'online');
  for (let i = 0; i < 10; i++) {
    health = healthDecision(health.failures, false, false);
    assert.equal(health.restart, false);
    assert.equal(health.failures, 0);
    assert.equal(health.status, 'backend_unavailable');
  }
  assert.equal(healthDecision(2, false, false).failures, 0);
});
