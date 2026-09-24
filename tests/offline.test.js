import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile, access } from 'node:fs/promises';

async function worker() {
  const listeners = {};
  const deleted = [];
  const assets = [];
  const entries = new Map();
  const scope = 'https://example.test/tambola/';
  let claimed = false;
  const cache = { async addAll(urls) { assets.push(...urls); }, async match(key) {
    return entries.get(typeof key === 'string' ? key : key.url);
  } };
  vm.runInNewContext(await readFile(new URL('../sw.js', import.meta.url), 'utf8'), {
    URL, self: { registration: { scope }, clients: { async claim() { claimed = true; } },
      addEventListener: (name, handler) => { listeners[name] = handler; } },
    caches: { async open() { return cache; }, async keys() { return [`tambola-caller-${scope}-old`, `tambola-caller-${scope}-v1.0.1`, 'another-app']; },
      async delete(key) { deleted.push(key); } },
    fetch: async () => { throw new Error('offline'); },
  });
  return { listeners, deleted, assets, entries, get claimed() { return claimed; } };
}
test('offline install precaches every asset and each asset exists', async () => {
  const w = await worker();
  let done;
  w.listeners.install({ waitUntil(promise) { done = promise; } });
  await done;
  assert.ok(w.assets.includes('./index.html'));
  assert.ok(w.assets.includes('./src/game.js'));
  for (const file of w.assets) await access(new URL(`../${file}`, import.meta.url));
});
test('activation only removes this app scope’s older caches', async () => {
  const w = await worker();
  let done;
  w.listeners.activate({ waitUntil(promise) { done = promise; } });
  await done;
  assert.deepEqual(w.deleted, ['tambola-caller-https://example.test/tambola/-old']);
  assert.equal(w.claimed, true);
});
test('offline navigations with a query and module requests return cached assets', async () => {
  const w = await worker();
  w.entries.set('./index.html', 'app shell');
  w.entries.set('https://example.test/tambola/src/game.js', 'game module');
  for (const [url, mode, expected] of [
    ['https://example.test/tambola/?shared=1', 'navigate', 'app shell'],
    ['https://example.test/tambola/src/game.js', 'cors', 'game module'],
  ]) {
    let response;
    w.listeners.fetch({ request: { url, mode, method: 'GET' }, respondWith(promise) { response = promise; } });
    assert.equal(await response, expected);
  }
});
test('worker leaves unrelated origins, paths and mutations alone', async () => {
  const w = await worker();
  for (const [url, method] of [['https://other.test/', 'GET'], ['https://example.test/other/', 'GET'], ['https://example.test/tambola/', 'POST']]) {
    w.listeners.fetch({ request: { url, method }, respondWith() { assert.fail('Unrelated request intercepted'); } });
  }
});
