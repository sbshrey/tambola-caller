import test from 'node:test';
import assert from 'node:assert/strict';
import { createVoice } from '../src/voice.js';

function browserWith(voices = []) {
  const calls = [];
  return { calls, SpeechSynthesisUtterance: class { constructor(text) { this.text = text; } },
    speechSynthesis: { cancel() { calls.push('cancel'); }, getVoices: () => voices,
      speak(utterance) { calls.push(utterance); } } };
}
test('speech prefers an installed Indian English voice and cancels stale announcements', () => {
  const local = { lang: 'en-IN', localService: true };
  const browser = browserWith([{ lang: 'en-US', localService: true }, local]);
  const voice = createVoice(assert.fail, browser);
  voice.announce(47);
  assert.equal(browser.calls[0], 'cancel');
  assert.equal(browser.calls[1].voice, local);
  assert.equal(browser.calls[1].text, 'Number 47. Forty seven.');
  voice.announce(12);
  assert.equal(browser.calls[2], 'cancel');
  assert.equal(browser.calls[3].text, 'Number 12. Twelve.');
});

function withAudio(browser = browserWith()) {
  const pending = [];
  const player = { preload: '', src: '', onerror: null, paused: false, loads: 0,
    pause() { this.paused = true; }, removeAttribute() { this.src = ''; }, load() { this.loads++; },
    play() { this.paused = false; return new Promise((resolve, reject) => pending.push({ resolve, reject })); } };
  browser.Audio = class { constructor() { return player; } };
  return { browser, player, pending };
}
test('number clips play immediately; repeat, stop and speech cancel the previous clip', () => {
  const { browser, player, pending } = withAudio();
  const voice = createVoice(assert.fail, browser);
  voice.announce(22);
  assert.ok(player.src.endsWith('/22.mp3'));
  assert.equal(player.paused, false);
  assert.equal(pending.length, 1);
  voice.announce(7);
  assert.ok(player.src.endsWith('/07.mp3'));
  voice.stop();
  assert.equal(player.paused, true);
  assert.equal(player.src, '');
  voice.announce(90);
  voice.speak('Congratulations!');
  assert.equal(player.paused, true);
  assert.equal(browser.calls.at(-1).text, 'Congratulations!');
});
test('a failed clip falls back once; stale failures cannot interrupt a newer number', async () => {
  const { browser, player, pending } = withAudio();
  const voice = createVoice(assert.fail, browser);
  voice.announce(22);
  const oldError = player.onerror;
  voice.announce(23);
  pending[0].reject(Error('late error'));
  oldError();
  await Promise.resolve();
  assert.equal(browser.calls.filter((call) => call.text).length, 0);
  const currentError = player.onerror;
  pending[1].reject(Error('offline'));
  currentError();
  await Promise.resolve();
  assert.deepEqual(browser.calls.filter((call) => call.text).map((call) => call.text), ['Number 23. Twenty three.']);
});
test('audio works without device speech, and a failed current clip reports the error', () => {
  const { browser, player } = withAudio({});
  let errors = 0;
  const voice = createVoice(() => errors++, browser);
  assert.equal(voice.supported, true);
  voice.announce(7);
  player.onerror();
  assert.equal(errors, 1);
  assert.equal(player.paused, true);
});
test('local English is preferred to a network-only Indian voice for offline use', () => {
  const local = { lang: 'en-GB', localService: true };
  const browser = browserWith([{ lang: 'en-IN', localService: false }, local]);
  createVoice(assert.fail, browser).announce(1);
  assert.equal(browser.calls.at(-1).voice, local);
});
test('unsupported speech is harmless and current errors reach the UI', () => {
  const unsupported = createVoice(assert.fail, {});
  assert.equal(unsupported.supported, false);
  unsupported.announce(1);
  unsupported.stop();
  let errors = 0;
  const browser = browserWith();
  const voice = createVoice(() => errors++, browser);
  voice.announce(10);
  const first = browser.calls.at(-1);
  first.onerror({ error: 'interrupted' });
  assert.equal(errors, 0);
  voice.announce(20);
  first.onerror({ error: 'network' });
  assert.equal(errors, 0);
  browser.calls.at(-1).onerror({ error: 'network' });
  assert.equal(errors, 1);
});
