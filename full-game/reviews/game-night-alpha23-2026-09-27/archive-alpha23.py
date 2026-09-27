from pathlib import Path
from xml.etree import ElementTree as E
import datetime, hashlib, json, re, shutil, subprocess

root = Path(__file__).resolve().parents[1]
work = root / '.test-workspace'
run = work / 'alpha23-computer-round-final-benchmark'
out = root / 'reviews/game-night-alpha23-2026-09-27'
def digest(p):
    return {'sha256': hashlib.sha256(p.read_bytes()).hexdigest(), 'bytes': p.stat().st_size}
def read(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))
def copy(p, name):
    q = out / name
    q.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(p, q)

journey = read(run / 'coin-release-journey.json')
assert journey['completed'] and journey['stage'] == 'passed'
assert journey['nativeProfileDeleted'] and journey['deletedQaPeers'] == 1
assert journey['labelledComputers'] == 2 and journey['computerFundedCoins'] == 600
assert journey['expectedPool'] == 1800 and journey['publicWinnerSharesVerified']
assert journey['ownedTickets'] == 6 and journey['distinctOwnNumbers'] == 90
assert journey['coldProcessRestoredMarks'] == 20
assert journey['newRoundPurchasedAndRefunded'] and journey['rememberedSixTickets']
assert len(journey['roundReports']) == 1 and journey['roundReports'][0]['prizes'] == 7
assert sum(x['coins'] for x in journey['prizeWinners']) == 1800
assert journey['computerWinnings'] + journey['humanWinnings'] == 1800
terminal = read(work / 'alpha23-computer-round-final-runner.json')
assert terminal['pid'] == 4412 and terminal['status'] == 'finished' and terminal['exitCode'] == 0 and terminal['signal'] is None
transcript = (work / 'alpha23-computer-round-final.txt').read_text(encoding='utf-8')
assert 'OK (1 test)' in transcript and 'FAILURES!!!' not in transcript
duration = float(re.search(r'Time: (\d+(?:\.\d+)?)', transcript)[1])
before = read(work / 'alpha23-final-preflight.json')
after = read(work / 'alpha23-postflight.json')
assert before['builtApks'] == after['builtApks']
assert before['installedApks'] == after['installedApks']
assert before['animationScales'] == after['animationScales']
assert before['fontScale'] == after['fontScale'] and not after['adbReverseMappings']
assert before['host']['serviceRuntimeSha256'] == after['host']['serviceRuntimeSha256']
assert before['sourceCommit'] == after['sourceCommit']
assert after['host']['httpsStatus'] == 200 and after['host']['httpsBody']['status'] == 'ready'
app = root / 'app/build/outputs/apk/lanRelease/app-lanRelease.apk'
assert digest(app) == after['builtApks']['io.github.sbshrey.tambola.game']
failed = read(work / 'alpha23-computer-round-benchmark/coin-release-journey.json')
assert not failed['completed'] and not failed['roundReports'] and failed['deletedQaPeers'] == 0
assert failed['calls'] == 0 and failed['failureType'] == 'IllegalStateException'
interrupted_cleanup = read(work / 'alpha23-interrupted-final-cleanup.json')
assert interrupted_cleanup['guestsRemainingAfterReplay'] == 0
assert interrupted_cleanup['journalIntentsAppended'] == 2 and interrupted_cleanup['noOtherProfilesTargeted']
unit_files = list((root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
units = [E.parse(p).getroot() for p in unit_files]
assert all(int(u.get('failures')) == int(u.get('errors')) == int(u.get('skipped')) == 0 for u in units)
assert sum(int(u.get('tests')) for u in units) == 15
issues = E.parse(root / 'app/build/reports/lint-results-lanRelease.xml').getroot().findall('issue')
lint = {s: sum(x.get('severity') == s for x in issues) for s in ['Error', 'Warning']}
assert lint['Error'] == 0
assert not out.exists(), 'Keep accepted evidence immutable'
out.mkdir()
for name in ['alpha23-preflight.json', 'alpha23-installed.json', 'alpha23-fixed-preflight.json', 'alpha23-postflight.json',
             'alpha23-reboot-preflight.json', 'alpha23-fresh-preflight.json', 'alpha23-final-preflight.json', 'alpha23-package-probe-after-reboot.json',
             'alpha23-optimized-build.txt', 'alpha23-driver-fix-build.txt', 'alpha23-driver-transition-build.txt', 'alpha23-computer-round-final-runner.json',
             'alpha23-manifest.txt', 'alpha23-signature.txt', 'alpha23-network-trust.txt']:
    copy(work / name, name)
copy(work / 'alpha23-computer-round-final.txt', 'native-transcript.txt')
copy(run / 'coin-release-journey.json', 'journey.json')
copy(run / 'coin-release-results.png', 'results.png')
copy(run / 'collection.txt', 'collection.txt')
benchmark_file, = run.rglob('*benchmarkData.json')
benchmark = read(benchmark_file)
copy(benchmark_file, 'benchmark.json')
for name in ['coin-release-journey.json', 'coin-release-failure.png', 'coin-release-failure.xml']:
    copy(work / 'alpha23-computer-round-benchmark' / name, 'initial-driver-failure/' + name)
copy(work / 'alpha23-computer-round.txt', 'initial-driver-failure/native-transcript.txt')
for name in ['alpha23-driver-timeout-logcat.txt', 'alpha23-timeout-final-journey.json', 'alpha23-timeout-cleanup.json',
             'alpha23-after-timeout.json', 'alpha23-cleanup-before.xml', 'alpha23-cleanup-profile.xml',
             'alpha23-cleanup-confirm.xml', 'alpha23-cleanup-after.xml', 'alpha23-computer-round-fixed.txt']:
    copy(work / name, 'emulator-package-stall/' + name)
copy(work / 'alpha23-computer-round-fixed-benchmark/coin-release-results.png', 'emulator-package-stall/results.png')
for name in ['alpha23-windows-interruption.json', 'Alpha23InterruptedQa.java', 'alpha23-interrupted-qa.ps1',
             'alpha23-interrupted-qa-inventory.json', 'alpha23-interrupted-qa-inspect.txt',
             'alpha23-interrupted-qa-apply.txt', 'alpha23-interrupted-final-cleanup.json']:
    copy(work / name, 'windows-interruption/' + name)
retired_call = read(work / 'alpha23-computer-round-fresh-benchmark/coin-release-journey.json')
assert retired_call['failureType'] == 'StaleObjectException' and not retired_call['completed']
assert retired_call['nativeProfileDeleted'] and retired_call['deletedQaPeers'] == 1
for name in ['coin-release-journey.json', 'coin-release-failure.png', 'coin-release-failure.xml']:
    copy(work / 'alpha23-computer-round-fresh-benchmark' / name, 'results-transition-failure/' + name)
copy(work / 'alpha23-computer-round-fresh.txt', 'results-transition-failure/native-transcript.txt')
copy(work / 'alpha23-fresh-runner.json', 'results-transition-failure/runner.json')
copy(root / 'app/build/reports/lint-results-lanRelease.xml', 'lint.xml')
for p in unit_files:
    copy(p, 'unit/' + p.name)
for name in ['alpha23-verify.py', 'archive-alpha23.py', 'run-alpha23-acceptance.mjs']:
    copy(work / name, name)
source_files = [root / 'app/build.gradle.kts', root / 'macrobenchmark/src/main/java/io/github/sbshrey/tambola/benchmark/CoinJourney.kt']
source_files.append(root / 'tools/android-smoke.mjs')
copy(root / 'tools/android-smoke.mjs', 'android-smoke.mjs')
release = root / 'releases/0.23.0-alpha23-wifi-optimized/Tambola-Together-0.23.0-alpha23-wifi-optimized.apk'
assert not release.exists()
release.parent.mkdir(parents=True)
shutil.copyfile(app, release)
assert digest(release) == digest(app)
validation = {'accepted': True, 'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'appSourceCommit': before['appSourceCommit'], 'driverSourceCommit': before['sourceCommit'],
    'app': {'path': release.relative_to(root).as_posix(), **digest(release), 'versionCode': 23,
        'versionName': '0.23.0-alpha23-wifi-optimized', 'debuggable': False, 'profileableByShell': True,
        'certificateSha256': '55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c'},
    'driver': before['builtApks']['io.github.sbshrey.tambola.benchmark'],
    'installedHashesMatch': True, 'animationAndFontSettingsRestored': True,
    'adbReverseEmpty': True, 'allQaProfilesDeleted': True,
    'nativeDurationSeconds': duration, 'unitTests': 15, 'lint': lint,
    'frameMetricsAreEmulatorDiagnosticsOnly': True,
    'traces': [{'path': p.relative_to(root).as_posix(), **digest(p)} for p in run.rglob('*.perfetto-trace')],
    'incompleteAttemptTraces': [{'path': p.relative_to(root).as_posix(), **digest(p)}
        for p in (work / 'alpha23-computer-round-fixed-benchmark').rglob('*.perfetto-trace')],
    'sourceHashes': {p.relative_to(root).as_posix(): digest(p)['sha256'] for p in source_files},
    'limits': ['One random round on the API30 software emulator; no physical frame-performance acceptance',
        'No new server deployment; gaming-handle source change is not installed',
        'Prior nine-round endurance belongs to alpha22, not this APK',
        'Development signing and PC-local HTTPS only; no production-release claim']}
(out / 'validation.json').write_text(json.dumps(validation, indent=2) + '\n', encoding='utf-8')
print(json.dumps(validation, indent=2))
print('Benchmark summary:', json.dumps({k:v for k,v in benchmark['benchmarks'][0].items() if k in ['metrics', 'sampledMetrics']})[:2000])
