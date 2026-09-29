# Native round layout checkpoint

The coin-game landscape arena now keeps the 90-number board on the left, the
last five calls above it, two tickets on the right, and pagination beside the
tickets. Player/prize counts and a compact power control occupy the header.
Feedback and recovery have reserved space so they do not move the ticket grid.
The current-number ring uses the existing server deadline and hides during
recovery. This checkpoint does not change the server call interval.

The power control applies a ready Auto-Dab or prize bonus in one tap to an
eligible visible ticket, preferring the most recently marked visible ticket.
It pulses when available and respects reduced-motion preferences. Existing
Shield behavior is still passive. Server-selected upcoming-power previews and
explicit Shield activation remain work for the next protocol change.

`CoinRoundLayoutTest` exercises English at 1x and Hindi at 2x, all 90 board
numbers, six-ticket pagination, stable ticket bounds across marks/new calls/
pending state, retained marks and visible-ticket power targeting.
`RoundRecoveryUiTest` checks actual text layout for clipping in both languages
and orientations at the emulator's 2x system font scale, exact retry/reconnect
callbacks, retained page/marks/bounds and the restored countdown ring.

Validated on 2026-09-29 with `tambola_lobby_review` at emulator-5582:
debug app and instrumentation APK builds succeeded; the six layout/recovery
tests passed. Both `ExpandedArenaUiTest` tests passed after correcting their
fixture to mark a called number (the domain rejects uncalled numbers).
English/Hindi localization parity passed for 892 resources. Screenshots were
visually inspected at normal English and 2x Hindi text sizes.

The screenshots are native emulator fixtures, not public multiplayer evidence.
They show fictional data with tickets 5 and 6 selected. The fixture timer text
is illustrative; it is not evidence that the server cadence has changed.

Release gates still include server power preview/activation, pending-mark
latency behavior, the compact claim panel, native round-summary integration,
cadence validation, public server deployment and a compatible signed APK.
The Figma export remains blocked by its previously observed quota; saved local
design/video artifacts remain the implementation reference.
