# Allocation transaction intervals — 27 September 2026

The repeated-purchase bottleneck is now more narrowly measured. In two instrumented 320-player servers, the union of observed allocation intervals covers **78–83%** of the repeated purchase batch's elapsed window. Within those intervals, JDBC executions account for **69–72%** of accumulated duration and commit for **18–23%**. The separate unprofiled run still misses the one-second target: **3,219 ms initially and 1,120 ms on repetition** at purchase p95. This is diagnosis, not a speed improvement; no shipping code, APK or installed service changed.

## Measurement and limits

The test-only JDBC launcher can now emit `COIN_ALLOCATION_WINDOW` when explicitly enabled. An interval begins after the matchmaking advisory-lock query returns and ends after commit returns. It includes fixed-category JDBC execution timings, commit duration and the residual time between them. Monotonic offsets allow a union of overlapping intervals; wall-clock timestamps associate each interval with one complete purchase wave. The original SQL timing format remains intact, and its old analyzer accepts every capture with identical total observed allocation duration.

These are **observed transaction intervals, not exact database lock lifetimes**. PostgreSQL releases a [transaction-level advisory lock](https://www.postgresql.org/docs/16/explicit-locking.html#ADVISORY-LOCKS) when the transaction ends, before the commit response reaches the application. Neighboring measured intervals can therefore overlap. Gaps include boundary, transport and scheduling effects and do not prove the database was idle. JDBC time includes driver, network, database and scheduling costs; the residual includes preparation, decoding and application work. Neither is CPU time. Union coverage uses batch elapsed time; SQL/commit shares use accumulated interval durations, not summed concurrent client latency. Do not combine these percentages as a per-request partition with earlier JFR captures.

A preliminary [CPU-clock probe](CpuClockProbe.java) deliberately performs twenty approximately five-millisecond busy intervals. On this Windows Java 17 runtime, every measured CPU delta is either zero or 15.625 ms. [Output](cpu-clock.txt). This is too coarse for attributing CPU to a short transaction. The Java API specifies nanosecond precision [without guaranteeing nanosecond accuracy](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html). No CPU field or CPU-utilization conclusion was added. This observation does not describe every JVM or operating system.

The analyzer requires every successful allocation in every wave, correct numeric fields and arithmetic, fixed query categories, the expected purchase writes, complete eight-seat tables, subscriptions, exact receipt retries, refunds and cleanup. Only validated timing lines are retained; raw service logs stay in the ignored test workspace. [Reproduction instructions](../../server/COIN_LOAD.md#allocation-transaction-intervals).

## Verification

- **Four PostgreSQL tests pass**, no failures/errors/skips, in **12.085 seconds**. They confirm the timing boundary around a waiter actually blocked in PostgreSQL, SQL and non-SQL elapsed time, rollback/failed-query cleanup, absence of duplicate windows after repeated commits, default compatibility and suppression of query text. [Test summary](test-summary.json), [transcript](tests.txt).
- **Three analyzer tests pass**, including an independent interval-union calculation, exclusion of unrelated private log lines and fifteen rejected corruptions/mislabeled inputs. [Analyzer transcript](analyzer-tests.txt). These do not rerun the entire server suite.
- The eight-player smoke captures all **16** primary allocations across two waves, with exact purchases, streams, receipt replays and refunds. The existing SQL analyzer agrees with the new interval totals.
- The first smoke-launch wrapper accidentally splatted a one-element PowerShell string as task characters. Gradle rejected the missing task before creating any fixture server/database. The wrapper was corrected to a typed string array; [the setup failure](initial-runner-error.txt) is retained.

## Full captures

Order: profile 1 → unprofiled → profile 2. Each starts a fresh service, primary database and journal database, then runs two 320-player waves with forty native transports shared by eight players each. The four-request service admission limit, eight-connection database pool, 512 MiB service heap and four service processors are unchanged. Client, fixture, profiler and full server runtime hashes match across all runs. Client, request-JFR and pool profiling are disabled. The two profiled runs differ from the middle run only by explicitly enabling test SQL/window instrumentation.

| Run / wave | Purchase p95 | Mean observed interval | Interval union / batch time | JDBC share inside intervals | Commit share |
| --- | ---: | ---: | ---: | ---: | ---: |
| Profile 1 / initial | 3,358 ms | 7.220 ms | 63.37% | 65.86% | 22.08% |
| Profile 1 / repeat | 1,367 ms | 3.959 ms | 82.68% | 68.61% | 22.52% |
| Unprofiled / initial | 3,219 ms | — | — | — | — |
| Unprofiled / repeat | 1,120 ms | — | — | — | — |
| Profile 2 / initial | 3,272 ms | 7.068 ms | 62.80% | 65.96% | 19.77% |
| Profile 2 / repeat | 1,448 ms | 3.772 ms | 77.89% | 72.13% | 18.32% |

[First capture](profile-1/allocation-windows.json), [second capture](profile-2/allocation-windows.json), [unprofiled fixture](plain-1/burst-evidence.json), [comparison](comparison.json), [identities](identities.json). Each profiled directory includes its complete filtered intervals and compatible legacy SQL summary. All **1,280** primary allocations in the large profiled runs are captured; the smoke adds 16. All three large runs finish forty full tables in each wave, 320 live subscriptions, exact purchase/leave receipt retries, full refunds and wallet conservation. Across large runs this verifies **1,920 primary purchases**, plus 16 in the smoke.

The ten query categories spread elapsed time across wallet reads, room selection/update, participant insertion, ledger work, event append and receipt insertion. No single query or CPU hotspot is proved by these timings. In the repeated waves the interval union is approximately 1.21–1.27 seconds, while the complete batch takes 1.53–1.55 seconds. The first waves also contain substantial time outside the observed intervals. Profiling adds overhead, and a single interleaved unprofiled run cannot estimate that overhead reliably.

These are cancelled loopback purchase waves, not full games, external TLS/mobile latency, competitive humans or capacity acceptance. The connection error retained in the preceding separate-client experiment was not reproduced here; this does not establish its cause or fix it.

## Next action and live state

The evidence supports testing a change that reduces work or serialization in the allocation/commit path while preserving exact receipts, independent failure handling, full-table packing and active-game availability. Preserve durable commit semantics. Do not remove admission protection or alter the acceptance workload to make the target pass. Earlier CTE batching and authentication drafts already failed to show repeatable gains; repeating them without a new causal hypothesis is not justified. A transaction-grouping experiment would need explicit independent-request rollback, duplicate, revocation, cancellation and concurrency coverage before any deployment.

[Independent cleanup](cleanup.json) confirms all four owned server PIDs, eight fixture databases and temporary test schemas are gone. The test database on port 55432 was stopped, with no remaining test-store connections; the serving database on 55433 stays running. [Public HTTPS checks](public-entry.json) pass at 14:19 UTC. The configured installed runtime and published alpha32 asset digest are unchanged. No game service or public bridge restart was performed.

Alpha32's UI and stable invitations remain available. High-load latency, physical-phone/mobile-data/TalkBack acceptance, native-rival gameplay, frame performance, production signing and editable-Figma updates remain open. The overall goal remains active.
