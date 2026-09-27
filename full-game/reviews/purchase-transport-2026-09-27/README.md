# Purchase transport diagnosis — 27 September 2026

The one-second purchase target remains open. Two fresh 320-player captures confirm that the load generator's shared HTTP clients add queueing before requests reach the service. Most measured time still occurs between sending a request and observing response headers. This checkpoint adds optional **test-only** transport observations and a validating analyzer. It changes no shipping client, server route, admission limit, database pool, APK or installed service.

## Measurement

The fixture retains forty HTTP transports, each shared by eight players. Its profiled client mirrors the native client's timeouts, redirect policy and WebSocket settings and records the observed default dispatcher limits: 64 requests overall and five per host. It uses the actual queue callbacks in [OkHttp 5.2.1 EventListener](https://raw.githubusercontent.com/square/okhttp/parent-5.2.1/okhttp/src/commonJvmAndroid/kotlin/okhttp3/EventListener.kt), rather than inferring queue time from an interceptor.

Every successful, single-exchange call is partitioned into dispatcher queueing, other time before request headers, request writing, waiting for response headers, response reading and closing. Connect time is nested in the pre-request phase. In this HTTP/1 fixture, OkHttp emits `responseHeadersStart` after reading headers; this is not a precise first-byte measurement. [CallServerInterceptor source](https://raw.githubusercontent.com/square/okhttp/parent-5.2.1/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/CallServerInterceptor.kt). Waiting for headers includes server processing, scheduling and transport; it does not isolate database or CPU time.

Only fixed numeric/boolean fields and aggregate settings are exported. There are no player/request identifiers, URLs, headers, bodies, credentials, exception messages or stacks. Callbacks perform no file I/O. `succeeded` means OkHttp completed the call; the surrounding purchase fixture independently verifies the application's purchase, receipt and refund results. Failed or repeated exchanges cannot pass the analyzer. [Reproduction instructions](../../server/COIN_LOAD.md#client-transport-phases).

## Verification and results

Two focused tests pass in 1.665 seconds. A real loopback HTTP server forces two calls to queue behind one active request, then separately delays body delivery after the client observes response headers. The tests check phase totals, ignored non-purchase routes, export fields and invalid failed connections. An initial JUnit signature error and an initially nondeterministic body-delay fixture are retained in [initial-test-error.txt](initial-test-error.txt) and [initial-delay-fixture.txt](initial-delay-fixture.txt). The corrected synchronization waits for the actual client callback. [Final tests](tests.txt), [test summary](test-summary.json). This is not a rerun of the complete server suite.

The analyzer independently checks complete primary/replay coverage, single exchanges, finite durations, phase arithmetic, dispatcher settings, summaries, subscriptions, full tables and successful cleanup. Eight tampered inputs were rejected, including missing calls, extra fields, inconsistent totals and failed cleanup. [Analyzer validation](analyzer-validation.json).

An eight-player smoke capture was followed by two profiled servers and an intervening unprofiled server. Each server completed two waves. The 320-player runs retained forty full eight-player tables, immediate WebSocket subscriptions, exact receipt replays, full refunds and wallet conservation. They used the same server and client JARs, a 512 MiB server heap and four server processors, with SQL, pool and server-request profiling disabled. Across the two full profiled runs, all 1,280 primary purchases and 1,280 receipt replays were captured. The smoke adds 16 of each. Source and binary identities are recorded in [identities.json](identities.json); the smoke precedes an extra test-synchronization counter, while both full captures use the final profiler source.

| Run / wave | Client purchase p95 | Queue p95 | Wait for headers p95 | Queued purchases | Queue share of summed API time | Headers-wait share |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Profile 1 / initial | 3,166 ms | 1,490 ms | 1,856 ms | 120/320 | 17.52% | 66.67% |
| Profile 1 / repeat | 1,451 ms | 676 ms | 931 ms | 120/320 | 17.58% | 77.49% |
| Unprofiled / initial | 3,107 ms | — | — | — | — | — |
| Unprofiled / repeat | 1,439 ms | — | — | — | — | — |
| Profile 2 / initial | 3,118 ms | 1,542 ms | 1,961 ms | 120/320 | 17.03% | 66.43% |
| Profile 2 / repeat | 1,260 ms | 553 ms | 811 ms | 120/320 | 17.45% | 76.67% |

[First capture](profile-1/client-transport-summary.json), [second capture](profile-2/client-transport-summary.json), [unprofiled fixture](unprofiled-1/burst-evidence.json), [smoke](smoke/client-transport-summary.json), [compact comparison](comparison.json). Complete validated call records and transcripts accompany each capture.

Queueing accounts for about 17–18% of summed API duration; waiting for headers accounts for 66–77%. The aggregate time outside the OkHttp call is about 10% initially and 0.53% on repetition. That residual includes client work, scheduling and profiling callback overhead; it does not identify an individual cause. These percentages use accumulated concurrent request durations, not elapsed wall time or CPU utilization. Phase percentiles describe separate populations and must not be added or subtracted.

The unprofiled run also misses the target. This sequence is not a matched ABBA comparison and establishes neither a speedup nor a profiling-overhead bound. The older server JFR captures came from different runs and cannot be combined with these percentages as a per-request partition. Cancellation/refund waves do not establish full-game capacity, ongoing claim latency or phone performance.

The next controlled investigation should distinguish transport sharing from service throughput, then measure earlier request/response processing before changing transactions again. Keep the 320-player workload, first-use wallets, receipts, refunds and four-request admission protection unchanged when comparing server candidates. A one-transport-per-player experiment would be a separately labelled diagnostic condition, not a replacement for existing acceptance.

## Cleanup and publication

Independent checks confirmed all four owned server processes and eight exact fixture databases were gone, with no remaining test schemas or other test-store connections. The test PostgreSQL instance on port 55432 was then stopped. [Cleanup](cleanup.json). The first startup omitted the explicit test port and failed to bind; [that failure is retained](database-startup-error.txt). Corrected startup and all fixture runs used 55432. The live database on 55433 was unaffected.

At 13:16 UTC, the public directory and trusted HTTPS game endpoint passed readiness, invitation, restricted-route and unauthorized-purchase checks. [Public verification](public-entry.json). The configured installed runtime remains `f26bc71a48b349ad77fa722a7ebe7938ef1d94981859af1f4ac78aa03e95e372`; the diagnostic runtime remains `fd51150ed608bbdc7f6326ca057f624bea9e56d759a80eeda60c0e29435d55b7`. Reading the configured service directory confirmed the installed identity. No service or bridge restart was performed.

[Alpha31 remains the published beta](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha31-stable-invites). GitHub's asset digest still matches the previously tested APK, SHA-256 `489cef056ac49323aaf9923159bdd01fbcc2f05b53ed4951be9ebf0666b0386a`, 29,142,089 bytes. This checkpoint does not download or rebuild it. Stable friend invitations and the PC host remain available. Physical-phone mobile-data/browser handoff, touch/audio/frame performance, production signing, editable design updates and high-load latency remain open.
