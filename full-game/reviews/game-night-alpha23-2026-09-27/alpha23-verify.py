import datetime, hashlib, json, os, pathlib, subprocess, sys

root = pathlib.Path(__file__).resolve().parents[1]
sdk = pathlib.Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'
adb = sdk / 'platform-tools/adb.exe'
work = root / '.test-workspace'
serial = 'emulator-5582'
packages = {
    'io.github.sbshrey.tambola.game': root / 'app/build/outputs/apk/lanRelease/app-lanRelease.apk',
    'io.github.sbshrey.tambola.benchmark': root / 'macrobenchmark/build/outputs/apk/lanRelease/macrobenchmark-lanRelease.apk',
}
def run(*args):
    return subprocess.check_output([str(a) for a in args], cwd=root, text=True, encoding='utf-8').strip()
def device(*args):
    return run(adb, '-s', serial, *args)
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def installed(package, label):
    paths = device('shell', 'pm', 'path', package).splitlines()
    assert len(paths) == 1 and paths[0].startswith('package:')
    target = work / f'alpha23-{label}-{package}.apk'
    device('pull', paths[0][8:], target)
    return sha(target)

assert device('emu', 'avd', 'name').splitlines()[0].startswith('tambola_full_game_')
assert not device('reverse', '--list'), 'No adb reverse mappings allowed'
mode = sys.argv[1]
report_path = work / f'alpha23-{mode}.json'
assert not report_path.exists(), 'Keep prior evidence'
run('node', '.test-workspace/server-wifi-health.mjs')
evidence = {
    'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'sourceCommit': run('git', 'rev-parse', 'HEAD'),
    'serial': serial,
    'avd': device('emu', 'avd', 'name').splitlines()[0],
    'builtApks': {p: {'sha256': sha(f), 'bytes': f.stat().st_size} for p, f in packages.items()},
    'installedApks': {p: installed(p, mode) for p in packages},
    'animationScales': {n: device('shell', 'settings', 'get', 'global', n) for n in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']},
    'fontScale': device('shell', 'settings', 'get', 'system', 'font_scale'),
    'host': json.loads((work / 'server-wifi-health.json').read_text()),
    'adbReverseMappings': [],
}
if mode == 'preflight':
    assert evidence['installedApks']['io.github.sbshrey.tambola.game'] == '05dfaa405629fec0a5ab57234941eca1229b9f2cb13034a496e3549759bdb4b4'
else:
    preflight = json.loads((work / 'alpha23-preflight.json').read_text())
    expected_builds = preflight['builtApks']
    if mode in ['fixed-preflight', 'final-preflight']:
        expected_builds = evidence['builtApks']
    elif mode == 'postflight' and (work / 'alpha23-final-preflight.json').exists():
        expected_builds = json.loads((work / 'alpha23-final-preflight.json').read_text())['builtApks']
    elif mode != 'installed':
        expected_builds = json.loads((work / 'alpha23-fixed-preflight.json').read_text())['builtApks']
    for p, built in evidence['builtApks'].items():
        assert evidence['installedApks'][p] == built['sha256'] == expected_builds[p]['sha256']
    assert evidence['builtApks']['io.github.sbshrey.tambola.game'] == preflight['builtApks']['io.github.sbshrey.tambola.game']
    evidence['appSourceCommit'] = preflight['sourceCommit']
    settings_reference = preflight
    if mode == 'fresh-preflight':
        assert evidence['avd'] == 'tambola_full_game_alpha23_api30'
        settings_reference = evidence
    elif mode in ['postflight', 'final-preflight'] and (work / 'alpha23-fresh-preflight.json').exists():
        settings_reference = json.loads((work / 'alpha23-fresh-preflight.json').read_text())
        assert evidence['avd'] == settings_reference['avd']
    assert evidence['animationScales'] == settings_reference['animationScales']
    assert evidence['fontScale'] == settings_reference['fontScale']
    assert evidence['host']['serviceRuntimeSha256'] == preflight['host']['serviceRuntimeSha256']
report_path.write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
print(json.dumps(evidence, indent=2))
