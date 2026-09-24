import test from 'node:test';
import assert from 'node:assert/strict';
import { clipUrl, createClipLoader, shareClip } from '../src/audio.js';

test('clip paths cover the valid numbers and reject invalid paths', () => {
  assert.ok(clipUrl(1).endsWith('/audio/numbers/01.mp3'));
  assert.ok(clipUrl(90).endsWith('/audio/numbers/90.mp3'));
  for (const n of [0, 91, 1.5, '../secret', undefined]) assert.throws(() => clipUrl(n));
});
test('clip preparation reuses the current file and changes it for a new number', async () => {
  const fetched = [];
  const load = createClipLoader(async (url) => {
    fetched.push(url);
    return new Response('audio bytes', { headers: { 'Content-Type': 'audio/mpeg' } });
  });
  const [a, b] = await Promise.all([load(22), load(22)]);
  assert.equal(a, b);
  assert.equal(a.name, 'Tambola-22-AI-voice.mp3');
  assert.equal(a.type, 'audio/mpeg');
  assert.equal(await a.text(), 'audio bytes');
  const c = await load(23);
  assert.notEqual(a, c);
  assert.equal(fetched.length, 2);
});
test('missing, HTML and failed audio responses do not produce shareable files', async () => {
  for (const fetcher of [async () => new Response('', { status: 404 }), async () => new Response('<html>', { headers: { 'Content-Type': 'text/html' } }), async () => { throw Error('offline'); }]) {
    assert.equal(await createClipLoader(fetcher)(22), null);
  }
});
test('file sharing preserves the tap activation and does not include private game details', async () => {
  const file = new File(['mp3'], 'Tambola-22-AI-voice.mp3', { type: 'audio/mpeg' });
  let called = false;
  const promise = shareClip(file, { canShare: (data) => data.files[0] === file, share(data) {
    assert.deepEqual(data, { files: [file] }); called = true; return Promise.resolve();
  } });
  assert.equal(called, true);
  assert.equal(await promise, 'opened');
});
test('file sharing distinguishes cancellation from unsupported or failed handoff', async () => {
  const file = new File(['mp3'], '22.mp3');
  assert.equal(await shareClip(file, {}), 'fallback');
  assert.equal(await shareClip(file, { canShare: () => false, share: assert.fail }), 'fallback');
  assert.equal(await shareClip(file, { canShare: () => true, share() { throw { name: 'AbortError' }; } }), 'cancelled');
  assert.equal(await shareClip(file, { canShare: () => true, share() { throw { name: 'NotAllowedError' }; } }), 'fallback');
});
