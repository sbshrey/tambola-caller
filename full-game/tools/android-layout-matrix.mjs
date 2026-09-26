// Native layout checks on the dedicated emulator. Restores pre-existing display overrides.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const serial = option('--serial', 'emulator-5582');
assert.match(serial, /^emulator-\d+$/);
const sdk = process.env.ANDROID_SDK_ROOT || resolve(process.env.LOCALAPPDATA, 'Android/Sdk');
const adb = resolve(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const app = 'io.github.sbshrey.tambola.game';
function run(command, binary = false) {
  const p = spawnSync(adb, ['-s', serial, ...command], { encoding: binary ? undefined : 'utf8', windowsHide: true, timeout: 30_000, maxBuffer: 16 * 1024 * 1024 });
  assert.equal(p.status, 0, `adb ${command.slice(0, 3).join(' ')} failed`);
  return binary ? p.stdout : p.stdout.trim();
}
assert.match(run(['emu', 'avd', 'name']).split(/\r?\n/)[0], /^tambola_full_game_/);
const oldSize = /Override size: (\d+x\d+)/.exec(run(['shell', 'wm', 'size']))?.[1] || 'reset';
const oldDensity = /Override density: (\d+)/.exec(run(['shell', 'wm', 'density']))?.[1] || 'reset';
const oldFont = run(['shell', 'settings', 'get', 'system', 'font_scale']);
assert.match(oldFont, /^(null|\d+(?:\.\d+)?)$/);
const cases = [
  { name: 'small', size: '720x1280', density: '320', font: '1.0' },
  { name: 'small-large-text', size: '720x1280', density: '320', font: '2.0' },
  { name: 'phone', size: '1080x1920', density: '420', font: '1.0' },
  { name: 'landscape', size: '1280x720', density: '320', font: '1.0' },
];
const selected = option('--case', 'all');
assert.ok(selected === 'all' || cases.some(c => c.name === selected));
const output = resolve(root, '.test-workspace/redesign-layout', serial);
await mkdir(output, { recursive: true });
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const installedPath = run(['shell', 'pm', 'path', app]).replace(/^package:/, '');
assert.match(installedPath, /^\/data\/app\/[A-Za-z0-9_+=.~/-]+\/base\.apk$/);
const candidateSha256 = sha(await readFile(resolve(root, 'app/build/outputs/apk/debug/app-debug.apk')));
assert.equal(run(['shell', 'sha256sum', installedPath]).split(/\s+/)[0], candidateSha256);
const evidence = { serial, api: run(['shell', 'getprop', 'ro.build.version.sdk']), candidateSha256, cases: [], restored: false };
try {
  for (const item of cases.filter(c => selected === 'all' || selected === c.name)) {
    run(['shell', 'wm', 'size', item.size]); run(['shell', 'wm', 'density', item.density]);
    run(['shell', 'settings', 'put', 'system', 'font_scale', item.font]);
    const label = `redesign-layout-${item.name}`;
    const code = await new Promise((done, reject) => {
      const child = spawn(process.execPath, ['tools/android-smoke.mjs', '--serial', serial, '--class', `${app}.ArenaLayoutTest`, '--label', label], { cwd: root, windowsHide: true, stdio: 'inherit' });
      child.once('error', reject); child.once('close', done);
    });
    evidence.cases.push({ ...item, passed: code === 0 });
    assert.equal(code, 0, `${item.name} layout failed`);
    const folder = resolve(output, item.name);
    await mkdir(folder, { recursive: true });
    for (const language of ['en', 'hi']) for (const theme of ['dark', 'light']) for (const tickets of [1, 3, 6]) {
      const name = `arena-${language}-${theme}-${tickets}`;
      const png = run(['exec-out', 'run-as', app, 'cat', `files/${name}.png`], true);
      assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
      await writeFile(resolve(folder, `${name}.png`), png);
    }
    console.log(`${item.name}: 12 language/theme/hand combinations passed`);
  }
} finally {
  try {
    run(['shell', 'wm', 'size', oldSize]); run(['shell', 'wm', 'density', oldDensity]);
    run(['shell', 'settings', oldFont === 'null' ? 'delete' : 'put', 'system', 'font_scale', ...(oldFont === 'null' ? [] : [oldFont])]);
    assert.equal(/Override size: (\d+x\d+)/.exec(run(['shell', 'wm', 'size']))?.[1] || 'reset', oldSize);
    assert.equal(/Override density: (\d+)/.exec(run(['shell', 'wm', 'density']))?.[1] || 'reset', oldDensity);
    assert.equal(run(['shell', 'settings', 'get', 'system', 'font_scale']), oldFont);
    evidence.restored = true;
  } finally { await writeFile(resolve(output, `evidence-${selected}.json`), JSON.stringify(evidence, null, 2) + '\n'); }
}
