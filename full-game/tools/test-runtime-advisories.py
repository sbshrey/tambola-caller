import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('advisories', Path(__file__).with_name('check-runtime-advisories.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class AdvisoryPaginationTest(unittest.TestCase):
    def test_individual_pages_remain_attached_to_the_right_package(self):
        packages = [('example:one', '1'), ('example:two', '2')]
        calls = []
        def fetch(path, body):
            calls.append(body)
            if len(calls) == 1:
                return {'results': [{'vulns': [{'id': 'GHSA-first'}], 'next_page_token': 'next'}, {}]}
            self.assertEqual(body['queries'], [{'package': {'ecosystem': 'Maven', 'name': 'example:one'}, 'version': '1', 'page_token': 'next'}])
            return {'results': [{'vulns': [{'id': 'GHSA-second'}]}]}
        self.assertEqual(module.query_packages(packages, fetch), {packages[0]: {'GHSA-first', 'GHSA-second'}, packages[1]: set()})

    def test_truncated_batch_cannot_report_no_findings(self):
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            module.query_packages([('example:one', '1')], lambda *_: {'results': []})

    def test_repeated_page_token_fails_instead_of_hanging_or_truncating(self):
        with self.assertRaisesRegex(ValueError, 'pagination'):
            module.query_packages([('example:one', '1')], lambda *_: {'results': [{'next_page_token': 'same'}]})

    def test_batch_item_error_is_not_a_clean_result(self):
        with self.assertRaisesRegex(ValueError, 'Failed OSV item'):
            module.query_packages([('example:one', '1')], lambda *_: {'results': [{'error': 'unavailable'}]})


if __name__ == '__main__': unittest.main()
