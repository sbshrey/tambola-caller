# Native Bingo practice checkpoint

Unreleased source on shrey/tambola-jalsa; the public v43 APK is unchanged.

Implemented:
- Two-game home with shared player/avatar/wallet profile controls, settings, and remembered last game.
- Separate Bingo card-count preference, 1-6 cards and bottom page arrows.
- 75-ball practice using the existing tested Bingo domain: varied 30-50-player rounds, saved calls/cards/marks/claims, pause/resume and ranked results.
- Landscape card area, current-call replay, call history, 2x2 pattern previews and remaining/total claims. User-confirmed wins trigger finite confetti; reduced motion disables it.
- Atomic bounded save reads and explicit corrupt-save reset, isolated from Tambola and online wallet storage.
- Shared audio settings and lifecycle pause; update prompts are gated during active Bingo play.

Validation:
- Debug APK and instrumentation build succeeded.
- Four emulator tests passed in 30.471 seconds: six-card navigation; activity recreation plus independent ViewModel disk restoration; final-call marking/claim/result/replay with a different card count; corrupt-save confirmation; Hindi at 200% text scale with playable controls and all four pattern choices.
- Two existing Tambola lobby/profile regression tests passed in 16.187 seconds after extracting the common profile component.
- Screenshots captured on the owned emulator include English home/play/results and Hindi large-text home/card/patterns.

Boundaries:
- Fresh-ViewModel disk restoration was tested; this is not a physical-device or forced-process-kill test.
- Final-call UI uses an owned persisted fixture. Full multi-player practice simulations are covered by domain tests from the prior Bingo domain checkpoint.
- Server-authoritative Bingo, wallet settlement, cross-game matching/invitations, reconnect and multi-game OTA acceptance are not implemented or claimed by this checkpoint.
- No new public APK or server deployment is part of this source checkpoint.
