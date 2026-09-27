# Tambola Together

Native Android game under development on `shrey/full-tambola-game`. The current scope is the [online coin game](../docs/ONLINE_COIN_GAME_PLAN.md), tested first on the [private Wi-Fi host](../docs/WIFI_HOST.md). That scope supersedes the earlier offline/family plans below.

**Design feedback without reinstalling:** open the [editable landscape Figma board](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=4-2696), with nine screens on the v2 page, or use the [clickable browser preview](../designs/landscape-lobby-v1/README.md). The preview has animated joining, five-second calls and simulated ticket/claim interactions. The native Game night design now includes welcome/lobby, landscape play, roster arrivals, the call timer, minimal Settings and the [ticket table and prize picker](reviews/game-night-arena-2026-09-27/README.md). These isolated review builds are not yet in the packaged alpha22 APK; optimized packaging, relevant real-round acceptance and physical-phone checks remain.

Play buys 1–6 disjoint tickets with free virtual coins, starts a short countdown and fills empty seats with labelled computers. Calls arrive every five seconds. Mark manually, page through one or two readable tickets with arrows, and claim a chosen prize beside its ticket. The pool funds six to eight prizes; results offer another round. Alpha17 adds automatic same-installation session renewal and durable logout recovery; [packaging and Wi-Fi acceptance](reviews/session-wifi-alpha17-2026-09-27/README.md) track its deployment status.

The current [optimized alpha22 APK](ALPHA_INSTALL.md) makes the full player list directly accessible and keeps all eight prizes visible with larger text. It retains the earlier cancellation-retry, ticket-choice and refill fixes. Its external UI check passed a complete 75-call round against two labelled computers, a shared prize with verified coin shares, cold-process mark recovery and another purchase/refund on the unchanged PC service. The 29.1 MB APK uses the existing development signature. [Computer-opponent evidence](reviews/computer-round-alpha22-2026-09-27/README.md) and the accepted [nine-round endurance run](reviews/coin-endurance-alpha22-2026-09-27/README.md) separate verified correctness from pending physical-phone, smoothness and production acceptance.

The [store, privacy and support draft](RELEASE_CONTENT_DRAFT.md) describes the current coin game and wallet retention. Operator details, public hosting, deletion support, physical-device acceptance and production signing remain unresolved; these pages have not been published.

## Earlier checkpoints

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

The [runtime dependency review](DEPENDENCY_REVIEW.md) records resolved library inventories, advisory checks and the Netty/Logback security update. Use the current service build or `releases/0.14.0-alpha14/engineering/Tambola-service.zip` for private-room testing with alpha14. CI checks exact runtime Maven versions; process reports identify every bundled service library, including changes that leave the main JAR unchanged. The earlier service-security package remains a historical alpha12 artifact.

```powershell
python tools/check-localization.py
.\gradlew.bat :domain:test :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The backend can build separately with `-PserverOnly=true`; its tests require an explicit isolated PostgreSQL database. See the service README for environment setup and the real-process restart smoke test.

The app ID is `io.github.sbshrey.tambola.game`. It installs beside the existing Tambola Keyboard. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`; it is an internal alpha, not a signed production release. Build-time imports check all 270 voice recordings and five original sound assets against repository manifests. `node tools/compose-sounds.mjs --check` also verifies that the PCM files reproduce exactly. No OpenAI key is needed to build or play.

## Current coin rules and data

- Tickets have 3 rows, 9 columns, and 15 unique numbers. Each row has 5; columns follow the standard 1–9, 10–19, …, 80–90 ranges and ascend vertically.
- Each player buys 1–6 tickets with no repeated number within that hand. A full six-ticket strip covers 1–90 once. The service returns only the player's own ticket numbers and never reveals future draws during play.
- A 12-second lobby accepts up to eight people. At the deadline, fewer than four humans are supplemented with labelled computer players to make four participants. The server calls a new number every five seconds.
- The game table displays one or two readable tickets per page. Marks and the selected page persist; calls never jump to another ticket. Claim opens beside its ticket and requires a prize choice.
- Ticket purchases cost 100 free coins each. The frozen ticket pool funds Early Five, Corners, Top/Middle/Bottom and one to three ranked house prizes. Earlier house-winning tickets cannot take a later house rank. Shared claims on the same call split a prize by winning ticket, with deterministic rounding; settled coin shares replace the old points/badges progression.
- The shared Kotlin domain module owns ticket validation, secure random draws, prize rules and save validation. The service validates selected claims against the current round and call, then settles payouts atomically. A forged mark cannot make an uncalled number eligible.
- Retries retain their original purchase/claim/cancellation identity. Cancelling an unstarted purchase refunds it once. At round completion, any unawarded pool is returned proportionally to purchased tickets. Results retain the chosen quantity for a new, explicit purchase.
- Calls continue on the server when a player backgrounds or disconnects. Audio follows the foreground/interruption controls; reconnect restores purchased tickets and saved marks on the same installation.
- Session/device credentials, cached online games, marks and pending requests use Android Keystore-backed encryption. Preferences use DataStore. Same-installation session renewal retains the server wallet, but there is no wallet recovery after local reset, uninstall or device loss. The [data disclosure](app/src/main/res/values/privacy.xml) and [release draft](RELEASE_CONTENT_DRAFT.md) describe retention and deletion.

Older offline saves, private-room invitations, setup/custom-rule editors, scores and badges remain in source for compatibility and their historical tests. They are not the current app's primary playing flow; the earlier checkpoint documents describe that functionality.

Assets in this build: native vector/Compose graphics, the repository's previously generated AI voice recordings, and original synthesized music/effects. No runtime AI calls, analytics SDK, ads, cash payments, microphone permission or OpenAI key are included. Network access is required for the coin game, including tables with computers; service health uses aggregate operational metrics.

## Test scope

[Performance acceptance](PERFORMANCE.md) tracks the fresh sustained service workload, native-client traffic measurement and remaining physical-device budgets separately from correctness checks.

The current [coin capacity fixture](server/COIN_LOAD.md) and [optimized native driver](macrobenchmark/README.md) cover ticket purchases, selected claims, wallet conservation, exact receipts, reconnects and cleanup at their documented scopes. The active coin plan links executed results and unresolved latency/endurance/physical-device gates; source fixtures alone are not acceptance evidence.

The domain suite includes 100,000 generated-ticket property cases, maximum-size unique deals, draw exhaustion, ties, marks, undo, ranked houses, corrupt saves, and standard-rule missing-number checks. Android instrumented tests exercise complete offline play, recreation/resume, family tickets, rule inspection, and cancellation. Actual results and limitations belong in the execution ledger; having tests in source is not proof they passed.

The [alpha05 recovery and multiplayer addendum](RECOVERY_AND_MULTIPLAYER_VALIDATION.md) records passing cold-process recovery for uncertain draw/deletion requests and a full two-emulator native UI game with matching results and rematch. It adds repeatable fixture drivers and evidence without changing the alpha05 APK. Physical phones, network switching and hosted-service acceptance remain pending.
