"""Query OSV for exact Maven coordinates in a runtime or observed-build inventory.

Only public Maven coordinates are sent. The input defines the reviewed scope. Native
code inside AARs, the JRE and operating system need separate review. No exploit probes.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import urllib.request


def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request('https://api.osv.dev/v1/' + path, data=data,
        headers={'Content-Type': 'application/json', 'User-Agent': 'Tambola-dependency-review/1'})
    with urllib.request.urlopen(req, timeout=45) as response:
        return json.load(response)


def query_packages(packages, fetch=request):
    matches = {key: set() for key in packages}
    for start in range(0, len(packages), 100):
        pending = [(key, None) for key in packages[start:start + 100]]
        seen = set()
        while pending:
            queries = []
            for (name, version), token in pending:
                query = {'package': {'ecosystem': 'Maven', 'name': name}, 'version': version}
                if token: query['page_token'] = token
                queries.append(query)
            result = fetch('querybatch', {'queries': queries})
            rows = result['results']
            if len(rows) != len(pending): raise ValueError('Incomplete OSV batch')
            following = []
            for (key, token), row in zip(pending, rows):
                if not isinstance(row, dict) or row.get('error'): raise ValueError('Failed OSV item')
                for vuln in row.get('vulns', []):
                    identifier = vuln['id']
                    if not re.fullmatch(r'[A-Za-z0-9._-]+', identifier): raise ValueError('Invalid advisory ID')
                    matches[key].add(identifier)
                next_token = row.get('next_page_token')
                if next_token:
                    if not isinstance(next_token, str) or (key, next_token) in seen: raise ValueError('Invalid OSV pagination')
                    seen.add((key, next_token)); following.append((key, next_token))
            pending = following
    return matches


def inventory_packages(inventory):
    kind = inventory.get('inventoryKind', 'runtime')
    if kind not in ('runtime', 'build') or inventory.get('formatVersion') != 1 or not inventory.get('scopes'):
        raise ValueError('Missing or unsupported Maven inventory')
    if kind == 'build' and (inventory.get('completed') is not True or
            not inventory.get('taskOutcomes') or any(row.get('failureType') for row in inventory['taskOutcomes'])):
        raise ValueError('Build inventory does not establish a completed invocation')
    packages = set()
    scopes = []
    for scope in inventory['scopes']:
        if not scope['components'] or not isinstance(scope.get('artifacts'), list):
            raise ValueError('Empty or incomplete Maven scope')
        # Plugin marker / platform-only resolutions legitimately have no binary artifacts.
        # The observed-build collector must affirm successful resolution; runtime stays strict.
        if (kind == 'runtime' and not scope['artifacts']) or (kind == 'build' and scope.get('resolved') is not True):
            raise ValueError('Unresolved Maven scope')
        scopes.append({'project': scope['project'], 'configuration': scope['configuration'],
            'modules': len(scope['components']), 'artifacts': len(scope['artifacts'])})
        for item in scope['components']:
            for key in ('group', 'name', 'version'):
                if not re.fullmatch(r'[A-Za-z0-9._+\-]+', item[key]):
                    raise ValueError('Invalid Maven coordinate')
            packages.add((item['group'] + ':' + item['name'], item['version']))
    return kind, sorted(packages), scopes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    # Preserve each dated baseline/candidate result; callers must choose a new file.
    if args.output.exists(): raise ValueError('Evidence output already exists')
    raw = args.inventory.read_bytes()
    inventory = json.loads(raw)
    kind, packages, scopes = inventory_packages(inventory)
    report = {'checkedAtUtc': datetime.now(timezone.utc).isoformat(), 'source': 'https://api.osv.dev/v1/querybatch',
        'inventorySha256': hashlib.sha256(raw).hexdigest(), 'inventoryKind': kind, 'scopes': scopes, 'queriedVersions': len(packages),
        'coverage': f'Exact Maven coordinates in the supplied {kind} inventory only; no proof of exploitability or absence of unknown vulnerabilities',
        'completed': False}
    exit_code = 2
    try:
        matches = query_packages(packages)
        identifiers = sorted(set().union(*matches.values()))
        with ThreadPoolExecutor(max_workers=4) as pool:
            records = list(pool.map(lambda name: request('vulns/' + name), identifiers))
        advisories = dict(zip(identifiers, records))
        for identifier, record in advisories.items():
            if record.get('id') != identifier: raise ValueError('Advisory identity mismatch')
        rows = [{'package': key[0], 'version': key[1], 'ids': sorted(ids),
            'activeIds': sorted(name for name in ids if not advisories[name].get('withdrawn'))}
            for key, ids in matches.items()]
        active = sorted({name for row in rows for name in row['activeIds']})
        report.update(completed=True, matches=rows, advisories=advisories, activeAdvisoryIds=active,
            affectedVersions=sum(bool(row['activeIds']) for row in rows))
        exit_code = 1 if active else 0
    except Exception as error:
        report['failureType'] = type(error).__name__
        if hasattr(error, 'code'): report['httpStatus'] = error.code
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: report[key] for key in ('completed', 'queriedVersions', 'affectedVersions', 'activeAdvisoryIds', 'failureType', 'httpStatus') if key in report}))
    return exit_code


if __name__ == '__main__': raise SystemExit(main())
