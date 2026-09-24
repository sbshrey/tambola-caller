export const TOTAL_NUMBERS = 90;
export const CLAIMS = Object.freeze({
  early5: { label: 'Early 5', minimum: 5 },
  top: { label: 'Top line', minimum: 5 },
  middle: { label: 'Middle line', minimum: 5 },
  bottom: { label: 'Bottom line', minimum: 5 },
  full: { label: 'Full house', minimum: 15 },
});

export function newGame(voiceEnabled = true) {
  return { version: 1, called: [], claims: {}, voiceEnabled };
}

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

export function recordClaim(state, type, winner = '') {
  if (!Object.hasOwn(CLAIMS, type) || state.called.length < CLAIMS[type].minimum) {
    throw new Error('There are not enough called numbers for this claim.');
  }
  return { ...state, claims: { ...state.claims,
    [type]: { winner: winner.trim().slice(0, 60), at: state.called.length } } };
}

export function removeClaim(state, type) {
  const claims = { ...state.claims };
  delete claims[type];
  return { ...state, claims };
}

// Stored data is untrusted: accept a complete valid game or reset safely.
export function parseGame(raw) {
  const state = JSON.parse(raw);
  if (!state || state.version !== 1 || typeof state.voiceEnabled !== 'boolean'
    || !Array.isArray(state.called) || state.called.length > TOTAL_NUMBERS
    || state.called.some((n) => !Number.isInteger(n) || n < 1 || n > TOTAL_NUMBERS)
    || new Set(state.called).size !== state.called.length
    || !state.claims || typeof state.claims !== 'object' || Array.isArray(state.claims)) {
    throw new Error('Invalid saved game.');
  }
  const claims = {};
  for (const [type, claim] of Object.entries(state.claims)) {
    if (!Object.hasOwn(CLAIMS, type) || !claim || typeof claim.winner !== 'string'
      || claim.winner.length > 60 || !Number.isInteger(claim.at)
      || claim.at < CLAIMS[type].minimum || claim.at > state.called.length) {
      throw new Error('Invalid saved claim.');
    }
    claims[type] = { winner: claim.winner, at: claim.at };
  }
  return { version: 1, called: [...state.called], claims, voiceEnabled: state.voiceEnabled };
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
