"""Exercise analyzer rejection against retained real captures, including both client topologies."""
import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("transport_analysis", ROOT / "tools/analyze-client-transport.py")
ANALYZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ANALYZER)


class TransportAnalysisTest(unittest.TestCase):
    def load(self, folder):
        path = ROOT / "reviews" / folder
        return [json.loads((path / name).read_text(encoding="utf-8-sig"))
                for name in ("burst-evidence.json", "client-transport.json")]

    def analyze(self, evidence, capture):
        with tempfile.TemporaryDirectory() as temporary:
            files = [Path(temporary) / name for name in ("evidence.json", "capture.json")]
            for file, data in zip(files, (evidence, capture)):
                file.write_text(json.dumps(data), encoding="utf-8")
            return ANALYZER.analyze(*files)

    def test_original_eight_player_sharing_remains_valid(self):
        result = self.analyze(*self.load("purchase-transport-2026-09-27/profile-1"))
        self.assertEqual((result["playersPerTransport"], result["httpTransports"]), (8, 40))
        self.assertFalse(result["capacityAcceptance"])

    def test_separate_clients_remain_labelled_diagnostic(self):
        result = self.analyze(*self.load("transport-sharing-2026-09-27/smoke"))
        self.assertEqual((result["playersPerTransport"], result["httpTransports"]), (1, 8))
        self.assertFalse(result["capacityAcceptance"])

    def test_mislabelling_and_incomplete_evidence_are_rejected(self):
        baseline = self.load("transport-sharing-2026-09-27/smoke")
        cases = {
            "wrong_topology": lambda e, c: e.update(playersPerTransport=8),
            "unsupported_topology": lambda e, c: e.update(playersPerTransport=2),
            "boolean_topology": lambda e, c: e.update(playersPerTransport=True),
            "wrong_transport_count": lambda e, c: e.update(sharedHttpTransports=1),
            "boolean_transport_count": lambda e, c: e.update(sharedHttpTransports=True),
            "missing_call": lambda e, c: c["samples"].pop(),
            "unexpected_field": lambda e, c: c["samples"][0].update(extra="unexpected"),
            "bad_duration": lambda e, c: c["samples"][0].update(callMs=0),
            "changed_dispatcher": lambda e, c: c["dispatcherLimits"][0].update(maxRequestsPerHost=8),
            "failed_cleanup": lambda e, c: e.update(cleanupComplete=False),
            "false_capacity_pass": lambda e, c: e.update(capacityAcceptance=True),
        }
        for name, mutate in cases.items():
            with self.subTest(name=name):
                evidence, capture = copy.deepcopy(baseline)
                mutate(evidence, capture)
                with self.assertRaises(ValueError):
                    self.analyze(evidence, capture)


if __name__ == "__main__":
    unittest.main(verbosity=2)
