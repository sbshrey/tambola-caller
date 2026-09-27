// One installed-host smoke check. Credentials and other players' data never enter evidence.
import assert from 'node:assert/strict';
import https from 'node:https';
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { parseRuntimeManifest } from '../tools/service-runtime.mjs';

const root = join(process.env.LOCALAPPDATA, 'TambolaTogetherHost');
const config = JSON.parse(await readFile(join(root, 'host.json'), 'utf8'));
assert.equal(config.origin, 'https://192.168.1.4:8443');
assert.equal(config.sourceCommit, 'cfdb236830a376b13d96d2cecf521032e3b441c9');
const ca = await readFile(join(root, 'tls-data/caddy/pki/authorities/local/root.crt'));
const manifest = async directory => {
  const jars = (await readdir(directory, { withFileTypes: true })).filter(f => f.name.endsWith('.jar'));
  assert.equal(jars.length, 51);
  assert(jars.every(f => f.isFile()));
  return parseRuntimeManifest((await Promise.all(jars.map(async f =>
    `${createHash('sha256').update(await readFile(join(directory, f.name))).digest('hex')}  ${f.name}\n`))).join(''));
};
const before = await manifest(config.serviceLib);
assert.deepEqual(before, await manifest('server/build/install/server/lib'));
async function request(path, method = 'GET', body, actor) {
  return new Promise((resolve, reject) => {
    const payload = body === undefined ? undefined : JSON.stringify(body);
    const req = https.request(new URL(path, config.origin), { method, ca, timeout: 8000,
      headers: { ...(payload ? { 'content-type': 'application/json', 'content-length': Buffer.byteLength(payload) } : {}),
        ...(actor ? { authorization: `Bearer ${actor.token}` } : {}) } }, response => {
      let content = '';
      response.on('data', chunk => { content += chunk; if (content.length > 524288) response.destroy(); });
      response.on('error', reject);
      response.on('end', () => {
        try { resolve({ status: response.statusCode, body: content ? JSON.parse(content) : null }); }
        catch { reject(new Error('Invalid JSON response')); }
      });
    });
    req.on('timeout', () => req.destroy(new Error('Request timeout')));
    req.on('error', reject); if (payload) req.write(payload); req.end();
  });
}
async function api(path, method, body, actor) {
  const reply = await request(path, method, body, actor);
  assert(reply.status >= 200 && reply.status < 300, `API status ${reply.status}`);
  return reply.body;
}
const evidence = { observedAt: new Date().toISOString(), sourceCommit: config.sourceCommit,
  serviceRuntimeSha256: before.serviceRuntimeSha256, result: 'pending', checks: [],
  scope: 'Verified HTTPS on this PC; simulated roster and first automatic call. Not physical-phone or full-round acceptance.' };
let actor;
try {
  assert.equal((await api('/health/ready')).status, 'ready');
  actor = await api('/v1/guests', 'POST', { displayName: 'Roster QA', avatar: 0 });
  const purchase = { id: randomUUID(), tickets: 6 };
  const bought = await api('/v1/matches', 'POST', purchase, actor);
  assert.equal(bought.snapshot.wallet.balance, 900);
  assert.equal(bought.snapshot.coins.ownTickets, 6);
  assert.deepEqual(await api('/v1/matches', 'POST', purchase, actor), bought);
  const path = `/v1/rooms/${bought.snapshot.code}`;
  let view = bought.snapshot;
  const deadline = Date.now() + 30000;
  while (view.phase === 'LOBBY' && Date.now() < deadline) {
    await delay(400); view = (await api(path, 'GET', undefined, actor)).snapshot;
  }
  assert.equal(view.phase, 'ACTIVE');
  assert.equal(view.members.length, 1, 'Another human joined; do not record or manipulate their profile');
  assert.equal(view.round.players.length, 4);
  const computers = view.round.players.filter(player => player.computer);
  assert.equal(computers.length, 3);
  assert.equal(new Set(computers.map(player => player.name)).size, 3);
  assert(computers.every(player => /^(ChaiChamp|NeonNinja|LuckyMango|PixelRaja|DiceDiva|MoonMaverick|TurboTikka|LotusLegend)$/.test(player.name)));
  assert.equal(view.round.ownTickets.length, 6);
  assert(view.round.ownTickets.every(ticket => ticket.playerId === actor.playerId));
  assert.equal(view.coins.pool, 1500);
  assert.equal(view.coins.prizes.length, 7);
  evidence.computers = computers.map(({ name, computer, avatar }) => ({ name, computer, avatar }));
  evidence.checks.push('one exact charge and receipt replay; six private tickets; three distinct fictional handles explicitly flagged computer; fixed 1500-coin pool with seven prizes');
  const firstPlayers = view.round.players;
  while (view.round.called.length === 0 && Date.now() < deadline) {
    await delay(400); view = (await api(path, 'GET', undefined, actor)).snapshot;
  }
  assert(view.round.called.length > 0);
  assert.deepEqual(view.round.players, firstPlayers);
  assert.equal(view.wallet.balance, 900);
  evidence.checks.push('first automatic call arrived; roster identities and wallet remained stable');
} finally {
  if (actor) {
    await api('/v1/guests/me/delete', 'POST', { id: randomUUID() }, actor);
    assert.equal((await request('/v1/wallet', 'GET', undefined, actor)).status, 401);
    evidence.checks.push('only temporary Roster QA profile deleted; deleted session rejected');
  }
}
assert.deepEqual(await manifest(config.serviceLib), before);
assert.equal((await api('/health/ready')).status, 'ready');
evidence.result = 'passed';
await writeFile('.test-workspace/gamer-handles-live-roster.json', JSON.stringify(evidence, null, 2) + '\n', { flag: 'wx' });
console.log(JSON.stringify(evidence, null, 2));
