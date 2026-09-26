// Loopback-only transparent test proxy. Counts transport bytes; never stores payloads.
import assert from 'node:assert/strict';
import { createConnection, createServer } from 'node:net';
import { once } from 'node:events';

export async function createByteMeter(targetPort, { connectionLimit = 32 } = {}) {
  assert.ok(Number.isInteger(targetPort) && targetPort > 0 && targetPort <= 65535);
  assert.ok(Number.isInteger(connectionLimit) && connectionLimit > 0 && connectionLimit <= 128);
  const sockets = new Set();
  const started = performance.now();
  const counts = { connections: 0, activeConnections: 0, rejectedConnections: 0,
    clientToServiceBytes: 0, serviceToClientBytes: 0, transportErrors: 0 };
  let closed = false, closePromise;
  const server = createServer({ allowHalfOpen: true, pauseOnConnect: true }, client => {
    if (closed || counts.activeConnections >= connectionLimit) {
      counts.rejectedConnections++; client.destroy(); return;
    }
    counts.connections++; counts.activeConnections++;
    const service = createConnection({ host: '127.0.0.1', port: targetPort, allowHalfOpen: true });
    sockets.add(client); sockets.add(service);
    let errors = false, remaining = 2;
    const fail = () => {
      if (!errors && !closed) { counts.transportErrors++; errors = true; }
      client.destroy(); service.destroy();
    };
    for (const socket of [client, service]) {
      socket.on('error', fail);
      socket.once('close', hadError => {
        sockets.delete(socket);
        if (hadError) fail();
        // A fully closed peer cannot consume buffered output. Half-close emits
        // end first and remains open here until the opposite direction finishes.
        (socket === client ? service : client).destroy();
        if (--remaining === 0) counts.activeConnections--;
      });
    }
    client.on('data', chunk => { counts.clientToServiceBytes += chunk.length; });
    service.on('data', chunk => { counts.serviceToClientBytes += chunk.length; });
    // pipe preserves backpressure and propagates FIN after buffered writes drain.
    client.pipe(service); service.pipe(client);
    service.once('connect', () => client.resume());
  });
  server.listen(0, '127.0.0.1');
  try { await once(server, 'listening'); }
  catch (error) { server.close(); throw error; }
  const port = server.address().port;
  return {
    port,
    snapshot: () => ({ ...counts, elapsedMs: Math.round(performance.now() - started), closed }),
    close: () => {
      if (closePromise) return closePromise;
      closed = true;
      closePromise = new Promise((resolve, reject) => {
        server.close(error => error ? reject(error) : resolve());
        for (const socket of sockets) socket.destroy();
      });
      return closePromise;
    },
  };
}
