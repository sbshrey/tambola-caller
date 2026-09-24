import { defaultSetup, validateSetup, validPrize } from './prizes.js';
export { CLAIMS } from './prizes.js';
export const TOTAL_NUMBERS = 90;

export function newGame(voiceEnabled = true) {
  return { version: 2, called: [], claims: {}, voiceEnabled, ...defaultSetup() };
}
export function restartGame(state) { return { ...newGame(state.voiceEnabled), players: state.players, schemes: state.schemes }; }

// Rejection sampling avoids bias when 2^32 is not divisible by the pool size.
export function randomIndex(size) {
  const limit = Math.floor(2 ** 32 / size) * size;
  const values = new Uint32Array(1);
  do { globalThis.crypto.getRandomValues(values); } while (values[0] >= limit);
  return values[0] % size;
}

export function drawNumber(state, pick = randomIndex) {
  if (state.called.length === TOTAL_NUMBERS) return state;
  const called = new Set(state.called);
  const remaining = Array.from({ length: TOTAL_NUMBERS }, (_, i) => i + 1)
    .filter((n) => !called.has(n));
  const index = pick(remaining.length);
  if (!Number.isInteger(index) || index < 0 || index >= remaining.length) {
    throw new RangeError('Random index is outside the remaining numbers.');
  }
  return { ...state, called: [...state.called, remaining[index]] };
}

export function undoNumber(state) {
  if (!state.called.length) return state;
  const called = state.called.slice(0, -1);
  const claims = Object.fromEntries(Object.entries(state.claims)
    .filter(([, claim]) => claim.at <= called.length));
  return { ...state, called, claims };
}

export function recordClaim(state, type, winnerIds = [], prize, keepLegacy = false) {
  const scheme = state.schemes.find((item) => item.id === type && item.enabled);
  if (!scheme || state.called.length < scheme.minimum) {
    throw new Error('There are not enough called numbers for this claim.');
  }
  if (!Array.isArray(winnerIds) || winnerIds.length > 2 || new Set(winnerIds).size !== winnerIds.length || winnerIds.some((id) => !state.players.some((player) => player.id === id))) throw Error('Select up to two different players.');
  const amount = prize ?? state.claims[type]?.prize ?? scheme.prize;
  if (!validPrize(amount)) throw Error('Enter a whole-rupee prize from ₹0 to ₹1,00,000.');
  const previous = state.claims[type];
  return { ...state, claims: { ...state.claims, [type]: { winnerIds: [...winnerIds], prize: amount,
    at: previous?.at ?? state.called.length,
    ...(keepLegacy && !winnerIds.length && previous?.legacyWinner ? { legacyWinner: previous.legacyWinner } : {}) } } };
}

export function removeClaim(state, type) {
  const claims = { ...state.claims };
  delete claims[type];
  return { ...state, claims };
}

// Stored data is untrusted: accept a complete valid game or reset safely.
export function parseGame(raw) {
  const state = JSON.parse(raw);
  if (!state || ![1, 2].includes(state.version) || typeof state.voiceEnabled !== 'boolean'
    || !Array.isArray(state.called) || state.called.length > TOTAL_NUMBERS
    || state.called.some((n) => !Number.isInteger(n) || n < 1 || n > TOTAL_NUMBERS)
    || new Set(state.called).size !== state.called.length
    || !state.claims || typeof state.claims !== 'object' || Array.isArray(state.claims)) {
    throw new Error('Invalid saved game.');
  }
  const setup = state.version === 1 ? defaultSetup() : validateSetup(state);
  const claims = {};
  for (const [type, claim] of Object.entries(state.claims)) {
    const scheme = setup.schemes.find((item) => item.id === type && item.enabled);
    if (!scheme || !claim || !Number.isInteger(claim.at) || claim.at < scheme.minimum || claim.at > state.called.length) {
      throw new Error('Invalid saved claim.');
    }
    if (state.version === 1) {
      if (typeof claim.winner !== 'string' || claim.winner.length > 60) throw Error('Invalid saved winner.');
      claims[type] = { winnerIds: [], prize: 10, at: claim.at, ...(claim.winner ? { legacyWinner: claim.winner } : {}) };
    } else {
      if (!Array.isArray(claim.winnerIds) || claim.winnerIds.length > 2 || new Set(claim.winnerIds).size !== claim.winnerIds.length || claim.winnerIds.some((id) => !setup.players.some((player) => player.id === id)) || !validPrize(claim.prize) || (claim.legacyWinner !== undefined && (typeof claim.legacyWinner !== 'string' || claim.legacyWinner.length > 60 || claim.winnerIds.length))) throw Error('Invalid saved winners or prize.');
      claims[type] = { winnerIds: [...claim.winnerIds], prize: claim.prize, at: claim.at, ...(claim.legacyWinner ? { legacyWinner: claim.legacyWinner } : {}) };
    }
  }
  return { version: 2, called: [...state.called], claims, voiceEnabled: state.voiceEnabled, ...setup };
}

const ONES = ['Zero', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight',
  'Nine', 'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen',
  'Seventeen', 'Eighteen', 'Nineteen'];
const TENS = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];
export function numberWords(number) {
  if (!Number.isInteger(number) || number < 1 || number > TOTAL_NUMBERS) return '';
  return number < 20 ? ONES[number]
    : `${TENS[Math.floor(number / 10)]}${number % 10 ? ` ${ONES[number % 10].toLowerCase()}` : ''}`;
}
