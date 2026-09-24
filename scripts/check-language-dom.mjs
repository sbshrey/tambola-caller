// Optional DOM integration check (no browser or real device APIs).
// Set TAMBOLA_JSDOM to an installed jsdom package path. Production has no dependency.
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { JSDOM } = require(process.env.TAMBOLA_JSDOM || 'jsdom');
const nativeCanvas = process.env.TAMBOLA_CANVAS ? require(process.env.TAMBOLA_CANVAS) : null;
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
window.HTMLCanvasElement.prototype.getContext = function () {
  if (!nativeCanvas) return new Proxy({}, { get: () => () => {} });
  this.drawing = nativeCanvas.createCanvas(this.width, this.height);
  return this.drawing.getContext('2d');
};
window.HTMLCanvasElement.prototype.toBlob = function (callback) { callback(new Blob([this.drawing ? this.drawing.toBuffer('image/png') : 'PNG'], { type: 'image/png' })); };
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

const imageButtons = ['share-number-image', 'share-recent-image', 'share-board-image', 'share-image'];
const files = [];
navigator.canShare = ({ files }) => files[0].type === 'image/png';
navigator.share = (payload) => { files.push(payload.files[0]); return Promise.resolve(); };
for (const [id, name, width, height] of [
  ['share-number-image', 'Tambola-number-call-3-number-47.png', 1080, 1080],
  ['share-recent-image', 'Tambola-recent-call-3-number-47.png', 1080, 720],
  ['share-board-image', 'Tambola-board-call-3-number-47.png', 1080, 1140],
  ['share-image', 'Tambola-call-3-number-47.png', 1080, 1540],
]) {
  assert.equal($(id).disabled, false);
  $(id).click();
  assert.equal(files.at(-1).name, name, 'native sharing must be called before yielding from the tap');
  assert.ok(imageButtons.every((id) => $(id).disabled), 'block overlapping shares');
  if (nativeCanvas) {
    const png = Buffer.from(await files.at(-1).arrayBuffer());
    assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    assert.equal(png.readUInt32BE(16), width);
    assert.equal(png.readUInt32BE(20), height);
  }
  await flush();
  assert.equal($('current-number').textContent, '47');
  assert.equal($('called-count').textContent, '3');
  assert.equal($('image-share-dialog').open, false);
}
// Cancellation stays quiet. Unsupported native sharing previews the chosen format.
navigator.share = async () => { throw { name: 'AbortError' }; };
$('share-board-image').click(); await flush();
assert.equal($('image-share-dialog').open, false);
navigator.canShare = () => false;
for (const [id, title, word] of [
  ['share-number-image', 'Share current number', 'number'],
  ['share-recent-image', 'Share recent numbers', 'recent'],
  ['share-board-image', 'Share tambola board', 'board'],
]) {
  $(id).click(); await flush();
  assert.equal($('image-share-dialog').open, true);
  assert.equal($('image-share-title').textContent, title);
  assert.equal($('download-image').download, `Tambola-${word}-call-3-number-47.png`);
  assert.match($('image-preview').src, /^blob:/);
  $('image-share-dialog').close();
  assert.equal($('image-preview').hasAttribute('src'), false);
  assert.equal($('download-image').hasAttribute('href'), false);
}
// Open a preview, undo, then switch language: both must invalidate the old snapshot.
$('share-number-image').click(); await flush();
$('undo').click();
assert.equal($('image-share-dialog').open, false);
assert.equal($('share-number-image').disabled, true);
await flush();
$('share-number-image').click(); await flush();
assert.match($('download-image').download, /call-2-number-22/);
$('call-language').value = 'hinglish'; $('call-language').dispatchEvent(new window.Event('change'));
assert.equal($('image-share-dialog').open, false);
await flush();
$('share-number-image').click(); await flush();
assert.match($('image-preview').alt, /Do chhoti battakhein/);
$('image-share-dialog').close();
// A slow native failure from an earlier call must not reopen a stale preview.
let resolveShare;
navigator.canShare = () => true;
navigator.share = () => new Promise((resolve, reject) => { resolveShare = () => reject({ name: 'NotAllowedError' }); });
$('share-recent-image').click();
$('undo').click(); await flush();
resolveShare(); await flush();
assert.equal($('image-share-dialog').open, false);
assert.equal($('current-number').textContent, '1');
$('new-game').click(); $('confirm-dialog').returnValue = 'confirm'; $('confirm-dialog').close();
await flush();
assert.ok(imageButtons.every((id) => $(id).disabled));
assert.equal($('download-image').hasAttribute('href'), false);
assert.deepEqual(JSON.parse(window.localStorage.getItem(STORAGE_KEY)).players, initial.players);
console.log(`Image integration passed: 4 formats, immediate native payloads${nativeCanvas ? ' with real PNG bytes/dimensions' : ''}, cancellation, format-specific fallback, URL cleanup, undo, language changes, stale errors, reset.`);
for (const timer of timers) clearTimeout(timer);
globalThis.setTimeout = originalSetTimeout;
dom.window.close();
