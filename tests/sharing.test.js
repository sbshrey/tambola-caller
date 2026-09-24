import test from 'node:test';
import assert from 'node:assert/strict';
import { newGame, undoNumber } from '../src/game.js';
import { numberMessage, whatsappMessageUrl, openNumberShare, copyText } from '../src/sharing.js';

test('number message includes the latest call, its words, count and five recent calls', () => {
  const state = { ...newGame(), called: [1, 2, 3, 4, 6, 7, 8, 5, 66, 13, 82, 47],
    claims: { early5: { winner: 'Private player', at: 5 } } };
  const before = structuredClone(state);
  assert.equal(numberMessage(state), '🎱 *47 — Forty seven*\nCall 12 of 90\nRecent: 47, 82, 13, 66, 5');
  assert.deepEqual(state, before);
  assert.equal(numberMessage(state).includes('Private player'), false);
  assert.equal(numberMessage(state).includes('http'), false);
});

test('messages handle the first and last call, undo and a fresh game', () => {
  assert.equal(numberMessage(newGame()), '');
  assert.equal(numberMessage({ ...newGame(), called: [1] }), '🎱 *1 — One*\nCall 1 of 90\nRecent: 1');
  const finished = { ...newGame(), called: Array.from({ length: 90 }, (_, i) => i + 1) };
  assert.equal(numberMessage(finished), '🎱 *90 — Ninety*\nCall 90 of 90\nRecent: 90, 89, 88, 87, 86');
  assert.equal(numberMessage(undoNumber(finished)), '🎱 *89 — Eighty nine*\nCall 89 of 90\nRecent: 89, 88, 87, 86, 85');
});

test('WhatsApp fallback safely encodes the full message without selecting a recipient', () => {
  const text = '🎱 *47 — Forty seven*\nCall 12 of 90\nRecent: 47, 82, 13, 66, 5 & # + ?';
  const url = new URL(whatsappMessageUrl(text));
  assert.equal(url.origin, 'https://wa.me');
  assert.equal(url.pathname, '/');
  assert.equal(url.searchParams.get('text'), text);
  assert.equal(url.hash, '');
  assert.deepEqual([...url.searchParams.keys()], ['text']);
});

test('native sharing is called immediately with only text and the correct receiver', async () => {
  let invoked = false;
  const device = {
    canShare(data) { assert.deepEqual(data, { text: 'Call 1' }); return true; },
    share(data) { assert.equal(this, device); assert.deepEqual(data, { text: 'Call 1' }); invoked = true; return Promise.resolve(); },
  };
  const result = openNumberShare('Call 1', device);
  assert.equal(invoked, true, 'no awaited operation should consume user activation before share()');
  assert.equal(await result, 'opened');
});

test('native sharing works when canShare is absent', async () => {
  assert.equal(await openNumberShare('Call 1', { async share() {} }), 'opened');
});

test('cancelling native sharing stays cancelled instead of opening another share UI', async () => {
  const device = { async share() { throw { name: 'AbortError' }; } };
  assert.equal(await openNumberShare('Call 1', device), 'cancelled');
});

test('missing, unsupported, denied and failed native sharing use the fallback', async () => {
  assert.equal(await openNumberShare('Call 1', {}), 'fallback');
  assert.equal(await openNumberShare('', { share: assert.fail }), 'fallback');
  assert.equal(await openNumberShare('Call 1', { canShare: () => false, share: assert.fail }), 'fallback');
  assert.equal(await openNumberShare('Call 1', { canShare() { throw new Error('blocked'); }, share: assert.fail }), 'fallback');
  for (const name of ['NotAllowedError', 'TypeError', 'DataError']) {
    assert.equal(await openNumberShare('Call 1', { async share() { throw { name }; } }), 'fallback');
  }
});

test('copy writes the exact message and reports unavailable or denied clipboard access', async () => {
  let copied;
  const device = { clipboard: { async writeText(text) { copied = text; } } };
  assert.equal(await copyText('🎱 *47 — Forty seven*\nCall 12 of 90', device), true);
  assert.equal(copied, '🎱 *47 — Forty seven*\nCall 12 of 90');
  assert.equal(await copyText('Call 1', {}), false);
  assert.equal(await copyText('Call 1', { clipboard: { async writeText() { throw new Error('denied'); } } }), false);
  assert.equal(await copyText('', { clipboard: { writeText: assert.fail } }), false);
});
