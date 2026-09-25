# Alpha 08 validation: avatars and verified-win celebrations

Date: 26 September 2026. Branch: `shrey/full-tambola-game`. This is an internal debug-signed alpha; the production goal remains active.

## Delivered behavior

- Eight original native illustrations, selectable per family/solo seat and online profile. Labels/checkmarks accompany the artwork. Tickets, round rosters and results preserve the dealt choice; rematches retain it. Correcting an overlong family list cannot leave an invalid avatar list behind.
- Online lobby changes update only the authenticated member/profile and reset only their readiness. Profile and room changes commit atomically. Old retries cannot undo a newer choice. Active-round edits are rejected, and deleted names/avatars are redacted in saved records and visible win messages.
- Dismissible inline cards for newly verified live awards, including ties/custom prizes, with links to winning-ticket inspection. Decorative confetti ends in about 1.5 seconds; reduced/system-disabled motion remains still. Persistent text and fixed caller controls remain usable. Restore, catch-up, Hear again and stale receipts do not replay old wins.
- Round format 3 reads older formats with Sun as the default avatar. Protocol envelope 2 adds round-player avatars; the new client reads legacy envelope-1 caches/receipts. Use the matching new APK/service and do not downgrade over newer saves. [Compatibility and provenance](AVATARS.md) records the precise boundary; no SQL/SQLite schema change is required.

## Candidate and build evidence

Candidate `Tambola-Together-0.8.0-alpha08.apk`: **42,072,713 bytes**, SHA-256 `55b58981c17a9f4368574eb716d6892bed7fe856071d830f5215dd57abecfd1d`. Package `io.github.sbshrey.tambola.game`, version code 8, minimum API 26, target API 36. Installed APK readback matches. Signature Scheme v2 verifies with the prior Android debug certificate, SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`.

Final debug APK/instrumentation/JVM/lint build passed in **1 minute 22 seconds**, with zero lint errors and one existing KAPT-to-KSP warning. The new modifier-parameter warning was fixed before acceptance. Normal dependency verification remains enabled.

Optimized release APK/AAB and release lint passed in **2 minutes 53 seconds**, with zero errors and the same one KAPT-to-KSP warning. These unsigned release artifacts use `https://rooms.example` solely to validate compilation/R8/packaging; they are not the distributed APK or a hosted production release.

A bounded scan of 434 packaged entries found no matching OpenAI-key/private-key patterns. All five uncompressed sound files match the existing source bytes; deterministic synthesis/manifests reproduce exactly. This is a bounded scan and byte check, not a general security audit or listening approval.

## JVM and service checks

The initial integrated source check passed **72 tests**: 25 domain, 11 client, 30 server and six app JVM tests, with zero failures or skips. Server tests use real isolated PostgreSQL, including concurrent duplicate avatar commands, invalid/stale/non-member commands, fixed round identities, profile carryover, atomic rollback and profile-deletion redaction. Existing HTTP/WebSocket/private-ticket cases remain in that suite.

Domain coverage reads version-1/version-2 saves and verifies version-3 round trips, retained calls/marks and invalid avatar rejection. Client coverage reads old cached fields, validates bounds and prevents stale receipts from rolling back the profile. Presentation coverage derives tied standard/custom winners from valid unique tickets, preserves scores and refreshes redacted display identities without changing the event ID. Setup coverage includes corrected excess names and rematch retention.

The final integrated Gradle command reran all six app JVM tests after the setup correction; unchanged domain/client/server tasks were up to date against the earlier passing reports. The package retains the current XML reports by module. This is 72 distinct cases, not 72 additional cases per build.

## Native device acceptance

The exact final APK passed **24/24 native tests in 290.774 seconds** on the dedicated Android 11/API 30 emulator, against the real local Netty/PostgreSQL service and existing response-loss fixture. This comprises the prior 22 journeys plus the new avatar/win and finite-motion cases; the existing online host/guest cases now exercise avatars and celebration recovery.

The family journey selects Moon/Peacock through the native picker, recreates setup, deals those identities, observes a real verified custom win, inspects/dismisses it, marks a called ticket, recreates the paused round, and checks results/rematch retention without replay. The online host registers with Moon, changes to Mango in the lobby, plays all 90 calls with a Peacock peer, checks fixed avatars after recreation and verifies that a dismissed live win does not return after reconnecting. Guest catch-up stays quiet; encrypted storage, profile-deletion response loss, audio, custom rules, tutorial/badges and prior gameplay cases remain in the passing suite.

The final installed APK readback still matches after acceptance. Owned service/proxy processes were stopped and their ADB mappings removed. The original emulator and isolated PostgreSQL cluster remain available. The accompanying report package identifies the source commit, final APK/test APK hashes, current JVM reports, final passing logs, screenshot evidence and restored settings.

The confetti component uses the Compose test duration-scale context to check scales 0, 1 and 10, reduced motion, completion and absence of a loop. This is not a physical compositor/frame-rate measurement. Native journey assertions and screenshots do not establish a full TalkBack or physical-phone acceptance.

A real **alpha07-to-alpha08 in-place update** preserved a genuine version-2 round created by the older installed APK. Before/after snapshots agree on identity, ticket hash, settings, custom rules and the manual mark; its one called number remained and a second call saved as format 3. The native post-update case passed in **4.506 seconds**. The old-APK fixture journey passed in **18.274 seconds**.

This upgrade check used the alpha08 APK before the subsequent bounded family-name/avatar-list correction: SHA-256 `909749f90fbe4961aba9706470aec36e8e07b91ebe5da2a56901f8b61327f985`. Save/protocol code was unchanged afterward. It is explicitly separate development evidence; the production-signed exact-candidate update gate remains outstanding. Safe before/after summaries identify it; raw databases, round payloads and session files are excluded from the package.

The final-candidate family avatar/win journey also passed at **360dp / 200% text in 15.579 seconds**. The picker scrolls to all choices; win inspection/dismissal remain reachable and caller controls stay fixed. The long card needs scrolling at this size. Dark/light pickers, the readable win card and avatar results were visually reviewed at normal and enlarged text, along with the online win card. Original font/density/night/animation settings were restored and verified.

The first instrumentation compile exposed an extra parenthesis in the new test fixture; it was corrected before any device run. A screenshot taken immediately after a preference update still showed the previous theme; the test now waits for Compose idle before capture, and both palettes are visually reviewed. The earlier 24/24 run in 287.899 seconds preceded the final excess-name setup correction and is kept as development evidence, separately from final-candidate acceptance.

## Repeatable commands

From `full-game/`, with JDK 17, SDK 36 and the explicit isolated PostgreSQL test environment:

```powershell
.\gradlew.bat :domain:test :client:test :server:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
node tools/android-smoke.mjs --online --fault-proxy --label alpha08-full-final
node tools/android-smoke.mjs --class io.github.sbshrey.tambola.game.AvatarWinTest --label alpha08-avatar-large
node tools/android-smoke.mjs --class io.github.sbshrey.tambola.game.WinMotionTest --animations --label alpha08-motion-focused
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease -PtambolaApiUrl=https://rooms.example --no-daemon --console=plain
```

The smoke runner requires installed app/test APKs and separately started local fixtures; it restores the animation settings it changes. The large-text run additionally sets 360dp width/200% font and restores its original device settings. `UpgradeAvatarTest` is deliberately excluded from ordinary runs and requires a genuine alpha07 baseline followed by an in-place update. Do not run it against personal app data.

## Remaining production gates

Hindi/full interface resources, further table/deal/mark presentation, full accessibility/TalkBack, physical audio/listening/routing, API/OEM/tablet coverage, long sessions/performance, wider network faults/load, large-history deletion and backup deletion suppression remain. Hosted TLS/service operations, a production client/service upgrade policy, privacy/support/store material and production signing are unfinished. The optimized release build uses a placeholder origin solely for build validation. No cloud deployment, public store submission, new paid media generation or production signing happened in this milestone.
