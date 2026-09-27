# Room-write batching experiment — 27 September 2026

**Decision: reject the production draft.** Combining more SQL work into fewer calls did not demonstrate a reliable improvement in the purchase p95 target. Production `CoinLedger.kt` and `RoomService.kt` were restored to the baseline. The two new correctness regressions are retained. No installed Wi-Fi service or APK was changed.

The tested draft combined the room update, participant retention and event append into one data-modifying CTE, and combined the existing-entry check, balance check and conditional debit into another. It kept the wallet lock in a preceding statement so a waiting debit obtains a fresh committed balance. Together these remove four database round trips from an ordinary new purchase's allocation transaction, without changing locks, table capacity, receipts or pool size. This structural reduction is not itself a measured latency improvement. The [unapplied patch](candidate-not-applied.patch) preserves the exact experiment.

## Correctness

The candidate passed **144 tests in all 26 server test classes**, with no failures, errors or skipped tests. The focused 32-test run also passed before that complete suite.

The new tests verify observable failure behavior:

- A second debit is confirmed blocked in PostgreSQL behind an uncommitted wallet debit. After the first commits, the second rejects an unaffordable purchase using the newly committed balance, leaving exactly one debit.
- A forced event-insert constraint failure rolls back the room update, new participant, debit and receipt together. Removing the fault allows the identical purchase request to succeed and replay exactly.

After reverting the draft, **all 32 focused tests passed again**, including both new regressions. Rebuilding the server distribution produced the exact original baseline runtime hash, verified across all 51 JARs. See [validation and individual test results](validation.json), the full/focused candidate transcripts, and the restored-baseline test/build transcripts. The 144-test candidate suite is distinct from this 32-test retained-baseline validation.

## Four-run comparison

Order: baseline → candidate → candidate → baseline. Each independent server ran two all-at-once 320-player waves with immediate WebSocket subscriptions, forty full eight-player tables, a 512 MiB server heap and four server processors. The client, fixture, runtime lifecycle and profiler source hashes match in every run. SQL/pool diagnostics were disabled. Only `server.jar` differs between the runtime manifests.

| Run | Runtime | First-wave purchase p95 | Repeat-wave purchase p95 |
| --- | --- | ---: | ---: |
| 1 | Baseline | 3,511.67 ms | 1,355.08 ms |
| 2 | Candidate | 3,534.92 ms | 1,399.97 ms |
| 3 | Candidate | 3,062.20 ms | 1,359.92 ms |
| 4 | Baseline | 3,409.11 ms | 1,625.97 ms |

The second candidate run improved on the final baseline, while the first candidate did not improve either p95 over the initial baseline. The candidate medians were lower, but p95 ranges overlap, and every wave misses the 1,000 ms target. Two runs per runtime do not establish a reliable improvement. That evidence does not justify retaining more complicated persistence SQL for this local-host latency objective.

All eight waves passed exact purchase/refund receipt replay, wallet conservation, full-table allocation, 320 observed streams and resource cleanup. These are cancelled synthetic purchase waves, not full rounds or capacity acceptance: registration, calls, claims, Android, TLS and physical network behavior are excluded. The ordinary regression suite separately exercises game behavior, but is not a sustained load test.

The first benchmark invocation stopped before constructing its isolated server because the wrapper omitted `TAMBOLA_TEST_DATABASE_URL`. [The failed setup transcript](comparison-setup-failed.txt) is preserved. After supplying the isolated database configuration, all four measured invocations completed successfully. This setup correction required no application change.

Baseline runtime: `dc731dd1cca93691bfe9974be677059b8ba45d7dfdf5368d7ea4621fc0154653`. Candidate runtime: `4235eb9723b5fae26675995fc36a18ecc1ca231c84fc148e9e677928ac1a564c`. The candidate was tested as a dirty source draft on `65efaee`; validation records its exact source hashes. It is not the retained production source. The [fresh host check](host-health-after.json) confirms the installed Wi-Fi runtime remains `f2b39e5…` and ready.

PostgreSQL reference: [data-modifying CTE semantics](https://www.postgresql.org/docs/16/queries-with.html#QUERIES-WITH-MODIFYING), including their shared snapshot and `RETURNING` behavior, and [row-lock behavior](https://www.postgresql.org/docs/16/explicit-locking.html#LOCKING-ROWS).
