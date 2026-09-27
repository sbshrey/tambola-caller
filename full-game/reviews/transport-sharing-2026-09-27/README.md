# Purchase transport sharing — 27 September 2026

Removing the load generator's dispatcher queue does **not** establish a purchase speedup or close the one-second target. The completed profiled comparison shows almost unchanged initial/repeated purchase p95 with either forty shared transports or 320 separate transports. The unprofiled sequence includes a connection failure and variable follow-ups; it is not an all-passing comparison. No shipping client, server, admission limit, database pool, APK or installed service changed.

## Test change and checks

`coinPurchaseBurst` now accepts `TAMBOLA_COIN_BURST_PLAYERS_PER_TRANSPORT=1` as a separately labelled diagnostic condition. The existing default remains eight players per transport, and full `coinGameLoad` acceptance is unchanged. Both conditions use the native client settings, 320 players, forty full eight-seat tables, mixed ticket quantities, immediate subscriptions, exact receipt retries and refunds. They reuse clients/server only between the two waves of an individual run; every run starts a fresh service and two fresh databases.

OkHttp 5.2.1's [dispatcher implementation](https://raw.githubusercontent.com/square/okhttp/parent-5.2.1/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dispatcher.kt) limits ordinary asynchronous requests per host, while excluding WebSocket connections from that limit. All profiled clients retain the observed defaults of 64 total/five per host. A separate transport per player changes connection reuse, scheduling, resource consumption and arrivals at the service as well as queueing. It is not a simulation of independent phones or a pure queue-removal microbenchmark.

The analyzer preserves compatibility with old eight-player captures and validates the explicitly recorded topology. [Three tests pass](analyzer-tests.txt), including eleven rejected modifications: wrong/unsupported/boolean topology, wrong/boolean transport count, missing calls, unexpected fields, inconsistent durations, changed dispatcher limits, failed cleanup and false capacity acceptance. The retained eight-player smoke completes both waves with separate clients and no measured dispatcher queueing. [Reproduction](../../server/COIN_LOAD.md#client-transport-phases).

## Initial comparison

All times below are client purchase p95 in milliseconds. A means eight players per transport; B means one. Instrumented and uninstrumented runs are separate populations.

| Run, in execution order | Clients | Profiling | Initial p95 | Repeat p95 | Result |
| --- | ---: | --- | ---: | ---: | --- |
| profile-a1 | 40 | On | 3,320 | 1,284 | Passed |
| profile-b1 | 320 | On | 3,338 | 1,293 | Passed |
| profile-b2 | 320 | On | 3,322 | 1,358 | Passed |
| profile-a2 | 40 | On | 3,353 | 1,276 | Passed |
| plain-b1 | 320 | Off | 3,352 | 1,078 | Passed |
| plain-a1 | 40 | Off | 2,962 | 1,402 | Passed |
| plain-a2 | 40 | Off | 3,195 | 1,394 | Passed |
| plain-b2 | 320 | Off | — | — | Connection failure during first purchase wave |

In the completed profiled ABBA sequence, each shared-client wave queues 120 of 320 purchases; each separate-client wave queues zero. Initial header-wait p95 increases from 2,095–2,124 ms to 2,887–2,923 ms while total request p95 remains similar. These are different sample percentiles and cannot be subtracted to partition a request. Queue shares of accumulated API duration are 15–20% for the shared clients and zero for separate clients. They are not wall time or CPU utilization. [Complete summaries and identities](comparison.json); each run directory contains the underlying fixture report, transcript and, where profiled, all validated transport records.

The unprofiled sequence cannot support a general improvement claim: its last condition fails, and its one earlier separate-client success is slower initially but faster on repetition. Two observations per condition would still be a small experiment, not a statistical guarantee or physical-network acceptance.

## Retained failure and follow-ups

[plain-b2](plain-b2/burst-evidence.json) reports `ConnectException:null:null` before a complete first wave. Its [transcript](plain-b2/transcript.txt) confirms the failed task, and cleanup succeeds. The original fixture did not capture whether the owned server was alive or the connection-error category at failure. The exact cause is therefore **unresolved**; neither listener backlog exhaustion nor a server crash is established. No partial purchases are counted as a verified wave.

The test fixture now records server liveness on failure and a fixed connection-error category if recognized. It exports no exception message, endpoint, token or request content. [The initial fixture](initial-fixture.kt) and [source hashes](identities.json) distinguish the initial comparison from the follow-ups; the actual server/client binaries are identical throughout.

Two separate-client follow-ups pass all checks:

- `plain-b3`: **profiling on**, p95 3,719 / 1,479 ms. The retained filename says plain, but the launch omitted `-Unprofiled`; evidence and comparison explicitly classify it as profiled. It is not part of the unprofiled comparison.
- `followup-unprofiled`: profiling off, p95 **4,345 / 1,924 ms**. This later run is not a replacement for the earlier failed run.

Neither follow-up reproduces the connection error, so the new failure-category path has no captured reproduction in these runs. Passing retries do not erase the failure or prove it was transient. Across all runs there are **nine successful 320-player/two-wave runs**, one failed 320-player run and the successful eight-player/two-wave smoke. The successful large runs verify 5,760 primary purchases plus their receipt replays, live subscriptions and refunds; all retain full tables and wallet conservation. This is cancellation-based diagnostic coverage, not completed-game capacity.

## Consequence and cleanup

Client queueing alone is not a sufficient explanation or remedy for the target miss. The next investigation should measure the admitted service's serial allocation/commit work and the surrounding request/response path under the original workload. Preserve the four-request protection for ongoing games and friends, and do not substitute the separate-client condition for existing acceptance. These captures do not identify CPU versus database/transport scheduling as the remaining cause.

[Independent cleanup](cleanup.json) confirms all eleven owned server PIDs and all 22 fixture databases are gone, with no remaining test-store connections or load databases. Test PostgreSQL on port 55432 was stopped; the serving database on 55433 remains running. [Identity checks](identities.json) confirm the configured installed runtime and public alpha32 asset digest are unchanged. Fresh [public endpoint checks](public-entry.json) pass at 14:06 UTC. No host or bridge restart was performed.

Alpha32's call-history/invitation improvements remain published. Physical-phone/mobile-data and TalkBack checks, native-rival gameplay, frame performance, production signing, editable-Figma work and high-load acceptance remain open. The overall goal is not complete.
