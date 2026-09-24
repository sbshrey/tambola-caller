import test from 'node:test';
import assert from 'node:assert/strict';
import { newGame, restartGame, recordClaim, removeClaim, undoNumber, parseGame } from '../src/game.js';
import { configureGame, awardShares, formatMoney, resultsMessage } from '../src/prizes.js';
import { loadGame, STORAGE_KEY, LEGACY_STORAGE_KEY } from '../src/storage.js';

function game() { return { ...newGame(), called: Array.from({ length: 15 }, (_, i) => i + 1), players: [{ id: 'a', name: 'Asha' }, { id: 'm', name: 'Meera' }, { id: 'r', name: 'Riya' }] }; }
test('tracking stays optional and every starting scheme has a ₹10 total prize', () => {
  const state = game();
  assert.ok(state.schemes.every((scheme) => scheme.prize === 10));
  const claimed = recordClaim(state, 'early5');
  assert.deepEqual(claimed.claims.early5.winnerIds, []);
  assert.equal(claimed.claims.early5.prize, 10);
  assert.deepEqual(awardShares(claimed, claimed.claims.early5), []);
});
test('one winner gets ₹10 and two winners get ₹5 each; odd rupees split to exact paise', () => {
  for (const [winners, prize, expected] of [[['a'], 10, [1000]], [['a', 'm'], 10, [500, 500]], [['a', 'm'], 11, [550, 550]], [['a', 'm'], 0, [0, 0]]]) {
    const state = recordClaim(game(), 'full', winners, prize);
    const shares = awardShares(state, state.claims.full);
    assert.deepEqual(shares.map((share) => share.paise), expected);
    assert.equal(shares.reduce((sum, share) => sum + share.paise, 0), prize * 100);
  }
  assert.equal(formatMoney(550), '₹5.50');
});
test('at most two unique, existing players can win and prizes must be whole rupees', () => {
  for (const winners of [['a', 'm', 'r'], ['a', 'a'], ['missing'], 'a']) assert.throws(() => recordClaim(game(), 'full', winners));
  for (const prize of [-1, NaN, 10.01, Infinity, 100001, '10']) assert.throws(() => recordClaim(game(), 'full', ['a'], prize));
});
test('setup can change midgame without redrawing numbers or repricing recorded awards', () => {
  const original = recordClaim(game(), 'early5', ['a', 'm']);
  const setup = structuredClone(original);
  setup.schemes.find((scheme) => scheme.id === 'early5').prize = 30;
  setup.players[0].name = 'Asha S';
  const updated = configureGame(original, setup);
  assert.deepEqual(updated.called, original.called);
  assert.equal(updated.claims.early5.prize, 10);
  assert.equal(awardShares(updated, updated.claims.early5)[0].name, 'Asha S');
  const edited = recordClaim(updated, 'early5', ['a', 'm'], 20);
  assert.equal(edited.claims.early5.at, 15);
  assert.deepEqual(awardShares(edited, edited.claims.early5).map((share) => share.paise), [1000, 1000]);
  assert.equal(original.players[0].name, 'Asha');
});
test('editing a claim preserves its original call position and undo removes only affected awards', () => {
  const base = { ...game(), called: [1, 2, 3, 4, 5] };
  const early = recordClaim(base, 'early5', ['a']);
  const later = { ...early, called: [1, 2, 3, 4, 5, 6] };
  const edited = recordClaim(later, 'early5', ['a', 'm']);
  assert.equal(edited.claims.early5.at, 5);
  assert.ok(undoNumber(edited).claims.early5);
  assert.deepEqual(undoNumber(undoNumber(edited)).claims, {});
});
test('assigned players and schemes cannot be removed or disabled until their claim is removed', () => {
  const state = recordClaim(game(), 'early5', ['a']);
  assert.throws(() => configureGame(state, { ...state, players: state.players.slice(1) }), /winner assignment/);
  assert.throws(() => configureGame(state, { ...state, schemes: state.schemes.filter((scheme) => scheme.id !== 'early5') }), /recorded claim/);
  const schemes = state.schemes.map((scheme) => scheme.id === 'early5' ? { ...scheme, enabled: false } : scheme);
  assert.throws(() => configureGame(state, { ...state, schemes }), /recorded claim/);
  assert.equal(configureGame(removeClaim(state, 'early5'), { ...state, schemes }).schemes[0].enabled, false);
});
test('custom schemes and disabled schemes obey the host-configured availability', () => {
  const base = game();
  const state = configureGame(base, { ...base, schemes: [...base.schemes, { id: 'corners', label: 'Four corners', minimum: 1, prize: 10, enabled: true }] });
  assert.equal(recordClaim(state, 'corners', ['m']).claims.corners.prize, 10);
  const disabled = configureGame(state, { ...state, schemes: state.schemes.map((scheme) => ({ ...scheme, enabled: false })) });
  assert.throws(() => recordClaim(disabled, 'full', ['a']));
  assert.equal(disabled.called.length, 15);
});
test('new round retains players, schemes and voice setting but clears calls and awards', () => {
  const state = { ...recordClaim(game(), 'full', ['a', 'm']), voiceEnabled: false };
  const next = restartGame(state);
  assert.deepEqual(next.players, state.players); assert.deepEqual(next.schemes, state.schemes);
  assert.deepEqual(next.called, []); assert.deepEqual(next.claims, {}); assert.equal(next.voiceEnabled, false);
});
test('results include per-scheme splits and correct combined totals without unrelated players', () => {
  const state = recordClaim(recordClaim(game(), 'early5', ['a', 'm']), 'top', ['a']);
  const text = resultsMessage(state);
  assert.match(text, /Early 5 — ₹10 prize\n  Asha: ₹5\n  Meera: ₹5/);
  assert.match(text, /Player totals\nAsha: ₹15\nMeera: ₹5\nTotal assigned: ₹20/);
  assert.match(text, /Full house: Not claimed/);
  assert.equal(text.includes('Riya'), false);
  assert.equal(resultsMessage(newGame()), '');
});
test('legacy games migrate without losing calls or free-text winners and retain a backup key', () => {
  const old = { version: 1, called: [1, 2, 3, 4, 5], voiceEnabled: false, claims: { early5: { winner: 'Asha & Meera', at: 5 } } };
  const raw = JSON.stringify(old);
  const values = new Map([[LEGACY_STORAGE_KEY, raw]]);
  const storage = { getItem: (key) => values.get(key), setItem: (key, value) => values.set(key, value) };
  const loaded = loadGame(storage);
  assert.equal(loaded.error, null); assert.equal(loaded.state.version, 2);
  assert.deepEqual(loaded.state.called, old.called);
  assert.equal(loaded.state.claims.early5.legacyWinner, 'Asha & Meera');
  assert.equal(values.get(LEGACY_STORAGE_KEY), raw);
  assert.deepEqual(parseGame(values.get(STORAGE_KEY)), loaded.state);
  values.set(LEGACY_STORAGE_KEY, JSON.stringify({ ...old, called: [] }));
  assert.deepEqual(loadGame(storage).state.called, old.called, 'old tabs cannot overwrite the new save');
});
test('untrusted saves reject invalid prize setup and winner assignments', () => {
  const state = recordClaim(game(), 'full', ['a', 'm']);
  for (const change of [
    (value) => { value.claims.full.winnerIds.push('r'); },
    (value) => { value.claims.full.prize = -10; },
    (value) => { value.players.pop(); value.players.pop(); },
    (value) => { value.players[1].name = 'ASHA'; },
    (value) => { value.schemes[0].prize = 0.1; },
    (value) => { value.schemes[0].minimum = 1; },
    (value) => { value.schemes[0].id = '__proto__'; },
  ]) { const invalid = structuredClone(state); change(invalid); assert.throws(() => parseGame(JSON.stringify(invalid))); }
  assert.deepEqual(parseGame(JSON.stringify(state)), state);
});
