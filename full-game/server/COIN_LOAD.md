# Coin-game capacity acceptance

`coinGameLoad` exercises the public coin matchmaker, native HTTP/WebSocket clients and selected manual claims against an isolated, journal-enabled server process. It never targets the installed host's database or endpoint. It creates two fresh databases on the explicitly configured local **test** PostgreSQL instance and removes them and its owned server process in `finally`.

Run from `full-game` with the pinned Java 17 runtime. Set `TAMBOLA_TEST_DATABASE_URL` to `jdbc:postgresql://127.0.0.1:<test-port>/tambola_test`, plus `TAMBOLA_TEST_DATABASE_USER` and `TAMBOLA_TEST_DATABASE_PASSWORD`. The test role needs database-creation permission. Keep its password out of transcripts and clear the environment variable afterwards.

```powershell
$env:TAMBOLA_COIN_LOAD_PLAYERS='320'
$env:TAMBOLA_COIN_LOAD_PROBE_CALLS='0'
.\gradlew.bat :server:coinGameLoad '-PserverOnly=true' --console=plain
```

The default is 80 players. Valid counts are multiples of eight from 8 through 320. A full capacity run uses 320 players across forty eight-seat tables. Each player buys 1–6 tickets, with the normal 12-second sales countdown and five-second calls. An optional `TAMBOLA_COIN_LOAD_PROBE_CALLS` value from 1–5 checks startup and delivery only; it skips claims and cannot pass full settlement or latency acceptance.

The default deliberately includes lazy wallet initialization in the purchase burst. `TAMBOLA_COIN_LOAD_PREPARE_WALLETS=true` first calls the wallet API for every seeded profile, matching the wallet state normally established by registration. These preparation calls also warm HTTP connections and service code; report this as a distinct condition, never as a server optimization. `TAMBOLA_COIN_LOAD_DIAGNOSTICS=true` samples aggregate PostgreSQL active wait categories during purchases every 25 ms. It records no SQL text or identifiers, but adds diagnostic overhead, so keep it off for final timing acceptance.

`TAMBOLA_COIN_LOAD_SQL_PROFILE=true` uses a **test-classpath-only** launcher that wraps JDBC calls after acquiring the matchmaking advisory lock. It records fixed query categories, lock wait, held-transaction duration and commit duration, with no parameters, query text or player data. It also captures the allocation query plan and aggregate room update counters. The current diagnostic lookup supports installed schema 006 and generated-field schema 007. It never changes the installed server. Both diagnostic modes prevent a full latency-acceptance pass even if their timings fall below the target.

To extract only validated timing records and an aggregate report from the private service log:

```powershell
python tools/analyze-coin-sql-profile.py .test-workspace/coin-load-<run-id>
```

The resulting `sql-timings.txt` excludes ordinary service diagnostics. `sql-profile-summary.json` includes the analyzer hash. Time outside JDBC execution includes statement preparation, result decoding and application work; it is not a pure CPU measurement. Keep diagnostic runs separate from final uninstrumented measurements.

## Purchases during active rounds

Set `TAMBOLA_COIN_LOAD_FIRST_COHORT=64` with 320 players for a full mixed-traffic run. The first 64 players start eight tables. Their first valid selected claim releases 128 joining players; another 128 start at that table's announced next-draw deadline. The triggering claimant waits until at least eight primary purchases have been issued and at least one remains pending. All forty tables then finish normally with the same privacy, receipt, prize and wallet checks as the default run.

The first cohort must be a positive multiple of eight, smaller than the total, with the remaining players divisible by sixteen. This mode requires a full run (`TAMBOLA_COIN_LOAD_PROBE_CALLS=0`); zero or an unset first cohort preserves all-at-once purchasing. Mixed runs have a fourteen-minute inner deadline and a sixteen-minute task timeout.

Evidence reports each cohort's successful primary purchase spans, issued requests and peak pending client requests. Existing-player claim samples start within either joining cohort's earliest-start/latest-end window. Existing-player delivery samples have persisted draw timestamps within those windows. The gap between cohorts is excluded, as are duplicate/replayed purchase requests from the window bounds. Pending client requests do not prove uninterrupted server CPU or database work. Missing overlap fails mixed latency acceptance; small overlapping-claim samples are smoke measurements, not a stable population p95. Mixed acceptance additionally requires both overlapping claim and delivery p95 below 1,000 ms, without relaxing the ordinary full-run purchase, claim or delivery targets.

## Repeated purchase diagnostic

`coinPurchaseBurst` isolates cold versus repeat purchases using the same server, seeded players and HTTP clients. It buys mixed 1–6-ticket hands concurrently, checks full eight-player tables, cancels before the normal sales deadline, and verifies exact purchase/leave retries and refunds. Two waves are the default; `TAMBOLA_COIN_BURST_WAVES` allows 1–4. `TAMBOLA_COIN_BURST_STREAMS=true` subscribes to live updates immediately after each purchase, while other purchases are still pending, and requires every subscription to receive a snapshot before cancellation. Streams stop before refunds. The fixture always records `capacityAcceptance: false`: cancellation is not completed-round rematching or endurance, and this diagnostic has no call/claim/settlement acceptance.

```powershell
$env:TAMBOLA_COIN_LOAD_PLAYERS='320'
$env:TAMBOLA_COIN_BURST_STREAMS='true'
.\gradlew.bat :server:coinPurchaseBurst '-PserverOnly=true' --console=plain
```

For pool-wait diagnosis only, set both `TAMBOLA_COIN_LOAD_SQL_PROFILE=true` and `TAMBOLA_COIN_LOAD_POOL_PROFILE=true`. The test launcher streams JFR `ThreadPark` events with a 1 ms threshold and emits only parks whose recorded stack includes matchmaking and Hikari connection acquisition. It writes fixed category labels, epoch times and durations; it does not export a JFR recording, stack text, query parameters or identities. Run `python tools/analyze-coin-pool-profile.py .test-workspace/coin-load-<run-id>` to extract validated events into `pool-parks.txt` and summarize overlaps with the purchase windows in `pool-profile-summary.json`.

These events are **not** complete acquisition timings or request counts: one request can park repeatedly, short parks and missing stack frames are excluded, and concurrent thread-wait sums exceed wall time. The analyzer requires a recording flush after the last purchase window, but a flush does not prove every wait was sampled. Instrumentation adds overhead; compare final timings with both profiling flags disabled. `burst-evidence.json` records the condition, source/runtime hashes, per-wave timing, exact-retry/refund checks and cleanup. Its two-minute inner deadline permits owned-resource cleanup before the three-minute task timeout.

By default the task uses the local `installDist` runtime. To test an existing candidate, set `TAMBOLA_COIN_LOAD_RUNTIME_LIB` to its JAR directory and `TAMBOLA_COIN_LOAD_RUNTIME_SHA256` to its previously verified canonical runtime hash. The fixture hashes the source, copies only regular JARs into its new run directory, and verifies the complete copied manifest before launch. It verifies that manifest again after play. It does not overwrite either runtime. The manifest algorithm matches `tools/service-runtime.mjs`.

### Per-request purchase phases

For complete request timing in the isolated burst fixture, set both `TAMBOLA_COIN_LOAD_SQL_PROFILE=true` and `TAMBOLA_COIN_LOAD_REQUEST_PROFILE=true`. The `tambola.PurchaseTiming` JFR event is disabled by default, including during an ordinary recording. The test launcher explicitly enables it and exports only booleans, counts, start timestamps and durations. It includes no request, player or room identifiers, credentials, SQL, exception messages or stack text.

Each event partitions route time into admission waiting, dispatch to the synchronous service, service work and the remaining response handling. Pool acquisition and allocation-lock query times are nested inside service time; adding them to service time would double-count them. Pool acquisition includes both primary and journal connections. The route measurement begins after body/token parsing and ends when `respond` returns or the operation fails. It does not measure socket flush, gateway/TLS transport or mobile latency.

```powershell
$env:TAMBOLA_COIN_LOAD_SQL_PROFILE='true'
$env:TAMBOLA_COIN_LOAD_REQUEST_PROFILE='true'
.\gradlew.bat :server:coinPurchaseBurst '-PserverOnly=true' --console=plain
python tools/analyze-purchase-phases.py .test-workspace/coin-load-<run-id>/burst-evidence.json .test-workspace/coin-load-<run-id>/service-diagnostic.log .test-workspace/coin-load-<run-id>/purchase-phases.json
```

The analyzer requires a complete successful event for every primary purchase in each wave, verifies phase arithmetic and emits an aggregate JSON plus a validated `.timings.txt` file. It records input, analyzer and timing hashes. Receipt retries outside purchase windows remain in the filtered evidence but are excluded from wave statistics. Keep raw service logs private. Profiling adds overhead; these results diagnose waits and cannot establish latency or capacity acceptance.

### Allocation transaction intervals

Set `TAMBOLA_COIN_LOAD_ALLOCATION_WINDOWS=true` together with SQL profiling for fixed-label elapsed intervals in the isolated purchase-burst fixture. Keep client, pool and request profiling disabled when isolating this diagnostic, and retain eight players per transport. The analyzer requires complete successful purchases, tables, subscriptions, receipt retries and refunds in every wave.

```powershell
$env:TAMBOLA_COIN_LOAD_PLAYERS='320'
$env:TAMBOLA_COIN_BURST_STREAMS='true'
$env:TAMBOLA_COIN_BURST_PLAYERS_PER_TRANSPORT='8'
$env:TAMBOLA_COIN_LOAD_SQL_PROFILE='true'
$env:TAMBOLA_COIN_LOAD_ALLOCATION_WINDOWS='true'
$env:TAMBOLA_COIN_LOAD_CLIENT_PROFILE='false'
$env:TAMBOLA_COIN_LOAD_POOL_PROFILE='false'
$env:TAMBOLA_COIN_LOAD_REQUEST_PROFILE='false'
.\gradlew.bat :server:coinPurchaseBurst '-PserverOnly=true' --console=plain
python tools/analyze-allocation-windows.py .test-workspace/coin-load-<run-id>/burst-evidence.json .test-workspace/coin-load-<run-id>/service-diagnostic.log .test-workspace/coin-load-<run-id>/allocation-windows.json
```

Each interval begins after the advisory-lock query returns and ends after commit returns. Monotonic offsets measure its duration, overlap and union; coarse wall-clock timestamps assign it to the fixture's purchase wave. The interval is a proxy for lock occupancy: the database releases its transaction lock before the commit response reaches the application, so neighboring observed intervals can overlap. Gaps include boundary/round-trip and scheduling effects and do not prove the database was idle. The original `COIN_SQL_TIMING` output remains available to its existing analyzer.

The report partitions accumulated interval duration into JDBC executions, commit and remaining time, and compares the interval union with the client's complete purchase window. JDBC time includes network, database and scheduling; the residual includes statement preparation, decoding and application work. Neither is CPU time. On this Windows JVM, a short probe observed CPU-clock steps of 15.625 ms, too coarse for per-transaction CPU attribution. No CPU field is exported. Filtered output contains only timestamps, durations, counts and fixed query categories; keep the raw service log private. Rollbacks and failed commits produce no successful interval. This is instrumented diagnosis and cannot pass latency/capacity acceptance.

The subsequent [bounded transaction-grouping experiment](../reviews/purchase-batching-2026-09-27/README.md) tested amortizing commits across at most four purchases while retaining per-entry savepoint rollback. It passed scoped correctness checks but was slower in an unprofiled ABBA comparison; the draft was removed and retained only as a review patch. Its grouped transaction boundaries are incompatible with the existing per-request/one-allocation timing assumptions, so those profilers were disabled for that experiment. Do not deploy the patch or interpret its correctness passes as latency acceptance.

### Client transport phases

Set `TAMBOLA_COIN_LOAD_CLIENT_PROFILE=true` for test-only OkHttp transport observations in `coinPurchaseBurst`. The profiled client mirrors the shipping client's timeout, redirect and WebSocket settings; the default unprofiled fixture still uses the native client directly. Keep SQL, pool and server-request profiling disabled when isolating this diagnostic. The fixture retains one transport per eight players and records its actual dispatcher limits (currently 64 requests overall, five per host).

```powershell
$env:TAMBOLA_COIN_LOAD_CLIENT_PROFILE='true'
$env:TAMBOLA_COIN_BURST_STREAMS='true'
$env:TAMBOLA_COIN_LOAD_SQL_PROFILE='false'
$env:TAMBOLA_COIN_LOAD_POOL_PROFILE='false'
$env:TAMBOLA_COIN_LOAD_REQUEST_PROFILE='false'
.\gradlew.bat :server:coinPurchaseBurst '-PserverOnly=true' --console=plain
python tools/analyze-client-transport.py .test-workspace/coin-load-<run-id>/burst-evidence.json .test-workspace/coin-load-<run-id>/client-transport.json .test-workspace/coin-load-<run-id>/client-transport-summary.json
```

The installed OkHttp 5.2.1 provides exact dispatcher queue start/end events. A completed single exchange is partitioned into queueing, remaining time before request headers, writing the request, waiting for response headers, reading the response and closing. Connect duration is nested in the pre-request phase and must not be added again. The header callback does not establish precise time to first byte. Header waiting includes server work and transport, not SQL or CPU alone.

The capture retains no identifiers, headers, URLs, request/response bodies, credentials, exception messages or stacks. File writing occurs after the workload. `succeeded` indicates call completion; application correctness is checked separately by the purchase fixture. The analyzer requires all primary purchases and receipt replays, valid single exchanges, complete phase arithmetic, unchanged dispatcher settings, full tables, subscriptions and successful refunds/cleanup. It rejects failed calls and incomplete captures.

For a separately labelled transport-sharing experiment, `coinPurchaseBurst` accepts `TAMBOLA_COIN_BURST_PLAYERS_PER_TRANSPORT=1`. The default is **8**; other values fail before the isolated service starts. One creates a separate native HTTP client for every player, while retaining its normal dispatcher settings, the same player count, purchases, streams, receipts and refund checks. Evidence records both players per transport and total transports. This also changes connection reuse, thread scheduling and how many requests reach the server together; it is not a pure measurement of queue removal or a server optimization. Compare fresh servers in an interleaved order with identical runtime identities, and keep profiled and unprofiled timings separate. The full `coinGameLoad` acceptance fixture is unchanged and still shares eight players per transport. Neither burst condition can pass capacity acceptance.

Failed bursts also record whether the owned server process was alive when the error reached the fixture. A `ConnectException` receives only a fixed category when recognized (for example `refused` or `timeout`), otherwise `unclassified`; exception text is never exported. This observation cannot establish the underlying TCP failure or what happened before the exception reached the fixture. Preserve failed runs alongside later passes.

Time outside OkHttp is the difference between **sums** of API and call durations, including client work, scheduling and callback overhead. It is not a difference of percentiles. Shares of accumulated concurrent duration are neither wall time nor CPU utilization. Do not combine separate server JFR runs with these captures as a per-request partition. This profiling cannot establish a latency improvement or capacity acceptance; repeat final timing with profiling disabled. [Validated captures and limits](../reviews/purchase-transport-2026-09-27/README.md).

## Coverage and limits

- Profiles are seeded directly into the isolated database. Wallet opening, ticket purchases, claims, receipts and wallet reads use public APIs. Registration, abuse limits and restricted database-role grants are separate tests.
- Clients share one HTTP transport per eight players. Their saved pending purchase is serialized and decoded in memory using the app's client model. This is neither independent phone networking nor Android disk/process recovery evidence.
- Purchases include concurrent exact duplicates and deliberately delayed application of a committed acknowledgement followed by replay. Some accepted claims are replayed too. This does not simulate packet loss or a severed network connection.
- Every actor receives only its own disjoint ticket hand and marks revealed numbers. Future draw order and nonce must remain concealed during play. Prize schedules must stay frozen. All tables must fill with eight humans; this run does not test computer seats.
- Claims run concurrently across actors, with a deliberate 200 ms reaction delay for some players. Results must include simultaneous claims and shared prizes. Closing a claim window before a selected request can complete fails this acceptance.
- Final checks independently calculate per-ticket shares, compare every wallet and result, verify one debit and purchase receipt per player, and require total ledger conservation. Replaying the original purchase after results must not roll back the current room or wallet.

The standalone service has a 512 MiB maximum heap and four active processors; the generator has a 1 GiB heap and eight active processors. The inner ten-minute watchdog permits cleanup before Gradle's twelve-minute timeout. Do not terminate an active run merely because the command tool yields a session ID.

## Evidence

Each fresh `.test-workspace/coin-load-<id>` directory contains `evidence.json`, server GC logs and, when selected, a private runtime copy. The JSON retains safe source/client/runtime hashes, aggregate progress, cleanup status, receipt checks, conservation checks and latency percentiles. It does not contain credentials, individual tickets or wallet identifiers.

Purchase and successful claim timings are client round trips. Draw delivery measures the same-host wall-clock interval from the persisted `drawn` event timestamp (captured before transaction commit) to each client's first snapshot containing that call. It includes transaction commit and polling, but does not measure lateness against the scheduled deadline, TLS, physical Wi-Fi, mobile rendering or touch response. Every actor/call sample must be present exactly once.

A full run exits successfully only when correctness and cleanup pass and the p95 of draw delivery, purchases and claims are each below 1,000 ms. `completed: true` alone is insufficient; inspect `allP95Under1000Ms` and the process exit status. Retain failures alongside subsequent successful runs and independently confirm that the recorded owned process and databases are gone. Do not infer current coin capacity from the older private-room fixtures.
