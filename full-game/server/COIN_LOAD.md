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
