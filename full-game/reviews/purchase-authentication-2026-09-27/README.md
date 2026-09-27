# Purchase authentication experiment — 27 September 2026

The production draft was **rejected**: combining purchase authentication with rate accounting did not produce a reliable latency improvement. Production source and the local service distribution were restored to the baseline. Four new regression tests remain. The installed PC service and alpha29 APK are unchanged.

## Hypothesis and guardrails

The previous [phase diagnosis](../purchase-phases-2026-09-27/README.md) found four connection acquisitions per primary purchase. The current purchase path separately authenticates and commits its rate-limit attempt, then authenticates again for the purchase transaction. The draft used one fresh authentication with the guest lock, charged the quota, and placed the purchase behind a JDBC savepoint. A recoverable purchase failure rolled back to that savepoint; the outer transaction then committed the attempt before rethrowing the failure. [Exact rejected patch](candidate.patch), [candidate runtime](candidate-runtime.json). This uses the documented [JDBC savepoint/rollback operations](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Connection.html) and [PostgreSQL savepoint behavior](https://www.postgresql.org/docs/16/sql-savepoint.html).

The four new real-PostgreSQL tests establish existing behavior before changing it:

- Twenty insufficient-balance purchases consume the quota while leaving no rooms, participants or purchase receipts; the next attempt is rate limited.
- An injected receipt-storage SQL failure preserves the attempt but rolls back the room, debit and participants. Retrying the same request succeeds once and then replays the same receipt.
- Four concurrent requests cannot spend the same final quota slot twice or charge twice.
- Independent journal revocation rejects purchase before consuming quota, even before primary-store replay.

All four pass on the baseline. The draft also passes all four, the two quick-admission tests, and five journal-authentication tests: **11 focused tests**, no failures or skips. The admission test holds up quick allocation while requiring an unrelated wallet read and friend-table join to finish. [Baseline test result](baseline-tests.json), [baseline transcript](baseline-tests.txt), [candidate test result](candidate-focused-tests.json), [candidate transcript](candidate-focused-tests.txt).

These checks do not cover every failure mode of the draft. In particular, its rate accounting would depend on the combined transaction successfully committing after rollback; connection loss or a crash before that commit was not validated against the current independently committed quota. Because the latency comparison failed to justify the added transaction complexity, the draft was removed without deployment or a new full-suite run.

## Matched unprofiled comparison

Each independent server ran two simultaneous 320-player waves across forty eight-seat tables, with immediate WebSocket subscriptions, a 512 MiB server heap and four server processors. SQL, pool and request profiling were disabled. The ABBA sequence used verified private runtime copies. All client/fixture/lifecycle/profiler source identities match, and only `server.jar` differs between the two 51-JAR manifests. [Complete comparison and source evidence hashes](comparison.json).

| Run | First purchase p95 | Repeat purchase p95 |
| --- | ---: | ---: |
| Baseline 1 | 3,330 ms | 1,477 ms |
| Candidate 1 | 3,370 ms | 1,360 ms |
| Candidate 2 | 3,386 ms | 1,476 ms |
| Baseline 2 | 3,415 ms | 1,299 ms |

Both candidate ranges fall within baseline variation. There is no repeatable speed benefit or one-second-target pass. All eight waves passed full-table allocation, 320 observed subscriptions, exact purchase and leave receipts, refunds and wallet conservation: 2,560 primary purchases and 2,560 exact purchase replays. These are cancelled purchase waves, not completed rounds, mobile/TLS measurements or capacity acceptance. The per-run JSON/transcripts linked from the comparison retain every run.

The result does not support removing fresh authorization or rate protection. Further work should measure client transport queuing and server processing before the current route timer, which remain outside the previous phase accounting, before another transaction rewrite.

## Restoration and cleanup

[The rebuilt local distribution](restored-runtime.json) exactly matches the original baseline runtime `fd51150ed608bbdc7f6326ca057f624bea9e56d759a80eeda60c0e29435d55b7`. The [restoration build](restored-build.txt) succeeds. The final production source diff is empty; only the new tests, plan note and review evidence remain. The four baseline tests above therefore exercise the retained production implementation; the candidate's focused result is not presented as a full-suite pass for either build.

[Independent cleanup](cleanup.json) confirmed all four owned server processes and eight exact fixture databases were gone, with no test schemas or other test-store connections. The test PostgreSQL instance on 55432 was then stopped. The configured installed runtime still matches `f26bc71a48b349ad77fa722a7ebe7938ef1d94981859af1f4ac78aa03e95e372` at source `3c37764c9540cde7a7fd647b9d761ca847edb228`; no installed host files were modified. [Public-entry checks](public-entry.json) verify the still-running Internet Beta endpoint.

The full goal remains open: large-burst purchase latency, physical-phone/mobile-data and frame/audio/touch acceptance, protected production signing and the remaining editable design updates are not closed by this experiment.
