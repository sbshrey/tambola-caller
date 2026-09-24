const CACHE_PREFIX = `tambola-caller-${self.registration.scope}-`;
const CACHE = `${CACHE_PREFIX}v1.0.1`;
const ASSETS = ['./', './index.html', './styles.css', './src/app.js', './src/game.js',
  './src/storage.js', './src/voice.js', './manifest.webmanifest', './icon.svg',
  './icons/icon-192.png', './icons/icon-512.png'];

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
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  const scope = new URL(self.registration.scope);
  if (event.request.method !== 'GET' || url.origin !== scope.origin || !url.pathname.startsWith(scope.pathname)) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    // Ignore query parameters on navigation so a shared app link also works offline.
    const cached = event.request.mode === 'navigate'
      ? await cache.match('./index.html') : await cache.match(event.request);
    return cached || fetch(event.request);
  })());
});
