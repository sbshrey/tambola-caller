# Purchase phase diagnosis — 27 September 2026

The current four-request quick-play admission limit protects friend tables and wallet reads, but large quick-purchase bursts still miss the one-second target. Earlier SQL and thread-park aggregates could not partition each request's waiting time. This change adds an opt-in JFR event around the purchase route and synchronous service, plus a fixed-field analyzer. It does not change game rules, allocations, database schemas or admission limits. It is not deployed and does not establish a speed improvement.

## Instrumentation and verification

`tambola.PurchaseTiming` is disabled by default, including during an ordinary JFR recording. The isolated test launcher explicitly enables it. The event records admission, dispatch, service and remaining response-handling durations; pool acquisition and allocation-query durations are nested inside service. It records four connection acquisitions per primary purchase in these runs, across primary and journal stores. The synchronous service scope restores its thread-local context on failure. No request/player/room identifiers, credentials, SQL or exception messages are event fields; the filtered export also excludes JFR thread metadata and stacks. [Reproduction instructions](../../server/COIN_LOAD.md#per-request-purchase-phases). The implementation uses the standard [Java 17 JFR event API](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/Event.html).

Three focused tests verify default-disabled behavior during a recording, exact duration arithmetic and exported field names, failure propagation, thread-context cleanup and cancelled admission. The complete server suite initially finished with 157 passes and four failures, all caused by the missing `TAMBOLA_PG_BIN` test environment variable. After supplying the PostgreSQL tools path, all 23 tests in the four affected classes plus the strengthened timing test class passed. Across the runs, all **161 unique tests** have passing results with no skips; this is not a single uninterrupted all-green run. [Initial transcript](full-suite-initial.txt), [initial cases](full-suite-initial.json), [corrected transcript](corrected-tests.txt), [corrected cases](corrected-tests.json), [combined result](test-summary.json).

An eight-player smoke run verified complete capture and analysis before the larger runs. The analyzer rejects missing primary events, invalid booleans and inconsistent phase totals. Retained evidence, analyzer and filtered timing hashes were independently checked. [Smoke evidence](smoke/burst-evidence.json), [analyzer validation](analyzer-validation.json). Raw service logs remain in the ignored test workspace.

## Two fresh profiled servers

Each server ran two 320-player purchase/refund waves across forty eight-player tables, with immediate WebSocket subscriptions. Both used the same 51-JAR candidate runtime, a 512 MiB service heap and four service processors. Each primary purchase and receipt replay produced one timing event: 1,280 primary purchases and 1,280 replays across the two servers. The eight-player smoke adds eight of each. All purchases, full tables, subscriptions, exact receipts, refunds and wallet conservation checks passed.

| Run / wave | Client request p95 | Measured route p95 | Pool acquisition p95 | Allocation query p95 | Admission share of summed route time |
| --- | ---: | ---: | ---: | ---: | ---: |
| First server / initial | 3,494 ms | 2,216 ms | 27.3 ms | 48.5 ms | 97.22% |
| First server / repeat | 1,315 ms | 813 ms | 0.012 ms | 10.9 ms | 96.45% |
| Second server / initial | 3,729 ms | 2,213 ms | 20.0 ms | 62.3 ms | 97.22% |
| Second server / repeat | 1,558 ms | 764 ms | 0.826 ms | 11.0 ms | 96.27% |

[First run](burst-1/burst-evidence.json), [first phase summary](burst-1/purchase-phases.json), [second run](burst-2/burst-evidence.json), [second phase summary](burst-2/purchase-phases.json), [runtime identities and compact table](runtime-and-summary.json). The phase summaries link their complete validated timing exports and include hashes.

The admission share uses summed concurrent request durations, not elapsed wall time or CPU utilization. Pool and allocation waits are part of service time and must not be added again. Remaining service time includes other database work, decoding and scheduling; it is not a pure CPU measurement. For the slowest 5% of measured routes, mean admission waiting was 2,186–2,205 ms initially and 720–781 ms on repetition.

Measured route time begins after body/token parsing and ends when `respond` returns or the operation fails. It excludes earlier transport and request processing and does not establish socket-flush time. Client and route percentiles are separate populations without retained request identities; subtracting their p95 values would not measure an individual phase. The larger client times leave an upstream/transport measurement gap. SQL/JFR profiling adds overhead, and no matched unprofiled comparison was performed here. These are cancelled loopback purchase waves, not full games, Android/mobile-network tests or capacity acceptance.

The finding narrows the next investigation to purchase throughput and work before the measured route. Simply removing admission would reintroduce the independently reproduced [shared-pool availability failure](../purchase-admission-2026-09-27/README.md). Increasing the pool or changing persistence without another controlled comparison is not justified by these results. The one-second purchase target remains open.

## Cleanup and live service

[Independent cleanup](cleanup.json) confirmed all three owned server processes and six exact fixture databases were gone, with no remaining test schemas or other test-store connections. The test PostgreSQL instance on port 55432 was then stopped. The serving database on port 55433 and public bridge remained running.

The configured installed runtime still matches `f26bc71a48b349ad77fa722a7ebe7938ef1d94981859af1f4ac78aa03e95e372`, from source `3c37764c9540cde7a7fd647b9d761ca847edb228`. The diagnostic candidate is `fd51150ed608bbdc7f6326ca057f624bea9e56d759a80eeda60c0e29435d55b7`. An initial identity check accidentally read the retained original service directory; reading `host.json`'s configured upgrade directory confirmed the active runtime. No host files were modified. [Public-entry verification](public-entry.json) confirms trusted HTTPS, directory resolution, the invitation page, blocked internal routes and unauthorized-purchase rejection.

[Alpha29 remains published](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha29-claim-feedback); there is no new APK in this diagnostic change. Physical-phone cellular play, touch/audio/frame performance, production signing and the remaining editable design updates are still open.
