const CACHE_PREFIX = `tambola-caller-${self.registration.scope}-`;
const CACHE = `${CACHE_PREFIX}v1.5.0`;
const ASSETS = ['./', './index.html', './styles.css', './src/app.js', './src/game.js',
  './src/storage.js', './src/voice.js', './src/sharing.js', './src/audio.js', './src/board-image.js', './src/prizes.js', './src/prize-ui.js', './src/languages.js', './src/calls.js', './src/call-phrases.js', './manifest.webmanifest', './icon.svg',
  './icons/icon-192.png', './icons/icon-512.png',
  ...['', 'hi/', 'hinglish/'].flatMap((folder) => Array.from({ length: 90 }, (_, index) => `./audio/${folder}numbers/${String(index + 1).padStart(2, '0')}.mp3`))];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(ASSETS)));
  // An update waits until the old app closes, keeping an active game consistent.
});
self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter((key) => key.startsWith(CACHE_PREFIX) && key !== CACHE)
      .map((key) => caches.delete(key)));
    await self.clients.claim();
  })());
});
self.addEventListener('message', (event) => {
  if (event.data?.type === 'offline-version') event.ports[0]?.postMessage({ version: '1.5.0', audioClips: 270 });
});

// Mobile media players can request byte ranges even for short cached clips.
async function audioRange(response, header) {
  const bytes = await response.arrayBuffer();
  const match = /^bytes=(\d*)-(\d*)$/.exec(header);
  let start = Number(match?.[1] || 0);
  let end = match?.[2] ? Number(match[2]) : bytes.byteLength - 1;
  if (match && !match[1] && match[2]) { start = Math.max(0, bytes.byteLength - Number(match[2])); end = bytes.byteLength - 1; }
  if (!match || (!match[1] && !match[2]) || start > end || start >= bytes.byteLength) {
    return new Response(null, { status: 416, headers: { 'Content-Range': `bytes */${bytes.byteLength}` } });
  }
  end = Math.min(end, bytes.byteLength - 1);
  return new Response(bytes.slice(start, end + 1), { status: 206, headers: {
    'Content-Type': 'audio/mpeg', 'Accept-Ranges': 'bytes',
    'Content-Length': String(end - start + 1), 'Content-Range': `bytes ${start}-${end}/${bytes.byteLength}`,
  } });
}
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  const scope = new URL(self.registration.scope);
  if (event.request.method !== 'GET' || url.origin !== scope.origin || !url.pathname.startsWith(scope.pathname)) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    // Ignore query parameters on navigation so a shared app link also works offline.
    const cached = event.request.mode === 'navigate' && !url.pathname.endsWith('.mp3')
      ? await cache.match('./index.html') : await cache.match(event.request);
    const range = event.request.headers?.get('range');
    if (cached && range && url.pathname.endsWith('.mp3')) return audioRange(cached, range);
    return cached || fetch(event.request);
  })());
});
