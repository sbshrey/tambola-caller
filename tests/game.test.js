import test from 'node:test';
import assert from 'node:assert/strict';
import { CLAIMS, newGame, drawNumber, undoNumber, recordClaim, removeClaim, parseGame, numberWords } from '../src/game.js';
import { defaultSetup } from '../src/prizes.js';
import { STORAGE_KEY, loadGame, saveGame } from '../src/storage.js';

function gameWith(count) {
  let game = { ...newGame(), players: [{ id: 'asha', name: 'Asha' }, { id: 'meera', name: 'Meera' }] };
  for (let index = 0; index < count; index++) game = drawNumber(game, () => 0);
  return game;
}

test('a fresh game has voice on and an empty board', () => {
  assert.deepEqual(newGame(), { version: 2, called: [], claims: {}, voiceEnabled: true, callLanguage: 'en', ...defaultSetup() });
  assert.equal(newGame(false).voiceEnabled, false);
});
test('each of 90 numbers is called exactly once and the game then stops', () => {
  for (let round = 0; round < 40; round++) {
    let game = newGame();
    for (let index = 0; index < 90; index++) {
      const previous = game;
      game = drawNumber(game);
      assert.equal(previous.called.length, index);
      assert.equal(game.called.length, index + 1);
    }
    assert.deepEqual([...game.called].sort((a, b) => a - b), Array.from({ length: 90 }, (_, i) => i + 1));
    assert.equal(drawNumber(game), game);
  }
});
test('draw takes both edges of the remaining pool and rejects invalid indexes', () => {
  assert.deepEqual(drawNumber(newGame(), () => 0).called, [1]);
  assert.deepEqual(drawNumber(newGame(), (length) => length - 1).called, [90]);
  for (const value of [-1, 90, 0.5, NaN]) assert.throws(() => drawNumber(newGame(), () => value));
});
test('undo restores the only remaining number and cannot go below zero', () => {
  const full = gameWith(90);
  const undone = undoNumber(full);
  assert.equal(undone.called.length, 89);
  assert.equal(drawNumber(undone).called.at(-1), 90);
  assert.deepEqual(undoNumber(newGame()), newGame());
});
test('claim validation enforces the earliest possible call', () => {
  for (const [type, config] of Object.entries(CLAIMS)) {
    assert.throws(() => recordClaim(gameWith(config.minimum - 1), type));
    assert.deepEqual(recordClaim(gameWith(config.minimum), type, ['asha']).claims[type].winnerIds, ['asha']);
  }
  assert.throws(() => recordClaim(gameWith(90), '__proto__'));
});
test('undo removes claims on the undone call while retaining earlier claims', () => {
  const early = recordClaim(gameWith(5), 'early5', ['asha']);
  const next = recordClaim(drawNumber(early, () => 0), 'top', ['meera']);
  assert.deepEqual(undoNumber(next).claims, early.claims);
  assert.deepEqual(undoNumber(undoNumber(next)).claims, {});
  assert.deepEqual(removeClaim(next, 'top').claims, early.claims);
  assert.deepEqual(next.claims.top.winnerIds, ['meera']);
});
test('a saved game round-trips numbers, claims and the voice preference', () => {
  const original = { ...recordClaim(gameWith(15), 'full', ['asha', 'meera']), voiceEnabled: false };
  assert.deepEqual(parseGame(JSON.stringify(original)), original);
});
test('corrupt and incompatible saves are rejected instead of introducing bad calls', () => {
  const bad = [null, {}, { ...newGame(), version: 3 }, { ...newGame(), called: [1, 1] },
    { ...newGame(), called: [0] }, { ...newGame(), called: [91] }, { ...newGame(), called: [1.5] },
    { ...newGame(), called: ['4'] }, { ...newGame(), voiceEnabled: 'yes' },
    { ...newGame(), claims: [] }, { ...newGame(), claims: { early5: { winner: 'Asha', at: 5 } } },
    { ...gameWith(5), claims: { early5: { winner: 'Asha', at: 6 } } },
    { ...gameWith(5), claims: { unknown: { winner: '', at: 5 } } }];
  for (const value of bad) assert.throws(() => parseGame(JSON.stringify(value)));
  assert.throws(() => parseGame('{broken'));
});
test('storage failures preserve a usable game and signal unsaved progress', () => {
  const blocked = { getItem() { throw new Error('denied'); }, setItem() { throw new Error('quota'); } };
  assert.deepEqual(loadGame(blocked).state, newGame());
  assert.ok(loadGame(blocked).error);
  assert.equal(saveGame(blocked, gameWith(3)), false);
  assert.equal(saveGame(undefined, newGame()), false);
});
test('storage uses only the app key and resumes a complete game', () => {
  const values = new Map([['unrelated', 'keep']]);
  const storage = { getItem: (key) => values.get(key), setItem: (key, value) => values.set(key, value) };
  assert.deepEqual(loadGame(storage).state, newGame());
  const game = gameWith(90);
  assert.equal(saveGame(storage, game), true);
  assert.deepEqual(loadGame(storage).state, game);
  assert.ok(values.has(STORAGE_KEY));
  assert.equal(values.get('unrelated'), 'keep');
  values.set(STORAGE_KEY, '{broken');
  assert.ok(loadGame(storage).error);
});
test('number words handle units, teens, tens, and compound numbers', () => {
  const examples = { 1: 'One', 9: 'Nine', 10: 'Ten', 13: 'Thirteen', 19: 'Nineteen', 20: 'Twenty', 40: 'Forty', 47: 'Forty seven', 80: 'Eighty', 89: 'Eighty nine', 90: 'Ninety' };
  for (const [number, words] of Object.entries(examples)) assert.equal(numberWords(Number(number)), words);
  for (let number = 1; number <= 90; number++) assert.ok(numberWords(number));
  for (const number of [0, 91, -1, 2.2, NaN]) assert.equal(numberWords(number), '');
});
