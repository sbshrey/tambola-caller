import { spawn, spawnSync } from 'node:child_process';
import { mkdir, writeFile, rename, readFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { setTimeout as pause } from 'node:timers/promises';
import { createGateway } from './public-gateway.mjs';

const directory = resolve(process.argv[2] || '.test-workspace/public-host');
const repo = 'sbshrey/tambola-caller';
const branch = 'codex/public-beta-channel';
const statePath = join(directory, 'status.json');
const gh = process.env.TAMBOLA_GH || 'gh';
const cloudflared = process.env.TAMBOLA_CLOUDFLARED || 'C:\\Program Files (x86)\\cloudflared\\cloudflared.exe';
await mkdir(directory, { recursive: true });
await writeFile(join(directory, 'tunnel.yml'), '{}\n');
async function state(value) {
  await writeFile(statePath + '.pending', JSON.stringify({ checkedAt: new Date().toISOString(), pid: process.pid, ...value }, null, 2));
  await rename(statePath + '.pending', statePath);
}
function github(path, payload) {
  const args = ['api', path, ...(payload ? ['--method', 'PUT', '--input', '-'] : [])];
  const result = spawnSync(gh, args, { input: payload ? JSON.stringify(payload) : undefined, encoding: 'utf8', windowsHide: true, timeout: 30_000 });
  if (result.status !== 0) throw Error('directory_publish_failed');
  return JSON.parse(result.stdout);
}
async function publish(origin) {
  const current = github(`repos/${repo}/contents/server.json?ref=${branch}`);
  const entry = { version: 1, service: 'tambola-together-public-beta-v1', origin, expiresAt: Date.now() + 24 * 60 * 60 * 1000 };
  github(`repos/${repo}/contents/server.json`, { branch, sha: current.sha, message: 'Refresh temporary Internet beta server address',
    content: Buffer.from(JSON.stringify(entry, null, 2) + '\n').toString('base64') });
  return entry.expiresAt;
}
const gateway = createGateway();
await new Promise((resolve, reject) => { gateway.once('error', reject); gateway.listen(18081, '127.0.0.1', resolve); });
let stopping = false;
let child;
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => { stopping = true; child?.kill(); gateway.close(); });
try {
  while (!stopping) {
    try {
      await state({ status: 'connecting' });
      let origin;
      let exited = false;
      let logTail = '';
      child = spawn(cloudflared, ['tunnel', '--config', join(directory, 'tunnel.yml'), '--no-autoupdate', '--url', 'http://127.0.0.1:18081', '--protocol', 'http2', '--metrics', '127.0.0.1:18082'],
        { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
      child.on('error', () => { exited = true; }); child.on('exit', () => { exited = true; });
      const observe = data => {
        logTail = (logTail + data.toString()).slice(-4096);
        origin ||= logTail.match(/https:\/\/[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com/)?.[0];
      };
      child.stdout.on('data', observe); child.stderr.on('data', observe);
      const until = Date.now() + 120_000;
      let ready = false;
      while (!stopping && !exited && Date.now() < until) {
        if (origin) {
          try {
            const response = await fetch(origin + '/health/ready', { redirect: 'error', signal: AbortSignal.timeout(8000) });
            const body = await response.json();
            ready = response.ok && body.status === 'ready' && [4, 5].includes(body.protocolVersion);
          } catch { }
          if (ready) break;
        }
        await pause(2000);
      }
      if (!ready || exited || stopping) throw Error('public_connection_unavailable');
      let expiresAt = await publish(origin);
      let nextPublishAttempt = expiresAt - 12 * 60 * 60 * 1000;
      let renewalPending = false;
      while (!stopping && !exited) {
        if (Date.now() > nextPublishAttempt) {
          try {
            expiresAt = await publish(origin);
            nextPublishAttempt = expiresAt - 12 * 60 * 60 * 1000;
            renewalPending = false;
          } catch {
            // A GitHub outage must not tear down an otherwise working game tunnel.
            nextPublishAttempt = Date.now() + 60_000;
            renewalPending = true;
          }
        }
        await state({ status: 'online', origin, expiresAt, renewalPending, tunnelPid: child.pid, directory: `https://raw.githubusercontent.com/${repo}/${branch}/server.json` });
        await pause(15_000);
        try { if ((await readFile(join(directory, 'stop.request'), 'utf8')).trim() === 'stop') stopping = true; } catch { }
      }
    } catch (error) {
      await state({ status: 'retrying', reason: ['directory_publish_failed', 'public_connection_unavailable'].includes(error.message) ? error.message : 'host_unavailable' });
    } finally { child?.kill(); child = null; }
    if (!stopping) await pause(15_000);
  }
} finally { gateway.closeAllConnections(); gateway.close(); await state({ status: 'stopped' }); }
