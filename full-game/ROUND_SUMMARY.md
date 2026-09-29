# Round results

Completed coin rounds created with the protocol 9 `roundSummary` capability open a dedicated results screen. The ranked player list scrolls; the header and Lobby / Play again actions stay in place. It initially opens at the current player's highlighted row without moving that player ahead of higher winners. My rank returns to that row and respects reduced motion.

New protocol 9 rounds use an eight-second call interval. The redesign capability opts clients into this pace; older clients still receive ten-second version 2 rounds (five seconds for version 1). Persisted ten-second rooms remain readable and keep their existing deadlines. The UI uses the server's interval and deadline rather than a hardcoded local countdown. RoundSummaryTest checks no early draw, one draw at the exact deadline, the next eight-second deadline, six owned tickets, and deadline retention after service restart.

Ranks sort by prize coins plus bonus coins, descending. Equal totals share competition ranks (1, 1, 3); names and player IDs break display-order ties. Returned entry coins do not count as winnings and appear separately for the current player. Each row lists the prizes won and any bonus.

The server derives every player's totals from the same calculation used for settlement. Totals are exposed only after the round finishes. Active rounds do not expose this map or other players' private power state. The client validates the complete roster and checks its own summary against its coin settlement. Older rooms omit the new fields; matchmaking separates incompatible clients, and friends receive an update-required response before a purchase when capabilities differ.

Play again asks for confirmation using the previous round's ticket count, including all six tickets. Insufficient funds disables the purchase and directs the player to choose fewer tickets in the lobby. Friends use the existing explicit-consent successor-round flow.

## Validation, 2026-09-29

- 19 focused PostgreSQL server tests passed: RoundSummaryTest, MatchPowersServerTest, FriendReplayTest, LargeMatchTest. These cover terminal totals, pool conservation, bonus settlement, service restart, compatibility, and replay consent.
- 47 client tests and 34 Android JVM tests passed (the unchanged client task reused its passing outputs).
- Debug app and instrumentation APKs built successfully.
- RoundSummaryUiTest passed both native emulator tests in 6.902 seconds: English normal text and Hindi 200% text, 50-player scrolling, owner rank 46, stable footer, displayed prize, My rank, and six-ticket replay confirmation. Hindi also exercises reduced motion.
- English/Hindi resource validation passed for 910 resources.
- The first server invocation failed because the credential path was incorrectly relative to the repository root; the corrected invocation used the isolated full-game test database and passed all 19 tests.

Screenshots: [English](reviews/round-summary-2026-09-29/round-summary-en-1.png), [Hindi large text](reviews/round-summary-2026-09-29/round-summary-hi-2.png). Native screenshots use fixture data; server tests independently validate real settlement calculations. This is source and emulator evidence, not a hosted deployment, physical-phone test, or published APK update.
