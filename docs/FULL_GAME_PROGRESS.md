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
| M2: shared ticket/rules engine | Core implementation verified | 20 domain tests, 100,000 generated tickets, bounded custom rules, real v1→v2 APK save migration; optional six-ticket strips remain a separate extension |
| M3: complete offline alpha | In progress | Solo/computer/family UI, custom editor/claims, ranked/90-call games, rematches, voices and results; alpha02 device/recovery evidence below; broader acceptance remains |
| M4: room service and hosting spike | In progress | 16 real PostgreSQL/HTTP/WebSocket tests, 32-player correctness and actual Java-process restart pass; hosting/TLS/operations remain |
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

## 25 September 2026: custom rule engine and local room service

- Added bounded versioned custom rules: rows, columns, numeric ranges, populated positions, count thresholds, AND/OR combinations, selected ticket ordinals and multiple-ticket requirements. Empty selections cannot win. Scoring, ties, undo and replay validation include custom awards. No executable meaning was guessed from existing regional prize names.
- Round format v2 reads and upgrades the checked-in first-alpha v1 fixture without changing its calls, marks or status. **17 domain tests pass** in total, including the prior 100,000-ticket test. The APK setup/rules/results UI still needs custom-rule integration.
- Added the public `protocol` and PostgreSQL-backed `server` modules. Implemented opaque guest sessions, hash-only token storage, private lobby membership/readiness/settings, server-owned calling, host succession, personalized snapshots, durable revision events, replay/resync, command idempotency, audit records and rematches.
- Database mutations, receipts and events commit atomically. Row locks with `SKIP LOCKED` coordinate workers; the plan now records this concrete alternative to a separate renewable lease. Future calls and the nonce are excluded from public DTOs until completion/cancellation. A published draw commitment can then be verified.
- Created an isolated PostgreSQL 16.9 test cluster on loopback port 55432 with a separate test database and random credential held outside source. Every JUnit case uses an isolated generated schema. The installed PostgreSQL service and other databases were not modified.
- **16 backend tests pass**: 13 PostgreSQL service cases and three HTTP/WebSocket cases. Coverage includes concurrent duplicate/stale commands, two scheduler workers, lost-host succession, injected transaction rollback, permissions, private ticket data, token revocation/expiry, room/rate limits, full two-player and **32-player / 192-ticket** 90-call rounds, audit/rematch, and replay cursor recovery.
- The separate real-process smoke test passed: start the Netty server, create two sessions, call a number, kill the owned process, restart against the same DB, recover the same state/event, retry the original command without a second draw, end the round and stop the process. This is local process-restart evidence, not hosted failover proof.
- `gradlew.bat -PserverOnly=true :server:test :server:installDist --no-daemon --console=plain` passed with normal dependency verification. Added a PostgreSQL CI job and smoke script; hosted CI execution remains unverified.
- Android `assembleDebug` and `lintDebug` passed after the shared-domain changes. The current lint report has **0 errors and 1 KAPT/KSP migration warning**. No warning suppression was added; the earlier alpha's available-version warnings are not reproduced in this lint run.
- The current-source `:app:connectedDebugAndroidTest` run passed **4/4** on the dedicated API 30 emulator, covering complete offline play, recreation, family tickets and active-round/history isolation. The Gradle run completed in 2 minutes 8 seconds. This regression run does not establish native online or real APK-update migration behavior.
- Added [service run/protocol documentation](../full-game/server/README.md), [service validation](../full-game/server/VALIDATION.md), and [custom rule format documentation](../full-game/domain/CUSTOM_RULES.md). The prior packaged APK and its evidence retain their original offline-only scope.
- No cloud resources were provisioned. Provider/project/budget selection remains open; local implementation is proceeding. Android online UX, explicit data deletion, hosting/TLS/secrets, monitoring/backups/restore, load/security/device acceptance, final media and production signing remain unfinished.

## 25 September 2026: Android custom rules and alpha02

- Exposed the shared rule model through an Android editor with field validation, all selectors, bounded AND/OR groups, chosen owned tickets and minimum matching-ticket counts. Added a separate illustrative-ticket playground with positive/incomplete examples. The UI does not assign guessed definitions to regional names.
- Setup now offers one/two/three houses and all-90-call play. It keeps incompatible custom rules visible when ticket allowance changes and prevents an invalid deal. Saved instance state retains draft edits across recreation; rematches retain the previous setup and deal a fresh round.
- Claim inspection keeps rule definitions visible, displays actual selected/missing numbers, identifies all awarded tickets and explains why later matches cannot change an earlier award. Results count custom prizes and treat tied leaders equally. A sharing preview leaves names out by default and lets the player choose what to include.
- Unified the standard engine/inspection selectors. **20 domain tests and four Android JVM setup tests pass**, including 250 varied compound/multi-ticket positive examples. **16 backend regression tests passed** after that engine reuse change. No hosted or native online claim is made.
- The updated seven-journey emulator suite passed, including a complete **90-call** custom two-ticket prize game, saved results, editor recreation, configuration correction/discard and retained rematch options. A focused preview/inspection journey also passed at **360dp / 200% text** with Wi-Fi/mobile data disabled. See [alpha02 validation](../full-game/ALPHA02_VALIDATION.md) for exact scope and timings.
- Direct instrumentation on the **final APK candidate** subsequently passed **8/8 in 182.416 seconds** at normal text size, including the new focused column-rule journey. These are eight unique Android journeys; the large-font run is an additional configuration check, not another distinct test case.
- Visual review found and fixed a keyboard obscuring sample feedback. Input Done, selector and preview actions clear focus; normal and large-font screenshots were inspected. Surface/chip/dialog colors now consistently use the game's native palette.
- Performed a real **alpha01→alpha02 APK update** on the dedicated emulator without clearing data between versions. A genuine version-1 round retained its identity, tickets, rules, five calls and two marks; a sixth call saved successfully as format version 2 and remained paused afterward. Added a guarded emulator snapshot utility for repeatable evidence.
- Candidate **0.2.0-alpha02 / code 2** remains offline and debug signed. Its SHA-256 is `801f9655556dfa572af1618ff657bfcca240d5db24bbf0ac256cec8403d0a34d`; size 37,690,400 bytes. Signature identity matches alpha01. No Internet/API key is included in the app, and no paid generation or infrastructure provisioning occurred.
- Build/lint pass with **0 errors and 1 KAPT/KSP warning**. Source and dependency checks pass. Cloud CI, physical phones, TalkBack, broad upgrade/fault/device/performance cases and production signing remain unverified.

Next slice: build native private-room/session/reconnect flows against the tested protocol, then continue the planned badges, onboarding, UI/audio/accessibility and release work. Keep the table awake during active play and keep computer identities clear in every ticket/award view as part of the gameplay review. The hosting choice remains open; local work can continue. See the full plan for the unchanged production scope.

Update this ledger after each milestone with exact commands, app/service revisions, artifact hashes, observed behavior, and remaining limitations. The broader APK goal remains unfinished until the production gates in the plan are satisfied.
