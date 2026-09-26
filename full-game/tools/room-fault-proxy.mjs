// Local test fixture only. Fixed loopback destinations; never logs headers, tokens or bodies.
import http from 'node:http';
import net from 'node:net';

const upstreamPort = 8081;
let armed = false;
let dropped = 0;
let commandArmed = false;
let commandsDropped = 0;
let matchArmed = false;
let matchesDropped = 0;
let refillArmed = false;
let refillsDropped = 0;
let sessionArmed = false;
let sessionsDropped = 0;
let rejectWallet = false;
const proxy = http.createServer((request, response) => {
  if (rejectWallet && request.method === 'GET' && request.url === '/v1/wallet') {
    rejectWallet = false; request.resume();
    response.writeHead(401, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    response.end(JSON.stringify({ code: 'unauthorized', message: 'QA simulated session rejection.' }));
    return;
  }
  const upstream = http.request({ hostname: '127.0.0.1', port: upstreamPort, method: request.method, path: request.url, headers: request.headers }, result => {
    const deletion = armed && request.method === 'POST' && request.url === '/v1/guests/me/delete';
    const command = commandArmed && request.method === 'POST' && /^\/v1\/rooms\/[A-HJ-NP-Z2-9]{8}\/commands$/.test(request.url);
    const match = matchArmed && request.method === 'POST' && request.url === '/v1/matches';
    const session = sessionArmed && request.method === 'POST' && request.url === '/v1/guests/me/session';
    const refill = refillArmed && request.method === 'POST' && request.url === '/v1/wallet/refill';
    if ((deletion || command || match || session || refill) && result.statusCode === 200) {
      // The upstream transaction has committed; discard its entire response before disconnecting.
      result.resume();
      result.once('end', () => { if (deletion) dropped++; else if (command) commandsDropped++; else if (match) matchesDropped++; else if (refill) refillsDropped++; else sessionsDropped++; response.destroy(); });
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
  } else if (request.method === 'POST' && request.url === '/arm-command-drop') {
    if (commandArmed) { response.writeHead(409); response.end(); return; }
    commandArmed = true;
  } else if (request.method === 'POST' && request.url === '/allow-commands') {
    commandArmed = false;
  } else if (request.method === 'POST' && request.url === '/arm-match-drop') {
    if (matchArmed) { response.writeHead(409); response.end(); return; }
    matchArmed = true;
  } else if (request.method === 'POST' && request.url === '/allow-matches') {
    matchArmed = false;
  } else if (request.method === 'POST' && request.url === '/arm-refill-drop') {
    if (refillArmed) { response.writeHead(409); response.end(); return; }
    refillArmed = true;
  } else if (request.method === 'POST' && request.url === '/allow-refills') {
    refillArmed = false;
  } else if (request.method === 'POST' && request.url === '/arm-session-drop') {
    if (sessionArmed) { response.writeHead(409); response.end(); return; }
    sessionArmed = true;
  } else if (request.method === 'POST' && request.url === '/allow-sessions') {
    sessionArmed = false;
  } else if (request.method === 'POST' && request.url === '/reject-next-wallet') {
    rejectWallet = true;
  } else if (request.method !== 'GET' || request.url !== '/status') {
    response.writeHead(404); response.end(); return;
  }
  response.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
  response.end(JSON.stringify({ fixture: 'tambola-delete-drop-v1', armed, dropped, commandArmed, commandsDropped, matchArmed, matchesDropped, refillArmed, refillsDropped, sessionArmed, sessionsDropped, rejectWallet }));
});
for (const server of [proxy, control]) server.on('error', error => { console.error(`Fixture listener failed: ${error.code}`); process.exit(1); });
proxy.listen(8080, '127.0.0.1', () => console.log('Test proxy listening on loopback 8080; upstream 8081.'));
control.listen(8082, '127.0.0.1', () => console.log('Test control listening on loopback 8082.'));
