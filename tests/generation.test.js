import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { calls, callsForLanguage } from '../scripts/calls.mjs';
import { LANGUAGES } from '../src/languages.js';
import { generate, hash, requestFor, selectCalls } from '../scripts/generate-audio.mjs';

test('generation requires an explicit bounded selection', () => {
  assert.equal(selectCalls([]).length, 0);
  assert.deepEqual(selectCalls(['--samples']).map((c) => c.number), [7, 22, 90]);
  assert.equal(selectCalls(['--all']).length, 90);
  assert.throws(() => selectCalls(['--numbers', '0,91']));
  assert.equal(selectCalls(['--numbers', '22,22']).length, 1);
});
test('generation resumes without rebilling unchanged clips and does not store the key', async () => {
  const folder = await mkdtemp(join(tmpdir(), 'tambola-audio-'));
  const directory = pathToFileURL(`${folder}/`);
  const apiKey = 'test-secret-only';
  let requests = 0;
  const options = { directory, apiKey, log() {}, async fetcher(url, options) {
    requests++;
    assert.equal(url, 'https://api.openai.com/v1/audio/speech');
    assert.equal(options.headers.Authorization, `Bearer ${apiKey}`);
    assert.equal(JSON.parse(options.body).input, calls[21].text);
    return new Response(Buffer.concat([Buffer.from('ID3'), Buffer.alloc(1500)]));
  } };
  try {
    await generate([calls[21]], options);
    await generate([calls[21]], options);
    assert.equal(requests, 1);
    const manifest = await readFile(new URL('manifest.json', directory), 'utf8');
    assert.equal(manifest.includes(apiKey), false);
    assert.equal(JSON.parse(manifest).clips[22].fingerprint, hash(JSON.stringify(requestFor(calls[21]))));
    await assert.rejects(generate([calls[22]], { ...options, fetcher: async () => new Response('unauthorized', { status: 401 }) }), /HTTP 401/);
    assert.equal(await readFile(new URL('manifest.json', directory), 'utf8'), manifest);
  } finally {
    assert.ok(resolve(folder).startsWith(join(resolve(tmpdir()), 'tambola-audio-')));
    await rm(folder, { recursive: true, force: true });
  }
});
for (const [language, { folder }] of Object.entries(LANGUAGES)) test(`${language} pack has all 90 clips, correct scripts and matching hashes`, async () => {
  const manifest = JSON.parse(await readFile(new URL(`../audio/${folder}manifest.json`, import.meta.url), 'utf8'));
  assert.equal(Object.keys(manifest.clips).length, 90);
  for (const call of callsForLanguage(language)) {
    const entry = manifest.clips[call.number];
    assert.equal(entry.text, call.text);
    assert.equal(entry.fingerprint, hash(JSON.stringify(requestFor(call))));
    const bytes = await readFile(new URL(`../audio/${folder}${entry.file}`, import.meta.url));
    assert.equal(hash(bytes), entry.sha256);
    assert.ok(bytes.length > 1000);
  }
});
