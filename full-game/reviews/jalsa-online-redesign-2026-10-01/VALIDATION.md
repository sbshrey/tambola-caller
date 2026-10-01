# Tambola Jalsa v47 implementation review

## Scope

- Reviewed the v44 home, Bingo entry, waiting room, live card and Tambola arena screenshots. The old home used two plain light cards; Bingo exposed a local Practice button and online quick rooms could start with one human plus simulated players.
- Reworked the home and Bingo presentation around live play. Tambola retains its established ticket, number board, claim and power layouts, while its waiting room now distinguishes a real opponent from a countdown.
- New app purchases opt into `realPlayersOnly`. The server isolates these queues from older simulated-seat queues, waits for two human members, and refunds unmatched entries after two minutes. Existing saved and older-client rooms retain their historical behavior to avoid changing an ongoing paid round.
- Local offline gameplay source and saved history remain for data compatibility, but the current app navigation does not enter local games.

## Validation completed before release

- Kotlin/Android builds and test compilation passed.
- Domain and client unit tests passed.
- The complete 219-test PostgreSQL server suite passed on a disposable local PostgreSQL 16 cluster with its backup/restore tools configured. An earlier test-setup attempt lacked `TAMBOLA_PG_BIN`; the complete rerun passed.
- Four added integration cases prove the two-human start and timeout/refund behavior for Tambola and Bingo. Both changed server test classes passed after those cases were added.
- Three native emulator tests passed on the final layout: home and Bingo navigation, Hindi portrait card selection at 200% text, and Hindi landscape live card and claim choices at 200% text. A landscape screenshot also confirmed the Friends label after the entry panel was made scrollable.
- English/Hindi resource parity passed for 969 resources. All 34 debug app unit tests, debug lint and publicBeta lint passed. The optimized v47 publicBeta APK built successfully.
- The v47 APK retains the beta application ID and the previous APK's signing certificate (`55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`). Its 31,672,842 bytes hash to `d9175727cce385d3f649865b9adecd6af8e7565fb5663f3fa3d849d568682827`.

## Hosted and updater checks

- Commit `60b2aaf3101474a9339e3071f042b69c9428fb24` was pushed to `shrey/tambola-jalsa`. The installed Windows host upgrade preflight found no unfinished rooms. Its upgrade retained a fresh database backup pair, applied migrations, and reported the new server process ready.
- The [public host verification](host-verification.json) passed over `https://play.thefinxperts.com`: readiness, retry-safe purchases, two-human starts with no computers for both games, and deletion of all disposable QA profiles. It did not play either game to a final claim.
- The [v47 prerelease](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha47-online-multiplayer) serves `tambola-beta-v47.apk`; GitHub reports the same 31,672,842 bytes and SHA-256 digest as the prepared asset.
- The installed v44 publicBeta on the emulator detected the published update from Settings, downloaded it, offered Android's install confirmation, and installed and launched v47 over the existing package. `dumpsys package` reported versionCode 47 and versionName `0.47.0-alpha47-internet-beta`. The process did not clear package data; a named profile was not present in this emulator fixture, so profile identity continuity was not independently observed.

Emulator screenshots and source tests do not establish physical-phone frame rate, touch comfort, or network behavior. A complete public two-player round through final settlement was not run for this release.
