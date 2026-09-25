# 0.1.0-alpha01 validation

Date: 25 September 2026. Package `io.github.sbshrey.tambola.game`, version code 1, min SDK 26, target SDK 36. This is an internal debug-signed offline alpha, not a production release.

## Build and domain evidence

- `gradlew.bat :domain:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` succeeded.
- 11 domain tests pass. The property test constructs and checks 100,000 tickets. Additional cases cover 192-ticket unique deals, complete 90-number draws, paused/terminal idempotency, actual simultaneous ties, called-number verification independent of manual marking, invalid marks, undo, ranked-house uniqueness, corrupted saves, and missing-number rejection for standard prizes.
- All 270 bundled MP3s passed the build's manifest SHA-256 checks. No OpenAI API key or generation request is used by the build/app.
- Debug lint has 0 errors and 18 warnings: dependency/tooling update notices and KAPT migration advice. The API 27 theme attribute found by the first lint pass was removed; explicit backup/transfer exclusions were added.
- The APK verifies with Android APK Signature Scheme v2 using a debug certificate. Manifest inspection shows API 26/36 and the vibration/non-exported-receiver permissions; it has no Internet or microphone permission.
- Dependency verification metadata is committed. The GitHub Actions definition exists, but hosted CI execution is not yet established.

## Android emulator evidence

Dedicated Android 11/API 30 x86 emulator, emulator runtime 37.3.1.0. Both `io.github.sbshrey.tambola.keyboard` and `io.github.sbshrey.tambola.game` install side by side.

Four instrumented journeys passed against the alpha APK:

1. Complete solo round through the real UI, results, enabled share action, and persisted history. Sharing is prepared; no message was sent or third-party delivery tested.
2. One call → activity recreation → paused resume → second call with history preserved.
3. Family tickets, enlarged marking UI, rule inspection, and explicit cancellation.
4. Viewing a cancelled round's result while a new active round remains resumable with its original calls.

The suite was rerun after fixing scroll restoration and keeping caller controls visible. The captured final normal-size run reports `OK (4 tests)` in 83.338 seconds with Wi-Fi and mobile data disabled. Instrumentation uses muted number voices to avoid depending on audio hardware and preserves the real accidental-double-tap guard. These are actual Compose interaction tests on an emulator, not screenshot-only checks.

The focused family test also passed at **360dp / 200% system text with Wi-Fi and mobile data disabled**. The enlarged-ticket screenshot was visually inspected. A failed initial attempt was traced to the test helper scrolling the horizontal ticket strip without first bringing it into the vertical viewport; the corrected test verifies the selected ticket before proceeding.

A separate host-driven check drew **86**, force-stopped the app, cold-launched it, resumed the saved table, and observed **the same number and one-call count with calling paused**. Visual review of the final home/table and large-text marking dialog confirmed the corrected scroll behavior and readable system bars. This verifies one process-restart sample on one emulator, not power-loss or storage-failure recovery in general.

Candidate APK: `Tambola-Together-0.1.0-alpha01.apk`, 37,824,919 bytes. SHA-256: `c6dc6e079cb946c8186bff8bbcc3e4ec9ce921b455588fce255d18e1fc216119`. Local APK, logs, checksum, installation guide, and screenshots are packaged under ignored `releases/0.1.0-alpha01/`.

## Scope still unverified or unfinished

- No physical phone, TalkBack session, full API 26/35/36 device matrix, Bluetooth/phone-call audio interruption, real listening audit, or performance/battery benchmark has passed for this new app.
- The focused recreation/process checks do not establish update-migration, low-disk, corrupt-database, or exhaustive recovery behavior.
- Private online rooms, hosting, authentication/authorization, reconnect, concurrency/load testing, backups/restore, and server deployment are not implemented by this alpha.
- Regional/custom rules, six-ticket strips, badges, final music/art and celebrations, light theme, Hindi UI, interactive onboarding, and production signing/distribution remain planned work.
- A passing debug build and these focused tests do not satisfy the production completion gates in `docs/FULL_GAME_PLAN.md`.
