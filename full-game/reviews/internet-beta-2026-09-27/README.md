# Alpha24 Internet beta

The separate, non-debuggable Android beta connects to the installed PC service through public HTTPS/WSS. A publisher-controlled directory lets the same APK follow a replacement Quick Tunnel without changing saved profile identity. The Internet bridge uses the existing service; this work does not migrate or replace its database/runtime. The installed host's recorded source at acceptance was `cfdb236830a376b13d96d2cecf521032e3b441c9`.

The released APK is **29,116,477 bytes**, version **24 / 0.24.0-alpha24-internet-beta**, package `io.github.sbshrey.tambola.game.beta`. It installs alongside the Wi-Fi app and uses the existing development certificate. SHA-256: `dbdd4a5a7764868feb43a5ce802544a9a2891014069b20f1888076edf0c97367`. Its installed emulator copy and GitHub release asset digest matched. All 270 voice clips are included. [Build identity](build-validation.json).

## Observed acceptance

- Optimized assembly, **37 client tests**, **15 Android unit tests**, and lint completed. Lint has **0 errors / 118 warnings**.
- The loopback gateway test checks blocked routes, body limits, stripped forwarding headers, and bidirectional WebSocket upgrade/data delivery. Public HTTPS probes verified protocol 4 readiness, blocked internal/non-game paths, and unauthenticated-purchase rejection.
- Stopping only the owned tunnel produced a new public hostname; the supervisor republished the directory and fresh public readiness succeeded. The installed Windows login shortcut restarts the bridge after this user signs in. This is not a boot-before-login or controlled Windows-reboot acceptance.
- The optimized APK played a **78-call round** against one passive HTTP QA peer and two labelled computers, with six owned tickets covering all 90 numbers once. All **seven prizes / 1,800 coins** settled. Independently calculated ticket shares matched **1,350 native winnings**, **450 computer winnings**, and the native **2,250 final wallet**. Cold process recovery retained **20 marks**, and six-ticket preference survived the result. [Round evidence](full-round/coin-release-journey.json), [results screenshot](full-round/coin-release-results.png).
- That full-round instrumentation run **did not pass as a whole**: its post-round replay purchase reached the lobby, then the external driver asserted that Cancel was enabled before connection/purchase completion enabled it. Both QA profiles were deleted by cleanup. The original failure and screenshot are retained under `full-round/`.
- The driver now waits for a control to be enabled before tapping. A separate focused native test on the **identical APK** passed, **JUnit OK (1 test), 18.276 seconds**: three tickets reduced the wallet **1,500 → 1,200**, enabled cancellation restored **1,500**, and a cold restart retained that wallet, the player profile and the three-ticket preference. The QA profile was deleted. [Focused report](purchase-refund/coin-release-journey.json), [test transcript](purchase-refund/instrumentation.txt). The complete full-round driver was not rerun after this narrow test-only wait correction.

## Earlier attempt

The first driver stopped the app while a claim receipt was still pending and waited only for the table or Resume control. The actual UI offered an enabled **Reconnect & check**, preserving the original request. The driver did not use it and its cleanup could not delete a profile with an unresolved command. The peer was deleted automatically. Later, visible UI retry resolved the old claim with the finished-round response; the one native QA profile was deleted through its normal confirmation, and a fresh welcome with 1,500 coins was verified. No app data was reset. `initial-driver-failure/` preserves the failure and cleanup screen tree. The driver now follows that explicit receipt-retry path during cold recovery.

## Scope

This is small-group Internet testing on a dedicated API30 emulator using public certificates and no adb reverse. It does not establish physical mobile-data, phone touch/audio/frame performance, competitive balance, sustained capacity, uptime or production signing. Quick Tunnels have no SLA; the PC must stay awake, signed in and online. The profile is separate from the Wi-Fi app. The signed APK is unchanged across the retained public checks; later edits affect the supervisor, driver, documentation and evidence only.

[Download prerelease](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha24-internet-beta) · [Operating guide](../../../docs/INTERNET_BETA.md).
