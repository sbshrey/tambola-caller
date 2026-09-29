// Keep the public address stable during backend outages; rotate only a broken tunnel.
export async function probeReady(origin, request = fetch) {
  try {
    const response = await request(origin + '/health/ready', {
      redirect: 'error', signal: AbortSignal.timeout(8000),
    });
    const body = await response.json();
    return response.ok && body.status === 'ready' &&
      [4, 5, 6, 7, 8, 9].includes(body.protocolVersion);
  } catch { return false; }
}

export function healthDecision(previousFailures, backendReady, publicReady) {
  const failures = backendReady && !publicReady ? previousFailures + 1 : 0;
  return {
    failures,
    restart: failures >= 3,
    status: !backendReady ? 'backend_unavailable' : publicReady ? 'online' : 'public_unavailable',
  };
}
