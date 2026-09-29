# Brief reactions for friends rounds

The [competitor comparison](../recognizable-prizes-2026-09-28/README.md) identified contextual social messages in nLife's Tambola listing. This implementation adopts four original, fixed positive responses: Good luck, Nice win, Well played and Thanks. It adds no free-text chat, computer-generated social messages or paid interaction.

## Behavior

- Available during active invited-friends coin rounds through the game's menu, only after the optional server route is confirmed available.
- The sender is the authenticated room member. A request contains a preset and the round ID, never an arbitrary sender or message.
- One reaction per player per ten seconds; at most one accepted reaction per second for the whole room. Both limits are enforced inside the room transaction.
- Exact command retries return the stored receipt. Reactions cannot modify tickets, calls, claims, prize shares or wallet balances.
- A reaction remains visible for at most six seconds, uses the existing feedback area and yields to wins, claim feedback, power feedback and the final-call notice. No sound interrupts calling.
- The chooser is localized in English/Hindi, exposes the remaining cooldown and disables sends during disconnected, busy, pending or invalid-session states.
- Reconnect/cold snapshots are silent. The client filters messages older than the connection baseline, clears social state when backgrounded or changing rounds, and verifies round/member/timestamp bounds before displaying responses.
- Profile deletion removes the member's reaction from live and archived room records. Read responses exclude departed members and expired reactions.

## Compatibility and release boundary

GET/POST `/v1/rooms/{code}/reactions` is separate from the strict protocol-v6 room snapshot. Existing clients receive the same snapshot fields and an ordinary `reacted` event type; no message text or sender is added to the event history. The internal room record stores at most one entry per member. New clients treat a missing optional route as unavailable and keep gameplay working on the installed host. Gateway allow-list changes are source-only.

No schema migration is needed. As with other serialized record extensions, downgrading the server after use requires restoring a compatible backup or explicitly removing the new internal `reactions` property from stored room/archive payloads; an old strict decoder cannot read it. This has not been deployed to the public service.

## Evidence

Tests use a newly created PostgreSQL cluster bound to loopback on a free port and the owned `tambola_lobby_review` API30 emulator. They do not use the public host's database, credentials, players or tunnel.

Focused PostgreSQL/HTTP cases cover authorization, sender identity, current-round restrictions, cooldowns, expiry, persistence across service reconstruction, immutable retry receipts, unchanged gameplay/coins and profile deletion. Client tests cover old-host fallback and rejection of foreign actors, rounds, duplicate or expired messages. Native fixtures cover the localized chooser, cooldown and timed disappearance. The public-gateway route/body/WebSocket tests and resource-parity check also pass.

Source, server integration and native fixture evidence do not yet prove a full two-device Android conversation against the new service. Public deployment and physical-phone validation are separate work.

Final local results: 196 server tests, 41 client tests and 21 Android unit tests pass with no failures or skips. Android build and lint pass. All four native reaction fixtures pass (`native.txt`), including Hindi portrait/landscape at 200% text, cooldown and expiry. The isolated PostgreSQL cluster and owned emulator were stopped after validation.

## Native network follow-up

The opt-in `FriendReactionsOnlineTest` now exercises the actual Android ViewModel, HTTP client, room stream, game menu and rendered feedback against an isolated loopback server/PostgreSQL instance. A second authenticated API client represents the invited peer; this is not two physical phones or human playtesting.

The first run used mismatched Classic/Power test clients and correctly failed to join. After matching the fixture modes, the test exposed a real cooldown defect: opening the menu re-anchored its timer to an old server timestamp. Reaction state now retains a monotonic response-time anchor. Both message expiry and menu cooldown use that anchor, including after a menu remount.

`native-online-final.txt`: six tests pass, comprising the live exchange and five UI/timer fixtures. The live case verifies peer-to-Android delivery, Android-to-peer delivery from the menu, expiry, unchanged ticket geometry/content and wallet, continued calls, suppression of a message accepted while backgrounded, and fresh delivery after reconnect. A separate timer regression verifies that an already-expired cooldown does not restart when the picker opens. Android unit tests and lint pass after the fix.

Current debug APK SHA-256: `036d9621c00b46f96128c7b1f2122f49858ec57f78d5ad16fb233f605f718875`. Screenshots `online-reaction-*.png` contain fictional test players. The test deletes both profiles in its cleanup. The public server, database, tunnel and APK remain unchanged.

Usability follow-up identified during setup: joining by code required selecting the host's Classic/Power mode. The [subsequent friend-mode change](../friend-mode-2026-09-28/README.md) handles that definitive rejection safely and shows the actual mode in the waiting room. See its separate evidence and release boundary.
