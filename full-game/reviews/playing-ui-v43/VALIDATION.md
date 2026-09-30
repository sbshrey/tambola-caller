# Tambola Jalsa v43 playing-screen validation

Release: https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha43-playing-ui
Source: c3ad729 on shrey/tambola-jalsa. Built from a clean managed checkout; unfinished native Bingo changes are not included.

## Changes

- Latest and recent calls above the tickets; latest call remains replayable.
- Labelled Tickets / Board tabs with selected-state highlighting. Board takes the full playfield; ticket pagination survives switching tabs.
- Circular per-ticket claim controls and prize progress based only on marked numbers already called. Existing remaining/total prize availability and server claim validation are preserved.
- Confirmed correct marks charge the power ring; existing activation rules are preserved.
- Confetti bursts only for the current player's confirmed prize event, respecting reduced-motion settings.

## Evidence

- CoinRoundLayoutTest: 2 tests passed (English, Hindi at 200% font size). Covers all 90 board cells, six-ticket pagination, tab return to the same page, stable ticket bounds during marks/calls/recovery, power targeting, claim quota states and non-clipped prize text. Also checks that uncalled marks do not charge prize progress.
- 49 client tests passed with zero failures/errors.
- Optimized publicBeta build succeeded with existing Firebase and ad configuration. Lint: 0 errors, 175 warnings; this is not a warning-free build.
- Package and signing certificate match v42; version increased to 43. Exact metadata is in published-updater.json.
- Published v42 -> v43 updater test passed in 44.327 seconds, including download verification and Android installation confirmation. Installed APK SHA-256 matches the GitHub asset.
- Profile-retention test passed in 16.477 seconds: wallet, six-ticket preference and session persisted; authenticated purchase/refund succeeded; owned test profile deleted.
- Emulator screenshots are included for English and Hindi large text.

No physical-phone acceptance or sustained gameplay/load test was performed. The server and hosting configuration were not changed. Existing installations require Android confirmation to install the update.
