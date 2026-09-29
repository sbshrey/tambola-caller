# Native implementation progress

## Current implementation checkpoint, 2026-09-29

The saved design has now been implemented in native landscape play: persistent left board, two tickets with right-side six-ticket pagination, compact claim panel, server-selected power preview and one-tap activation, and durable queued marks. End-of-round standings scroll to the highlighted player's true rank and confirm replay purchases. New protocol 9 rounds call every eight seconds; persisted rounds keep their existing interval. Earlier sections below describe historical checkpoints, not the current remaining scope.

Evidence is in `full-game/ROUND_SUMMARY.md`, `full-game/MARKING.md`, `full-game/POWER_ROOMS.md`, and the native review directories. The cadence follow-up passed 14 focused PostgreSQL tests, 47 client tests, and 34 Android JVM tests. The acceptance driver, hosted server rollout, signed APK build, data-preserving upgrade checks and GitHub v41 publication are now complete. See full-game/RELEASE_41.md for current acceptance scope and the final updater check. Figma publication remains externally unavailable; the user authorized resuming from the saved design and video.

The final cadence build also passed three native tests in 17.228 seconds: pending-mark recovery with an eight-second server clock, six-ticket bounds across marks/calls/recovery, and Hindi 200% text navigation. These are emulator fixtures, not a live release journey.

## Claim availability and results concept

Implemented the claim-card remaining/total label using confirmed awards, plus ties-open, full and already-claimed labels. English and Hindi strings label amounts as prize pools. No server rules, timing, balances or claim decisions changed.

Validation performed:

- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`: successful; 33 tests, zero failures/errors. Includes four new availability tests for partial quotas, tie windows including oversubscription, ownership precedence and distinct-player counting.
- `python full-game/tools/check-localization.py`: passed, 890 resources with English/Hindi key, plural, placeholder and Unicode parity.
- `render_results.py`: generated editable SVG/PNG states and a 10-second scrolling results video. Geometry checks pass and the highlighted-own-rank still was visually inspected.

Not yet verified: native claim-card layout on small screens/large text, full round redesign, scrolling results integration and settled reward totals, replay entry/confirmation, server-selected upcoming powers, marking responsiveness under latency, cadence, Figma publication and a public APK release. The debug APK is build evidence only and is not the shareable Internet Beta APK.

Results direction supersedes the initial paged-list suggestion: descending total prizes won, equal totals share rank, stable tie ordering, one initial scroll to the highlighted own row. Subsequent updates must preserve user scrolling. Personal summary and replay/lobby actions stay fixed. Two-ticket-per-page arrows remain the gameplay design for six tickets.
