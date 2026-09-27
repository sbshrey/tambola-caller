"""Collect the completed alpha22 endurance run only, after its actual terminal exit."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
bench = root / '.test-workspace/alpha22-endurance-benchmark'
dest = root / 'reviews/coin-endurance-alpha22-2026-09-27'
assert not dest.exists(), 'Never overwrite an archived run'
terminal_path = root / '.test-workspace/alpha22-endurance-runner-terminal.json'
terminal = json.loads(terminal_path.read_text(encoding='utf-8-sig'))
assert terminal['exit_code'] == 0 and not terminal.get('session_id'), 'Runner must have exited'
assert 'Instrumentation passed; original system animation settings restored.' in terminal['output']
journey_path = bench / 'coin-release-journey.json'
transcript_path = root / '.test-workspace/alpha22-endurance.txt'
benchmark_paths = list(bench.rglob('*benchmarkData.json'))
assert len(benchmark_paths) == 1
trace_paths = sorted(bench.rglob('*.perfetto-trace'))
assert len(trace_paths) == 9
assert all('realCoinEndurance_iter' in p.name for p in trace_paths)
assert {re.search(r'_iter(\d+)_', p.name).group(1) for p in trace_paths} == {f'{i:03}' for i in range(9)}
stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S')
analysis_path = root / f'.test-workspace/alpha22-endurance-final-analysis-{stamp}.json'
subprocess.run([sys.executable, str(root / 'tools/analyze-coin-endurance.py'), str(journey_path),
                '--benchmark', str(benchmark_paths[0]), '--transcript', str(transcript_path),
                '--output', str(analysis_path)], check=True, timeout=30)
analysis = json.loads(analysis_path.read_text())
assert analysis['accepted']
preflight_path = root / '.test-workspace/alpha22-endurance-preflight.json'
preflight = json.loads(preflight_path.read_text(encoding='utf-8-sig'))
adb = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'


def device(*args):
    return subprocess.check_output([str(adb), '-s', 'emulator-5582', *args], timeout=30).decode().strip()


def identity(path):
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(block)
    return {'sha256': digest.hexdigest(), 'bytes': path.stat().st_size}


installed = {}
for package, expected in preflight['apks'].items():
    path = device('shell', 'pm', 'path', package).removeprefix('package:')
    assert re.fullmatch(r'/data/app/[A-Za-z0-9_/=+.~-]+/base.apk', path)
    installed[package] = device('shell', 'sha256sum', path).split()[0]
    assert installed[package] == expected, f'Installed {package} changed'
assert device('reverse', '--list') == ''
scales = {name: device('shell', 'settings', 'get', 'global', name)
          for name in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']}
assert all(re.fullmatch(r'(null|\d+(?:\.\d+)?)', value) for value in scales.values())
assert scales == preflight['animationScales']
assert device('shell', 'settings', 'get', 'system', 'font_scale') == preflight['fontScale']
app = root / 'releases/0.22.0-alpha22-wifi-optimized/Tambola-Together-0.22.0-alpha22-wifi-optimized.apk'
assert identity(app)['sha256'] == installed['io.github.sbshrey.tambola.game']
subprocess.run(['node', str(root / '.test-workspace/server-wifi-health.mjs')], cwd=root,
               check=True, timeout=30, stdout=subprocess.DEVNULL)
health_path = root / '.test-workspace/server-wifi-health.json'
host = json.loads(health_path.read_text())
assert host['httpsStatus'] == 200 and host['installedRuntimeMatchesCandidate']
assert host['sourceCommit'] == 'b6d5eb6f7cc9c6c70006e4d9c422c2abe75187e0'
assert host['serviceRuntimeSha256'] == 'f2b39e5bb622b0bd9c368da654a9412fc3e34d617e171497a1f521b7ec80ee10'
assert (datetime.datetime.now(datetime.timezone.utc) - datetime.datetime.fromisoformat(host['checkedAt'].replace('Z', '+00:00'))).total_seconds() < 60
validation = {
    'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'appSourceCommit': preflight['appSourceCommit'],
    'driverSourceCommit': preflight['sourceCommit'],
    'installedApksUnchanged': installed,
    'packagedApp': identity(app),
    'serverSourceCommit': host['sourceCommit'],
    'serviceRuntimeSha256': host['serviceRuntimeSha256'],
    'animationScalesObservedAfter': scales,
    'runnerExitCode': terminal['exit_code'],
    'animationRestorationBasis': 'Runner exit 0 confirms restoration; independent preflight animation and font settings are also compared after the run.',
    'noAdbReverseMappings': True,
    'enduranceAnalysis': analysis,
    'localTraces': [{'path': p.relative_to(root).as_posix(), **identity(p)} for p in trace_paths],
    'collectorSha256': identity(Path(__file__))['sha256'],
    'limits': [
        'API30 software emulator and passive peers; not physical ARM64/Wi-Fi acceptance or human competition',
        'No injected HTTP-response loss in these nine rounds; separate alpha21 regression covers a lost leave response',
        'Memory samples do not establish leak freedom and emulator CPU frames do not establish smoothness',
        'Browser/Figma design work and a lightweight local design preview ran concurrently from round 5 onward; frame diagnostics are not an isolated performance comparison',
        'Current-user login startup only; no actual reboot or public endpoint proof',
        'Development-signed app; physical firewall, device checks, production signing and store release remain open',
    ],
}
dest.mkdir()
for source, name in [
    (journey_path, 'coin-release-journey.json'),
    (bench / 'coin-release-results.png', 'coin-release-results.png'),
    (benchmark_paths[0], 'benchmark.json'),
    (transcript_path, 'native-transcript.txt'),
    (terminal_path, 'runner-terminal.json'),
    (preflight_path, 'preflight.json'),
    (health_path, 'host-health-after.json'),
    (analysis_path, 'endurance-analysis.json'),
    (Path(__file__), 'collector.py'),
]:
    shutil.copy2(source, dest / name)
(dest / 'validation.json').write_text(json.dumps(validation, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'evidence': str(dest), 'rounds': analysis['roundsCompleted'],
                  'measuredRoundDurationMs': analysis['measuredRoundDurationMs'],
                  'frames': analysis['frameCount'], 'installedApksUnchanged': True,
                  'hostReady': True, 'animationScalesObservedAfter': scales}, indent=2))
