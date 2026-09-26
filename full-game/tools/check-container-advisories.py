"""Gate a complete Grype image report; retain lower-severity unresolved findings."""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re


def evaluate(report, image, now):
    if not re.fullmatch(r'sha256:[a-f0-9]{64}', image):
        raise ValueError('Expected an immutable Docker image ID')
    source = report['source']
    if source['type'] != 'image' or source['target']['imageID'] != image:
        raise ValueError('Scan does not identify the expected image')
    descriptor = report['descriptor']
    if descriptor['name'] != 'grype' or descriptor['version'] != '0.119.0':
        raise ValueError('Unexpected scanner version; review the tool update')
    database = descriptor['db']['status']
    built = datetime.fromisoformat(database['built'].replace('Z', '+00:00'))
    if database['valid'] is not True or built.tzinfo is None or not -timedelta(minutes=10) <= now - built <= timedelta(days=5):
        raise ValueError('Missing, invalid or stale advisory database')
    config = descriptor['configuration']
    if any(config.get(key) for key in ('only-fixed', 'only-notfixed', 'ignore-wontfix', 'exclude', 'vex-documents')):
        raise ValueError('Filtered findings cannot establish this gate')
    # Grype adds these kernel-header defaults; permit no application-specific suppression.
    defaults = [{'package': {'name': name, 'type': kind, 'upstream-name': upstream}, 'match-type': 'exact-indirect-match'}
        for name, kind, upstream in [('kernel-headers', 'rpm', 'kernel'), ('linux(-.*)?-headers-.*', 'deb', 'linux.*'),
            ('linux-libc-dev', 'deb', 'linux'), ('linux-kbuild-.*', 'deb', 'linux.*')]]
    def nonempty(value):
        return {key: nonempty(item) for key, item in value.items() if item} if isinstance(value, dict) else value
    if any(nonempty(rule) not in defaults for rule in config.get('ignore', [])):
        raise ValueError('Custom ignored findings require review')
    matches = report['matches']
    if not isinstance(matches, list): raise ValueError('Incomplete match list')
    blocked = []
    for match in matches:
        vulnerability, artifact = match['vulnerability'], match['artifact']
        severity = vulnerability['severity']
        if severity not in ('Negligible', 'Low', 'Medium', 'High', 'Critical'):
            raise ValueError('Unclassified severity requires review')
        if severity in ('High', 'Critical') or (severity == 'Medium' and vulnerability['fix']['state'] == 'fixed'):
            blocked.append({'id': vulnerability['id'], 'severity': severity, 'package': artifact['name'], 'version': artifact['version']})
    return {'imageId': image, 'scannerVersion': descriptor['version'], 'databaseBuiltUtc': database['built'],
        'matches': len(matches), 'uniqueAdvisories': len({row['vulnerability']['id'] for row in matches}),
        'severities': dict(Counter(row['vulnerability']['severity'] for row in matches)),
        'blocked': blocked, 'passed': not blocked}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--image', required=True)
    args = parser.parse_args()
    result = evaluate(json.loads(args.report.read_text(encoding='utf-8')), args.image, datetime.now(timezone.utc))
    print(json.dumps(result, indent=2))
    return 0 if result['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
