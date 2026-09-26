# Coin-game concurrency — 27 September 2026

The installed Wi-Fi runtime completed forty coin tables with **320 synthetic players**, but the purchase burst missed the sub-second p95 target. This is a correctness pass and a **performance failure**, not production capacity acceptance. The installed host and alpha20 APK were not changed by this test.

## Installed candidate

Full run `4e9b62795c1e4733` used committed fixture `c7cc2f3` and a verified private copy of the installed runtime. Its canonical 51-JAR SHA-256 is `b80fe5ae8fb9435d22c5313f9d8b39e53f9b9cc87499f1be6c81af8e7ff92abb`; `server.jar` is `4df54270b7e6785ff984ab4aa7bda254d253f6879da3991cbe155b2295a0392d`. The service came from alpha17 source `9fe335d47b5b76688c0a148f64e074af4630fbdf`. The complete manifest, client and fixture hashes are in [the raw result](installed-full.json).

The local build's domain/protocol libraries differed from the installed libraries, despite identical `server.jar`. The fixture therefore copied and verified the complete installed distribution before launching a separate JVM with fresh primary and journal databases. It bound only to loopback and did not use the live host's stores or endpoint.

| Measure | Result |
| --- | ---: |
| Players / full human tables | 320 / 40 |
| Owned tickets / settled coin pool | 1,116 / 111,600 |
| Accepted claims / shared prizes | 345 / 27 |
| Peak simultaneous claim requests | 5 |
| Closed claim windows | 0 |
| Exact receipt replays checked | 436 |
| Delayed acknowledgement retries | 40 |
| Draw delivery samples | 24,840 / 24,840 |
| Aggregate ending balance | 480,000 coins |
| Purchase RTT p50 / p95 / p99 | 2,345.579 / **3,549.797** / 3,934.744 ms |
| Claim RTT p50 / p95 / p99 | 12.302 / 37.019 / 64.429 ms |
| Draw event → snapshot p50 / p95 / p99 | 287 / 520 / 551 ms |

All actors finished, with 70–82 calls per table. Every owned hand was private and internally disjoint, the prize schedule remained frozen, and active snapshots concealed future draw order and nonce. Independent expected-share arithmetic matched every wallet and final result; the ledger contained exactly one purchase debit and receipt per player. Replaying original purchases after completion did not roll back current results or balances. These assertions precede `completed: true` in the fixture. The task correctly exited **1** because `allP95Under1000Ms` was false. [The transcript](installed-full-transcript.txt) retains this failure.

The service used a 512 MiB maximum heap and four active processors. Native clients shared forty HTTP transports and ran on the same Windows PC. Calls retained the real five-second pace. There was no phone, TLS, Wi-Fi, signup, restricted-role, rendering or packet-loss measurement. Acknowledgements were deliberately delayed in the generator; no network response was actually dropped. Draw latency starts at the persisted event timestamp before transaction commit, rather than at the scheduled deadline. See [the fixture guide](../../server/COIN_LOAD.md) for exact definitions and reproduction.

## Diagnostic controls

The earlier 16-player, three-call probe `6e5cfad60cd84436` passed startup, privacy and receipt checks. It skipped claims and has no full-round or latency pass. It used a local build and an earlier uncommitted fixture; its source/runtime hashes remain in [the raw probe](initial-probe.json). Do not substitute it for the installed-candidate run.

The full run seeded profiles without wallets, exercising lazy initialization. Real registration creates a starter wallet first. A follow-up 320-player, one-call probe `cc6c6beaa2544dd2` opened wallets through the public API before purchases. This also warmed HTTP connections and code, so it is a separate condition. Purchase p95 was still **3,491.470 ms**. Aggregate 25 ms database-wait sampling recorded 616 advisory-lock waits, 45 WAL-write waits and 36 executing observations across 114 polls (multiple active sessions may appear in each poll). These are sampled states, not attributed per-request durations. [The diagnostic result](prepared-wallet-probe.json) supports investigating the matchmaking critical section; it does not close full-round acceptance.

The original full run and small probe both reported cleanup complete, and [an independent check](initial-cleanup.json) found their exact databases and server PIDs absent. Later run cleanup is recorded separately. No test wallets or credentials were copied into these review artifacts.

## Candidate change

Source `9518e08` moves wallet creation ahead of shared lobby selection, reuses the already-locked pre-purchase room record, and avoids pruning events before the retention limit can be reached. Returning to an owned table no longer takes the global matchmaking lock. The guest lock and single transaction still cover debit, membership and receipt; no coin amount, prize rule or storage migration changes.

[Forty tests across five fresh suites](targeted-tests.json) passed, with no failures, errors or skips: CoinLedger, CoinMatch, HTTP, Operations HTTP and RuntimePrivileges. The new regression holds the matchmaking advisory lock while a different transaction successfully re-enters an already-owned table, keeping its original quantity and balance. Existing tests cover simultaneous retries, overspending, rollback, allocation, refunds, settlement and restricted runtime roles. [The successful transcript](targeted-tests-transcript.txt) is retained.

Prepared-wallet diagnostic probe `a663b20785fc4182` completed with purchase p95 **3,289.231 ms**, still above the target. Its 92 wait polls recorded 507 advisory-lock observations. This single probe is not enough to claim a quantified speedup; it confirms the serialized allocation path remains the main sampled queue. See [the candidate probe](candidate-probe.json). The installed server has not been upgraded to this source.

Full candidate run `848094449c5b41fe` then repeated the original cold-wallet condition with diagnostics off. It completed all forty tables/320 players, 1,116 tickets, **361 accepted claims**, **42 shared prizes**, 445 exact receipt replays and all **24,808** expected call deliveries. All 320 final results and wallets matched; 111,600 coins were settled and aggregate balances remained 480,000. No claim window closed before submission. Tables finished after 71–83 calls. [The raw candidate result](candidate-full.json) records runtime SHA-256 `f2a528991964b8ae3fc3645009edb9b8b7a45eedf5c4cc91595864409f5f14f5` and server JAR `48fee1c20f684f78284f348ae506dbc709e966befcb0be55ba65e866d487e08f`.

| Candidate full-run measure | p50 | p95 | p99 |
| --- | ---: | ---: | ---: |
| Purchase RTT | 1,850.490 ms | **3,664.863 ms** | 3,852.878 ms |
| Claim RTT | 11.922 ms | 32.895 ms | 59.368 ms |
| Draw event → snapshot | 294 ms | 523 ms | 552 ms |

The task exited **1** after correctness and cleanup passed because purchase latency still failed. The candidate does **not** demonstrate an overall purchase speedup; the cold full-run p95 was slightly higher than the installed run. Random rounds, fresh JVMs and single trials do not establish a controlled regression estimate either. The established behavior change is that owned-table re-entry no longer waits for unrelated lobby allocation, covered by the explicit lock regression. Further matchmaking work and another full capacity acceptance remain necessary.

[Final independent cleanup](final-cleanup.json) found all five recorded server PIDs and all ten exact test databases absent. The fresh [host health check](host-health.json) returned verified HTTPS 200/ready with the original 51-JAR runtime unchanged. One emulator and no physical ADB device were available. The alpha20 APK remains the current Wi-Fi build; this server candidate has not been deployed. Physical Wi-Fi/firewall, current-flow endurance, installed-backup restore, reboot behavior, production signing and public distribution remain separate open gates. `artifact-hashes.json` records the bytes retained in this review directory.
