# Similar-game research and implementation audit

Objective: review similar games and implement improvements that make Tambola Together easier to use and more enjoyable. Latest user direction: a simple lobby without scrolling or long gameplay explanations; users should learn by playing.

This audit covers the current local Android implementation. It does not turn the repository's separate production-release plan, Store signing, hosting uptime or physical-device performance gates into claims that they have passed.

## Research basis

- [Earlier hands-on browser review](../friends-replay-2026-09-27/README.md): Tambola Online guest entry, host/join, party code, manual calling, visible pending claim state and interruptions around the playfield. The report explicitly distinguishes observation from claims about server correctness.
- [Publisher comparison](../recognizable-prizes-2026-09-28/README.md): Octro's standard/private play, nLife's invite/direct-mark/board/contextual-social features, and Bingo Blitz's distinct power states and limits. Native competitor installation was not verified.
- All implementation, pattern diagrams and UI styling are original. Competitor art, source code and names are not incorporated into the product.

## Requirements and implementation evidence

| Requirement | Current implementation and evidence |
| --- | --- |
| Enter a game quickly without reading a tutorial | Compact lobby groups room choice, 1–6 tickets, cost, Play and Friends; longer help is opened explicitly. [Lobby review](../compact-lobby-2026-09-28/README.md). |
| Main lobby and purchase flow require no scroll | Main lobby, code entry, shared-link invitation and current six-prize picker have no scroll container. Long optional rosters, help and account details remain scrollable for access to their complete content. [Code-entry review](../friend-entry-2026-09-28/README.md), [invitation review](../invitation-review-2026-09-28/README.md). |
| Learn the prize choices during play | Six prize choices use original schematic patterns, with localized accessible rules; they do not inspect marks or hint which claim is ready. [Prize review](../recognizable-prizes-2026-09-28/README.md). |
| Powers are understandable when unavailable or active | Picker distinguishes absent inventory, discarded tickets, active Auto-Dab, armed bonus and already-used powers. Pure state-transition tests and native fixtures cover the states. [Lobby/power review](../compact-lobby-2026-09-28/README.md). |
| Friends can coordinate without cluttering tickets | Four optional preset reactions use the existing feedback slot, expire, yield to claim/win feedback and are rate-limited/authenticated by the server. Native/API-peer integration covers delivery, expiry and reconnect suppression. [Reactions review](../friend-reactions-2026-09-28/README.md). |
| Joining does not require guessing the host's mode | A definitive pre-charge mismatch permits one durable corrected request; ambiguous failures retain exact retry semantics. Actual room mode appears in the waiting room. Both native join directions, restore, one debit and refunds pass. [Mode review](../friend-mode-2026-09-28/README.md). |
| Keep the accepted game rather than replace its rules | Current `RoomEconomy.kt` retains ten-second calls and 50-player capacity for version 2; `ClaimArena.kt` uses two tickets per page; `RoomService.kt` rejects uncalled marks. `CoinPool.kt` retains six fair prize pools, 50,000 beta coins and seven increasing daily rewards. `MatchPowers.kt` retains five unique manual marks per drop, two inventory slots, Shield, 15-second Auto-Dab and a 25% bonus. Domain/server regression suites cover these rules. |
| Preserve purchase, claim and recovery safety | Reactions cannot alter wallets or tickets; mode changes happen only after the specific confirmed rejection; fresh requests are persisted before sending. Server tests verify unchanged wallets on rejection, exactly one debit and receipt replay. Native tests verify activity restoration and refunds. |
| Support English/Hindi and larger text | Resource parity checks plus native portrait/landscape fixtures, including 200% text. Final screenshots are retained with each review. |
| Deliver a runnable artifact | Current debug APK at `app/build/outputs/apk/debug/app-debug.apk`, source changes and reproducible tests. Individual reviews identify exact historical candidates; the final audit records the current hash. |

## Validation interpretation

Emulator layout checks and authenticated test clients prove the described behavior within their tested scope. They do not measure human enjoyment, prove two physical phones work on mobile data, or establish production performance. The design choices are evidence-informed improvements, not a claim of superiority over every competitor.

The public APK/service have not been upgraded by this work. Reactions require the updated local server; the updated client hides them when the optional route is unavailable. The no-scroll layout and mode-recovery changes are client changes. Public deployment and retained serialized-record rollback requirements remain separately documented.

## Final local candidate validation

- Debug APK, instrumentation APK and Android lint build successfully.
- JVM suites: 58 domain, 43 client, 197 server and 21 Android local tests, all passing (319 total). Protocol has no standalone test sources.
- Gateway: two tests pass. English/Hindi resource validation passes for 869 resources.
- Final combined native run: 38 lobby, code-entry, shared-invitation, recovery and reaction cases pass. Eight prize-picker cases require a separate rerun with the explicit system-font matrix argument; the initial combined invocation omitted it and failed only their setup assertion. Logs are in the invitation review folder.
- Current debug APK SHA-256: `2c66069c5c40510f50ceb4ca429b0b817fa5f9178255e3cfe10b2e8668d4eccd`.

The requested research-informed usability improvements are implemented locally, including the latest no-scroll lobby direction. Separate physical-device, public deployment and release acceptance work remains outside these local validation claims.

The corrected prize-picker invocation passes all eight cases at real system 200% text. Final native coverage totals 46 passing cases across the combined run and corrected matrix. The owned temporary PostgreSQL fixture and dedicated emulator were stopped after validation; no public services were changed.
