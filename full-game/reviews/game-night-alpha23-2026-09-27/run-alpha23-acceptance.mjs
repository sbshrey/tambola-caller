import {spawn} from 'node:child_process';
import {writeFileSync, existsSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const label = process.argv[2];
if (!/^alpha23-[a-z0-9-]+$/.test(label || '')) throw new Error('Supply a unique alpha23 run label');
const statusPath = new URL(`./${label}-runner.json`, import.meta.url);
if (existsSync(statusPath)) throw new Error('Keep prior runner evidence');
const status = {label, startedAt: new Date().toISOString(),
  pid: process.pid, status: 'running'};
const save = () => writeFileSync(statusPath, JSON.stringify(status, null, 2) + '\n');
save();
const child = spawn(process.execPath, ['tools/android-smoke.mjs', '--benchmark', '--animations',
  '--label', status.label, '--class', 'io.github.sbshrey.tambola.benchmark.CoinReleaseBenchmark#realComputerRound'],
  {cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']});
status.childPid = child.pid; save();
child.stdout.pipe(process.stdout); child.stderr.pipe(process.stderr);
child.on('error', error => {status.spawnError = error.code; save();});
child.on('close', (exitCode, signal) => {
  Object.assign(status, {status: 'finished', finishedAt: new Date().toISOString(), exitCode, signal});
  save(); process.exitCode = exitCode === 0 ? 0 : 1;
});
