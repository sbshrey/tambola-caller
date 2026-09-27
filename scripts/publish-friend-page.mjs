import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Publish only the invitation assets and compatible worker routing to the existing Pages branch.
// The rest of main's tree is retained; --expected-base prevents silently overwriting newer work.
const root = fileURLToPath(new URL('../', import.meta.url));
const gh = process.env.TAMBOLA_GH || 'gh';
const repo = 'repos/sbshrey/tambola-caller';
const expected = process.argv[process.argv.indexOf('--expected-base') + 1];
assert.match(expected || '', /^[a-f0-9]{40}$/, 'Pass --expected-base with the inspected main commit');
function api(path, payload, method = 'POST') {
  return JSON.parse(execFileSync(gh, ['api', `${repo}/${path}`, ...(payload ? ['--method', method, '--input', '-'] : [])],
    { cwd: root, input: payload ? JSON.stringify(payload) : undefined, encoding: 'utf8', windowsHide: true }));
}
const pages = api('pages');
assert.equal(pages.source.branch, 'main'); assert.equal(pages.source.path, '/'); assert.equal(pages.build_type, 'legacy');
const ref = api('git/ref/heads/main');
assert.equal(ref.object.sha, expected, 'Main changed; inspect it before publishing');
const sourceCommit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8', windowsHide: true }).trim();
const paths = ['friends/index.html', 'friends/invite.js', 'friends/styles.css', 'sw.js'];
const files = await Promise.all(paths.map(async path => ({ path, content: await readFile(new URL('../' + path, import.meta.url), 'utf8') })));
const publishedWorker = Buffer.from(api(`contents/sw.js?ref=${expected}`).content, 'base64').toString('utf8');
const manifest = value => value.replaceAll('\r\n', '\n').split("self.addEventListener('install'")[0].trim();
assert.equal(manifest(files.find(file => file.path === 'sw.js').content), manifest(publishedWorker),
  'Immediate worker activation requires the same caller cache and asset manifest');
for (const { path, content } of files) {
  const committed = execFileSync('git', ['show', `${sourceCommit}:${path}`], { cwd: root, encoding: 'utf8', windowsHide: true });
  assert.equal(content.replaceAll('\r\n', '\n'), committed.replaceAll('\r\n', '\n'), 'Commit reviewed publishing files first');
}
if (!process.argv.includes('--apply')) {
  console.log(JSON.stringify({ ready: true, base: expected, sourceCommit, paths }));
} else {
  const tree = api(`git/commits/${expected}`).tree.sha;
  const entries = files.map(({ path, content }) => ({ path, mode: '100644', type: 'blob',
    sha: api('git/blobs', { content: Buffer.from(content.replaceAll('\r\n', '\n')).toString('base64'), encoding: 'base64' }).sha }));
  const nextTree = api('git/trees', { base_tree: tree, tree: entries }).sha;
  const commit = api('git/commits', { message: 'Publish stable Tambola friend invitations', tree: nextTree, parents: [expected] }).sha;
  assert.equal(api('git/refs/heads/main', { sha: commit, force: false }, 'PATCH').object.sha, commit);
  const result = { publishedAt: new Date().toISOString(), base: expected, sourceCommit, mainCommit: commit, files: entries,
    site: 'https://sbshrey.github.io/tambola-caller/friends/', scope: 'Only these four files changed on main; Pages build and public checks still required.' };
  await writeFile(new URL('../full-game/reviews/stable-invitations-2026-09-27/pages-publication.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify(result));
}
