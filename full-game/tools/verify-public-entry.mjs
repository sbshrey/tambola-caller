import assert from 'node:assert/strict';
import { writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
const directory = 'https://raw.githubusercontent.com/sbshrey/tambola-caller/codex/public-beta-channel/server.json';
const entry = await (await fetch(directory + '?minute=' + Math.floor(Date.now() / 60000), { redirect: 'error' })).json();
assert.equal(entry.service, 'tambola-together-public-beta-v1');
assert.ok(entry.expiresAt > Date.now());
assert.match(entry.origin, /^https:\/\/[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com$/);
const request = (path, options = {}) => fetch(entry.origin + path, { ...options, redirect: 'error', signal: AbortSignal.timeout(15000) });
const health = await request('/health/ready');
assert.equal(health.status, 200);
assert.ok([5, 6].includes((await health.json()).protocolVersion));
const invitation = await request('/friends/ABCDEFG2');
assert.equal(invitation.status, 200);
assert.equal(invitation.headers.get('referrer-policy'), 'no-referrer');
const invitationHtml = await invitation.text();
assert.match(invitationHtml, /intent:\/\/friends\/ABCDEFG2#Intent;scheme=tambola-beta;package=io.github.sbshrey.tambola.game.beta;/);
assert.ok(invitationHtml.includes('https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha39-power-rooms'));
for (const path of ['/internal/metrics', '/health/live', '/.env', '/invite/ABCD2345']) assert.equal((await request(path)).status, 404);
assert.equal((await request('/v1/matches', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{"id":"00000000-0000-4000-8000-000000000001","tickets":1}' })).status, 401);
const evidence = { passed: true, observedAt: new Date().toISOString(), origin: entry.origin,
  checks: ['public system-trusted HTTPS', 'directory reaches a compatible service', 'code-only friend landing available', 'internal and non-game paths blocked', 'unauthenticated purchase rejected'],
  scope: 'Public endpoint requests from this PC; no physical-phone or cellular-network acceptance' };
await mkdir(resolve('.test-workspace/internet-beta'), { recursive: true });
await writeFile(resolve('.test-workspace/internet-beta/public-entry.json'), JSON.stringify(evidence, null, 2) + '\n');
console.log(JSON.stringify(evidence));
