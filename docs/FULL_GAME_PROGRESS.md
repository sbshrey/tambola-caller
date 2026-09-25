# Complete Tambola Game: execution ledger

Plan: [FULL_GAME_PLAN.md](FULL_GAME_PLAN.md).
Branch: `shrey/full-tambola-game`.

## 25 September 2026: M0 planning

- Created the development branch from `main` at `76b6a2c5da5a4729a7dbcd28fac83878780ac9d8` with a clean starting worktree.
- Inspected the web caller, Android keyboard, prize catalog, prerecorded assets, generator, release script, and validation notes.
- Recorded the complete product scope, executable rule definitions, native Android/shared-engine architecture, UI/audio direction, phased work, estimates, and production gates.
- The user confirmed offline practice + shared-device family play + private online rooms, with free social play using points/badges. Monetary prize tracking and in-app payments are outside the confirmed scope.
- Verified `npm test`: 69 passed, zero failed. Verified `npm run build`: succeeded.
- Verified existing keyboard `gradlew.bat --no-daemon testDebugUnitTest lintRelease`: succeeded. Unit task reused up-to-date output; its reports contain 24 passing tests. Release lint reports no issues.
- Confirmed the current shell has an OpenAI key without printing its value. No paid generation or API access test was performed.
- No new game app code, APK, service, signing key, infrastructure, or store submission was created in M0.

## Milestone status

| Milestone | Status | Evidence |
| --- | --- | --- |
| M0: plan and branch | Complete | Plan and baseline checks above |
| M1: native foundation and design | Complete for the initial prototype | Native Compose APK installs beside keyboard; normal/200% text UI reviewed; API 36 tooling, dependency verification, CI definition |
| M2: shared ticket/rules engine | In progress | Standard tickets/rules/ties/points/persistence validation pass 11 tests, including 100,000 tickets; custom regional rules remain |
| M3: complete offline alpha | In progress | Solo/computer/family UI, saved rounds, marking, voices, results; four emulator journeys pass; broader device/recovery acceptance remains |
| M4: room service and hosting spike | Pending | — |
| M5: online Android experience | Pending | — |
| M6: final art/audio/accessibility | Pending | — |
| M7: production validation | Pending | — |
| M8: signed release and handoff | Pending | — |

## 25 September 2026: first native offline alpha

- Added the isolated `full-game/` Kotlin/Compose application and pure Kotlin `domain` module, with API 26 minimum and API 36 target. Installed SDK 36 alongside the existing SDKs. The new package and the signed keyboard package were both installed on the dedicated test emulator.
- Implemented Home, setup, game table, ticket enlargement/marking, board/history, rule explanations, points/results sharing, saved-round history, and settings. Practice supports 0–5 labelled computer players; family setup supports 2–8 people; each receives 1–6 unique tickets.
- Added secure ticket generation and draw shuffling, same-call ties, standard prize verification, ranked-house domain rules, per-player scoring, practice undo, and save validation by replay. Automated eligibility uses called numbers rather than client marks.
- Added Room snapshots/history and DataStore preferences. Replacing an active round is transactional; viewing an old result keeps the current round intact. Backgrounding pauses offline calling; restoration does not speak or draw automatically.
- Packaged and hash-checked all 270 existing voice clips. Added audio-focus handling, pace controls, haptics, reduced motion, large-number marking, and a wrapping ticket overview for large text. No paid generation occurred.
- Visual inspection found and fixed screen-scroll inheritance, disappearing call controls, and low-contrast system bars. Call controls now remain at the bottom while tickets scroll.
- Domain tests: **11 passed**, including **100,000 generated-ticket property cases**. The initial final-app instrumentation run passed **4/4** on Android 11/API 30, including a complete round, recreation/resume, family play, and history/current-round isolation.
- Build and debug lint succeed with **0 errors and 18 warnings**. Remaining warnings concern newer available dependency/tooling versions and KAPT-to-KSP migration; they are not suppressed. Dependency SHA-256 verification metadata and a GitHub Actions build definition were added; the remote CI workflow has not run yet.
- The first wrapper download timed out in Java. The same official Gradle archive was downloaded with PowerShell, checked against the pinned official SHA-256, and used to seed the wrapper cache. The committed wrapper then ran successfully.
- The focused family journey also passed at **360dp / 200% font size with Wi-Fi and mobile data disabled**. Its enlarged-ticket screenshot was visually inspected. The first attempt exposed a test-helper issue: scrolling a horizontal ticket strip did not bring its parent into the vertical viewport; the test now scrolls the strip into view before tapping and asserts selection.
- The final current-test-source run also passed **4/4 with networking disabled**, including the complete solo round, in 83.338 seconds.
- A separate host-driven process test called number 86, force-stopped the app, cold-launched it, and verified the same one-call round restored in a paused state. Normal-size home/table screenshots were reviewed after this check. This is one emulator process-restart sample, not exhaustive power-loss testing.
- Packaged candidate: `full-game/releases/0.1.0-alpha01/Tambola-Together-0.1.0-alpha01.apk`, SHA-256 `c6dc6e079cb946c8186bff8bbcc3e4ec9ce921b455588fce255d18e1fc216119`. APK signature verification passed (debug certificate, v2 scheme); size 37,824,919 bytes.
- This is a debug-signed internal alpha. Private online rooms, configurable regional rules, badges, final music/art/celebrations, Hindi interface, physical-phone testing, production signing, and deployment remain unfinished.

Next implementation slice after the alpha checks: complete the configurable rule model and persistence/recovery coverage, then introduce the public command/event protocol and room service while continuing the UI/audio work. See the plan for the full unchanged production scope.

Update this ledger after each milestone with exact commands, app/service revisions, artifact hashes, observed behavior, and remaining limitations. The broader APK goal remains unfinished until the production gates in the plan are satisfied.
