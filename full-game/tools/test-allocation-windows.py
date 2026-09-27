"""Check real retained allocation intervals, safe filtering and rejection of incomplete diagnostics."""
import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('allocation_analysis', ROOT / 'tools/analyze-allocation-windows.py')
ANALYZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ANALYZER)
FIXTURE = ROOT / 'reviews/allocation-windows-2026-09-27/smoke'


class AllocationAnalysisTest(unittest.TestCase):
    def load(self):
        evidence = json.loads((FIXTURE / 'burst-evidence.json').read_text(encoding='utf-8-sig'))
        lines = (FIXTURE / 'allocation-windows.timings.txt').read_text().splitlines()
        return evidence, lines

    def analyze(self, evidence, lines):
        with tempfile.TemporaryDirectory() as folder:
            paths = Path(folder) / 'evidence.json', Path(folder) / 'log.txt'
            paths[0].write_text(json.dumps(evidence), encoding='utf-8')
            paths[1].write_text('\n'.join(lines) + '\n', encoding='utf-8')
            return ANALYZER.analyze(*paths)

    def test_real_capture_coverage_matches_an_independent_interval_union(self):
        evidence, lines = self.load()
        result, filtered = self.analyze(evidence, lines)
        self.assertEqual(filtered.splitlines(), lines)
        self.assertEqual(result['samples'], evidence['players'] * evidence['requestedWaves'])
        self.assertFalse(result['capacityAcceptance'])
        for wave, summary in zip(evidence['waves'], result['waves']):
            intervals = []
            for line in lines:
                fields = line.split('|')
                if int(fields[1]) >= wave['purchaseStartEpochMs'] - 1 and int(fields[2]) <= wave['purchaseEndEpochMs'] + 1:
                    intervals.append((int(fields[3]), int(fields[4])))
            points = sorted({value for interval in intervals for value in interval})
            covered = sum(right - left for left, right in zip(points, points[1:])
                          if any(start <= left and end >= right for start, end in intervals))
            self.assertAlmostEqual(covered / 1e6, summary['observedUnionMs'], places=8)

    def test_unrelated_private_log_lines_are_never_retained(self):
        evidence, lines = self.load()
        _, filtered = self.analyze(evidence, ['private-token-and-SQL-do-not-export', *lines, 'another-private-line'])
        self.assertEqual(filtered.splitlines(), lines)

    def test_incomplete_or_mislabelled_captures_fail(self):
        original = self.load()

        def field(lines, index, value):
            fields = lines[0].split('|')
            fields[index] = value
            lines[0] = '|'.join(fields)

        cases = {
            'failed_fixture': lambda e, rows: e.update(passed=False),
            'failed_cleanup': lambda e, rows: e.update(cleanupComplete=False),
            'wrong_flag': lambda e, rows: e.update(allocationWindowProfiling=False),
            'different_clients': lambda e, rows: e.update(playersPerTransport=1),
            'fractional_client_count': lambda e, rows: e.update(sharedHttpTransports=1.0),
            'invalid_hash': lambda e, rows: e.update(serviceRuntimeSha256='private-content'),
            'missing_row': lambda e, rows: rows.pop(),
            'duplicate_row': lambda e, rows: rows.append(rows[0]),
            'extra_field': lambda e, rows: rows.__setitem__(0, rows[0] + '|private-content'),
            'negative_time': lambda e, rows: field(rows, 5, '-1'),
            'false_partition': lambda e, rows: field(rows, 6, '999999999999999'),
            'unknown_query': lambda e, rows: field(rows, 7, 'private-query:1:0'),
            'outside_wave': lambda e, rows: field(rows, 1, '1'),
            'missing_subscriptions': lambda e, rows: e['waves'][0].update(streamsObservedBeforeCancellation=0),
            'false_capacity': lambda e, rows: e.update(capacityAcceptance=True),
        }
        for name, mutate in cases.items():
            with self.subTest(name=name):
                evidence, rows = copy.deepcopy(original)
                mutate(evidence, rows)
                with self.assertRaises(ValueError):
                    self.analyze(evidence, rows)


if __name__ == '__main__':
    unittest.main(verbosity=2)
