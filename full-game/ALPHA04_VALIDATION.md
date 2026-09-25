# 0.4.0-alpha04 validation

Date: 26 September 2026 (Asia/Calcutta). Package `io.github.sbshrey.tambola.game`, version code 4, minimum API 26, target API 36. This is an internal debug-signed alpha with the same loopback-only room-service setup as alpha03. The broader production goal remains unfinished.

## Tutorial and badges

- Home offers a skippable five-part interactive tutorial, also available through How to play and Settings. It explains the 15-number ticket, calls sample number 7 with optional bundled voice, lets the player dab called numbers, demonstrates a top-line win and explains modes/results/rematches. Lesson changes return to the top; sample calls/marks and the current lesson survive activity recreation.
- The tutorial has its own labelled sample ticket and presentation state. It never creates/saves a domain round, replaces a real game or earns real points/badges. Finishing records a separate preference; skipping a replay cannot remove prior completion. Sound/language preferences apply to the actual app and are labelled accordingly.
- Three explainable badges are available: First round, First full house and Five together. Solo, computer-opponent, family-table and online-profile milestones are separate. Only completed rounds count; cancellations, duplicate results, computer house wins and tutorial examples cannot advance the wrong milestone. All full/ranked house awards and tied human winners qualify.
- Offline milestones derive from validated saved rounds and clear with offline-round deletion. Family milestones belong to the shared table, not identities inferred from display names. Completed-history decoding runs away from the UI thread and is skipped when only an active round changes.
- Online milestones persist atomically with the encrypted session/snapshot. The ledger stores at most five distinct completion IDs and one house-result ID; it is not an ever-growing lifetime-statistics counter. It survives 50-result history eviction. Old snapshots without the field recover from cached completed history. Sign-out/reset removes the profile's local badges, with explicit UI copy.
- Badges use native shapes, clear earned/locked text and accessibility state descriptions. A brief native reveal respects reduced motion. No additional assets, SDKs, API keys, paid generation or analytics were introduced.

## Automated evidence

The JVM/service reports contain **55 passing tests**: 23 domain, nine client, 19 server and four setup tests. Added cases exercise bounded/idempotent progress over 1,000 result IDs; cancellation; human/computer/mode attribution; every house rank and ties; online replay; old-format JSON recovery; and retained milestones after 55 cancelled snapshots evict the original result from the 50-entry cache. Domain tests retain the 100,000-ticket and 250 custom-example cases. Real PostgreSQL/HTTP/WebSocket server regressions passed after the shared module/client changes.

Debug assembly/lint and optimized release APK/AAB/lint compilation passed under normal dependency SHA-256 verification. Both lint reports contain **zero errors and one unsuppressed KAPT-to-KSP migration warning**. The release build completed in 2 minutes 48 seconds; it is a compile check, not an install/run acceptance of a production-signed app.

The exact candidate below passed **13/13 instrumented tests in 307.52 seconds** on the dedicated Android 11/API 30 emulator: eight offline gameplay journeys, two tutorial/badge journeys, two online journeys and one encrypted-storage test. The tutorial retained its marks/lesson across recreation and left an existing game's ID, cards, calls and marks unchanged. Badge acceptance checked five valid saved completed games, recreation, separate mode scopes and deletion; the independent full solo journey checked badges earned by actual UI play. The native 90-call online journey checked the current player's earned badges and their screen; the cancelled guest journey checked that it earned no completion badge.

Both tutorial/badge journeys also passed at **360dp / 200% system font in 16.265 seconds**, including calling, marking, lesson navigation, verified feedback, finish/skip, mode selection and deletion. Density/font and system animation settings were restored and checked. This is additional configuration coverage, not an additional unique-test count. Normal and large-text screenshots were visually inspected.

A final test-only screenshot change scrolls the earned house badge into view and asserts it is displayed. That focused 200% text journey passed in **8.414 seconds**; visual review confirmed the icon, wrapping title, earned state and full explanation fit the scrollable page. The application APK did not change after the 13-test run.

The first focused device run passed tutorial isolation/recreation but timed out in the badge fixture case: the fixture used a separate Room connection and the app's observer had not been invalidated. The test now performs a real create/cancel through the app after seeding valid completed rounds, then checks the displayed badges. No production database configuration was changed to accommodate the fixture. The full offline gameplay journey also independently checks that an actually played complete solo game earns First round and First full house.

Commands from `full-game/`:

```powershell
.\gradlew.bat :domain:test :client:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
.\gradlew.bat :server:test :server:installDist --no-daemon --console=plain
node tools/android-smoke.mjs --online --label alpha04-full-tests
node tools/android-smoke.mjs --label alpha04-large-text --class io.github.sbshrey.tambola.game.LearnAndBadgesTest
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease -PtambolaApiUrl=https://rooms.example --no-daemon --console=plain
```

The server commands use the explicit isolated PostgreSQL environment. Device runs install both APKs on the dedicated `tambola_full_game_api30` emulator; online traffic reaches loopback through `adb reverse`. The runner temporarily disables system animations and verifies restoration. The large-text run additionally sets density 480 and font scale 2.0 inside a `try/finally` that restores both settings. The release compile uses a placeholder HTTPS origin solely to check configuration/R8/packaging; unsigned placeholder release artifacts are not distributed.

## Candidate and limitations

Candidate: `Tambola-Together-0.4.0-alpha04.apk`, **39,528,402 bytes**. SHA-256: `994f54b4f2f84e5d03be07af5c4bada695f0f5a25f5de42d563ebdcd1f13c635`. Reading back the installed `base.apk` produced the same hash. APK Signature Scheme v2 verifies with the same Android debug certificate as previous alphas; certificate SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`.

This milestone does not establish physical-phone acceptance, TalkBack, actual tutorial voice audition, pending-request process death, native multi-device network faults, hosted TLS/operations, server-side deletion, backup/restore, signed release behavior or a real earlier-APK data migration. Badges are local social milestones, not server-synced account achievements. Final music/art/celebrations, Hindi UI/light theme, performance/device/audio/security acceptance and production signing still remain. Previous alpha packages and evidence are preserved.
