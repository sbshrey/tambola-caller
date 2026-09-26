// Dedicated installed host only. Temporary QA profiles are deleted in finally.
// Logs contain public checks, never credentials, hidden draw order or other hands.
import assert from 'node:assert/strict';
import https from 'node:https';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
const execute = promisify(execFile);
const directory = join(process.env.LOCALAPPDATA, 'TambolaTogetherHost');
const config = JSON.parse(await readFile(join(directory, 'host.json'), 'utf8'));
assert.match(config.origin, /^https:\/\/192\.168\.[0-9]+\.[0-9]+:8443$/);
const ca = await readFile(join(directory, 'tls-data/caddy/pki/authorities/local/root.crt'));
const evidence = { startedAt: new Date().toISOString(), origin: config.origin, checks: [] };
const credentials = [];
function request(path, method = 'GET', body, token, trust = true) {
  return new Promise((done, reject) => {
    const payload = body === undefined ? undefined : JSON.stringify(body);
    const req = https.request(new URL(path, config.origin), {
      method, ca: trust ? ca : undefined, timeout: 6000,
      headers: { ...(payload ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    }, res => {
      let text = ''; res.on('data', chunk => text += chunk);
      res.on('end', () => done({ status: res.statusCode, body: text ? JSON.parse(text) : null }));
    });
    req.on('timeout', () => req.destroy(new Error('request_timeout')));
    req.on('error', reject); if (payload) req.write(payload); req.end();
  });
}
async function api(path, method, body, actor) {
  const response = await request(path, method, body, actor?.token);
  assert.ok(response.status >= 200 && response.status < 300, `API ${response.status}: ${response.body?.code ?? 'unknown'}`);
  return response.body;
}
const wait = ms => new Promise(done => setTimeout(done, ms));
async function recovery(role) {
  const script = `
    $ErrorActionPreference='Stop'
    $root=Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost'
    $state=Get-Content -LiteralPath (Join-Path $root 'state.json') -Raw | ConvertFrom-Json
    if ($state.status -ne 'ready') { throw 'Host must be ready before fault probe' }
    if ($env:TAMBOLA_TEST_ROLE -eq 'database') {
      $config=Get-Content -LiteralPath (Join-Path $root 'host.json') -Raw | ConvertFrom-Json
      $data=Join-Path $root 'data'
      if ($config.postgresData -ne $data) { throw 'Database identity mismatch' }
      & (Join-Path $config.postgresBin 'pg_ctl.exe') -D $data -m fast -w stop *> $null
      if ($LASTEXITCODE -ne 0) { throw 'Could not stop owned database for recovery test' }
      Write-Output 1
    } elseif ($env:TAMBOLA_TEST_ROLE -eq 'worker') {
      $process=Get-CimInstance Win32_Process -Filter "ProcessId = $($state.supervisorPid)"
      if (!$process.CommandLine.Contains((Join-Path $root 'windows-host.ps1')) -or !$process.CommandLine.Contains('-Action Run')) { throw 'Worker identity mismatch' }
      Stop-Process -Id $process.ProcessId -Force
      Write-Output $process.ProcessId
    } else {
      $child=$state.children | Where-Object role -eq $env:TAMBOLA_TEST_ROLE
      $process=Get-Process -Id $child.pid
      if ($process.Path -ne $child.path -or $process.StartTime.ToUniversalTime().Ticks -ne ([DateTimeOffset]$child.startedAt).UtcTicks) { throw 'Child identity mismatch' }
      Stop-Process -Id $process.Id -Force
      Write-Output $process.Id
    }
  `;
  const started = Date.now();
  const { stdout } = await execute(join(directory, 'runtime/powershell/pwsh.exe'), ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', script],
    { windowsHide: true, env: { ...process.env, TAMBOLA_TEST_ROLE: role }, timeout: 10000 });
  const oldPid = Number(stdout.trim()); assert.ok(oldPid > 0);
  while (Date.now() - started < 45000) {
    await wait(1000);
    try {
      const state = JSON.parse(await readFile(join(directory, 'state.json'), 'utf8'));
      const pid = role === 'worker' ? state.supervisorPid : state.children?.find(child => child.role === (role === 'database' ? 'server' : role))?.pid;
      if (!pid || pid === oldPid || state.status !== 'ready') continue;
      assert.equal((await request('/health/ready')).status, 200);
      evidence.checks.push({ recovery: role, milliseconds: Date.now() - started });
      console.log(`${role} recovered with verified TLS`); return;
    } catch { /* brief restart window */ }
  }
  throw new Error(`${role} did not recover within 45 seconds`);
}
let room, receipt, claimRequest;
try {
  assert.equal((await request('/health/ready')).status, 200);
  await assert.rejects(() => request('/health/ready', 'GET', undefined, undefined, false));
  assert.equal((await request('/internal/metrics')).status, 404);
  evidence.checks.push({ verifiedTls: true, untrustedCertificateRejected: true, internalMetricsHidden: true });
  for (const name of ['WiFi QA One', 'WiFi QA Two']) credentials.push(await api('/v1/guests', 'POST', { displayName: name }));
  const [host, peer] = credentials;
  room = (await api('/v1/rooms', 'POST', { id: randomUUID(), options: {
    game: { mode: 'ONLINE', ticketsPerPlayer: 2, manualClaims: true, playAllNumbers: true },
    intervalSeconds: 5, automaticCalling: false,
  } }, host)).snapshot;
  const base = `/v1/rooms/${room.code}`;
  async function command(actor, action, existing) {
    const update = await api(`${base}/commands`, 'POST', existing ?? { id: randomUUID(), expectedRevision: room.revision, action }, actor);
    room = update.snapshot; return update;
  }
  room = (await api(`${base}/join`, 'POST', undefined, peer)).snapshot;
  await command(peer, { type: 'ready', value: true });
  await command(host, { type: 'ready', value: true });
  await command(host, { type: 'start' });
  for (let number = 0; number < 90; number++) await command(host, { type: 'draw' });
  const card = room.round.ownTickets[0];
  const numbers = card.cells.filter(number => number !== 0);
  claimRequest = { id: randomUUID(), expectedRevision: room.revision, action: {
    type: 'claim', roundId: room.round.id, drawIndex: 90, markedNumbers: numbers,
    selection: { ticketId: card.id, prizeId: 'TOP_LINE' },
  } };
  receipt = await command(host, claimRequest.action, claimRequest);
  assert.deepEqual(room.round.awards.map(award => award.prize), ['TOP_LINE']);
  assert.deepEqual(room.round.awards[0].ticketIds, [card.id]);
  const peerView = (await api(base, 'GET', undefined, peer)).snapshot;
  assert.ok(peerView.round.ownTickets.every(ticket => ticket.playerId === peer.playerId));
  assert.equal(peerView.round.revealedOrder, null);
  evidence.checks.push({ twoAuthenticatedClients: true, manualCalls: 90, selectedClaim: true, ownTicketPrivacy: true });
  for (const role of ['server', 'tls', 'worker', 'database']) {
    await recovery(role);
    assert.deepEqual(await command(host, claimRequest.action, claimRequest), receipt);
    const restored = (await api(base, 'GET', undefined, host)).snapshot;
    assert.deepEqual(restored.round, receipt.snapshot.round);
  }
  evidence.checks.push({ exactClaimReceiptAfterRestarts: true, roundAndTicketsPersisted: true });
  evidence.passed = true;
} finally {
  let removed = 0;
  for (const actor of credentials) {
    try { await api('/v1/guests/me/delete', 'POST', { id: randomUUID() }, actor); removed++; }
    catch { evidence.cleanupFailed = true; }
  }
  evidence.deletedQaProfiles = removed;
  evidence.finishedAt = new Date().toISOString();
  const folder = resolve('.test-workspace/wifi-host'); await mkdir(folder, { recursive: true });
  await writeFile(join(folder, 'validation.json'), JSON.stringify(evidence, null, 2) + '\n');
}
assert.ok(!evidence.cleanupFailed, 'QA profile cleanup must succeed');
console.log('Wi-Fi host verification passed; temporary profiles deleted. Physical phone reachability is a separate check.');
