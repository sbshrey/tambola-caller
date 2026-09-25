# Tambola Together

Native Android game under development on `shrey/full-tambola-game`. See the [implementation plan](../docs/FULL_GAME_PLAN.md) and [execution ledger](../docs/FULL_GAME_PROGRESS.md) for the full production scope and evidence.

The alpha implements solo/computer and shared-device family rounds, digital tickets, number calling, manual/assisted marking, standard/custom prizes, ranked houses, 90-call play, ties, points, pause/resume, local history, and three offline voice languages. The Android [custom-rule editor](domain/CUSTOM_RULES.md) includes sample-ticket examples and rule inspection during play; rematches retain the agreed setup. Native private rooms now include a lobby, private tickets, host controls, encrypted sessions and reconnect flows against the local [room service](server/README.md). See [alpha03 validation](ALPHA03_VALIDATION.md) and the [native client guide](client/README.md). Badges, final music/art, Hindi UI, hosted play and production release validation remain unfinished.

## Build

JDK 17, Android SDK platform 36, and an `ANDROID_SDK_ROOT` or `local.properties` SDK location are required. The wrapper pins Gradle 8.13 with its official SHA-256; the version catalog pins library/plugin versions. Gradle dependency verification checks the committed SHA-256 metadata. New dependencies require a reviewed metadata update.

```powershell
.\gradlew.bat :domain:test :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The backend can build separately with `-PserverOnly=true`; its tests require an explicit isolated PostgreSQL database. See the service README for environment setup and the real-process restart smoke test.

The app ID is `io.github.sbshrey.tambola.game`. It installs beside the existing Tambola Keyboard. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`; it is an internal alpha, not a signed production release. Build-time voice import checks all 270 recordings against the repository manifests. No OpenAI key is needed to build or play.

## Rules and data

- Tickets have 3 rows, 9 columns, and 15 unique numbers. Each row has 5; columns follow the standard 1–9, 10–19, …, 80–90 ranges and ascend vertically.
- The shared Kotlin domain module owns ticket validation, secure random draws, standard prize rules, same-draw ties, points, round status, and save validation by replay. A mark is a player's visual aid; it cannot forge a winning ticket.
- Setup can end the round at the first, second or third house, or continue through all 90 calls. Ranked houses replace standalone full-house points; previously winning tickets cannot win a later rank.
- The custom-prize editor supports AND/OR groups, row/column/range/position selectors, count thresholds, specific owned tickets and multiple-ticket requirements. Live sample examples use separate illustrative tickets. Invalid/impossible counts and incompatible ticket allowances are explained before dealing.
- Prize inspection keeps the locked definition visible and shows actual selected numbers, missing calls, winning tickets and the award call. All eligibility is independent of manual marking.
- Every winning ticket appears in an award. Players receive points once per prize even with multiple winning tickets; tied players each get full points.
- Leaving the foreground pauses offline play and audio. Restoring a round does not automatically speak or draw.
- Calling controls remain at the bottom of the game screen while the tickets scroll. The large-number marking sheet supports smaller screens; at large system text sizes the ticket overview switches to wrapping row layouts.
- Room stores rounds atomically. Preferences use DataStore. Offline history is local; deleting offline rounds requires confirmation. Online snapshots and sessions use a separate encrypted store.
- Setup/editor drafts use the ViewModel and saved instance state to survive recreation. Rematches retain players/settings/custom prizes and produce a fresh round when dealt. Sharing starts with a preview and leaves player names out unless selected.

Assets in this build: native vector/Compose graphics and the repository's previously generated AI voice recordings. No runtime AI calls, analytics, ads, payments, microphone permission, or OpenAI key are included. Internet permission supports private rooms; solo and family play remain offline.

## Test scope

The domain suite includes 100,000 generated-ticket property cases, maximum-size unique deals, draw exhaustion, ties, marks, undo, ranked houses, corrupt saves, and standard-rule missing-number checks. Android instrumented tests exercise complete offline play, recreation/resume, family tickets, rule inspection, and cancellation. Actual results and limitations belong in the execution ledger; having tests in source is not proof they passed.
