# Tambola Together

Native Android game under development on `shrey/full-tambola-game`. See the [implementation plan](../docs/FULL_GAME_PLAN.md) and [execution ledger](../docs/FULL_GAME_PROGRESS.md) for the full production scope and evidence.

Alpha13 redesigns play around a compact game table: one-tap quick games, all 1–6 owned tickets together without a ticket carousel or scrolling, non-repeating numbers across each player's hand, and a covered handoff for shared-device family play. See the [design research](../docs/GAMEPLAY_REDESIGN.md), [alpha13 validation](ALPHA13_VALIDATION.md) and [installation guide](ALPHA_INSTALL.md). Alpha14 packages the verified build-tool migration with Android 8/11/16, upgrade, multiplayer and recovery acceptance; see [alpha14 validation](ALPHA14_VALIDATION.md). It remains an internal development build.

Private-room **invitation links** retain English/Hindi review, explicit registration/join and preservation of current rooms and pending commands. Public App Links require the actual domain and installed signing certificate; see [invite setup](INVITES.md). Hosting, production signing and physical-device acceptance remain open.

**Your game data**, introduced in alpha11, remains available from Settings and before online registration. It explains storage, retention, deletion, the separate recovery record and aggregate monitoring; deletion confirmation states that recovery records currently have no automatic expiry. Provider retention policy and public privacy/support work remain release gates.

The alpha10 storage update keeps one application-owned offline database across screen lifecycles and preserves coroutine cancellation during shutdown. It fixes a closed-connection/locked-history failure reproduced on Android 15. See [the lifecycle investigation](STORAGE_LIFECYCLE.md) and [alpha10 validation](ALPHA10_VALIDATION.md) for the failing fixture, fixed-candidate checks and tablet layouts.

The service recovery update adds an independent deletion journal and replays it before accepting requests after a database restore. All 41 service cases and a real-process primary-backup restore drill pass locally. See [recovery validation](server/RECOVERY_VALIDATION.md) and the [restore runbook](server/BACKUP_RECOVERY.md). That service-only update preserved the alpha10 APK and protocol 2; alpha11 now adds the Android disclosure. Hosted backup/retention acceptance remains release work.

The [service capacity update](server/CAPACITY_VALIDATION.md) completes ten simultaneous 32-player games with 1,920 tickets and 28,800 measured call deliveries. The revised polling interval meets the local p95 delivery budget at 708 ms, with increased CPU use documented against the baseline. All 41 service cases and both process recovery drills pass on that candidate. This adds service evidence without changing the APK; hosted/physical-device and longer fault/soak checks remain.

The [service permission update](server/PERMISSIONS_VALIDATION.md) separates owner migrations from restricted runtime startup. All 50 service cases and three real-process checks pass on its exact binary, including denied destructive SQL, gameplay/restart with four independent roles and deletion replay after primary restore. Follow the [new database setup](server/DATABASE_PERMISSIONS.md) before serving this revision. Provider deployment/rotation and restricted-role restore acceptance remain open; the APK is unchanged.

The [service operations update](server/OPERATIONS_VALIDATION.md) adds protected aggregate metrics, readiness that detects failed/stalled workers, retryable retention cleanup and a tested non-root Docker image. All 56 service cases, real standalone/container permission-fault recovery and the alert scenarios pass. The new 320-player run delivers all 90 calls with matching results and 732 ms p95 latency. The [operations runbook](server/OPERATIONS.md) covers deployment and incident response; real hosting, TLS, alert delivery and phone acceptance remain outstanding.

The alpha implements solo/computer and shared-device family rounds, digital tickets, number calling, manual/assisted marking, standard/custom prizes, ranked houses, 90-call play, ties, points, pause/resume, local history, and three offline voice languages. The Android [custom-rule editor](domain/CUSTOM_RULES.md) includes sample-ticket examples and rule inspection during play; rematches retain the agreed setup. Native private rooms include a lobby, private tickets, host controls, encrypted sessions and reconnect flows against the local [room service](server/README.md). The interactive tutorial teaches calling/marking/verification on an isolated sample ticket, and each mode has completion/house badges. Explicit online-profile deletion includes shared-record redaction and durable confirmation retries. See [alpha05 validation](ALPHA05_VALIDATION.md) and the [native client guide](client/README.md). Final presentation/audio acceptance, Hindi editorial/device acceptance, hosted play and production release validation remain unfinished.

The alpha09 language update adds an independent English/Hindi interface choice, localized rules/errors/accessibility text and result sharing. Language changes retain game and editor state; caller voice remains independent. See [localization notes](LOCALIZATION.md), [alpha09 validation](ALPHA09_VALIDATION.md) and the [installation guide](ALPHA_INSTALL.md).

The [device compatibility addendum](DEVICE_MATRIX_VALIDATION.md) records additional checks against the unchanged alpha09 APK, including cold-process language/round restoration and Android's per-app language integration. It identifies each instrumentation build separately from the application candidate.

The alpha08 presentation update adds eight original selectable avatars and finite, dismissible celebrations for verified live wins. Choices persist through rematches; online lobby changes update the profile, and deletion redacts stored/visible identities. See [avatar and compatibility notes](AVATARS.md), [presentation direction](PRESENTATION.md) and [alpha08 validation](ALPHA08_VALIDATION.md).

The alpha07 audio update adds an original offline music loop and cues, independent saved volumes, call ducking and foreground/interruption handling. See [audio design and provenance](AUDIO.md) and [alpha07 validation](ALPHA07_VALIDATION.md) for behavior, executed checks and remaining device/listening acceptance.

## Build

The alpha06 appearance update adds saved System/Light/Dark themes, native home artwork and a number reveal that respects reduced motion and system animation settings. See [presentation direction](PRESENTATION.md) and [alpha06 validation](ALPHA06_VALIDATION.md) for the exact candidate evidence and remaining work.

A patched JDK 17, Android SDK platform 36, and an `ANDROID_SDK_ROOT` or `local.properties` SDK location are required. The wrapper pins Gradle 9.7.1 with its official SHA-256; the version catalog pins library/plugin versions. Gradle dependency verification checks the committed SHA-256 metadata. New dependencies require a reviewed metadata update. The [build-tool review](BUILD_TOOL_REVIEW.md) records the alpha14 migration's acceptance status and remaining findings.

The [runtime dependency review](DEPENDENCY_REVIEW.md) records resolved library inventories, advisory checks and the Netty/Logback security update. Use the current service build or `releases/service-security-2026-09-26/Tambola-service.zip` for private-room testing with the unchanged alpha12 APK. CI checks exact runtime Maven versions; process reports identify every bundled service library, including changes that leave the main JAR unchanged.

```powershell
python tools/check-localization.py
.\gradlew.bat :domain:test :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The backend can build separately with `-PserverOnly=true`; its tests require an explicit isolated PostgreSQL database. See the service README for environment setup and the real-process restart smoke test.

The app ID is `io.github.sbshrey.tambola.game`. It installs beside the existing Tambola Keyboard. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`; it is an internal alpha, not a signed production release. Build-time imports check all 270 voice recordings and five original sound assets against repository manifests. `node tools/compose-sounds.mjs --check` also verifies that the PCM files reproduce exactly. No OpenAI key is needed to build or play.

## Rules and data

- Tickets have 3 rows, 9 columns, and 15 unique numbers. Each row has 5; columns follow the standard 1–9, 10–19, …, 80–90 ranges and ascend vertically.
- The shared Kotlin domain module owns ticket validation, secure random draws, standard prize rules, same-draw ties, points, round status, and save validation by replay. A mark is a player's visual aid; it cannot forge a winning ticket.
- Setup can end the round at the first, second or third house, or continue through all 90 calls. Ranked houses replace standalone full-house points; previously winning tickets cannot win a later rank.
- The custom-prize editor supports AND/OR groups, row/column/range/position selectors, count thresholds, specific owned tickets and multiple-ticket requirements. Live sample examples use separate illustrative tickets. Invalid/impossible counts and incompatible ticket allowances are explained before dealing.
- Prize inspection keeps the locked definition visible and shows actual selected numbers, missing calls, winning tickets and the award call. All eligibility is independent of manual marking.
- Every winning ticket appears in an award. Players receive points once per prize even with multiple winning tickets; tied players each get full points.
- Leaving the foreground pauses offline play and audio. Restoring a round does not automatically speak or draw.
- The game table keeps all owned tickets and the main play action on screen. Manual play uses one large action to dab confirmed called numbers; adaptive layouts support landscape and large system text. Other players' ticket grids are private.
- Room stores rounds atomically. Preferences use DataStore. Offline history is local; deleting offline rounds requires confirmation. Online snapshots and sessions use a separate encrypted store.
- Setup/editor drafts use the ViewModel and saved instance state to survive recreation. Rematches retain players/settings/custom prizes and produce a fresh round when dealt. Sharing starts with a preview and leaves player names out unless selected.

Assets in this build: native vector/Compose graphics, the repository's previously generated AI voice recordings, and original synthesized music/effects. No runtime AI calls, analytics, ads, payments, microphone permission, or OpenAI key are included. Internet permission supports private rooms; solo and family play remain offline.

## Test scope

The domain suite includes 100,000 generated-ticket property cases, maximum-size unique deals, draw exhaustion, ties, marks, undo, ranked houses, corrupt saves, and standard-rule missing-number checks. Android instrumented tests exercise complete offline play, recreation/resume, family tickets, rule inspection, and cancellation. Actual results and limitations belong in the execution ledger; having tests in source is not proof they passed.

The [alpha05 recovery and multiplayer addendum](RECOVERY_AND_MULTIPLAYER_VALIDATION.md) records passing cold-process recovery for uncertain draw/deletion requests and a full two-emulator native UI game with matching results and rematch. It adds repeatable fixture drivers and evidence without changing the alpha05 APK. Physical phones, network switching and hosted-service acceptance remain pending.
