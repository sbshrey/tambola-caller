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
