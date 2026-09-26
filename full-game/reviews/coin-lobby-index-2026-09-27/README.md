# Open coin-lobby lookup — 27 September 2026

This work follows the [320-player capacity failure](../coin-capacity-2026-09-27/README.md). It reduces work in serialized lobby selection while preserving eight-seat tables, atomic purchases and exact receipts. It does not change ticket prices, prizes, protocol 4, the alpha20 APK or the installed Wi-Fi host.

## Diagnosis and design

A test-only JDBC launcher profiles fixed query categories after the matchmaking advisory lock is acquired. It never records parameters, query text or identities. The private ordinary server log is excluded from these artifacts; only validated timing records are retained. Runs used fresh isolated primary/journal databases, a 512 MiB server heap and four active processors. All these diagnostic probes skip claims and are **not** full-round or latency acceptance.

| Prepared-wallet diagnostic | Players | Summed lobby-selection execution | Summed locked-transaction duration |
| --- | ---: | ---: | ---: |
| Prior candidate, schema 006 | 320 | 533.045 ms | 2,189.328 ms |
| JSON-expression index experiment | 320 | 212.469 ms | 1,902.047 ms |
| Generated sales fields and index | 320 | 143.219 ms | 1,755.517 ms |

These are sampled totals from separate instrumented runs, not per-player response times or a controlled throughput-speedup estimate. The generated-field probe still recorded purchase p95 of **2,883.710 ms**. JDBC proxy and logging overhead are included. Time outside execution includes preparation, decoding and application work, not just CPU time. Commit duration remained a material part of the serialized work.

The first expression index accelerated lookup but referenced the frequently changing room payload. PostgreSQL's [HOT update conditions](https://www.postgresql.org/docs/16/storage-hot.html) and [expression-index maintenance behavior](https://www.postgresql.org/docs/16/indexes-expressional.html) prompted a narrower design: index small [stored generated columns](https://www.postgresql.org/docs/16/ddl-generated-columns.html) instead. A controlled database regression confirms two payload-only updates are HOT with the final design; adding the expression-index control makes the next two non-HOT. Aggregate probe counters also include worker housekeeping, so they are not a per-call comparison.

Migration `007_open_coin_lobbies.sql` computes sales-close time and human-seat count only for matchable lobbies; other phases get nulls. The database keeps these fields synchronized during inserts, changes and restoration. A partial index covers only lobbies below eight seats, ordered by sales-close time and ID. The application lookup uses those fields and retains expiry, deadline, capacity and row-lock checks. Captured query plans verify index use. Earlier migration resources remain unchanged.

## Retained experiments

- `baseline-profile`: run `6fef913ceb89414a`, prior candidate runtime, 320 players and one call.
- `expression-profile`: run `02d22d90cc834f82`, uncommitted expression-index experiment, 320 players and one call. Its experimental migration is retained as `expression-index-experiment.sql.txt`; it was never installed on the Wi-Fi host.
- `expression-short`: run `e393584012fc4e8e`, 80 players and three calls, used to inspect update counters. It is not a lower-capacity acceptance substitute.
- `generated-profile`: run `53a6104560074861`, final generated-column design, 320 players and three calls.

Each folder retains exact runtime manifests, available source hashes, filtered timing records, analyzer identity, aggregate summaries, Gradle outcome and the query plan when collected. Wallets were prepared through the public API before these purchases; this also warms transport and code. All probes report cleanup complete. The diagnostic launcher lives only in test classes and is not packaged into `server.jar`. Diagnostic flags cannot satisfy the fixture's full latency-acceptance flag.

## Final candidate checks

Source `0e6ed19` contains the generated-field migration and indexed lookup. [Fifty-one tests in eight fresh suites](regression-tests.json) passed with no failures, errors or skips. They cover the four new storage/migration checks, coin purchases and ledger behavior, HTTP and operations endpoints, event polling, actual restricted runtime roles, and a real primary-backup restore with deletion-journal replay. The coin-specific restore test verifies that the restored sales metadata remains correct and tickets can still be purchased once, with the expected wallet debit. These are isolated test-store restores, not the separate installed-host backup drill.

The final source keeps diagnostic code out of `server.jar`; it lives under the test source set. The SQL analyzer retains only whitelisted timing labels. Earlier migrations were not edited. Promotion of this server would require migration 007; the installed host is still on schema 006.

Unprofiled full run `ce7eb22d72504229` then used cold wallets and the real five-second pace with all diagnostic flags off. It completed forty tables/320 synthetic players, 1,116 tickets, **367 accepted claims**, **45 shared prizes** and all **25,152** expected call deliveries. All 320 final wallets/results matched independently calculated shares; 111,600 coins were settled and aggregate balances remained 480,000. There were 446 exact receipt replays, 40 delayed acknowledgement retries, four peak concurrent claims and zero closed claim windows. Tables finished after 71–83 calls. [Raw evidence](full-run/evidence.json) and [the failed performance-task transcript](full-run/transcript.txt) are retained.

| Unprofiled full-round measure | p50 | p95 | p99 |
| --- | ---: | ---: | ---: |
| Purchase RTT | 2,222.242 ms | **3,673.253 ms** | 3,820.470 ms |
| Claim RTT | 12.670 ms | 37.748 ms | 52.381 ms |
| Draw event → snapshot | 297 ms | 524 ms | 555 ms |

The service JAR SHA-256 is `18409e3b395deaa7c624e0420c51abf1366d72c191bd77443fc5407b56acbc19`; the complete 51-JAR runtime is `ed71327c575420bc6af79daec573604257948d0fb1ba90debe18cf4c152393eb`. Runtime identity was verified unchanged after play. Correctness and cleanup passed, but the task correctly exited **1** because purchase p95 remained above 1,000 ms. The narrower lookup improvement has **not** produced a measured overall purchase-delay improvement. Connection acquisition, work before the matchmaking lock and warm repeat-purchase behavior need further measurement before choosing the next change. Grouping or batching must preserve full tables and independent durable receipts.

[Independent cleanup](cleanup.json) found all five recorded server processes and all ten exact test databases absent. [Fresh host verification](host-health.json) returned HTTPS 200/ready with the original installed runtime unchanged. One emulator and no physical ADB device were available. This server candidate is committed but not deployed; no APK or installed-host migration changed. Physical Wi-Fi/firewall, current manual-coin endurance, the installed-backup restore drill, reboot behavior and public release signing/distribution remain open. The isolated restore tests above do not close those broader gates.

Reproduction and measurement boundaries are in [the fixture guide](../../server/COIN_LOAD.md). `artifact-hashes.json` covers the retained review bytes; diagnostic probes and failed capacity outcomes are retained alongside passing correctness results.
