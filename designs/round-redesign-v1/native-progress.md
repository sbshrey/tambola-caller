# Native implementation progress

## Claim availability and results concept

Implemented the claim-card remaining/total label using confirmed awards, plus ties-open, full and already-claimed labels. English and Hindi strings label amounts as prize pools. No server rules, timing, balances or claim decisions changed.

Validation performed:

- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`: successful; 33 tests, zero failures/errors. Includes four new availability tests for partial quotas, tie windows including oversubscription, ownership precedence and distinct-player counting.
- `python full-game/tools/check-localization.py`: passed, 890 resources with English/Hindi key, plural, placeholder and Unicode parity.
- `render_results.py`: generated editable SVG/PNG states and a 10-second scrolling results video. Geometry checks pass and the highlighted-own-rank still was visually inspected.

Not yet verified: native claim-card layout on small screens/large text, full round redesign, scrolling results integration and settled reward totals, replay entry/confirmation, server-selected upcoming powers, marking responsiveness under latency, cadence, Figma publication and a public APK release. The debug APK is build evidence only and is not the shareable Internet Beta APK.

Results direction supersedes the initial paged-list suggestion: descending total prizes won, equal totals share rank, stable tie ordering, one initial scroll to the highlighted own row. Subsequent updates must preserve user scrolling. Personal summary and replay/lobby actions stay fixed. Two-ticket-per-page arrows remain the gameplay design for six tickets.
