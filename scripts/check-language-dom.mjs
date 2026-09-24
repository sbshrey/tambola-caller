// Optional DOM integration check (no browser or real device APIs).
// Set TAMBOLA_JSDOM to an installed jsdom package path. Production has no dependency.
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { JSDOM } = require(process.env.TAMBOLA_JSDOM || 'jsdom');
const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
const dom = new JSDOM(html, { url: 'https://example.test/tambola/', pretendToBeVisual: true });
const { window } = dom;
const timers = new Set();
const originalSetTimeout = globalThis.setTimeout;
globalThis.setTimeout = (...args) => { const timer = originalSetTimeout(...args); timers.add(timer); return timer; };
globalThis.window = window; globalThis.document = window.document; globalThis.location = window.location;
Object.defineProperty(globalThis, 'navigator', { value: window.navigator, configurable: true });
globalThis.Option = window.Option;
const selections = [];
window.HTMLMediaElement.prototype.load = function () {};
window.HTMLMediaElement.prototype.pause = function () {};
window.HTMLMediaElement.prototype.play = function () { selections.push(this.src); return Promise.resolve(); };
window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
window.HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new window.Event('close')); };
window.HTMLCanvasElement.prototype.getContext = () => new Proxy({}, { get: () => () => {} });
window.HTMLCanvasElement.prototype.toBlob = function (callback) { callback(new Blob(['PNG'], { type: 'image/png' })); };
const downloads = [];
globalThis.fetch = async (url) => { downloads.push(url); return new Response('test-mp3', { headers: { 'Content-Type': 'audio/mpeg' } }); };
const { newGame } = await import('../src/game.js');
const { STORAGE_KEY } = await import('../src/storage.js');
const initial = { ...newGame(false), called: [1, 22, 47], players: [{ id: 'p', name: 'Asha' }] };
window.localStorage.setItem(STORAGE_KEY, JSON.stringify(initial));
await import('../src/app.js');
const $ = (id) => window.document.getElementById(id);
const flush = () => new Promise((resolve) => setTimeout(resolve, 10));
await flush();
assert.equal($('call-language').value, 'en');
assert.equal($('current-number').textContent, '47');
assert.equal($('call-phrase').textContent, 'Year of Independence');
for (const [language, phrase, words, folder] of [
  ['hi', 'आज़ादी का साल', 'सैंतालीस', 'hi/'],
  ['hinglish', 'Aazaadi ka saal', 'Forty seven', 'hinglish/'],
  ['en', 'Year of Independence', 'Forty seven', ''],
]) {
  $('call-language').value = language;
  $('call-language').dispatchEvent(new window.Event('change'));
  await flush();
  assert.equal($('call-phrase').textContent, phrase);
  assert.equal($('number-words').textContent, words);
  assert.ok($('download-audio').href.endsWith(`/audio/${folder}numbers/47.mp3`));
  assert.equal($('share-audio').disabled, false);
  $('repeat').click();
  assert.ok(selections.at(-1).endsWith(`/audio/${folder}numbers/47.mp3`));
  $('preview-voice').click();
  assert.ok(selections.at(-1).endsWith(`/audio/${folder}numbers/22.mp3`));
  const saved = JSON.parse(window.localStorage.getItem(STORAGE_KEY));
  assert.deepEqual(saved.called, initial.called);
  assert.deepEqual(saved.players, initial.players);
  assert.equal(saved.callLanguage, language);
  assert.equal($('current-number').textContent, '47');
}
// Rapid selection must leave only the final language ready for sharing.
for (const language of ['hi', 'hinglish', 'hi']) {
  $('call-language').value = language; $('call-language').dispatchEvent(new window.Event('change'));
}
await flush();
assert.ok($('download-audio').href.endsWith('/hi/numbers/47.mp3'));
assert.equal($('number-words').textContent, 'सैंतालीस');
assert.equal($('share-audio').disabled, false);
console.log('DOM integration passed: selector, preview/repeat, language-specific downloads, saved preference, rapid switching, unchanged game.');
for (const timer of timers) clearTimeout(timer);
globalThis.setTimeout = originalSetTimeout;
dom.window.close();
