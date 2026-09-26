import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

BASE = '4c7b933f71976e9fc7ebd6f9e4f3332bca54d6bf'


def entries(raw):
    parsed = ET.fromstring(raw)
    config = parsed.find('{*}configuration')
    assert config.findtext('{*}verify-metadata') == 'true'
    assert config.find('{*}trusted-artifacts') is None
    result = {}
    for component in parsed.findall('.//{*}component'):
        coordinate = tuple(component.attrib[key] for key in ('group', 'name', 'version'))
        for artifact in component.findall('{*}artifact'):
            key = coordinate + (artifact.attrib['name'],)
            assert all(re.fullmatch(r'[A-Za-z0-9._+\-]+', value) for value in key)
            result[key] = {sha.attrib['value'] for sha in artifact.findall('{*}sha256')}
    return result


def verify(item):
    (group, name, version, artifact), expected = item
    path = f'{group.replace(".", "/")}/{name}/{version}/{artifact}'
    if group.startswith(('com.android', 'androidx', 'com.google.testing.platform', 'com.google.android')):
        repositories = ['https://dl.google.com/dl/android/maven2/']
    else:
        repositories = ['https://repo1.maven.org/maven2/']
        if artifact.endswith('.pom') and name.endswith('.gradle.plugin'):
            repositories.append('https://plugins.gradle.org/m2/')
    for repository in repositories:
        url = repository + path
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'Tambola-build-review/1'})
            with urllib.request.urlopen(request, timeout=60) as response:
                actual = hashlib.file_digest(response, 'sha256').hexdigest()
                final_url = response.url
        except urllib.error.HTTPError as error:
            if error.code in (404, 410):
                continue
            raise
        assert expected == {actual}, f'Changed artifact: {group}:{name}:{version}/{artifact}'
        return dict(group=group, name=name, version=version, artifact=artifact,
            url=url, resolvedUrl=final_url, sha256=actual)
    raise ValueError(f'No official source found for {group}:{name}:{version}/{artifact}')


parser = argparse.ArgumentParser()
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
assert not args.output.exists(), 'Preserve prior evidence'
before = entries(subprocess.check_output(['git', 'show', BASE + ':full-game/gradle/verification-metadata.xml']))
metadata = Path('gradle/verification-metadata.xml').read_bytes()
after = entries(metadata)
assert all(value == after.get(key) for key, value in before.items()), 'Existing checksum trust changed'
added = [(key, value) for key, value in after.items() if key not in before]
assert added, 'No newly trusted artifacts to review'
report = dict(checkedAtUtc=datetime.now(timezone.utc).isoformat(), completed=False,
    baseCommit=BASE, metadataSha256=hashlib.sha256(metadata).hexdigest(),
    method='Fresh official HTTPS Maven/Google/Plugin Portal artifact bytes compared to new Gradle SHA-256 entries; not publisher signature verification',
    existingEntriesPreserved=len(before), addedArtifacts=len(added))
try:
    with ThreadPoolExecutor(max_workers=4) as pool:
        report['verified'] = list(pool.map(verify, added))
    report['completed'] = True
except Exception as error:
    report['failureType'] = type(error).__name__
    report['failure'] = str(error)
args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: value for key, value in report.items() if key != 'verified'}))
raise SystemExit(0 if report['completed'] else 1)
