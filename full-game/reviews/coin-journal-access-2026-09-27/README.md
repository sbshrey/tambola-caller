# Fresh journal access and purchase-burst diagnosis

Source `42de58d` combines authenticated journal identity/head and access suppression into one fresh SQL snapshot; follow-up `4bca9bd` preserves a missing primary cursor as explicitly invalid. This closes a reproduced recovery-validation gap and removes one journal transaction per authentication. It has **not established a purchase-latency improvement**. The installed Wi-Fi host and alpha20 APK were not changed during this investigation.

## Reproduced recovery gap

Previously, authentication checked the primary recovery cursor against the journal head, looked up the primary credential, then separately checked journal identity and suppression. An older journal restored between those reads could pass the earlier head check and the later identity check even though its head was now behind the primary cursor. The new code reads the primary cursor, obtains the credential with its existing guest lock policy, and compares that cursor against the same fresh journal snapshot used for deletion/logout suppression. It keeps the recovery-failure guard, immediate suppression, missing-credential outage behavior, and guest-before-room lock ordering. It introduces no cached authorization or protocol/schema changes beyond the existing migration 007 candidate.

The new interleaved-restore regression **fails on a verified private copy of the installed runtime**: the old poll returned without the required SQL exception. Its other four focused tests passed. The same five tests pass on the candidate. The previous local candidate and installed source have byte-identical authentication method bodies; the retained [proof metadata](old-runtime-proof.json), [expected-failure transcript](old-runtime-proof-transcript.txt), and Gradle initialization script document the runtime precedence and test method. Tests used random isolated test schemas; they did not restore or mutate the installed databases.

The broader [68-test run](regression-tests.json) passed before adding that final regression; the subsequent [five-test run](rollback-regression.json) adds one distinct case, for **69 distinct passing tests**. Coverage includes coins/retries/refunds, HTTP, event polling, session renewal/revocation, independent deletion recovery, primary-backup restoration, and actual restricted database roles. The first test command had its `--tests` options attached to the wrong Gradle task and failed before execution; [that command error](test-command-error.txt) is retained alongside the successful retry. These test-store restores do not substitute for the installed-backup restore drill.

## What the latency diagnosis shows

All purchase probes used 320 seeded players, forty full eight-seat tables, 1–6 tickets, normal sales deadlines, and the same clients/server for two waves. Every wave verified exact purchase/leave receipts, a single debit per purchase, full refunds, and ledger conservation. The second wave followed cancellations, not completed rounds. No probe claims call/claim/settlement acceptance.

| Condition | Cold purchase p95 | Repeat purchase p95 |
| --- | ---: | ---: |
| Previous candidate, SQL/JFR profiling, no streams | 2,276.167 ms | 762.333 ms |
| Previous candidate, SQL/JFR profiling, immediate streams | 3,732.401 ms | 1,531.430 ms |
| Previous candidate, unprofiled, immediate streams | 2,317.872 ms | 1,250.443 ms |
| Fresh-journal candidate, unprofiled, immediate streams | 2,702.825 ms | 1,430.378 ms |

These are individual diagnostic runs, not a randomized repeated benchmark. Both unprofiled runs used the same final fixture source, runtime budgets and subscription condition. The candidate measured slower in that pair, so fewer queries must not be described as faster purchases. The original no-stream exploration used an earlier fixture revision; its exact source hash is recorded, and it is not a matched production comparison. Its figures chiefly motivated the immediate-stream condition.

The profiled streaming run recorded 1,000 primary rate-authentication pool-park events overlapping the cold purchase window (163,659.587 ms of summed concurrent thread waits), and 1,103 in the warm window (77,755.466 ms). Event-duration p95 was 382.091/218.362 ms. This identifies contention before matchmaking work reaches a connection; it does not isolate every contributor to request latency. The journal-pool sample had no qualifying events in those windows, which is not proof of zero acquisition overhead.

JFR records only parks of at least 1 ms with both matchmaking and Hikari acquisition in the captured stack. Events are not requests or complete acquisition durations; one request can park more than once. Missing stacks and shorter waits are excluded. Concurrent wait sums are not wall-clock duration. The analyzer verifies later flush markers and retains only fixed labels and numeric fields in `pool-parks.txt`, with no full server log or JFR export. Instrumentation overhead is visible, so profiled results cannot satisfy latency acceptance. [Fixture instructions](../../server/COIN_LOAD.md) explain conditions and reproduction.

## Full-round and host verification

Unprofiled full run `341bfd4e98164690` used source `42de58d` at the real five-second pace, with cold wallets and all diagnostic flags off. It completed forty tables, 320 players and 1,116 tickets, with **356 accepted claims**, **36 shared prizes**, **440 exact receipt replays**, forty delayed acknowledgement retries, four peak concurrent claims and zero closed claim windows. Every player's result/wallet matched independently calculated prize shares. The run settled 111,600 coins, preserved aggregate balance 480,000, and verified all **25,096/25,096** expected call deliveries. Tables completed after 72–83 calls.

| Full-round client measure | p50 | p95 | p99 |
| --- | ---: | ---: | ---: |
| Purchase RTT | 1,790.534 ms | **3,423.316 ms** | 3,591.142 ms |
| Claim RTT | 12.678 ms | 36.662 ms | 59.083 ms |
| Draw event → snapshot | 301 ms | 526 ms | 557 ms |

Gameplay correctness and cleanup passed. The task correctly exited **1** because purchase p95 remains above 1,000 ms. This isolated full run is lower than the previous 3,673 ms sample, whereas the matched cancellation probe was slower; neither establishes a repeatable overall speedup. [Raw results](full-run/evidence.json), [failed performance transcript](full-run/transcript.txt) and GC data are retained. The full run's server JAR is `08bbc213a40674b0f38a074ba6d70aa8568a854def07c392b941398459d232ba`; its 51-JAR runtime is `7b4bc3d9d9fe4822b13b67182bedad11d418db07e4d98ef02e856b53db5593b0`, verified unchanged after play.

The subsequent `4bca9bd` follow-up keeps SQL NULL distinct from an empty journal identity instead of coercing it. All [five focused tests passed again](null-cursor-tests.json), including uninitialized/mismatched/ahead cursors and the restored-journal race. That follow-up was built only after the full round exited, so the full-round runtime above must not be represented as the final binary. A separate three-call probe records normal matchmaking, owned hands and stream delivery on the final build; it deliberately skips claims/settlement and cannot close full capacity or endurance acceptance.

Final probe `857f1d0a5618452f` passed with all 320 clients connected and receiving at least three calls. Purchase p95 remained 3,626.179 ms. Its raw delivery percentiles/sample counters are zero because the short mode skips the full delivery oracle; those zeros are **not measured zero latency**. The final server JAR is `93bf3b68f63da06f1faf8bcfd95e8ffeffb706b0d5a2e18ebd04937d717ad10d`; its complete runtime is `9884bbf0a6017448372605e3c0314c478291a7c16a692882a64c0917329a1b8b`. [Probe evidence](final-probe/evidence.json) keeps the exact manifest. Diagnostic launchers/tests are absent from the final server JAR.

[Independent cleanup](cleanup.json) found all six recorded server PIDs and twelve exact load databases absent, with no random test schemas remaining in the control test database. [Fresh host health](host-health.json) returned verified HTTPS 200/ready, with the installed runtime unchanged. One emulator and no physical ADB device were available. The candidate is committed but **not deployed**; no APK, installed-host migration or firewall rule changed. Current manual-coin endurance, installed-backup restoration, physical Wi-Fi/firewall, reboot behavior, public hosting and production signing/distribution remain open. The next investigation targets redundant work on unchanged stream polls while preserving fresh authorization, membership, presence and cursor checks.

`artifact-hashes.json` covers the retained review bytes. Timing/proof runs, failed outcomes and source/runtime boundaries are retained alongside passing correctness checks.
