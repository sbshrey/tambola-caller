# New purchases during active games

Fixture source `a9ec87a` adds an opt-in full mixed-traffic scenario. Run `b4fd4eaf684a4e55` tested the unchanged production runtime from `36df836`: 64 players first, then 128 joining during the first valid selected claim, then 128 joining at that table's announced next-draw deadline. All forty eight-seat tables used 1–6 owned tickets, normal twelve-second sales and five-second calls. Profiles were seeded; wallet preparation and diagnostic profiling were disabled. See [reproduction and timing definitions](../../server/COIN_LOAD.md).

The triggering claimant waited for at least eight issued primary purchases and one still pending. All three cohorts reached their intended purchase counts; peak pending primary requests were 64, 128 and 119. Successful primary purchase windows were 1,274, 1,056 and 1,153 ms. Duplicate/replayed purchases are checked separately and do not extend those windows. A pending client request is not proof of continuous server CPU work.

## Results

All 320 players finished, with 1,116 tickets, **347 accepted claims**, **25 shared prizes**, **435 exact receipt checks**, forty delayed acknowledgement retries and zero closed claim windows. All private hands, fixed pools, results and independently calculated wallet shares passed. Settlement paid 111,600 coins while preserving aggregate wallet/ledger balance 480,000. Every expected call delivery arrived exactly once: **25,040/25,040**. Rounds finished after 73–84 calls; four claims were concurrently in flight at peak.

| Client measurement | Samples | p50 | p95 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| All purchases | 320 | 622.423 ms | **1,084.945 ms** | 1,257.571 ms |
| Initial 64 purchases | 64 | 838.080 ms | 1,189.415 ms | 1,257.571 ms |
| First joining 128 purchases | 128 | 561.291 ms | 936.297 ms | 1,046.404 ms |
| Second joining 128 purchases | 128 | 502.009 ms | 1,075.903 ms | 1,092.812 ms |
| All claims | 347 | 11.142 ms | 40.359 ms | 564.975 ms |
| All draw-event deliveries | 25,040 | 299 ms | 528 ms | 1,087 ms |
| Existing claims starting during joins | **3** | 91.218 ms | 564.975 ms | 564.975 ms |
| Existing draw events during joins | **64** | 492 ms | 632 ms | 821 ms |

Both overlapping measurements fall below the one-second p95 target. **Three overlapping claims are only a smoke sample**, not a stable population percentile or a guarantee under sustained joining traffic. The two joining windows exclude the gap between them. Delivery samples select events timestamped within those windows; they do not select every response received while a purchase was pending.

The complete run correctly exited **1** after 9 minutes 2 seconds: correctness and cleanup passed, but overall purchase p95 exceeded 1,000 ms. `mixedTrafficObserved` and `mixedP95Under1000Ms` are true; `allP95Under1000Ms` is false. Dividing purchases into cohorts is a different workload from the previous all-at-once 320-player burst, **not a server speedup**. No production admission, transaction or pool setting was changed for this run.

Draw delivery is the same-host interval from the persisted event timestamp, captured before commit, to each native client's first matching snapshot. This is not scheduled-deadline, physical Wi-Fi, Android rendering, TLS or restricted-role acceptance. Synthetic clients submit revealed owned marks through the same public selected-claim API.

## Identity and cleanup

The service JAR is `a8488b60c8c1b638dbdeb250ec26bd8d59a332f7b415782fe4336a16c83d5d1b`; the complete 51-JAR runtime is `f2b39e5bb622b0bd9c368da654a9412fc3e34d617e171497a1f521b7ec80ee10`. Both match the preceding full-round candidate. [Raw evidence](evidence.json) retains the fixture/client/lifecycle hashes and complete runtime manifest. [The transcript](transcript.txt) retains the failed latency outcome; GC data is preserved without private service output.

[Independent cleanup](cleanup.json) confirmed PID 18592 and both exact run databases absent, with zero random test schemas remaining. [Pre-deployment host health](host-health-before.json) confirmed verified HTTPS 200/ready and the unchanged installed alpha17 runtime. This test made no APK, installed-host, firewall or signing changes. Any subsequent Wi-Fi deployment requires its own recorded identity and verification.

The purchase target, sustained manual-coin endurance, installed-backup restoration, physical phone/firewall, reboot and public release gates remain open. The mixed run supports proceeding with guarded Wi-Fi testing of the already regression-tested server fixes; it does not certify production capacity. `artifact-hashes.json` records retained review bytes.
