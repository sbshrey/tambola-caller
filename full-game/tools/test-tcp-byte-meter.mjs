import assert from 'node:assert/strict';
import { createConnection, createServer } from 'node:net';
import { once } from 'node:events';
import { setTimeout as delay } from 'node:timers/promises';
import test from 'node:test';
import { createByteMeter } from './tcp-byte-meter.mjs';

test('binary request and response counts preserve all bytes across a half-close', { timeout: 5_000 }, async () => {
  const request = Buffer.alloc(512 * 1024, 0xa5);
  const response = Buffer.alloc(768 * 1024, 0x5a);
  let received = 0;
  const backend = createServer({ allowHalfOpen: true }, socket => {
    socket.on('data', bytes => { assert.ok(bytes.every(byte => byte === 0xa5)); received += bytes.length; });
    socket.on('end', () => socket.end(response));
  });
  backend.listen(0, '127.0.0.1'); await once(backend, 'listening');
  const meter = await createByteMeter(backend.address().port);
  let client;
  try {
    client = createConnection({ host: '127.0.0.1', port: meter.port });
    const chunks = []; client.on('data', bytes => chunks.push(bytes));
    const ended = once(client, 'end');
    client.end(request); await ended;
    assert.equal(received, request.length);
    assert.deepEqual(Buffer.concat(chunks), response);
    const counts = meter.snapshot();
    assert.equal(counts.clientToServiceBytes, request.length);
    assert.equal(counts.serviceToClientBytes, response.length);
    assert.equal(counts.connections, 1);
    assert.equal(counts.transportErrors, 0);
    assert.equal(counts.rejectedConnections, 0);
  } finally {
    client?.destroy(); await meter.close(); await new Promise(resolve => backend.close(resolve));
  }
});

test('bounds live connections and closes owned sockets without counting cleanup as failure', { timeout: 5_000 }, async () => {
  const peers = new Set();
  const backend = createServer(socket => {
    peers.add(socket); socket.once('close', () => peers.delete(socket));
  });
  backend.listen(0, '127.0.0.1'); await once(backend, 'listening');
  const meter = await createByteMeter(backend.address().port, { connectionLimit: 1 });
  const clients = [];
  try {
    const first = createConnection({ host: '127.0.0.1', port: meter.port }); clients.push(first);
    await once(first, 'connect');
    const second = createConnection({ host: '127.0.0.1', port: meter.port }); clients.push(second);
    await once(second, 'close');
    assert.equal(meter.snapshot().rejectedConnections, 1);
    const firstClosed = once(first, 'close'); await meter.close(); await firstClosed;
    await meter.close();
    await delay(0);
    assert.equal(meter.snapshot().closed, true);
    assert.equal(meter.snapshot().transportErrors, 0);
  } finally {
    clients.forEach(client => client.destroy()); await meter.close();
    peers.forEach(peer => peer.destroy()); await new Promise(resolve => backend.close(resolve));
  }
});

test('a refused upstream closes the client and releases its slot', { timeout: 5_000 }, async () => {
  const probe = createServer(); probe.listen(0, '127.0.0.1'); await once(probe, 'listening');
  const unusedPort = probe.address().port; await new Promise(resolve => probe.close(resolve));
  const meter = await createByteMeter(unusedPort);
  let client;
  try {
    client = createConnection({ host: '127.0.0.1', port: meter.port });
    await once(client, 'close'); await delay(0);
    assert.equal(meter.snapshot().transportErrors, 1);
    assert.equal(meter.snapshot().activeConnections, 0);
  } finally { client?.destroy(); await meter.close(); }
});
