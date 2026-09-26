import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseRuntimeManifest } from './service-runtime.mjs';

test('library-only updates change the runtime identity even with identical service bytes', () => {
  const jar = '1'.repeat(64), older = '2'.repeat(64), newer = '3'.repeat(64);
  const before = parseRuntimeManifest(`${jar}  server.jar\n${older}  dependency.jar\n`);
  const after = parseRuntimeManifest(`${newer}  dependency.jar\n${jar}  server.jar\n`);
  assert.equal(before.serviceJarSha256, after.serviceJarSha256);
  assert.notEqual(before.serviceRuntimeSha256, after.serviceRuntimeSha256);
  assert.deepEqual(after, parseRuntimeManifest(`${jar}  /app/lib/server.jar\n${newer}  /app/lib/dependency.jar\n`));
});

test('missing, duplicate, path-bearing or malformed artifacts cannot attest a runtime', () => {
  const hash = '0'.repeat(64);
  for (const input of ['', `${hash}  dependency.jar`, `${hash}  server.jar\n${hash}  server.jar`,
    `${hash}  ../server.jar`, `bad-hash  server.jar`]) assert.throws(() => parseRuntimeManifest(input));
});
