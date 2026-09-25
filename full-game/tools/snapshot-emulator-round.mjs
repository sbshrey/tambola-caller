// Snapshot an owned test emulator's offline round for APK upgrade/recovery checks.
// Stops only this game's process. Never accepts a physical device or another AVD.
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const [serial, label] = process.argv.slice(2);
assert.match(serial || '', /^emulator-\d+$/);
assert.match(label || '', /^[a-z0-9-]{1,40}$/);
const root = fileURLToPath(new URL('../', import.meta.url));
const sdk = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME || path.join(process.env.LOCALAPPDATA, 'Android/Sdk');
const adb = path.join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const app = 'io.github.sbshrey.tambola.game';
function run(args, optional = false) {
  const result = spawnSync(adb, ['-s', serial, ...args], { windowsHide: true });
  if (!optional) assert.equal(result.status, 0, `ADB command failed: ${args[0]}`);
  return result;
}
assert.match(run(['emu', 'avd', 'name']).stdout.toString(), /^tambola_full_game_/);
run(['shell', 'am', 'force-stop', app]);
const output = path.join(root, '.test-workspace', 'upgrade');
fs.mkdirSync(output, { recursive: true });
const database = path.join(output, `${label}.db`);
for (const suffix of ['', '-wal', '-shm']) {
  const target = database + suffix;
  // These three literal filenames are inside this script's fixed workspace directory.
  if (fs.existsSync(target)) fs.unlinkSync(target);
  if (suffix === '-shm') continue; // SQLite recreates its shared-memory index from the WAL.
  const result = run(['exec-out', 'run-as', app, 'cat', `databases/tambola-together.db${suffix}`], suffix !== '');
  if (result.status !== 0) continue;
  if (!suffix) assert.equal(result.stdout.subarray(0, 15).toString(), 'SQLite format 3');
  else if (result.stdout.length < 32 || ![0x377f0682, 0x377f0683].includes(result.stdout.readUInt32BE(0))) continue;
  fs.writeFileSync(target, result.stdout);
}
const query = String.raw`
import hashlib, json, sqlite3, sys
with sqlite3.connect(sys.argv[1]) as db:
    row = db.execute('SELECT payload FROM rounds WHERE completed = 0 ORDER BY createdAt DESC LIMIT 1').fetchone()
    if row is None: raise RuntimeError('No active round')
    r = json.loads(row[0])
    print(json.dumps(dict(id=r['id'], version=r['version'], called=r['called'], status=r['status'],
        marks={k: sorted(v) for k,v in r['marks'].items()},
        ticketHash=hashlib.sha256(json.dumps(r['tickets'], sort_keys=True).encode()).hexdigest(),
        settings={k: v for k,v in r['settings'].items() if k != 'customPrizes'},
        customPrizes=r['settings'].get('customPrizes', [])), sort_keys=True))
`;
const result = spawnSync('python', ['-c', query, database], { windowsHide: true, encoding: 'utf8' });
assert.equal(result.status, 0, 'Could not read the isolated round snapshot');
const snapshot = JSON.parse(result.stdout);
const info = run(['shell', 'dumpsys', 'package', app]).stdout.toString();
snapshot.appVersion = info.match(/versionName=([^\s]+)/)?.[1];
fs.writeFileSync(path.join(output, `${label}.json`), JSON.stringify(snapshot, null, 2));
console.log(JSON.stringify({ label, appVersion: snapshot.appVersion, roundVersion: snapshot.version,
  roundId: snapshot.id, calls: snapshot.called.length, markedNumbers: Object.values(snapshot.marks).flat().length }));
