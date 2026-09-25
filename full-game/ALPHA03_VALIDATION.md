# 0.3.0-alpha03 validation

Date: 26 September 2026 (Asia/Calcutta). Package `io.github.sbshrey.tambola.game`, version code 3, minimum API 26, target API 36. This is a debug-signed internal alpha. Private rooms use an isolated local service; no public service or production release is delivered by this milestone.

## Delivered behavior

- Native guest registration, room creation/join codes, lobby roster, ready checks, host removal/locking, standard/custom rules, private owned tickets, manual/assisted marking, server-confirmed calls and prizes, pause/resume, ending, results, rematch and online history.
- Shared offline/online table and rule inspection avoid exposing another player's cards or constructing a client-side authoritative round. Round rosters and labels for awarded tickets preserve readable results after membership changes. A three-house game requires at least three total tickets before starting.
- Android Keystore AES-256/GCM protects the session, cached snapshot, pending operation, marks and history in an atomic file excluded from backups. Decryption failure preserves the file and offers an explicit reset; there is no cleartext fallback. Reset is local only, and server-side data deletion remains unfinished.
- Mutating actions persist their identity before sending. Uncertain actions stay pending; retries retain the original request UUID and revision. A newer stream snapshot cannot be rolled back by an older receipt. Initial/reconnect catch-up does not announce old calls.
- Release endpoint configuration accepts HTTPS origins only; debug cleartext is limited to named loopback endpoints. Tokens use request headers, redirects are disabled, HTTP responses are bounded while reading, and WebSocket frames are checked before decoding. The WebSocket size check is after engine receipt and is not an allocation limit.
- Offline play retains its existing Room saves and bundled voices. Active visible tables keep the screen awake; leaving the online screen stops local networking/audio while the service can continue the round.

## Build and automated evidence

- The current reports contain **50 passing JVM/service tests**: 20 domain, seven client, 19 server and four Android setup tests. The domain suite includes 100,000 generated-ticket cases and 250 custom-rule example cases. The latest combined verification reran server/setup tests and reused passing up-to-date domain/client outputs; those reused results are not new executions.
- Server tests use real isolated PostgreSQL schemas. The added native-client integration test connects the production HTTP/WebSocket client to an actual ephemeral Netty listener. This exposed and fixed an unsupported OkHttp/Ktor `maxFrameSize` setting before Android acceptance.
- The exact debug APK candidate below passed **11/11 device tests in 201.52 seconds** on dedicated Android 11/API 30 emulator `tambola_full_game_api30`: eight offline journeys, two online journeys and one encrypted-storage case. The guarded runner restored and verified all system animation settings after the run.
- The online host journey completes all **90 calls**, compares calls/scores with another authenticated production client running on Android, verifies private cards, a custom prize requiring two owned tickets, three-house configuration, pause/recreation/reconnect, sharing privacy and retained rematch rules. The second player has no separate Compose UI in this test.
- The native guest journey joins by code, marks a called number, leaves the online screen while calls continue, catches up with tickets/marks intact, sees no host controls, then leaves a cancelled round and opens encrypted local history.
- Storage tests verify round-trip decryption, different ciphertext for repeated plaintext, absent plaintext token, rejection of modified ciphertext without deleting it, and explicit clearing. These are functional Keystore/GCM checks, not a device hardware-security attestation.
- A separate guest journey on the same APK passed at **360dp / 200% system font in 11.214 seconds**. Density and font settings were restored afterward. Normal and large-font screenshots were visually reviewed; cards wrap into readable rows and remain scrollable.
- `assembleDebug`, debug JVM tests and `lintDebug` pass under normal dependency SHA-256 verification. Debug lint reports **zero errors and one unsuppressed KAPT-to-KSP migration warning**. The optimized release APK/AAB and release lint were also compiled with the placeholder HTTPS origin `https://rooms.example`; those unsigned artifacts are compile checks only and are not distributed as a working service release.

Commands used from `full-game/` (with the documented isolated PostgreSQL environment):

```powershell
.\gradlew.bat :domain:test :client:test :server:test :server:installDist :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
node tools/android-smoke.mjs --online --label alpha03-verified-tests
node tools/android-smoke.mjs --online --label alpha03-verified-large-text --class 'io.github.sbshrey.tambola.game.OnlineGameTest#nativeGuestJoinsMarksAndCatchesUpWithoutHostControls'
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease -PtambolaApiUrl=https://rooms.example --no-daemon --console=plain
```

Both instrumentation APKs were installed explicitly. The large-text command ran inside a `try/finally` that set density 480/font scale 2.0 and restored density/font afterward. Wi-Fi/mobile data were disabled; the local service was reached through `adb reverse tcp:8080 tcp:8080`. This does not simulate real mobile networking or TLS.

An earlier combined candidate run timed out during an offline setup confirmation. A guarded runner now disables platform transitions during automation and restores them afterward; the subsequent complete run passed. The specific timeout cause was not independently proved. The first final large-text run tried to click a number below the viewport; adding an explicit test scroll fixed that test, with no application binary change. These failed attempts are not counted as passes.

## Candidate and evidence package

Candidate: `Tambola-Together-0.3.0-alpha03.apk`; **39,220,350 bytes**. SHA-256: `1af0e2050069eed9cd2261543234b5bd11236054ec8e4b0f8b9ed4f35bff93e2`.

The installed `base.apk` was read back and matched that exact hash. APK Signature Scheme v2 verifies, with the same debug certificate as alpha01/alpha02: SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`. Manifest inspection confirms version/API values and Internet, vibration and the AndroidX private receiver permission. No OpenAI key or microphone permission is included.

The ignored local package under `releases/0.3.0-alpha03/` contains the APK, checksum, installation/client guides, this validation report, source-commit record, test/lint evidence, signature/manifest inspection and fictional-player screenshots. No database, password, session ciphertext or unsigned placeholder release build belongs in that package. Existing alpha01/alpha02 artifacts remain unchanged. Certificate continuity permits Android to update the older alphas, but this milestone does not add a real alpha02-to-alpha03 retained-data upgrade test.

## Remaining production gates

Two independent native UIs/physical phones on different networks; process death with an uncertain operation; network switching, server restart and session expiry in native UI; API 26/35/36 and tablets; TalkBack, interruptions, audio, performance and longer sessions; explicit online deletion/recovery; hosting/TLS, least-privilege roles, monitoring, cost limits, backup/restore and rollback; badges/tutorial/final art/music/celebrations/Hindi UI/light theme; release signing and an exact signed-candidate install/update acceptance remain unfinished. Cloud CI was updated but has not been run. No paid generation, cloud provisioning, public deployment, message sending or store submission occurred in this milestone.
