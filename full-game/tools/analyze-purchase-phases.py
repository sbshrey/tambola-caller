"""Summarize fixed-field JFR diagnostics; never publish raw server output."""
import argparse
import hashlib
import json
import math
from pathlib import Path

FIELDS = ("admission", "dispatch", "service", "pool", "allocation", "remainder", "total")


def stats(values):
    values = sorted(values)
    return {"count": len(values), "mean": sum(values) / len(values),
            **{label: values[math.ceil(len(values) * fraction) - 1]
               for label, fraction in (("p50", .5), ("p95", .95), ("p99", .99), ("max", 1))}}


def analyze(evidence, diagnostic):
    assert evidence["passed"] and evidence["cleanupComplete"] and evidence["requestProfiling"]
    rows = []
    for line in diagnostic.splitlines():
        if not line.startswith("COIN_REQUEST_TIMING|"):
            continue
        fields = line.split("|")
        assert len(fields) == 12
        assert fields[2] in ("true", "false") and fields[3] in ("true", "false")
        row = dict(zip(FIELDS, map(int, fields[5:])))
        assert all(value >= 0 for value in row.values())
        assert row["total"] == sum(row[key] for key in ("admission", "dispatch", "service", "remainder"))
        assert row["pool"] + row["allocation"] <= row["service"]
        row.update(epoch_ms=int(fields[1]), friend=fields[2] == "true", success=fields[3] == "true", acquisitions=int(fields[4]))
        assert row["epoch_ms"] > 0 and row["acquisitions"] >= 0
        row["service_other"] = row["service"] - row["pool"] - row["allocation"]
        rows.append(row)
    waves = []
    for wave in evidence["waves"]:
        selected = [r for r in rows if wave["purchaseStartEpochMs"] <= r["epoch_ms"] <= wave["purchaseEndEpochMs"]]
        assert len(selected) == evidence["players"], "Every purchase must have exactly one complete timing event"
        assert all(r["success"] and not r["friend"] for r in selected)
        tail = sorted(selected, key=lambda r: r["total"])[-math.ceil(len(selected) * .05):]
        sums = {key: sum(r[key] for r in selected) for key in FIELDS}
        waves.append({"wave": wave["wave"], "clientRoundTripMs": wave["purchaseRoundTripMs"],
                      "routePhaseMs": {key: stats([r[key] / 1e6 for r in selected]) for key in (*FIELDS, "service_other")},
                      "poolAcquisitions": stats([r["acquisitions"] for r in selected]),
                      "summedRouteTimeShare": {key: sums[key] / sums["total"] for key in ("admission", "dispatch", "service", "remainder")},
                      "slowestFivePercentMeanMs": {key: sum(r[key] for r in tail) / len(tail) / 1e6 for key in (*FIELDS, "service_other")}})
    return {"runId": evidence["runId"], "serviceRuntimeSha256": evidence["serviceRuntimeSha256"],
            "scope": "Profiled loopback purchase bursts. Route timing begins after parsing and ends after respond returns; it is not socket-flush or mobile latency. Pool and allocator waits are nested inside service time. No request identities retained.",
            "capacityAcceptance": False, "totalEvents": len(rows), "waves": waves}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    parser.add_argument("diagnostic", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    diagnostic = args.diagnostic.read_text(encoding="utf-8")
    summary = analyze(json.loads(args.evidence.read_text(encoding="utf-8-sig")), diagnostic)
    timings = "\n".join(line for line in diagnostic.splitlines() if line.startswith("COIN_REQUEST_TIMING|")) + "\n"
    timing_path = args.output.with_suffix(".timings.txt")
    summary.update(evidenceSha256=hashlib.sha256(args.evidence.read_bytes()).hexdigest(),
                   analyzerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                   timingsSha256=hashlib.sha256(timings.encode("utf-8")).hexdigest(),
                   timingsFile=timing_path.name)
    timing_path.write_text(timings, encoding="utf-8", newline="\n")
    args.output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary))
