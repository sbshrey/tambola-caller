# Quick-play admission and friend-table availability

This review tests whether waiting quick-play purchases can stay out of the database connection pool. The starting source is `da0f919`; the installed service is separately identified as `bf03af5`. The immutable starting runtime is `a29430f47f9203b84f1f7b6b0fd840a2426b9eee168a881af40998a58be6dc1a` across 51 JARs.

## Reproduced problem

The new HTTP regression holds the existing PostgreSQL quick-allocation advisory lock, then sends sixteen quick purchases. With the starting code, an unrelated wallet read and friends-table purchase cannot finish within the test's 1,500 ms deadline while that lock remains held. [The expected baseline failure](baseline-regression.xml) records that exact timeout. The second regression, rejection followed by another purchase, passes on the baseline.

The candidate uses an application-local coroutine semaphore allowing four concurrent quick-play service calls. Waiting requests suspend before borrowing a database connection; friends-table purchases bypass this queue. Quick purchases therefore occupy at most half the eight-connection primary pool. PostgreSQL's allocation lock remains in place for cross-process correctness. Authentication, transaction boundaries, prices, wallets, receipts, game rules and connection-pool size are unchanged. See Kotlin's [semaphore contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-semaphore/).

Both [targeted candidate tests](candidate-regression.xml) pass: the wallet still has 1,500 coins, the independent friend purchase pays 200 into a 500-coin table, and releasing the quick lock allows sixteen purchases to form two eight-player tables. Every purchase replays exactly and each buyer has 1,300 coins. A rejected purchase also releases admission for the next player. These controlled timing bounds establish this regression's behavior, not public-network latency or capacity.

## Initial diagnosis

[Profiled baseline run](baseline-profile/burst-evidence.json) `79545e2026c14939` completed two 320-player purchase/refund waves with immediate WebSocket subscriptions. First/repeat purchase p95 was 3,484.009/1,452.797 ms. All receipt, refund, full-table, stream and conservation checks passed; capacity acceptance is explicitly false.

The SQL profile recorded 20,831.984 ms of summed allocator-lock waits across 640 purchases, compared with 3,185.899 ms of summed work after that lock. The JFR pool sample recorded 1,035/1,122 rate-authentication pool parks overlapping the two purchase windows. This supports investigating connection occupancy before more persistence rewrites. JFR events are not requests or complete acquisition durations, concurrent sums are not wall-clock time, and profiling overhead excludes these timings from acceptance. Only filtered numeric/label records are retained here; the raw service diagnostic log is excluded.

## Validation status

The final four-permit build passed **all 158 tests in 29 server classes**, with no failures, errors or skipped tests. [Final validation](final-validation.json), [individual suite results](full-suite.json), [the transcript](full-suite.txt) and [the strengthened availability/failure regressions](final-regression.xml) identify the tested source. All 51 runtime JARs still match the measured candidate after that test-only strengthening. This is an availability fix with a throughput tradeoff, **not a purchase-speed improvement**. The reviewed source `3c37764c9540cde7a7fd647b9d761ca847edb228` is now deployed on the PC.

| Four-permit comparison | First-wave purchase p95 | Repeat-wave purchase p95 |
| --- | ---: | ---: |
| Baseline 1 | 3,080.77 ms | 1,253.06 ms |
| Candidate 1 | 3,477.45 ms | 1,364.42 ms |
| Candidate 2 | 3,420.85 ms | 1,396.56 ms |
| Baseline 2 | 3,138.03 ms | 1,382.46 ms |

Each independent server ran two simultaneous 320-player waves, forty eight-seat tables, immediate WebSocket subscriptions, a 512 MiB heap and four server processors. Only `server.jar` differs between the two 51-JAR runtimes; client, fixture, lifecycle and profiler source hashes match. Profiling was disabled. [The complete comparison](comparison.json) retains identities, timestamps and each raw run. The candidate runtime is `f26bc71a48b349ad77fa722a7ebe7938ef1d94981859af1f4ac78aa03e95e372`.

All eight waves passed exact purchase/refund receipts, full-table allocation, 320 observed subscriptions and wallet conservation, and removed their private databases/processes. Candidate cold p95 was roughly 9–13% higher than baseline; warm ranges nearly overlap. Neither meets the 1,000 ms target. The reason to retain this bounded queue is the independently reproduced prevention of unrelated wallet/friend-table starvation when quick allocation is held up. Four concurrent purchases allow authentication and allocation work to overlap, avoiding the much larger slowdown of the rejected single-request queue. The change does not guarantee responsiveness under every overload condition, across multiple application instances, or when a different request holds the same player's or table's row lock.

These are cancelled purchase waves, not complete games. They exclude registration, calls, claims, Android, TLS and mobile networks, and explicitly do not claim capacity acceptance. The final regression suite strengthens the failure-release check to eight consecutive rejected purchases, so leaking any of the four permits cannot be hidden by unused permits. That test-only change followed the comparison and does not alter its service runtime.

[Independent cleanup](cleanup.json) confirmed all nine recorded server PIDs and eighteen exact fixture databases are absent, with no remaining test schemas or other test-store client connections. The [installed service before deployment](host-before.json) remained healthy at source `bf03af5`, with runtime `5673b3092e1b27d06f3b76f0632c69ac13748dd8f810c4abc7d8ab36594e3aa8`. The measured baseline additionally contains alpha28's invitation parser; it is not byte-identical to that installed runtime. The complete production-source diff from the installed service consists of that parser and the bounded admission change. No database schema, game rule or APK update is required.

## Installed and public checks

The idle-host preflight found no unfinished rooms before the service update. Both fresh database archives and the previous runtime were retained in the protected host upgrade directory. [Deployment collection](deployment.json) verifies the archives' hashes and readability, unchanged primary/journal schemas 007/003, a fresh ready process and restricted-role startup. This does not repeat the earlier copied-backup restore drill. [The installed runtime](host-after.json) matches every measured candidate JAR, and the public tunnel stayed on the same address.

[Installed TLS transactions](installed-coin-smoke.json) passed quick purchases, session rotation, old-token rejection, exact purchase/leave retries, full refunds and QA-profile deletion. The [public HTTPS friends probe](public-friends.json) used the ordinary published directory, created a two-person 3+2-ticket/500-coin table, checked the invitation page, replayed both purchases exactly, refunded both players to 1,500 and deleted its two QA profiles. [Public route checks](public-entry.json) also passed trusted TLS, hidden internal routes and unauthenticated purchase rejection. The test PostgreSQL service on port 55432 was stopped after independent cleanup; the serving database on 55433 remains running.

The existing alpha28 APK remains published and compatible; its anonymous download returned HTTP 200 with the expected 29,141,105 bytes after deployment. There is no new APK release. These PC-origin HTTP checks do not replace a physical Android/mobile-data handoff, a fresh full native round, or the unresolved 320-player latency target.

## Rejected single-request queue

The first draft admitted only one quick purchase at a time. It passed all 158 server tests and the availability regression, but was slower in both matched 320-player comparisons. First/repeat purchase p95 was 4,002/2,116 and 3,963/2,299 ms, compared with the surrounding baseline runs' 3,364/1,137 and 3,110/1,355 ms. That draft was not deployed. Its [exact patch](single-admission/candidate.patch), [full suite](single-admission/full-suite.json), [runtime](single-admission/candidate-runtime.json) and [ABBA comparison](single-admission/comparison.json) are preserved separately from the revised four-permit candidate. All eight purchase/refund waves preserved receipts, full tables and balances. The draft's correctness results do not constitute a full-suite pass for the revised candidate.
