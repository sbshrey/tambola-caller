import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import { resolve } from 'node:path';

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
export function parseRuntimeManifest(text) {
  const rows = text.trim().split(/\r?\n/).map(line => {
    const match = line.match(/^([a-f0-9]{64}) {2}(?:\/app\/lib\/)?([A-Za-z0-9_.+-]+\.jar)$/);
    assert.ok(match, 'Invalid runtime manifest line');
    return { file: match[2], sha256: match[1] };
  }).sort((a, b) => a.file < b.file ? -1 : a.file > b.file ? 1 : 0);
  assert.equal(new Set(rows.map(row => row.file)).size, rows.length);
  assert.ok(rows.some(row => row.file === 'server.jar'));
  const manifest = rows.map(row => `${row.sha256}  ${row.file}\n`).join('');
  return { serviceJarSha256: rows.find(row => row.file === 'server.jar').sha256,
    serviceRuntimeSha256: sha256(manifest), serviceRuntimeManifest: manifest };
}

export async function serviceRuntime(root) {
  const directory = resolve(root, 'server/build/install/server/lib');
  const jars = (await readdir(directory, { withFileTypes: true })).filter(entry => entry.name.endsWith('.jar'));
  assert.ok(jars.length > 0 && jars.every(entry => entry.isFile()), 'Runtime JARs must be regular files');
  return parseRuntimeManifest((await Promise.all(jars.map(async entry =>
    `${sha256(await readFile(resolve(directory, entry.name)))}  ${entry.name}\n`))).join(''));
}
