"""Validate elapsed allocation intervals; never export unfiltered server logs or infer CPU time."""
import argparse
import hashlib
import json
import math
import re
from pathlib import Path

PREFIX = "COIN_ALLOCATION_WINDOW|"
LABELS = {"wallet_lock", "ledger_previous", "wallet_view", "ledger_insert", "room_select",
          "room_insert", "room_update", "participants_insert", "event_insert", "receipt_insert", "other"}


def check(value, message):
    if not value:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def stats(values):
    ordered = sorted(values)
    return {"sumMs": sum(values), "meanMs": sum(values) / len(values),
            "p50Ms": ordered[math.ceil(len(values) * .5) - 1],
            "p95Ms": ordered[math.ceil(len(values) * .95) - 1], "maxMs": ordered[-1]}


def analyze(evidence_path, log_path):
    evidence = json.loads(evidence_path.read_text(encoding="utf-8-sig"))
    check(evidence.get("passed") is True and evidence.get("cleanupComplete") is True, "Incomplete fixture or cleanup")
    check(evidence.get("capacityAcceptance") is False and evidence.get("sqlProfiling") is True
          and evidence.get("allocationWindowProfiling") is True, "Wrong profiling condition")
    sharing = evidence.get("playersPerTransport", 8)
    check(type(sharing) is int and sharing == 8 and type(evidence["sharedHttpTransports"]) is int
          and evidence["sharedHttpTransports"] * 8 == evidence["players"], "Changed transport condition")
    check(all(isinstance(evidence.get(key), str) and re.fullmatch('[a-f0-9]{64}', evidence[key])
              for key in ('serviceRuntimeSha256', 'profilerSourceSha256')), "Invalid source/runtime hash")
    count, waves = evidence["players"], evidence["requestedWaves"]
    check(type(count) is int and count in range(8, 321, 8), "Invalid player count")
    check(type(waves) is int and waves in range(1, 5), "Invalid wave count")
    check([wave["wave"] for wave in evidence["waves"]] == list(range(1, waves + 1)), "Incomplete waves")
    retained, samples = [], []
    for line in log_path.read_text(encoding="utf-8").splitlines():
        if not line.startswith(PREFIX):
            continue
        fields = line.split('|')
        check(len(fields) == 8, "Unexpected allocation fields")
        check(all(value.isascii() and value.isdecimal() for value in fields[1:7]), "Non-numeric allocation fields")
        start_ms, end_ms, start_ns, end_ns, wait_ns, commit_ns = map(int, fields[1:7])
        check(0 < start_ms <= end_ms and 0 <= start_ns < end_ns, "Invalid interval")
        held_ns = end_ns - start_ns
        check(abs(end_ms - start_ms - held_ns / 1e6) <= 2, "Clock mismatch")
        queries = {}
        for item in fields[7].split(';'):
            parts = item.split(':')
            check(len(parts) == 3 and parts[0] in LABELS and parts[0] not in queries, "Unknown/duplicate query category")
            check(all(value.isascii() and value.isdecimal() for value in parts[1:]), "Non-numeric query fields")
            number, nanos = map(int, parts[1:])
            check(number > 0, "Empty query count")
            queries[parts[0]] = (number, nanos)
        check(all(queries.get(key, (0,))[0] == 1 for key in ("room_select", "wallet_lock", "ledger_previous",
                  "ledger_insert", "room_update", "participants_insert", "event_insert", "receipt_insert")), "Incomplete purchase work")
        check(queries.get("wallet_view", (0,))[0] == 2 and queries.get("room_insert", (0,))[0] in (0, 1), "Unexpected wallet/room work")
        query_ns = sum(nanos for _, nanos in queries.values())
        check(commit_ns + query_ns <= held_ns, "SQL/commit duration exceeds containing interval")
        samples.append({"startMs": start_ms, "endMs": end_ms, "startNs": start_ns, "endNs": end_ns,
                        "waitMs": wait_ns / 1e6, "heldMs": held_ns / 1e6, "commitMs": commit_ns / 1e6,
                        "jdbcMs": query_ns / 1e6, "outsideJdbcMs": (held_ns - commit_ns - query_ns) / 1e6, "queries": queries})
        retained.append(line)
    check(len(samples) == count * waves, "Missing/extra successful allocation intervals")
    check(len({(row['startNs'], row['endNs']) for row in samples}) == len(samples), "Duplicate interval")
    summaries, assigned = [], set()
    previous_end = 0
    for wave in evidence["waves"]:
        beginning, ending = wave["purchaseStartEpochMs"], wave["purchaseEndEpochMs"]
        check(type(beginning) is int and type(ending) is int and previous_end < beginning < ending, "Invalid purchase window")
        previous_end = ending
        check(wave["tables"] * 8 == count and wave["allRefundsAndReceiptReplaysVerified"] is True
              and wave["streamsObservedBeforeCancellation"] == count, "Incomplete table/receipt/refund/subscription checks")
        selected = [index for index, row in enumerate(samples) if row["startMs"] >= beginning - 1 and row["endMs"] <= ending + 1]
        check(len(selected) == count and assigned.isdisjoint(selected), "Allocation coverage does not match wave")
        assigned.update(selected)
        rows = sorted((samples[index] for index in selected), key=lambda row: row["startNs"])
        totals = {label: {"count": 0, "sumMs": 0} for label in LABELS}
        for row in rows:
            for label, (number, nanos) in row['queries'].items():
                totals[label]["count"] += number
                totals[label]["sumMs"] += nanos / 1e6
        check(totals["room_insert"]["count"] == count // 8, "Room creation count mismatch")
        left, right = rows[0]["startNs"], rows[0]["endNs"]
        union_ns = 0
        for row in rows[1:]:
            if row["startNs"] > right:
                union_ns += right - left
                left, right = row["startNs"], row["endNs"]
            else:
                right = max(right, row["endNs"])
        union_ns += right - left
        span_ns = max(row["endNs"] for row in rows) - rows[0]["startNs"]
        metrics = {key: stats([row[key] for row in rows]) for key in ("waitMs", "heldMs", "commitMs", "jdbcMs", "outsideJdbcMs")}
        check(union_ns / 1e6 <= ending - beginning + 2, "Interval union exceeds client window")
        summaries.append({"wave": wave["wave"], "allocations": count,
                          "clientPurchaseP95Ms": wave["purchaseRoundTripMs"]["p95"], "clientWindowMs": ending - beginning,
                          "observedAllocationSpanMs": span_ns / 1e6, "observedUnionMs": union_ns / 1e6,
                          "unionShareOfClientWindowPercent": union_ns / 1e6 / (ending - beginning) * 100,
                          "gapsWithinObservedSpanMs": (span_ns - union_ns) / 1e6,
                          "overlappingObservedMs": metrics["heldMs"]["sumMs"] - union_ns / 1e6,
                          "metrics": metrics,
                          "shareOfSummedHeldTimePercent": {key: metrics[key]["sumMs"] / metrics["heldMs"]["sumMs"] * 100
                                                          for key in ("commitMs", "jdbcMs", "outsideJdbcMs")},
                          "queries": dict(sorted(((key, value) for key, value in totals.items() if value["count"]), key=lambda item: -item[1]["sumMs"]))})
    check(len(assigned) == len(samples), "Unassigned allocation intervals")
    filtered = '\n'.join(retained) + '\n'
    return {"passed": True, "capacityAcceptance": False, "samples": len(samples), "waves": summaries,
            "serviceRuntimeSha256": evidence["serviceRuntimeSha256"], "profilerSourceSha256": evidence["profilerSourceSha256"],
            "inputSha256": {"fixture": digest(evidence_path), "privateLog": digest(log_path), "analyzer": digest(Path(__file__)),
                            "filteredTimings": hashlib.sha256(filtered.encode()).hexdigest()},
            "scope": "After advisory-query return through commit return; approximate lock occupancy, not exact database lock lifetime. JDBC includes transport, server and scheduling; outside-JDBC is not CPU. Overlap and gaps include boundary/round-trip effects. Instrumented diagnostic, not capacity acceptance."}, filtered


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('evidence', type=Path)
    parser.add_argument('log', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    result, filtered = analyze(args.evidence, args.log)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    args.output.with_suffix('.timings.txt').write_text(filtered, encoding='utf-8', newline='\n')
    print(json.dumps({"passed": True, "samples": result["samples"], "waves": len(result["waves"])}))
