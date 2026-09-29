# Quick-play rooms with 30–50 players

New clients opt into `largeMatch` for public quick play, in both Power and Classic modes. Each room derives a stable target of 30–50 players from its random room ID. That target is independent of tickets and the committed draw order. Computer seats fill progressively over the first ten seconds of the existing twelve-second countdown. Real players replace computer seats before the start, and additional real players can grow a room to the capacity of 50. The final roster, ticket pool and winner counts lock when the round starts; no opponents appear halfway through play.

Each computer uses three legal tickets and the existing delayed, eligibility-checked claims. There are no invented prize wins, adjusted draws or guaranteed human losses/wins. The public player record retains `computer = true`; the app labels these opponents **Computer**. Countdown avatars show the owner and four recent arrivals while the total reports every seat. Friends rooms remain invitation-only.

## Compatibility and rollout

Protocol 7 adds an omitted-by-default capability flag to match requests and room options. Older installed clients reject more than five computer seats, so they must never be matched into these larger rooms. Legacy requests retain their old room sizes and response protocol. Match selection separates capability and Power/Classic mode. A legacy request attempting to re-enter a large room receives `update_required` before purchasing.

Server deployment must precede publication of the new client APK. Existing active rounds are not rewritten. No schema migration or coin-allocation-policy change is needed; larger rosters naturally produce a larger fixed virtual-coin pool under version-2 rules. This change is source work for the next update, not proof that the installed v40 APK or the public host already offers larger rounds.

## Verification

`LargeMatchTest` uses isolated PostgreSQL schemas to cover progressive filling, stable restart behavior, durable purchase replay, a complete round with legal computer awards, private unrevealed draws, six owned tickets, real-player replacement, 50-human capacity and overflow, old-client segregation, and Classic/Power separation. Existing Power, expanded-match and friends tests are run alongside it. Native small-screen rendering, public-host rollout and APK update acceptance remain release gates.

Validated on 2026-09-29: 58 domain tests, 43 client tests, 19 focused server integration tests (including four new large-match tests), and 33 app unit tests passed with zero failures or skips. `:app:assembleDebug` succeeded. English/Hindi localization checks passed for 890 resources. The isolated test database was stopped after validation; the installed game server was not restarted. The initial run caught a Classic-mode capability restriction; the corrected candidate passed the rerun in both modes.
