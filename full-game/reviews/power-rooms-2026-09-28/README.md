# Alpha38 Power rooms review

Power rooms add server-owned, private marking and free drops after five unique correct manual dabs. Shield, 15-second Auto-Dab and a 25% final prize-share bonus have equal drop odds, a two-power inventory and per-ticket use limits. A false claim consumes a usable Shield or discards only that ticket. Classic retains warning-only false claims. Normal tickets have no called-number hint styling; wrong marks are rejected immediately.

Quick Power lobbies add practice seats over their twelve-second countdown. Stable names use `Player` plus six digits, with a Practice label and a simulation disclosure. Friends rooms contain invited players only. The recent-number arrow opens all 90 board cells without scrolling. Fresh wins have visual/audio feedback and optional offline-device speech; number calls retain priority.

## Verification before deployment

- Full PostgreSQL server suite: 187 passed, no failures/errors/skips. This preceded the final Player-ID naming and profile-power-redaction changes. The focused final run covers those changes: 5 power-room tests plus 7 profile-deletion tests passed.
- Final domain 58 and client 39 unit tests passed. Android debug unit suite: 18 passed.
- Eight native Compose tests passed on the owned API30 emulator: English/Hindi number-board layouts, 200% text, landscape/portrait, all 90 cells visible, history behavior, power activation, Shield use and per-ticket discard. Captured screenshots are included.
- Public gateway tests: 2 passed, including bounded routes and WebSocket forwarding.
- Release lint passes. Public APK and host acceptance records are added after verification below.

The optional audio regression run exposed an older test setup that expected automatic custom awards while creating a manual-claim round. It was stopped, corrected to explicitly create an automatic-award round, and given an exhausted-draw assertion. Its final rerun result is recorded separately. No production audio behavior was changed for this test correction.

## Release constraints

Firebase is configured with diagnostics off by default. The real AdMob IDs remain configured, but the shareable APK has live rewarded ads disabled: consent/app readiness and a real signed Google reward callback remain unverified. Offline speech depends on an installed local English/Hindi voice; the emulator check does not establish voice availability on every phone. Public acceptance uses this PC and the owned emulator, not a physical cellular phone. The temporary server depends on this PC staying awake and online.
