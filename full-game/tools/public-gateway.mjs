import http from 'node:http';
import { isIP } from 'node:net';
import { invitationPage } from './public-invite.mjs';

// The tunnel is the only ingress. This listener is always loopback and never proxies arbitrary URLs.
export function createGateway({ upstreamPort = 18080, maxStreams = 80 } = {}) {
  const buckets = new Map();
  let streams = 0;
  const allowed = (req, websocket = false) => {
    const path = req.url;
    if (websocket) return req.method === 'GET' && /^\/v1\/rooms\/[A-HJ-NP-Z2-9]{8}\/events(?:\?after=\d+)?$/.test(path);
    if (req.method === 'GET' && path.length <= 4120 && /^\/admob\/reward\?[A-Za-z0-9%_.~=&+\/-]+$/.test(path)) return true;
    if (req.method === 'GET' && /^\/v1\/wallet\/ad-intents\/[a-f0-9-]{36}$/.test(path)) return true;
    if (req.method === 'GET' && /^\/v1\/rooms\/[A-HJ-NP-Z2-9]{8}\/reactions$/.test(path)) return true;
    if (req.method === 'GET' && /^\/v1\/bingo\/rooms\/B-[A-Z2-9]{8}$/.test(path)) return true;
    if (req.method === 'POST' && (path === '/v1/bingo/matches' || /^\/v1\/bingo\/rooms\/B-[A-Z2-9]{8}\/commands$/.test(path))) return true;
    if (req.method === 'GET') return path === '/health/ready' || path === '/v1/wallet' || /^\/v1\/rooms\/[A-HJ-NP-Z2-9]{8}(?:\?after=\d+)?$/.test(path);
    return req.method === 'POST' && (/^\/v1\/(guests|matches|wallet\/(refill|login-rewards|ad-intents)|rooms)$/.test(path) ||
      /^\/v1\/guests\/me\/(device|session|logout|delete)$/.test(path) || /^\/v1\/rooms\/[A-HJ-NP-Z2-9]{8}\/(join|commands|reactions)$/.test(path));
  };
  function admit(req, upgrade = false) {
    const raw = req.headers['cf-connecting-ip'];
    const ip = typeof raw === 'string' && isIP(raw) ? raw : 'loopback';
    const minute = Math.floor(Date.now() / 60_000);
    if (buckets.size > 4096) for (const [key, bucket] of buckets) if (bucket.minute !== minute) buckets.delete(key);
    if (!buckets.has(ip) && buckets.size >= 4096) return false;
    let bucket = buckets.get(ip);
    if (!bucket || bucket.minute !== minute) { bucket = { minute, requests: 0, guests: 0, upgrades: 0 }; buckets.set(ip, bucket); }
    return ++bucket.requests <= 600 && (req.url !== '/v1/guests' || ++bucket.guests <= 60) && (!upgrade || ++bucket.upgrades <= 120);
  }
  function headers(req, upgrade = false) {
    const result = { host: `127.0.0.1:${upstreamPort}` };
    for (const key of ['authorization', 'content-type', ...(upgrade ? ['sec-websocket-key', 'sec-websocket-version', 'sec-websocket-protocol'] : [])])
      if (req.headers[key]) result[key] = req.headers[key];
    if (upgrade) { result.connection = 'Upgrade'; result.upgrade = 'websocket'; }
    return result;
  }
  const server = http.createServer({ maxHeaderSize: 8192, requestTimeout: 12_000, headersTimeout: 10_000 }, (req, res) => {
    const fail = (status) => { res.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store', connection: 'close' }); res.end('{}'); };
    const invitation = (req.method === 'GET' || req.method === 'HEAD') && invitationPage(req.url);
    if (invitation) {
      if (!admit(req)) return fail(429);
      if (req.headers['transfer-encoding'] || Number(req.headers['content-length'] || 0) !== 0) return fail(413);
      res.writeHead(200, { ...invitation.headers, 'content-length': Buffer.byteLength(invitation.body) });
      return res.end(req.method === 'HEAD' ? undefined : invitation.body);
    }
    if (!allowed(req)) return fail(404);
    if (!admit(req)) return fail(429);
    if (Number(req.headers['content-length'] || 0) > 32768) return fail(413);
    let size = 0;
    const parts = [];
    const deadline = setTimeout(() => { if (!res.writableEnded) fail(408); }, 10_000);
    req.on('close', () => clearTimeout(deadline));
    req.on('error', () => { if (!res.writableEnded) fail(400); });
    req.on('data', part => {
      size += part.length;
      if (size > 32768) { if (!res.writableEnded) fail(413); }
      else parts.push(part);
    });
    req.on('end', () => {
      clearTimeout(deadline);
      if (res.writableEnded) return;
      const outbound = headers(req);
      outbound['content-length'] = size;
      const proxy = http.request({ host: '127.0.0.1', port: upstreamPort, path: req.url, method: req.method, headers: outbound, timeout: 25_000 }, response => {
        res.writeHead(response.statusCode, { 'content-type': response.headers['content-type'] || 'application/json',
          'cache-control': 'no-store', 'x-content-type-options': 'nosniff', ...(response.headers.date ? { date: response.headers.date } : {}) });
        response.pipe(res);
        response.on('error', () => res.destroy());
      });
      proxy.on('timeout', () => proxy.destroy());
      proxy.on('error', () => { if (!res.headersSent) fail(502); else res.destroy(); });
      res.on('close', () => proxy.destroy());
      proxy.end(Buffer.concat(parts));
    });
  });
  server.maxConnections = 160;
  server.on('upgrade', (req, socket, head) => {
    const reject = status => socket.end(`HTTP/1.1 ${status}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
    if (!allowed(req, true) || req.headers.upgrade?.toLowerCase() !== 'websocket') return reject('404 Not Found');
    if (streams >= maxStreams || !admit(req, true)) return reject('429 Too Many Requests');
    streams++;
    let upstream;
    const proxy = http.request({ host: '127.0.0.1', port: upstreamPort, path: req.url, headers: headers(req, true), timeout: 10_000 });
    socket.on('error', () => socket.destroy());
    socket.once('close', () => { streams--; upstream?.destroy(); proxy.destroy(); });
    proxy.on('upgrade', (response, peer, peerHead) => {
      upstream = peer;
      peer.setTimeout(0); proxy.setTimeout(0);
      const responseHeaders = Object.entries(response.headers).map(([key, value]) => `${key}: ${value}`).join('\r\n');
      socket.write(`HTTP/1.1 101 Switching Protocols\r\n${responseHeaders}\r\n\r\n`);
      if (peerHead.length) socket.write(peerHead);
      if (head.length) peer.write(head);
      peer.on('error', () => socket.destroy()); peer.on('close', () => socket.destroy());
      socket.pipe(peer); peer.pipe(socket);
    });
    proxy.on('response', response => { response.resume(); reject(`${response.statusCode} Rejected`); });
    proxy.on('timeout', () => proxy.destroy());
    proxy.on('error', () => socket.destroy());
    proxy.end();
  });
  return server;
}
