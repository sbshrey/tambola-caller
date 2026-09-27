"""Validate complete test-only OkHttp captures and summarize per-wave transport time."""
import argparse
import hashlib
import json
import math
from pathlib import Path

PHASES = ("dispatcherQueueMs", "nonQueueBeforeRequestMs", "requestWriteMs",
          "awaitResponseHeadersMs", "responseReadMs", "closingMs")
METRICS = ("callMs", *PHASES, "connectMsNested")
BASE = {"wave", "primary", "succeeded", "validSingleExchange", "queueCount", "connectionCount", "requestCount", "responseCount"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(condition, message):
    if not condition:
        raise ValueError(message)


def statistics(values):
    values = sorted(values)
    return {"sum": sum(values), "p50": values[math.ceil(len(values) * .5) - 1],
            "p95": values[math.ceil(len(values) * .95) - 1], "max": values[-1]}


def analyze(evidence_path, capture_path):
    evidence = json.loads(evidence_path.read_text(encoding="utf-8-sig"))
    capture = json.loads(capture_path.read_text(encoding="utf-8-sig"))
    check(evidence.get("passed") is True and evidence.get("cleanupComplete") is True, "Fixture failed or cleanup is incomplete")
    check(evidence.get("capacityAcceptance") is False and evidence.get("clientTransportProfiling") is True, "Wrong fixture mode")
    check(evidence["sharedHttpTransports"] * 8 == evidence["players"], "Transport sharing changed")
    check(set(capture) == {"schema", "scope", "dispatcherLimits", "samples"} and capture["schema"] == 1, "Unknown capture schema")
    check(capture["dispatcherLimits"] == [{"maxRequests": 64, "maxRequestsPerHost": 5}], "Default dispatcher settings changed")
    count, waves = evidence["players"], evidence["requestedWaves"]
    check(type(count) is int and count in range(8, 321, 8) and type(waves) is int and waves in range(1, 5), "Invalid fixture size")
    check([wave["wave"] for wave in evidence["waves"]] == list(range(1, waves + 1)), "Missing or duplicated wave summary")
    check(len(evidence["waves"]) == waves and len(capture["samples"]) == 2 * count * waves, "Incomplete primary/replay capture")
    for row in capture["samples"]:
        check(set(row) == BASE | set(METRICS), "Unknown/missing fields in a completed call")
        check(type(row["wave"]) is int and 1 <= row["wave"] <= waves, "Invalid wave")
        check(type(row["primary"]) is bool and row["succeeded"] is True and row["validSingleExchange"] is True, "Failed or incomplete exchange")
        check(all(type(row[key]) is int and row[key] == 1 for key in ("connectionCount", "requestCount", "responseCount")), "Repeated exchange cannot use this partition")
        check(type(row["queueCount"]) is int and row["queueCount"] in (0, 1), "Unexpected queue sequence")
        check(all(type(row[key]) in (int, float) and math.isfinite(row[key]) and row[key] >= 0 for key in METRICS), "Invalid duration")
        check(abs(sum(row[key] for key in PHASES) - row["callMs"]) < .001, "Partition does not sum to call time")
        check(row["connectMsNested"] <= row["nonQueueBeforeRequestMs"] + .001, "Connect duration is not nested inside pre-request time")
        check(row["queueCount"] > 0 or row["dispatcherQueueMs"] == 0, "Queue time without queue events")
    summary = []
    for wave in evidence["waves"]:
        number = wave["wave"]
        check(wave["allRefundsAndReceiptReplaysVerified"] is True and wave["tables"] * 8 == count, "Allocation/refund failure")
        check(wave["streamsObservedBeforeCancellation"] == count, "Missing subscriptions")
        rows = [row for row in capture["samples"] if row["wave"] == number and row["primary"]]
        replays = [row for row in capture["samples"] if row["wave"] == number and not row["primary"]]
        check(len(rows) == count and len(replays) == count, "Incomplete wave")
        calculated = {key: statistics([row[key] for row in rows]) for key in METRICS}
        recorded = wave["clientTransport"]
        check(recorded["count"] == count and recorded["queuedCalls"] == sum(row["queueCount"] > 0 for row in rows), "Bad summary count")
        for key, stats in calculated.items():
            check(all(abs(value - recorded[key][stat]) < .001 for stat, value in stats.items()), "Incorrect summary statistics")
        api_sum, call_sum = recorded["apiSumMs"], calculated["callMs"]["sum"]
        check(math.isfinite(api_sum) and api_sum >= call_sum > 0, "Invalid outside-call duration")
        check(abs(api_sum - call_sum - recorded["outsideOkHttpSumMs"]) < .001, "Bad outside-call arithmetic")
        summary.append({"wave": number, "purchases": count, "receiptReplays": len(replays),
                        "clientRoundTripP95Ms": wave["purchaseRoundTripMs"]["p95"],
                        "queuedCalls": recorded["queuedCalls"], "metrics": calculated,
                        "shareOfSummedApiTimePercent": {**{key: calculated[key]["sum"] / api_sum * 100 for key in PHASES},
                                                       "outsideOkHttp": (api_sum - call_sum) / api_sum * 100}})
    return {"passed": True, "capacityAcceptance": False, "players": count, "waves": summary,
            "serviceRuntimeSha256": evidence["serviceRuntimeSha256"], "clientJarSha256": evidence["clientJarSha256"],
            "inputSha256": {"fixture": digest(evidence_path), "transport": digest(capture_path), "analyzer": digest(Path(__file__))},
            "interpretation": "Shares use sums of concurrent request durations, not wall time or CPU. Server and network waiting remain together in awaitResponseHeadersMs. Connect time is nested. Outside-OkHttp time is an aggregate residual, not a percentile difference. Profiling does not establish a latency improvement."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence", type=Path)
    parser.add_argument("capture", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = analyze(args.evidence, args.capture)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"passed": True, "players": result["players"], "waves": len(result["waves"])}))
