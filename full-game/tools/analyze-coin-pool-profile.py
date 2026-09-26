"""Retain fixed-label connection-pool park events and compare purchase-burst windows."""
import argparse
import hashlib
import json
import math
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    directory = parser.parse_args().directory
    evidence = json.loads((directory / "burst-evidence.json").read_text(encoding="utf-8"))
    assert evidence["sqlProfiling"] and evidence["poolProfiling"]
    labels = ("rate_primary", "rate_journal", "match_primary", "match_journal")
    events, flushes, retained = [], [], []
    for line in (directory / "service-diagnostic.log").read_text(encoding="utf-8").splitlines():
        if line.startswith("COIN_POOL_PARK|"):
            match = re.fullmatch(r"COIN_POOL_PARK\|([a-z_]+)\|([0-9]+)\|([0-9]+)", line)
            assert match and match[1] in labels
            label, start, duration = match[1], int(match[2]), int(match[3]) / 1e6
            assert start > 0 and duration >= 0
            events.append((label, start, duration))
            retained.append(line)
        elif line.startswith("COIN_POOL_FLUSH|"):
            assert re.fullmatch(r"COIN_POOL_FLUSH\|[0-9]+", line)
            flushes.append(int(line.split("|")[1]))
            retained.append(line)
    assert events and flushes and evidence["waves"], "Missing events, flush markers or waves"

    waves = []
    for wave in evidence["waves"]:
        start, end = wave["purchaseStartEpochMs"], wave["purchaseEndEpochMs"]
        assert end > start and max(flushes) > end + 1000, "Recording not flushed beyond purchase window"
        groups = {}
        for label in labels:
            overlapping = [(s, d) for name, s, d in events if name == label and s < end and s + d > start]
            durations = sorted(d for _, d in overlapping)
            groups[label] = {
                "parkEventsOverlappingWindow": len(durations),
                "sumOfWindowOverlapsMs": sum(min(end, s + d) - max(start, s) for s, d in overlapping),
                "fullEventDurationMs": {
                    "p50": durations[math.ceil(len(durations) * .5) - 1],
                    "p95": durations[math.ceil(len(durations) * .95) - 1],
                    "max": durations[-1],
                } if durations else None,
            }
        waves.append({"wave": wave["wave"], "purchaseWindowMs": end - start,
                      "purchaseRoundTripMs": wave["purchaseRoundTripMs"], "poolParks": groups})
    summary = {
        "runId": evidence["runId"],
        "definition": "Test-only JFR ThreadPark events with >=1 ms threshold and both RoomService.match and Hikari ConcurrentBag.borrow in the recorded stack. Includes instrumentation overhead. Events are not request counts or full connection-acquisition durations; missing stacks or shorter parks are not measured. Summed overlapping waits are concurrent thread time, not elapsed wall time. Epoch-millisecond window boundaries are approximate. No capacity acceptance.",
        "analyzerSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "events": len(events), "lastFlushEpochMs": max(flushes), "waves": waves,
    }
    (directory / "pool-parks.txt").write_text("\n".join(retained) + "\n", encoding="utf-8")
    (directory / "pool-profile-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
