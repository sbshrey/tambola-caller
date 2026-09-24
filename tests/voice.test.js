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
