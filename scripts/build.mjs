import { mkdir, copyFile, cp } from 'node:fs/promises';
const root = new URL('../', import.meta.url);
const output = new URL('dist/', root);
await mkdir(output, { recursive: true });
for (const name of ['.nojekyll', 'index.html', 'styles.css', 'icon.svg', 'manifest.webmanifest', 'sw.js']) {
  await copyFile(new URL(name, root), new URL(name, output));
}
for (const name of ['src', 'icons']) await cp(new URL(name, root), new URL(name, output), { recursive: true });
await mkdir(new URL('audio/numbers/', output), { recursive: true });
// Require the complete voice pack; never ship a build with missing number clips.
for (let number = 1; number <= 90; number++) {
  const path = `audio/numbers/${String(number).padStart(2, '0')}.mp3`;
  await copyFile(new URL(path, root), new URL(path, output));
}
console.log('Static site built in dist/ — ready for any HTTPS static host.');
