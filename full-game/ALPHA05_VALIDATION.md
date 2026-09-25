# Alpha 05 validation: online-profile deletion

Date: 26 September 2026. Branch: `shrey/full-tambola-game`. This is a debug-signed internal alpha; the production release goal remains open.

## Behavior delivered

- A player can delete an online profile from a lobby, active game, finished game or expired unrevoked session. The confirmation explains shared-record retention, downloaded copies, backups and the independence of offline games. Cancelling keeps the profile.
- PostgreSQL removes the profile/access and current memberships, redacts its name/avatar in current and archived rounds and other players' saved retry responses, and retains agreed tickets/calls/scores. A remaining player becomes host and can continue the same round. An empty room closes.
- A deletion request and its confirmation commit atomically. The device persists the UUID before sending; a hash-only service receipt supports the original request for 30 days after access is removed. Duplicate requests return the original confirmation. A 401 or timeout never implies success.
- Android suspends the room connection while deletion is pending, preserves the encrypted request after an uncertain response, and clears the encrypted file and atomic-file siblings only after confirmation. A storage cleanup failure has a distinct message. Deletion leaves offline rounds and their badges unchanged.
- Ordered, checksum-verified migration 002 adds revocation state, historical participant indexing and deletion receipts without modifying migration 001. Pre-upgrade expired/revoked tokens are conservatively treated as revoked because the old schema cannot distinguish them.

See [the exact deletion/retention policy](server/PROFILE_DELETION.md) for protocol, locking, recovery limits and operational requirements.

## JVM, database and build evidence

Current reports contain **64 passing JVM/service tests**: 23 domain, 10 client, 27 server and four Android setup cases. The domain reports are unchanged from alpha04; client, real PostgreSQL/HTTP/WebSocket server and setup checks ran for this change. The domain report retains the 100,000-ticket and 250 custom-example checks.

Seven new database cases cover active-host deletion and continued play; identity-specific redaction; injected rollback; concurrent duplicate deletion and a racing join; expiry/revocation and receipt cleanup; last-member/unrelated-profile behavior; and migration/backfill/checksum rejection. HTTP and production-client integration additionally exercise the endpoint and idempotent confirmation. Client coverage verifies serialization, original request reuse and rejection of a mismatched confirmation.

Debug and optimized release APK/AAB compilation and lint pass with normal dependency SHA-256 verification. Both lint reports have **zero errors and one unsuppressed KAPT-to-KSP migration warning**. Release compilation uses `https://rooms.example` solely to verify configuration/R8/packaging; those unsigned placeholder artifacts are not distributed. The debug candidate uses loopback for local integration.

Commands from `full-game/` (server checks require the documented isolated PostgreSQL environment):

```powershell
.\gradlew.bat :client:test :server:test :server:installDist :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease -PtambolaApiUrl=https://rooms.example --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --no-daemon --console=plain
node tools/android-smoke.mjs --online --fault-proxy --label alpha05-verified-tests
```

## Device and artifact acceptance

The active-round deletion journey cancels the confirmation once, deletes the host with responses deliberately dropped after commit, checks the original encrypted pending request, recreates the activity, retries successfully, verifies removal of local online storage and confirms that the remaining host can call number two. The original offline round retains its ID, tickets, call and marks.

Screenshot review caught a stale Connected label after stream cancellation and, at large text size, disabled host controls crowding out the pending explanation. The final candidate sets the connection to suspended and hides the fixed game controls during profile deletion. Tests check this state and the absence of calling controls; screenshots confirm readable scrolling content and an accessible retry button. An earlier lobby-only test APK and deliberately cancelled superseded full run are excluded from final acceptance.

Candidate: `Tambola-Together-0.5.0-alpha05.apk`, **39,307,208 bytes**. SHA-256: `d244c8fa746cd47fcc863a90bd309081af140bd2acacab27513414b4f60d4c6d`. Installed `base.apk` readback matches. Signature Scheme v2 verifies with the existing Android debug certificate, SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`. Package `io.github.sbshrey.tambola.game`, version code 5, minimum API 26, target API 36. The final loopback debug/test build took 1 minute 23 seconds; the optimized release compile took 2 minutes 26 seconds.

The exact candidate passed **15/15 instrumented tests in 226.598 seconds**: eight offline gameplay journeys, two tutorial/badge journeys, three online journeys and two encrypted-storage checks. This includes the native full 90-call online round, private tickets and synchronized scoring, active-round deletion/host succession and failed-device-cleanup reporting. The runner verified restoration of the original system animation settings. Installed APK readback after the run still matched the candidate hash.

The final deletion journey also passed at **360dp / 200% system text in 16.396 seconds** on the dedicated Android 11/API 30 emulator. It includes cancellation, scrollable confirmation, original pending intent, retry, activity recreation, continued peer calling and preserved offline progress. Large-text screenshots were visually inspected, including the retry action scrolled into view. Density/font/animation settings were restored and verified. This repeats one existing case at another configuration; it is not another unique test.

The first enlarged-text attempt stopped at the ordinary **Start a fresh round?** dialog because a preceding test left an offline game active. The test now accepts that fixture setup confirmation before creating the offline round whose identity it will preserve. The earlier pass before the visual layout fix is also superseded by the final candidate above.

A full run on the final application APK passed 14 cases but timed out before host registration; its screenshot showed the name field still focused with the keyboard open. The online test helper now invokes the field's existing Done action before scrolling to the next button, avoiding an input-method/layout timing race. This changes only instrumentation, not the candidate APK. The failed run is retained separately and is not reported as a passing suite.

## Evidence boundaries

The drop-response proxy forwards to a fixed loopback service and discards successful deletion replies only after the upstream body completes. It drops every successful transport retry while armed. It does not log tokens or request bodies. This proves a committed-but-unconfirmed deletion over actual Android HTTP and a PostgreSQL transaction, not a mobile carrier fault or hosted TLS outage.

The native second player uses the production client on Android without a second Compose UI. Activity recreation is not cold process death. Two independent physical/native UIs, pending-request cold-process recovery, broader API/device/TalkBack/audio/performance acceptance and real networks remain open. Large retained-history deletion latency/memory, deletion suppression after backup restore, backup expiry, public hosting/TLS/monitoring, and production signing remain release gates. Final art/music/celebrations, Hindi UI and light theme also remain unfinished. No cloud provisioning or paid generation occurred.
