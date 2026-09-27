"""Summarize the native endurance artifacts without accepting an unfinished run.

Only the fixture's public diagnostics are read. No app databases, accounts or
server credentials are needed. Partial mode always exits 2, never acceptance 0.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def require(condition, label):
    if not condition:
        raise ValueError(label)


def integer(value, label, minimum=0):
    require(type(value) is int and value >= minimum, label)
    return value


def percentile(values, fraction):
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    return ordered[lower] + (ordered[math.ceil(position)] - ordered[lower]) * (position - lower)


def summarize(journey, partial, benchmark=None, transcript=None):
    require(journey.get("expectedRounds") == 9, "wrong_round_target")
    require(not journey.get("failureType"), "journey_failed")
    rows = journey.get("roundReports", [])
    require(isinstance(rows, list) and len(rows) <= 9, "invalid_round_reports")
    require(journey.get("roundsCompleted", 0) == len(rows), "round_counter_mismatch")
    require(journey.get("ownedTickets") == 6 and journey.get("distinctOwnNumbers") == 90, "owned_hand_not_verified")
    main_balance, peer_balances, refill_coins, duration = 1500, [1500] * 3, 0, 0
    summaries = []
    for index, row in enumerate(rows, 1):
        require(row.get("round") == index, "round_order_mismatch")
        require(row.get("balanceBefore") == main_balance, "wallet_chain_broken")
        for peer, balance in enumerate(peer_balances):
            if balance < 100:
                peer_balances[peer] += 500
                refill_coins += 500
        quantities = [min(6, balance // 100) for balance in peer_balances]
        require(row.get("peerTicketQuantities") == quantities, "peer_purchase_mismatch")
        tickets = 6 + sum(quantities)
        pool = tickets * 100
        prizes = 6 if tickets < 12 else 7 if tickets < 24 else 8
        require(row.get("pool") == pool and row.get("prizes") == prizes, "pool_or_prizes_mismatch")
        peer_balances = [balance - quantity * 100 for balance, quantity in zip(peer_balances, quantities)]
        main_balance += pool - 600
        aggregate = main_balance + sum(peer_balances)
        require(aggregate == 6000 + refill_coins, "coin_conservation_mismatch")
        require(row.get("finalBalance") == main_balance and row.get("aggregateBalance") == aggregate, "settlement_mismatch")
        calls = integer(row.get("calls"), "invalid_calls", 1)
        require(calls <= 90, "too_many_calls")
        round_duration = integer(row.get("durationMs"), "invalid_round_duration", 1)
        duration += round_duration
        require(row.get("replayQuantityAndRefund") is True, "missing_replay_refund")
        reconnect = "process-death" if index == 3 else "foreground"
        require(row.get("reconnect") == reconnect, "reconnect_scenario_mismatch")
        marks = integer(row.get("retainedMarks"), "invalid_restored_marks", 20)
        require(marks <= calls, "restored_marks_exceed_calls")
        summaries.append({"round": index, "calls": calls, "prizes": prizes, "pool": pool,
                          "nativeBalance": main_balance, "aggregateBalance": aggregate,
                          "roundDurationMs": round_duration, "reconnect": reconnect, "retainedMarks": marks})

    samples = journey.get("memorySamples", [])
    require(isinstance(samples, list), "invalid_memory_samples")
    groups = {}
    memory_rows = {}
    for sample in samples:
        round_number = integer(sample.get("round"), "invalid_memory_round", 1)
        require(round_number <= 9, "memory_round_out_of_range")
        pid = integer(sample.get("pid"), "invalid_memory_pid", 1)
        stage = sample.get("stage")
        expected_stages = {"entry", "round-complete", "cold-restored" if round_number == 3 else "foreground-restored"}
        require(stage in expected_stages, "unexpected_memory_stage")
        key = (round_number, stage)
        require(key not in memory_rows, "duplicate_memory_sample")
        memory_rows[key] = sample
        for metric in ["elapsedMs", "totalPssKb", "javaHeapPssKb", "nativeHeapPssKb", "activities"]:
            integer(sample.get(metric), "invalid_memory_measure")
        groups.setdefault(pid, []).append(sample)
    for index in range(1, len(rows) + 1):
        for stage in ["entry", "round-complete", "cold-restored" if index == 3 else "foreground-restored"]:
            require((index, stage) in memory_rows, "missing_memory_sample")
    if len(rows) >= 3:
        require(memory_rows[3, "entry"]["pid"] != memory_rows[3, "cold-restored"]["pid"], "process_death_not_observed")
    memory = [{"pid": pid, "samples": len(values), "firstRound": values[0]["round"], "lastRound": values[-1]["round"],
               "firstPssKb": values[0]["totalPssKb"], "lastPssKb": values[-1]["totalPssKb"],
               "peakPssKb": max(v["totalPssKb"] for v in values),
               "peakJavaHeapPssKb": max(v["javaHeapPssKb"] for v in values),
               "peakNativeHeapPssKb": max(v["nativeHeapPssKb"] for v in values),
               "peakActivityObjectCount": max(v["activities"] for v in values)} for pid, values in groups.items()]

    result = {"accepted": False, "scope": "Native endurance correctness and fixture cleanup; not smoothness or release readiness",
              "mode": "partial" if partial else "final", "roundsCompleted": len(rows),
              "measuredRoundDurationMs": duration, "nativeBalance": main_balance, "refillCoins": refill_coins,
              "rounds": summaries, "memoryByProcess": memory,
              "limits": ["One software emulator with three passive API peers; not physical phone or capacity acceptance",
                         "Memory snapshots and Activity object counts do not prove a leak or leak freedom",
                         "No controlled response loss in this endurance scenario",
                         "APK identities, runtime health and device-setting restoration require separate verification"]}
    if partial:
        return result
    require(len(rows) == 9 and duration >= 3_600_000, "incomplete_hour_or_rounds")
    require(journey.get("completed") is True and journey.get("minimumHourVerified") is True
            and journey.get("measuredRoundDurationMs") == duration and journey.get("stage") == "passed", "journey_not_accepted")
    require(journey.get("nativeProfileDeleted") is True and journey.get("deletedQaPeers") == 3, "profile_cleanup_incomplete")
    require(len(samples) == 27, "incomplete_memory_samples")
    require(transcript and "OK (1 test)" in transcript
            and not any(marker in transcript for marker in ["FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed"]), "junit_not_passed")
    require(benchmark is not None, "benchmark_missing")
    candidates = [b for b in benchmark.get("benchmarks", []) if b.get("name") == "realCoinEndurance"
                  and b.get("className") == "io.github.sbshrey.tambola.benchmark.CoinReleaseBenchmark"]
    require(len(candidates) == 1, "wrong_benchmark")
    measured = candidates[0]
    require(measured.get("repeatIterations") == 9, "wrong_benchmark_iterations")
    counts = measured["metrics"]["frameCount"]["runs"]
    frames = measured["sampledMetrics"]["frameDurationCpuMs"]
    runs = frames["runs"]
    require(len(counts) == len(runs) == 9, "missing_frame_runs")
    for row, count, values in zip(summaries, counts, runs):
        require(values and len(values) == count, "frame_count_mismatch")
        require(all(type(v) in [int, float] and math.isfinite(v) and v >= 0 for v in values), "invalid_frame_duration")
        row["frameCount"] = len(values)
        row["frameDurationCpuMs"] = {"p50": percentile(values, .5), "p95": percentile(values, .95), "p99": percentile(values, .99)}
    result.update(accepted=True, frameCount=sum(counts), reportedAggregateFrameCpuMs={k: v for k, v in frames.items() if k != "runs"},
                  percentileDefinition="Per-round linear interpolation at (n-1)*p; aggregate values preserved from Macrobenchmark")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("journey", type=Path)
    parser.add_argument("--benchmark", type=Path)
    parser.add_argument("--transcript", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--partial", action="store_true", help="Diagnostic only; always exits 2")
    args = parser.parse_args()
    require(not args.output.exists(), "output_already_exists")
    if not args.partial:
        require(args.benchmark and args.transcript, "final_inputs_required")
    inputs = {"journey": args.journey, "benchmark": args.benchmark, "transcript": args.transcript}
    try:
        result = summarize(read_json(args.journey), args.partial,
                           read_json(args.benchmark) if args.benchmark else None,
                           args.transcript.read_text(encoding="utf-8-sig") if args.transcript else None)
    except (ValueError, KeyError, TypeError) as error:
        result = {"accepted": False, "failure": str(error) if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError) else type(error).__name__}
    result["inputSha256"] = {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in inputs.items() if path}
    result["analyzerSha256"] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    with args.output.open("x", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"accepted": result["accepted"], "mode": result.get("mode"),
                      "roundsCompleted": result.get("roundsCompleted"), "failure": result.get("failure"), "output": str(args.output)}))
    return 2 if args.partial else 0 if result["accepted"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
