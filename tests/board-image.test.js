import test from 'node:test';
import assert from 'node:assert/strict';
import { IMAGE_FORMATS, imageSummary, imageDescription, drawBoardImage, createBoardImage, createImagePreparer } from '../src/board-image.js';
import { shareFile } from '../src/sharing.js';
import { newGame, undoNumber } from '../src/game.js';

const flush = () => new Promise((resolve) => setImmediate(resolve));

function drawingCanvas() {
  const labels = [];
  const context = new Proxy({ fillText(value) { labels.push(value); } }, { get: (target, key) => target[key] ?? (() => {}) });
  return { labels, getContext: () => context, toBlob: (callback) => callback(new Blob(['png'], { type: 'image/png' })) };
}

test('separate PNGs contain only the requested numbers and use distinct sizes and filenames', async () => {
  const state = { ...newGame(), called: [1, 2, 3, 4, 6, 7, 8, 5, 66, 13, 82, 47], players: [{ id: 'p', name: 'Private player' }] };
  const summary = imageSummary(state);
  const all = Array.from({ length: 90 }, (_, i) => i + 1);
  for (const [kind, expected] of [['number', [47]], ['recent', summary.recent], ['board', all], ['combined', [47, ...summary.recent, ...all]]]) {
    const canvas = drawingCanvas();
    const file = await createBoardImage(state, { canvas, kind });
    assert.equal(file.type, 'image/png');
    assert.match(file.name, new RegExp(`^Tambola${kind === 'combined' ? '' : '-' + kind}-call-12-number-47\\.png$`));
    assert.equal(canvas.width, IMAGE_FORMATS[kind].width);
    assert.equal(canvas.height, IMAGE_FORMATS[kind].height);
    assert.deepEqual(canvas.labels.filter((value) => /^\d{1,2}$/.test(value)).map(Number), expected, kind);
    assert.equal(canvas.labels.some((value) => value.includes('Private player')), false);
    assert.equal(canvas.labels.includes('Year of Independence'), kind === 'number');
    assert.equal(canvas.labels.includes('Forty seven'), kind === 'number' || kind === 'combined');
  }
});

test('number PNG follows the language and recent PNG handles fewer than ten calls', () => {
  const canvas = drawingCanvas();
  const summary = imageSummary({ called: [47], callLanguage: 'hi' });
  drawBoardImage(canvas, summary, 'number');
  assert.ok(canvas.labels.includes('सैंतालीस'));
  assert.ok(canvas.labels.includes('आज़ादी का साल'));
  assert.match(imageDescription(summary, 'number'), /सैंतालीस/);
  assert.equal(imageDescription(summary, 'recent'), 'Last 1 call, latest first: 47.');
  const recent = drawingCanvas();
  drawBoardImage(recent, summary, 'recent');
  assert.equal(recent.labels.filter((value) => value === '–').length, 9);
  assert.throws(() => drawBoardImage(canvas, summary, '__proto__'), /Unknown image format/);
});

test('each separate format discards stale images after undo, language change and reset', async () => {
  for (const kind of ['number', 'recent', 'board']) {
    const updates = [], pending = [];
    const prepare = createImagePreparer((image) => updates.push(image), (state, options) => new Promise((resolve) => pending.push({ state, options, resolve })), kind);
    prepare({ called: [22, 47], callLanguage: 'en' }); await flush();
    prepare({ called: [22], callLanguage: 'hi' }); await flush();
    assert.deepEqual(pending[1].options, { kind });
    assert.deepEqual(pending[1].state, { called: [22], callLanguage: 'hi' });
    pending[1].resolve('current'); await flush();
    pending[0].resolve('stale'); await flush();
    assert.equal(updates.at(-1).file, 'current');
    prepare({ called: [], callLanguage: 'hi' });
    assert.equal(updates.at(-1).file, null);
    assert.equal(updates.at(-1).pending, false);
  }
});
test('image data includes the latest number, last ten calls and full board without player names', () => {
  const state = { ...newGame(), called: [1, 2, 3, 4, 6, 7, 8, 5, 66, 13, 82, 47], claims: { early5: { winner: 'Private player', at: 5 } } };
  const data = imageSummary(state);
  assert.deepEqual(data, { language: 'en', latest: 47, count: 12, remaining: 78, called: state.called, recent: [47, 82, 13, 66, 5, 8, 7, 6, 4, 3] });
  assert.equal(JSON.stringify(data).includes('Private player'), false);
  state.called.push(90);
  assert.equal(data.count, 12);
  assert.equal(data.called.length, 12, 'image must be a frozen-in-time copy');
});
test('first, final, undo and new-game images reflect exactly the current board', () => {
  assert.equal(imageSummary(newGame()), null);
  assert.deepEqual(imageSummary({ called: [90] }).recent, [90]);
  const complete = { ...newGame(), called: Array.from({ length: 90 }, (_, i) => i + 1) };
  assert.equal(imageSummary(complete).remaining, 0);
  const undone = imageSummary(undoNumber(complete));
  assert.equal(undone.latest, 89);
  assert.equal(undone.remaining, 1);
  assert.equal(undone.called.includes(90), false);
});
test('image preparation ignores stale encodes and reuses an unchanged board', async () => {
  const updates = [];
  const pending = [];
  const prepare = createImagePreparer((value) => updates.push(value), (state) => new Promise((resolve) => pending.push({ state, resolve })));
  prepare({ called: [22] });
  await flush();
  prepare({ called: [22, 47] });
  await flush();
  pending[1].resolve('new file');
  await flush();
  pending[0].resolve('stale file');
  await flush();
  assert.equal(updates.at(-1).file, 'new file');
  assert.deepEqual(updates.at(-1).summary.called, [22, 47]);
  prepare({ called: [22, 47], voiceEnabled: false });
  await flush();
  assert.equal(pending.length, 2);
  prepare({ called: [22, 47, 90] });
  await flush();
  prepare({ called: [] });
  pending[2].resolve('old game');
  await flush();
  assert.equal(updates.at(-1).file, null);
  assert.equal(updates.at(-1).summary, null);
  assert.equal(updates.at(-1).pending, false);
});
test('failed PNG encoding reports an error and never leaves a stale file enabled', async () => {
  const updates = [];
  const prepare = createImagePreparer((value) => updates.push(value), async () => { throw Error('Canvas unavailable'); });
  prepare({ called: [22] });
  await flush();
  assert.equal(updates.at(-1).error, true);
  assert.equal(updates.at(-1).pending, false);
  assert.equal(updates.at(-1).file, null);
  await assert.rejects(createBoardImage({ called: [22] }, { canvas: { getContext: () => null } }), /drawing is unavailable/);
});
test('PNG sharing hands off the exact prepared file immediately and handles cancellation/fallback', async () => {
  const file = new File(['png bytes'], 'Tambola-call-12-number-47.png', { type: 'image/png' });
  let sent;
  const device = { canShare: ({ files }) => files[0].type === 'image/png', share(data) { sent = data; return Promise.resolve(); } };
  const result = shareFile(file, device);
  assert.deepEqual(sent, { files: [file] });
  assert.equal(await result, 'opened');
  assert.equal(await shareFile(file, { ...device, share() { throw { name: 'AbortError' }; } }), 'cancelled');
  assert.equal(await shareFile(file, { ...device, canShare: () => false }), 'fallback');
  assert.equal(await shareFile(file, { ...device, share() { throw { name: 'NotAllowedError' }; } }), 'fallback');
});
