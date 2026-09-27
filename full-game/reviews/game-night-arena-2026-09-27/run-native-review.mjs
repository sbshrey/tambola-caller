import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = process.cwd();
const sdk = resolve(process.env.LOCALAPPDATA, 'Android/Sdk');
const adb = resolve(sdk, 'platform-tools/adb.exe');
const serial = 'emulator-5582';
const livePackage = 'io.github.sbshrey.tambola.game';
const reviewPackage = `${livePackage}.uireview`;
const testPackage = `${reviewPackage}.test`;
const largeText = process.argv.includes('--large-text');
assert.ok(process.argv.slice(2).every(arg => arg === '--large-text'));
const folder = resolve(root, `.test-workspace/game-night-arena-native${largeText ? '-font150' : ''}`);
await mkdir(folder, {recursive:true});
function run(...args) {
    const r = spawnSync(adb, ['-s', serial, ...args], {encoding:'utf8', windowsHide:true, timeout:30000});
    assert.equal(r.status, 0, `adb ${args.slice(0,3).join(' ')} failed`);
    return r.stdout.trim();
}
assert.match(run('emu','avd','name').split(/\r?\n/)[0], /^tambola_full_game_/);
function installedHash() {
    const location = run('shell','pm','path',livePackage).replace(/^package:/,'');
    assert.match(location, /^\/data\/app\/[A-Za-z0-9_+=.~\/-]+\/base\.apk$/);
    return run('shell','sha256sum',location).split(/\s/)[0];
}
const before = installedHash();
assert.equal(before, '05dfaa405629fec0a5ab57234941eca1229b9f2cb13034a496e3549759bdb4b4');
const tools = (await readdir(resolve(sdk,'build-tools'))).sort().reverse();
const aapt = resolve(sdk,'build-tools',tools[0],'aapt2.exe');
const apks = [['app/build/outputs/apk/debug/app-debug.apk',reviewPackage],
    ['app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',testPackage]];
const hashes = {};
for (const [file,pkg] of apks) {
    const path = resolve(root,file);
    const manifest = spawnSync(aapt,['dump','badging',path],{encoding:'utf8',windowsHide:true,timeout:30000});
    assert.equal(manifest.status,0);
    assert.equal(manifest.stdout.match(/^package: name='([^']+)'/m)?.[1],pkg);
    hashes[pkg] = createHash('sha256').update(await readFile(path)).digest('hex');
    console.log(`${pkg}: ${run('install','-r',path)}`);
}
let output = '';
let code;
const originalFont = run('shell','settings','get','system','font_scale');
assert.match(originalFont,/^(null|\d+(?:\.\d+)?)$/);
try {
    if (largeText) run('shell','settings','put','system','font_scale','1.5');
    const child = spawn(adb,['-s',serial,'shell','am','instrument','-w','-e','tambolaUiReview','true','-e','class',
        `${livePackage}.CoinLobbyTest,${livePackage}.LandingActivityTest,${livePackage}.ManualTableTest,${livePackage}.TableExperienceTest`,`${testPackage}/androidx.test.runner.AndroidJUnitRunner`],
        {windowsHide:true,stdio:['ignore','pipe','pipe']});
    const append = b => { output += b.toString(); process.stdout.write(b); };
    child.stdout.on('data',append); child.stderr.on('data',append);
    code = await new Promise((resolve,reject)=>{child.once('error',reject);child.once('exit',resolve);});
} finally {
    if (largeText) {
        if (originalFont === 'null') run('shell','settings','delete','system','font_scale');
        else run('shell','settings','put','system','font_scale',originalFont);
    }
    const restoredFont = run('shell','settings','get','system','font_scale');
    assert.equal(restoredFont,originalFont);
    await writeFile(resolve(folder,'instrumentation.txt'),output);
    const after = installedHash();
    await writeFile(resolve(folder,'identity.json'),JSON.stringify({serial,appSha256:hashes,installedAlpha22Before:before,installedAlpha22After:after,exitCode:code,
        originalSystemFontScale:originalFont,requestedSystemFontScale:largeText?'1.5':originalFont,restoredSystemFontScale:restoredFont},null,2)+'\n');
    assert.equal(after,before,'The installed Wi-Fi APK changed');
    const names = run('shell','run-as',reviewPackage,'ls','files').split(/\s+/).filter(n=>/^(game-night-|coin-|table-|manual-)[a-z0-9-]+\.png$/.test(n));
    for (const name of names) {
        const bytes=spawnSync(adb,['-s',serial,'exec-out','run-as',reviewPackage,'cat',`files/${name}`],{windowsHide:true,maxBuffer:20*1024*1024,timeout:30000});
        assert.equal(bytes.status,0); await writeFile(resolve(folder,name),bytes.stdout);
    }
}
assert.equal(code,0); assert.match(output,/OK \(17 tests\)/); assert.doesNotMatch(output,/FAILURES|INSTRUMENTATION_FAILED/);
console.log('Seventeen native UI checks passed; installed alpha22 is unchanged.');
