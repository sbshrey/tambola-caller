# Tambola Jalsa v48 onboarding follow-up

The [v47 implementation review](../jalsa-online-redesign-2026-10-01/VALIDATION.md) records the server-authoritative two-human matchmaking redesign, public host validation, and older-app update checks. This version changes only Android home onboarding and its English/Hindi copy; the v47 server remains deployed.

## Release checks

- Android debug and optimized publicBeta builds, 34 unit tests, debug/publicBeta lint, three native emulator UI tests and 970 English/Hindi resource checks passed.
- With no player profile on the emulator, the [home](home-fresh-player.png) said “Create your player to join live tables.” Tapping the Bingo card opened [player setup](player-setup-from-bingo.png) before a game screen. The Tambola card uses the same gate in the app navigation. Registered-player routing still uses the existing online destinations.
- The prepared 31,673,082-byte `tambola-beta-v48.apk` has SHA-256 `a9d4eebb42dab22bd44686b29568fe3e5a7c5f5200632a9e3ceaa2076ec48a55`. It retains package `io.github.sbshrey.tambola.game.beta` and signer SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`, matching the installed beta lineage.

GitHub release digest and v47-to-v48 update detection remain to be checked after publication. Physical-phone acceptance and a complete public round through final settlement remain open.
