# Tambola Together

Native Android game under development on `shrey/full-tambola-game`. See the [implementation plan](../docs/FULL_GAME_PLAN.md) and [execution ledger](../docs/FULL_GAME_PROGRESS.md) for the full production scope and evidence.

This first offline alpha implements solo/computer and shared-device family rounds, digital tickets, number calling, manual/assisted marking, verified standard prizes, ties, points, pause/resume, local history, and three offline voice languages. Private online rooms, custom regional rules, badges, final music/art, Hindi UI, and production release validation remain planned work.

## Build

JDK 17, Android SDK platform 36, and an `ANDROID_SDK_ROOT` or `local.properties` SDK location are required. The wrapper pins Gradle 8.13 with its official SHA-256; the version catalog pins library/plugin versions. Gradle dependency verification checks the committed SHA-256 metadata. New dependencies require a reviewed metadata update.

```powershell
.\gradlew.bat :domain:test :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The app ID is `io.github.sbshrey.tambola.game`. It installs beside the existing Tambola Keyboard. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`; it is an internal alpha, not a signed production release. Build-time voice import checks all 270 recordings against the repository manifests. No OpenAI key is needed to build or play.

## Rules and data

- Tickets have 3 rows, 9 columns, and 15 unique numbers. Each row has 5; columns follow the standard 1–9, 10–19, …, 80–90 ranges and ascend vertically.
- The shared Kotlin domain module owns ticket validation, secure random draws, standard prize rules, same-draw ties, points, round status, and save validation by replay. A mark is a player's visual aid; it cannot forge a winning ticket.
- The first full house ends the alpha UI's round. Domain support also covers 90-call games and three ranked houses, pending configuration UI.
- Every winning ticket appears in an award. Players receive points once per prize even with multiple winning tickets; tied players each get full points.
- Leaving the foreground pauses offline play and audio. Restoring a round does not automatically speak or draw.
- Calling controls remain at the bottom of the game screen while the tickets scroll. The large-number marking sheet supports smaller screens; at large system text sizes the ticket overview switches to wrapping row layouts.
- Room stores rounds atomically. Preferences use DataStore. History is local; deleting rounds requires confirmation.

Assets in this build: native vector/Compose graphics and the repository's previously generated AI voice recordings. No runtime AI calls, analytics, ads, payments, microphone permission, or network permission are included in the offline alpha.

## Test scope

The domain suite includes 100,000 generated-ticket property cases, maximum-size unique deals, draw exhaustion, ties, marks, undo, ranked houses, corrupt saves, and standard-rule missing-number checks. Android instrumented tests exercise complete offline play, recreation/resume, family tickets, rule inspection, and cancellation. Actual results and limitations belong in the execution ledger; having tests in source is not proof they passed.
