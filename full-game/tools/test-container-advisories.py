import copy
from datetime import datetime, timezone
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('container_advisories', Path(__file__).with_name('check-container-advisories.py'))
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
IMAGE = 'sha256:' + 'a' * 64
NOW = datetime(2026, 9, 26, tzinfo=timezone.utc)
REPORT = {'source': {'type': 'image', 'target': {'imageID': IMAGE}}, 'descriptor': {
    'name': 'grype', 'version': '0.119.0', 'db': {'status': {'valid': True, 'built': '2026-09-25T00:00:00Z'}},
    'configuration': {}}, 'matches': []}


class ContainerGateTest(unittest.TestCase):
    def test_stale_database_or_unrelated_image_cannot_pass(self):
        with self.assertRaises(ValueError): module.evaluate(REPORT, 'sha256:' + 'b' * 64, NOW)
        report = copy.deepcopy(REPORT); report['descriptor']['db']['status']['built'] = '2026-09-19T00:00:00Z'
        with self.assertRaises(ValueError): module.evaluate(report, IMAGE, NOW)

    def test_filtered_or_incomplete_reports_cannot_pass(self):
        for key in ('only-fixed', 'only-notfixed', 'ignore-wontfix', 'exclude', 'vex-documents'):
            report = copy.deepcopy(REPORT); report['descriptor']['configuration'][key] = True
            with self.assertRaises(ValueError): module.evaluate(report, IMAGE, NOW)
        report = copy.deepcopy(REPORT); del report['matches']
        with self.assertRaises(KeyError): module.evaluate(report, IMAGE, NOW)
        report = copy.deepcopy(REPORT); report['descriptor']['configuration']['ignore'] = [{'package': {'name': 'libc6'}}]
        with self.assertRaises(ValueError): module.evaluate(report, IMAGE, NOW)

    def test_available_medium_fixes_and_all_high_findings_block(self):
        for severity, state, passed in [('High', 'not-fixed', False), ('Critical', 'fixed', False),
            ('Medium', 'fixed', False), ('Medium', 'not-fixed', True), ('Low', 'not-fixed', True)]:
            report = copy.deepcopy(REPORT)
            report['matches'] = [{'vulnerability': {'id': 'TEST-1', 'severity': severity, 'fix': {'state': state}},
                'artifact': {'name': 'example', 'version': '1'}}]
            self.assertEqual(module.evaluate(report, IMAGE, NOW)['passed'], passed)


if __name__ == '__main__': unittest.main()
