"""Run only after the successful build, guarded upgrade and both HTTPS checks."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
work = root / '.test-workspace'
target = root / 'reviews/gamer-handles-server-2026-09-27'
source = 'cfdb236830a376b13d96d2cecf521032e3b441c9'
runtime = '3f0e1aa1db385105ea7575ef3dfa89422dcb2640749c9756b0a4cf5179f2cda8'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
roster = read(work / 'gamer-handles-live-roster.json')
smoke = read(work / 'coin-host-smoke.json')
health = read(work / 'server-wifi-health.json')
config = read(Path(os.environ['LOCALAPPDATA']) / 'TambolaTogetherHost/host.json')
upgrade = Path(config['serviceLib']).parent
deployment_bytes = (upgrade / 'deployment.json').read_bytes()
assert deployment_bytes and deployment_bytes.count(0) == len(deployment_bytes), 'Reassess the deployment record rather than assuming the observed damage'
process = read(work / 'gamer-handles-live-process.json')
assert all(value['sourceCommit'] == source for value in (roster, smoke, health, config, process))
assert roster['result'] == smoke['result'] == 'passed'
assert roster['serviceRuntimeSha256'] == health['serviceRuntimeSha256'] == runtime
assert health['httpsStatus'] == 200 and health['installedRuntimeMatchesCandidate']
assert process['configuredJavaMatches'] and process['configuredLibrariesInCommand']
assert process['supervisorStatus'] == 'ready' and process['processStartedAt'] == process['supervisorStartedAt']
assert 'Updated host ready. Source: ' + source in (work / 'gamer-handles-upgrade.txt').read_text(encoding='utf-8-sig')
backup = read(upgrade / 'backups/20260927-062050.json')
assert len(backup['files']) == 2
for entry in backup['files']:
    assert entry['name'] in ('20260927-062050-primary.dump', '20260927-062050-journal.dump')
    file = upgrade / 'backups' / entry['name']
    assert file.stat().st_size == entry['bytes']
    assert hashlib.sha256(file.read_bytes()).hexdigest() == entry['sha256']
    listed = subprocess.run([str(Path(config['postgresBin']) / 'pg_restore.exe'), '--list', str(file)],
        capture_output=True, timeout=15, creationflags=subprocess.CREATE_NO_WINDOW)
    assert listed.returncode == 0, 'A retained backup failed archive validation'
    entry['archiveListingPassed'] = True
assert 'BUILD SUCCESSFUL' in (work / 'gamer-handles-server-validation.txt').read_text(encoding='utf-8-sig')
suites = []
for file in sorted((root / 'server/build/test-results/test').glob('TEST-*.xml')):
    doc = ET.parse(file).getroot()
    suites.append({key: doc.attrib[key] for key in ('name', 'tests', 'failures', 'errors', 'skipped', 'time')})
assert len(suites) >= 25 and all(int(s['failures']) == int(s['errors']) == int(s['skipped']) == 0 for s in suites)
apk = root / 'releases/0.23.0-alpha23-wifi-optimized/Tambola-Together-0.23.0-alpha23-wifi-optimized.apk'
apk_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
assert apk_hash == '872b505cd397397940d88d190d65f7748ad7c4a85c17d397d7a3de390265d033'
target.mkdir(exist_ok=False)
for name, origin in {
    'server-validation.txt': work / 'gamer-handles-server-validation.txt',
    'upgrade-preflight.txt': work / 'gamer-handles-upgrade-preflight.txt',
    'upgrade-transcript.txt': work / 'gamer-handles-upgrade.txt',
    'live-roster.json': work / 'gamer-handles-live-roster.json',
    'https-transactions.json': work / 'coin-host-smoke.json',
    'host-health-before.json': work / 'gamer-handles-health-before.json',
    'host-health-after.json': work / 'server-wifi-health.json',
    'deployment-status-damaged.bin': upgrade / 'deployment.json',
    'live-process.json': work / 'gamer-handles-live-process.json',
    'verify-gamer-handles.mjs': Path(__file__).with_name('verify-gamer-handles.mjs'),
    'archive-gamer-handles.py': Path(__file__),
}.items():
    shutil.copyfile(origin, target / name)
validation = {'sourceCommit': source, 'runtimeSha256': runtime,
    'tests': sum(int(s['tests']) for s in suites), 'suites': suites,
    'unchangedAlpha23ApkSha256': apk_hash,
    'scope': 'Fresh full server suite on isolated PostgreSQL, guarded idle-host deployment, verified HTTPS purchase/refund/session and computer-roster checks. No new native full-round or physical-phone acceptance.'}
validation['deploymentRecordDamage'] = {'bytes': len(deployment_bytes), 'allZeroBytes': True,
    'sha256': hashlib.sha256(deployment_bytes).hexdigest(),
    'note': 'Final status record is unusable. Successful upgrade transcript, original live probes, fresh runtime/process/readiness attestation and checksum-valid backup pair are separate evidence. The damaged original was not rewritten.'}
(target / 'validation.json').write_text(json.dumps(validation, indent=2) + '\n', encoding='utf-8')
(target / 'backup-validation.json').write_text(json.dumps(backup, indent=2) + '\n', encoding='utf-8')
names = ', '.join(p['name'] for p in roster['computers'])
(target / 'README.md').write_text(f'''# Fictional gamer handles on the Wi-Fi server

27 September 2026. Installed source `{source}` now assigns computer opponents fictional gamer-style handles: ChaiChamp, NeonNinja, LuckyMango, PixelRaja, DiceDiva, MoonMaverick, TurboTikka and LotusLegend. The list rotates by room identity; names remain stable for the round. Public player records retain `computer: true`, and the existing Android UI labels computer opponents. These are original example handles, not imported real accounts.

The same candidate includes the previously reviewed [unused wallet snapshot removal](../wallet-query-2026-09-27/README.md) and [transaction-local timeout batching](../timeout-batching-2026-09-27/README.md). There are no schema or protocol changes and no claim of a measured latency gain from this deployment.

## Checks and deployment

- **{validation['tests']} server tests passed** across {len(suites)} classes on isolated PostgreSQL port 55432, including coin accounting, concurrency, private tickets, sessions, restoration and restricted permissions. The first database startup did not reach readiness because its open log was inside the recovery-scanned data directory; the owned test process was stopped, logging moved outside that directory, and the successful suite followed. The serving database on 55433 was unaffected.
- The guarded upgrade required a clean committed distribution and no unfinished games, checked again after stopping serving. It retained both database backups and the previous libraries/configuration, then started a fresh restricted service. No profile reset or APK reinstall was used.
- Verified HTTPS purchases, exact receipt retries, refunds, session rotation and QA-profile deletion passed. A separate real-time round reached its first automatic call with **{names}**, all explicitly flagged computers. Six owned tickets, a fixed 1,500-coin pool, seven prizes, stable names and unchanged balance were checked. The sole temporary roster profile was deleted and its session rejected afterward.
- The installed 51-JAR runtime is `{runtime}`. HTTPS readiness and runtime hashes were rechecked after both probes. The alpha23 APK remains byte-identical: `{apk_hash}`.

[Validation](validation.json), [live roster](live-roster.json), [HTTPS transactions](https-transactions.json) and [postflight health](host-health-after.json) record the evidence. The final deployment status file was found to contain 351 zero bytes after a later Windows restart; it is preserved as `deployment-status-damaged.bin` and is not accepted as a successful status record. The successful upgrade transcript and initial live probes are intact. Fresh [process attestation](live-process.json) confirms the configured Java process uses the expected deployed libraries, and [backup validation](backup-validation.json) checks both original hashes, sizes and archive listings. This is archive validation, not a restore rehearsal or a causal diagnosis of the file damage. Secrets, private database contents and backups remain outside Git.

This verifies the server update on this PC. The [earlier alpha23 full-round acceptance](../game-night-alpha23-2026-09-27/README.md) used the previous runtime and remains separately scoped. Physical-phone Wi-Fi, sound/input, frame performance, public hosting and production signing remain open. The host starts after login and requires this PC to remain awake and connected.
''', encoding='utf-8')
manifest = {p.relative_to(target).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
    for p in sorted(target.rglob('*')) if p.is_file()}
(target / 'artifact-hashes.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'archive': str(target), 'tests': validation['tests'], 'files': len(manifest), 'runtime': runtime}))
