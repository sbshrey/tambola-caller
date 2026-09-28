# Alpha38 Power rooms review

Power rooms add server-owned, private marking and free drops after five unique correct manual dabs. Shield, 15-second Auto-Dab and a 25% final prize-share bonus have equal drop odds, a two-power inventory and per-ticket use limits. A false claim consumes a usable Shield or discards only that ticket. Classic retains warning-only false claims. Normal tickets have no called-number hint styling; wrong marks are rejected immediately.

Quick Power lobbies add practice seats over their twelve-second countdown. Stable names use `Player` plus six digits, with a Practice label and a simulation disclosure. Friends rooms contain invited players only. The recent-number arrow opens all 90 board cells without scrolling. Fresh wins have visual/audio feedback and optional offline-device speech; number calls retain priority.

## Verification before deployment

- Full PostgreSQL server suite: 187 passed, no failures/errors/skips. This preceded the final Player-ID naming and profile-power-redaction changes. The focused final run covers those changes: 5 power-room tests plus 7 profile-deletion tests passed.
- Final domain 58 and client 39 unit tests passed. Android debug unit suite: 18 passed.
- Eight native Compose tests passed on the owned API30 emulator: English/Hindi number-board layouts, 200% text, landscape/portrait, all 90 cells visible, history behavior, power activation, Shield use and per-ticket discard. Captured screenshots are included.
- Public gateway tests: 2 passed, including bounded routes and WebSocket forwarding.
- Debug and public-beta lint pass. The optimized APK is non-debuggable, uses the existing beta signer, contains no telemetry probe activity or advertising-ID permissions, and has diagnostics off by default.

The optional audio regression run exposed an older test setup that expected automatic custom awards while creating a manual-claim round. It was stopped, corrected to explicitly create an automatic-award round, and given an exhausted-draw assertion. Two older audio tests also referenced the previous Settings text button/volume sliders and a notice absent from the compact lobby. They now use the current setting tags, check retention of existing independent volume values, and explicitly exercise the mixer resume API for audio-focus recovery. No production audio behavior was changed for these test corrections.

## Release constraints

Firebase is configured with diagnostics off by default. The real AdMob IDs remain configured, but the shareable APK has live rewarded ads disabled: consent/app readiness and a real signed Google reward callback remain unverified. Offline speech depends on an installed local English/Hindi voice; the emulator check does not establish voice availability on every phone. Public acceptance uses this PC and the owned emulator, not a physical cellular phone. The temporary server depends on this PC staying awake and online.

## Public acceptance

The idle PC host was upgraded from committed source `2bf58269a5dad26faeb7c390aad8e025aac290e4` after both databases were backed up. No unfinished rooms were present. The new host passed public HTTPS Power-room acceptance: six private tickets, five real ten-second calls, authoritative marks and a free random bonus, private peer state, no future draw order, wrong-mark rejection, activation, retry-safe false-claim penalties and profile cleanup. See `public-powers.json`.

Legacy Classic/rules-v2 lobby and economy checks also passed over public HTTPS, including fixed pools, daily rewards and retry-safe refunds. The backend accepts configured ad intents but never credits an unverified client callback; the APK still hides live rewarded ads. See `public-classic.json`.

The exact release APK passed native public purchase/cancellation/refund, cold-restart wallet recovery and profile deletion on API30, with no adb forwarding. Its SHA-256 is `dde513977d1fa1d52474651c2c9df99881fc6290b5e875fda3e300ca0a159207`. See `native-public-validation.json`, `native-public-journey.json` and `native-public.txt`.

The corrected native audio regression suite passed all five tests (`native-audio.txt`). The final debug build also reran the eight board/power tests successfully; only the separately corrected obsolete audio fixtures failed in that intermediate combined run.
