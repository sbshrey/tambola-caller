import { mkdir, copyFile, cp } from 'node:fs/promises';
import { LANGUAGES } from '../src/languages.js';
const root = new URL('../', import.meta.url);
const output = new URL('dist/', root);
await mkdir(output, { recursive: true });
for (const name of ['.nojekyll', 'index.html', 'styles.css', 'icon.svg', 'manifest.webmanifest', 'sw.js']) {
  await copyFile(new URL(name, root), new URL(name, output));
}
for (const name of ['src', 'icons']) await cp(new URL(name, root), new URL(name, output), { recursive: true });
// Require every supported voice pack; never offer an incomplete language.
for (const { folder } of Object.values(LANGUAGES)) {
  await mkdir(new URL(`audio/${folder}numbers/`, output), { recursive: true });
  for (let number = 1; number <= 90; number++) {
    const path = `audio/${folder}numbers/${String(number).padStart(2, '0')}.mp3`;
    await copyFile(new URL(path, root), new URL(path, output));
  }
}
console.log('Static site built in dist/ — ready for any HTTPS static host.');
