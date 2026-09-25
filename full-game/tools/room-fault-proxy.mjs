// Local test fixture only. Fixed loopback destinations; never logs headers, tokens or bodies.
import http from 'node:http';
import net from 'node:net';

const upstreamPort = 8081;
let armed = false;
let dropped = 0;
const proxy = http.createServer((request, response) => {
  const upstream = http.request({ hostname: '127.0.0.1', port: upstreamPort, method: request.method, path: request.url, headers: request.headers }, result => {
    if (armed && request.method === 'POST' && request.url === '/v1/guests/me/delete' && result.statusCode === 200) {
      // The upstream transaction has committed; discard its entire response before disconnecting.
      result.resume();
      result.once('end', () => { dropped++; response.destroy(); });
    } else {
      response.writeHead(result.statusCode, result.headers);
      result.pipe(response);
    }
    result.on('error', () => response.destroy());
  });
  upstream.on('error', () => { if (!response.headersSent) response.writeHead(502); response.end(); });
  request.on('error', () => upstream.destroy());
  request.pipe(upstream);
});
proxy.on('upgrade', (request, socket, head) => {
  const upstream = net.connect(upstreamPort, '127.0.0.1', () => {
    const headers = request.rawHeaders.reduce((lines, value, index, all) => index % 2 === 0 ? [...lines, `${value}: ${all[index + 1]}`] : lines, []);
    upstream.write(`${request.method} ${request.url} HTTP/1.1\r\n${headers.join('\r\n')}\r\n\r\n`);
    if (head.length) upstream.write(head);
    socket.pipe(upstream); upstream.pipe(socket);
  });
  socket.on('error', () => upstream.destroy()); upstream.on('error', () => socket.destroy());
  socket.on('close', () => upstream.destroy()); upstream.on('close', () => socket.destroy());
});
const control = http.createServer((request, response) => {
  if (request.method === 'POST' && request.url === '/arm-delete-drop') {
    if (armed) { response.writeHead(409); response.end(); return; }
    armed = true;
  } else if (request.method === 'POST' && request.url === '/allow-deletes') {
    armed = false;
  } else if (request.method !== 'GET' || request.url !== '/status') {
    response.writeHead(404); response.end(); return;
  }
  response.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
  response.end(JSON.stringify({ fixture: 'tambola-delete-drop-v1', armed, dropped }));
});
for (const server of [proxy, control]) server.on('error', error => { console.error(`Fixture listener failed: ${error.code}`); process.exit(1); });
proxy.listen(8080, '127.0.0.1', () => console.log('Test proxy listening on loopback 8080; upstream 8081.'));
control.listen(8082, '127.0.0.1', () => console.log('Test control listening on loopback 8082.'));
