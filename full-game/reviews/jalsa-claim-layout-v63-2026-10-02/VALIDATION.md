# Tambola Jalsa v63 beta follow-up

## Why this update exists

The [v61 physical playtest](../jalsa-phone-beta-v61-2026-10-01/VALIDATION.md) completed a two-player Tambola friends round. The short landscape claim picker showed six prize cards but clipped their titles, progress and place counts, preventing a usable claim. v63 replaces the tall progress rings in this compact embedded picker with readable title, progress, coin amount and available-place text in each card. The larger non-embedded picker retains its existing presentation. The close control is drawn as a vector X so enlarged fonts do not clip it.

The visible app now requests `FLAG_KEEP_SCREEN_ON` through lobbies and results as well as active rounds. This addresses the user's request to keep the game awake. Android may still lock during an installer or when the app is no longer visible; the physical phone reports a device-admin five-minute limit on the system timeout. The user's USB stay-awake setting remains enabled.

## Verification

- The debug app unit tests passed. `CoinRoundLayoutTest` passed all three instrumented cases on an emulator set to the phone's short landscape height, including six-ticket navigation and Hindi at 200% text scale. It asserts all six prize choices and titles are inside their card bounds and none of the claim text is clipped.
- The exact signed v63 publicBeta APK was built with `-PtambolaFirebase=true`; `:app:assemblePublicBeta` and `:app:lintPublicBeta` passed. Telemetry remains opt-in by default. The update preparation check accepted the v61-to-v63 package/signature/version transition: versionCode 63, package `io.github.sbshrey.tambola.game.beta`, 31,694,562 bytes, SHA-256 `72818af5ffe7e4d380ac10c99a380b2e7739eec39a1bcf146d21c515087bdb9c`, signer SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`.
- The signed v63 APK installed over v61 on the emulator without removing the QA profile or wallet. A quick Tambola table waited ten seconds, then started with one person and labelled computer seats. The live arena showed its ticket, call strip, board switch, power and Claim. The v63 claim picker on this signed build showed all six titles, progress percentages, coin pools and place counts within their cards. The Android window manager reported `KEEP_SCREEN_ON` for the foreground game activity.
- Public readiness returned HTTP 200 and protocol 9 during validation.

## Beta boundary

This is a candidate for a small, monitored beta with the operator-hosted service kept online. The server still runs on the operator's Windows PC; sustained availability, off-host recovery, wider physical-device coverage and full account recovery after uninstall or device loss remain open. Virtual coins are not cash. Computer players are explicitly labelled; the app must not imply they are people. Live rewarded ads remain disabled pending consent and server-verified settlement.

Phone install and in-app updater acceptance, if performed, are recorded separately below before treating this APK as fully distributed.
