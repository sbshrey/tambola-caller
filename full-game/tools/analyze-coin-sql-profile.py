"""Summarize fixed-label JDBC diagnostics; never emit unfiltered server log lines."""
import argparse
import hashlib
import json
import math
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="One isolated coin-load output directory")
    args = parser.parse_args()
    source = args.directory / "service-diagnostic.log"
    labels = {
        "wallet_lock", "ledger_previous", "wallet_view", "ledger_insert", "room_select",
        "room_insert", "room_update", "participants_insert", "event_insert", "receipt_insert", "other",
    }
    rows, groups, retained = [], {}, []
    for line in source.read_text(encoding="utf-8").splitlines():
        if not line.startswith("COIN_SQL_TIMING|"):
            continue
        _, wait, locked, commit, items = line.split("|")
        sample = {key: int(value) / 1e6 for key, value in zip(("wait", "locked", "commit"), (wait, locked, commit))}
        assert all(value >= 0 for value in sample.values())
        executed = 0.0
        for item in items.split(";"):
            label, count, nanos = item.split(":")
            assert label in labels and int(count) > 0 and int(nanos) >= 0
            values = groups.setdefault(label, {"count": 0, "totalMs": 0.0})
            values["count"] += int(count)
            values["totalMs"] += int(nanos) / 1e6
            executed += int(nanos) / 1e6
        sample["outsideExecuteCalls"] = sample["locked"] - sample["commit"] - executed
        assert sample["outsideExecuteCalls"] >= 0
        rows.append(sample)
        retained.append(line)
    assert rows, "No successful profiled transactions"

    def stats(key):
        values = sorted(row[key] for row in rows)
        return {"sumMs": sum(values), "p50": values[math.ceil(len(values) * .5) - 1],
                "p95": values[math.ceil(len(values) * .95) - 1], "max": values[-1]}

    result = {
        "samples": len(rows),
        "definition": "Diagnostic proxy timings in milliseconds. OutsideExecuteCalls includes preparation, decoding and application work, not just CPU time. Includes instrumentation overhead; not capacity acceptance.",
        "analyzerSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "segments": {key: stats(key) for key in rows[0]},
        "queries": dict(sorted(groups.items(), key=lambda item: -item[1]["totalMs"])),
    }
    # Retain only the validated timing records, excluding all ordinary server diagnostics.
    (args.directory / "sql-timings.txt").write_text("\n".join(retained) + "\n", encoding="utf-8")
    (args.directory / "sql-profile-summary.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
