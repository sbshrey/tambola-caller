import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createHash } from 'node:crypto';
import { createConnection } from 'node:net';
import { once } from 'node:events';
import { createGateway } from './public-gateway.mjs';

test('public ingress bounds routes and bodies, strips forwarded headers and carries a WebSocket', async () => {
  const seen = [];
  const upstream = http.createServer((req, res) => {
    seen.push({ path: req.url, headers: req.headers });
    req.resume(); req.on('end', () => { res.setHeader('content-type', 'application/json'); res.end('{"status":"ready"}'); });
  });
  upstream.on('upgrade', (req, socket) => {
    assert.equal(req.headers.authorization, 'Bearer test-only');
    assert.equal(req.headers['x-forwarded-for'], undefined);
    const accept = createHash('sha1').update(req.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
    socket.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
    socket.write(Buffer.from([0x81, 2, 111, 107]));
    socket.on('error', () => {}); socket.on('data', () => socket.destroy());
  });
  upstream.listen(0, '127.0.0.1'); await once(upstream, 'listening');
  const gateway = createGateway({ upstreamPort: upstream.address().port });
  gateway.listen(0, '127.0.0.1'); await once(gateway, 'listening');
  const origin = `http://127.0.0.1:${gateway.address().port}`;
  try {
    for (const path of ['/internal/metrics', '/health/live', '/invite/ABCD2345', '/v1/rooms/../internal', '/v1/wallet?token=oops'])
      assert.equal((await fetch(origin + path)).status, 404);
    assert.equal((await fetch(origin + '/v1/guests', { method: 'POST', body: 'x'.repeat(32769) })).status, 413);
    assert.equal(seen.length, 0);
    assert.equal((await fetch(origin + '/v1/wallet', { headers: { authorization: 'Bearer test-only', 'x-forwarded-for': '1.2.3.4' } })).status, 200);
    assert.equal(seen[0].headers.authorization, 'Bearer test-only');
    assert.equal(seen[0].headers['x-forwarded-for'], undefined);
    const socket = createConnection(gateway.address().port, '127.0.0.1');
    await once(socket, 'connect');
    const data = [];
    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => { socket.destroy(); reject(Error('WebSocket timeout')); }, 3000);
      socket.on('error', reject);
      socket.on('data', chunk => {
        data.push(chunk);
        if (Buffer.concat(data).includes(Buffer.from([0x81, 2, 111, 107]))) { clearTimeout(timeout); socket.write('done'); socket.destroy(); resolve(); }
      });
      socket.write('GET /v1/rooms/ABCD2345/events?after=0 HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nAuthorization: Bearer test-only\r\nX-Forwarded-For: 1.2.3.4\r\n\r\n');
    });
    assert.match(Buffer.concat(data).toString(), /101 Switching Protocols/);
  } finally { gateway.closeAllConnections(); gateway.close(); upstream.closeAllConnections(); upstream.close(); }
});
