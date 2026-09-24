import { mkdir, copyFile, cp } from 'node:fs/promises';
const root = new URL('../', import.meta.url);
const output = new URL('dist/', root);
await mkdir(output, { recursive: true });
for (const name of ['.nojekyll', 'index.html', 'styles.css', 'icon.svg', 'manifest.webmanifest', 'sw.js']) {
  await copyFile(new URL(name, root), new URL(name, output));
}
for (const name of ['src', 'icons']) await cp(new URL(name, root), new URL(name, output), { recursive: true });
console.log('Static site built in dist/ — ready for any HTTPS static host.');
