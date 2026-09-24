import test from 'node:test';
import assert from 'node:assert/strict';
import { getCall, callsForLanguage } from '../src/calls.js';
import { HINDI_NUMBERS, numberWords, normalizeLanguage } from '../src/languages.js';
import { newGame, parseGame, restartGame, recordClaim } from '../src/game.js';
import { clipUrl, createClipLoader } from '../src/audio.js';
import { createVoice } from '../src/voice.js';
import { imageSummary, createImagePreparer } from '../src/board-image.js';
import { numberMessage } from '../src/sharing.js';
import { requestFor, selectCalls } from '../scripts/generate-audio.mjs';
import { detectsNumber } from '../scripts/validate-audio.mjs';

test('all 90 numbers have a saying and an explicit full-number ending in all three languages', () => {
  assert.equal(HINDI_NUMBERS.length, 91);
  for (const language of ['en', 'hi', 'hinglish']) {
    const calls = callsForLanguage(language);
    assert.equal(calls.length, 90);
    assert.equal(new Set(calls.map((call) => call.number)).size, 90);
    for (const call of calls) {
      assert.ok(call.phrase.trim(), `${language}:${call.number}`);
      assert.ok(call.text.endsWith(numberWords(call.number, language).toLowerCase() + (language === 'hi' ? '।' : '.')));
      assert.equal(call.text.includes('undefined'), false);
    }
  }
  assert.equal(getCall(47).phrase, 'Year of Independence');
  assert.equal(getCall(47, 'hi').phrase, 'आज़ादी का साल');
  assert.equal(getCall(56, 'hi').phrase, 'छप्पन भोग');
  assert.match(getCall(56, 'hinglish').text, /छप्पन भोग\. Number five, six\. fifty six\./);
  assert.equal(getCall(56, 'hinglish').phrase, 'Chhappan bhog');
});

test('Hindi number names distinguish adjacent and easily confused numbers', () => {
  for (const [number, expected] of [[39, 'उनतालीस'], [47, 'सैंतालीस'], [59, 'उनसठ'], [60, 'साठ'], [66, 'छियासठ'], [69, 'उनहत्तर'], [79, 'उन्यासी'], [89, 'नवासी'], [90, 'नब्बे']]) assert.equal(numberWords(number, 'hi'), expected);
  assert.equal(normalizeLanguage('__proto__'), 'en');
  assert.equal(normalizeLanguage('unknown'), 'en');
  for (const number of [0, 91, NaN, '22']) assert.throws(() => getCall(number));
});

test('language survives saves and new rounds while old saves default to English', () => {
  const original = recordClaim({ ...newGame(), called: [1, 2, 3, 4, 5], callLanguage: 'hi' }, 'early5');
  assert.deepEqual(parseGame(JSON.stringify(original)), original);
  assert.equal(restartGame(original).callLanguage, 'hi');
  const old = { ...original }; delete old.callLanguage;
  assert.equal(parseGame(JSON.stringify(old)).callLanguage, 'en');
  assert.equal(parseGame(JSON.stringify({ ...original, callLanguage: '../secret' })).callLanguage, 'en');
  assert.equal(original.called.length, 5);
});

test('clip URLs, filenames and preparation distinguish the same number in different languages', async () => {
  const requests = [];
  const load = createClipLoader(async (url) => { requests.push(url); return new Response(url, { headers: { 'Content-Type': 'audio/mpeg' } }); });
  const english = await load(22, 'en');
  const hindi = await load(22, 'hi');
  const hinglish = await load(22, 'hinglish');
  assert.equal(hindi.name, 'Tambola-22-Hindi-AI-voice.mp3');
  assert.equal(hinglish.name, 'Tambola-22-Hinglish-AI-voice.mp3');
  assert.notEqual(english, hindi);
  assert.notEqual(hindi, hinglish);
  assert.equal(await load(22, 'hinglish'), hinglish);
  assert.equal(requests.length, 3);
  assert.ok(clipUrl(22, 'hi').endsWith('/audio/hi/numbers/22.mp3'));
  assert.ok(clipUrl(22, 'hinglish').endsWith('/audio/hinglish/numbers/22.mp3'));
  assert.equal(clipUrl(22, '../../private'), clipUrl(22, 'en'));
});

test('Hindi fallback uses a Hindi voice and old English failures cannot interrupt it', async () => {
  const spoken = [], pending = [];
  const hindi = { lang: 'hi-IN', localService: true };
  const player = { pause() {}, removeAttribute() {}, load() {}, play() { return new Promise((resolve, reject) => pending.push({ resolve, reject })); } };
  const browser = { SpeechSynthesisUtterance: class { constructor(text) { this.text = text; } }, speechSynthesis: {
    cancel() {}, getVoices: () => [{ lang: 'en-IN', localService: true }, hindi], speak: (value) => spoken.push(value),
  } };
  const voice = createVoice(assert.fail, browser, player);
  voice.announce(47, 'en');
  const staleError = player.onerror;
  voice.announce(47, 'hi');
  assert.ok(player.src.endsWith('/hi/numbers/47.mp3'));
  staleError(); pending[0].reject(Error('old'));
  await Promise.resolve(); assert.equal(spoken.length, 0);
  player.onerror();
  assert.equal(spoken[0].voice, hindi);
  assert.equal(spoken[0].lang, 'hi-IN');
  assert.match(spoken[0].text, /संख्या सैंतालीस/);
});

test('Hindi and Hinglish text shares follow the selection and exclude player details', () => {
  const state = { ...newGame(), called: [1, 47], players: [{ id: 'p', name: 'Private name' }] };
  const hindi = numberMessage({ ...state, callLanguage: 'hi' });
  assert.match(hindi, /47 — सैंतालीस/); assert.match(hindi, /आज़ादी का साल/);
  assert.match(numberMessage({ ...state, callLanguage: 'hinglish' }), /Aazaadi ka saal/);
  assert.equal(hindi.includes('Private name'), false);
});

test('changing language rebuilds a board image without changing calls or exposing players', async () => {
  const updates = [], requests = [];
  const prepare = createImagePreparer((value) => updates.push(value), async (state) => { requests.push(state); return new File(['png'], 'board.png'); });
  const state = { ...newGame(), called: [47] };
  prepare(state); await new Promise((resolve) => setImmediate(resolve));
  prepare({ ...state, callLanguage: 'hi', players: [{ id: 'secret', name: 'Private' }] }); await new Promise((resolve) => setImmediate(resolve));
  assert.equal(requests.length, 2);
  assert.deepEqual(requests[1], { called: [47], callLanguage: 'hi' });
  assert.equal(imageSummary({ ...state, callLanguage: 'hi' }).language, 'hi');
  assert.equal(updates.at(-1).summary.latest, 47);
});

test('generator language selection is explicit and produces language-specific requests', () => {
  const calls = selectCalls(['--all', '--language', 'hi']);
  assert.equal(calls.length, 90); assert.ok(calls.every((call) => call.language === 'hi'));
  assert.match(requestFor(calls[46]).instructions, /entirely in natural Hindi/);
  assert.notEqual(requestFor(calls[46]).input, requestFor(getCall(47, 'en')).input);
  assert.throws(() => selectCalls(['--all', '--language', '../hi']));
  assert.throws(() => selectCalls(['--language']));
});

test('transcription number checks tolerate punctuation and repeated digits without substring matches', () => {
  assert.equal(detectsNumber('Number 47, forty-seven.', 47), true);
  assert.equal(detectsNumber('Number 4747.', 47), true);
  assert.equal(detectsNumber('संख्या सैंतालीस।', 47), true);
  assert.equal(detectsNumber('नंबर ४७', 47), true);
  assert.equal(detectsNumber('पूरा नंबर है अठावन।', 58), true);
  assert.equal(detectsNumber('अठ्ठावन', 58), true);
  assert.equal(detectsNumber('अठावन', 57), false);
  assert.equal(detectsNumber('Number 47.', 7), false);
  assert.equal(detectsNumber('forty eight', 47), false);
});
