# Native room client

`client` is the JVM networking/state boundary shared by the Android app and real-network server tests. It depends on the public `protocol`, not server persistence. Android renders a `TableRound` containing visible cards and public results; it never fabricates a domain `Round` or receives an unrevealed draw order.

## State and transport

- Bearer sessions travel in request headers only. Endpoints are build configuration; redirects are disabled. Release configuration accepts HTTPS origins only. The debug default is `http://127.0.0.1:8080` for a dedicated emulator with `adb reverse`.
- Requests time out; response bodies are bounded to 512 KiB while reading. WebSocket frames are checked against the same limit **after receipt and before JSON decoding**. The OkHttp engine does not support Ktor's `maxFrameSize` setting; this is not an engine-level allocation limit. The configured service is trusted, and adversarial transport/memory testing remains a production gate.
- WebSocket output is a conflated stream of full personalized snapshots. Server revisions prevent older command receipts from rolling back a newer table. Reconnects use the last stored revision, apply a full snapshot, and acknowledge server revisions. Initial/catch-up snapshots do not speak old numbers.
- A create/join/command/logout intent is durably saved before sending. A request with an uncertain outcome remains pending; retry uses the original UUID, expected revision, and action. Other mutations wait for resolution. A stale-revision rejection refreshes the table and asks the player to review it before issuing a new action.
- Online marking is a local visual aid. Only owned, called numbers can be marked. Awards and scores come from the authoritative service. Completed snapshots check the revealed permutation and nonce against the original SHA-256 commitment; this establishes consistency with the commitment, not independent proof that the server chose a fair shuffle.
- Snapshot validation rejects other players' ticket data, premature draw disclosure, invalid calls/rosters/award references/scores, and incompatible protocol versions. An immutable round roster and awarded-ticket ownership labels keep results readable after a member leaves. Unawarded opposing ticket IDs and all opposing card numbers remain private.

## Android persistence and lifecycle

The Android `OnlineStore` encrypts the session, cached table, pending action, manual marks, and up to 50 completed personalized snapshots together using Android Keystore AES-256/GCM and an atomic file in `noBackupFilesDir`. The key is non-exportable through Android Keystore; hardware protection depends on the device and is not assumed. Application backups and device transfer are disabled. No cleartext fallback or API key is embedded.

Persistence completes before state becomes visible. Failed decryption leaves the file intact and offers an explicit local-data reset; reset is not server-side account deletion. Signing out revokes the bearer session before removing local online data. The remaining server-side deletion workflow is a production gate.

The encrypted snapshot also keeps a bounded badge ledger: at most five distinct completed-round IDs and the first qualifying house-round ID. Repeated snapshots cannot advance the same milestone twice, cancelled rounds do not count, and another player's house does not qualify. This is a milestone record rather than a lifetime-statistics counter. It persists when the 50-result cache rolls over. Older snapshots without the field recover progress from their cached completed history; signing out/resetting removes this local ledger along with the profile.

The stream runs while the online page is foregrounded. Leaving it stops local audio and networking; the server can continue calling and can transfer host responsibility after absence. Returning restores the same profile and tickets. Local offline rounds use their existing Room database and remain independent. Active tables keep the display awake.

## Local verification

Use only the isolated `tambola_test` PostgreSQL database described in [the service README](../server/README.md). Run the service on loopback port 8080 with its documented environment variables. Build and install both debug APKs on the dedicated test emulator, then:

```powershell
adb -s emulator-5582 reverse tcp:8080 tcp:8080
node tools/android-smoke.mjs --online --label android-online-smoke
```

The runner requires Node 22+, checks the dedicated `tambola_full_game_*` AVD and loopback service, temporarily disables system transition animations, and restores and verifies their original values even after a test failure. It writes the JUnit output to ignored `.test-workspace/`. Use `--class io.github.sbshrey.tambola.game.OnlineGameTest` to run just the two online journeys; without it, all offline, online and storage journeys run. It does not install APKs or start the database/service.

The online instrumentation test is opt-in and refuses a non-emulator or a non-default endpoint. Its second player uses the same production client on Android, without a second Compose UI. Two separate phone UIs, real network interruption, process death with a pending request, API 26/35/36, physical devices, TLS, load, and hosted operations need additional acceptance checks. The [alpha04 report](../ALPHA04_VALIDATION.md) records the completed 13-test run and separate 200% tutorial/badge checks; [alpha03](../ALPHA03_VALIDATION.md) retains the earlier online large-text evidence.

`NativeClientTest` connects the production client to a real ephemeral Netty listener and isolated PostgreSQL schema; it is not an in-memory HTTP-engine simulation. `client:test` separately covers bounded HTTP decoding, credential placement, durable request identity, replay/catch-up, marking, snapshots, and commitment checks. See the execution ledger for actually completed runs.

To configure a future HTTPS service at build time, pass `-PtambolaApiUrl=https://<approved-room-origin>`. Release builds without an origin display an unavailable state for online play. This setting does not deploy or validate a service.

Primary implementation references: [Ktor client WebSockets](https://ktor.io/docs/client-websockets.html), [Ktor timeouts](https://ktor.io/docs/client-timeout.html), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), and [Android network security configuration](https://developer.android.com/privacy-and-security/security-config). Runtime behavior is pinned and verified against the versions in the repository.
