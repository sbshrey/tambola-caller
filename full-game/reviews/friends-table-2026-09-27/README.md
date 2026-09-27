# Friends tables — alpha25 candidate

The primary missing friend-play behavior was coordination: quick play closes ticket sales in twelve seconds and cannot promise that friends reach the same table. This change adds a separate invite-code flow with a waiting room controlled by its host. Quick play remains available.

## Design research and plugin work

Reviewed publisher pages on 27 September 2026, not independently installed/played rivals:

- [Party Tambola](https://www.partytambola.in/play-tambola-online) documents a host opening a room, sharing its code, and automatic calling/claim verification. Its page describes an Android-only multiplayer app.
- [Tambola Online](https://tambolaonline.com/how-to-play-tambola-online-with-friends) documents guest nicknames and joining a private party by code. These are publisher descriptions; they do not establish comparative usability or reliability.

Our change adopts the useful room-code pattern while retaining free virtual coins, manual marking, exact ticket claims, server-authoritative payouts, and saved recovery. No unsupported claim that this is objectively better than every competitor is made.

Used the Figma plugin to inspect the existing Game night design and subscribed Material 3 library, import Roboto text styles and Material button instances, and build an editable friends-room composition. [Figma host waiting room](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=12-2). Native implementation uses the existing GameNightPalette, accessible controls, English/Hindi resources, and two columns in wide windows. The Figma composition is a design reference; native screenshots are the implementation evidence.

## Contract

- `MatchRequest` adds optional `friendTable` / `friendCode`; the existing `/v1/matches` transaction buys tickets, admits membership, and records the exact retry receipt together.
- Private tables are not `matchable`. A profile resumes its existing live coin table before any new purchase, regardless of quick/friends mode.
- Two to eight actual people, 1–6 tickets each; no computers. Host-only start requires every member to be connected. The pool freezes under the room lock; racing entry either joins the pool or is rejected without charge.
- Fifteen-minute waiting deadline; leave/expiry refunds use the existing ledger. Host succession and profile suppression reuse the existing room lifecycle. Once started, five-second calling and claims remain automatic/server verified.
- New fields are omitted when at their defaults. Existing alpha24 quick-play requests and responses preserve their strict wire shape and receipt hashes. Old binaries cannot decode stored **private** tables after this feature is used: retain the upgrade's paired database backups for a coordinated rollback, rather than swapping only the old server JARs.

## Local acceptance

- Initial optimized build, 37 client tests, 15 Android JVM tests, and lint passed.
- 25 real-PostgreSQL focused tests passed: seven friends scenarios, fourteen existing coin-round scenarios, four lobby-index/restore scenarios. The first attempt failed because the dedicated test PostgreSQL instance was stopped; it was started on loopback 55432 and all tests rerun. The installed game database on 55433 was not used by these tests.
- Two native debug tests passed on the dedicated API30 emulator in 34.026 seconds: create/wait beyond quick countdown/copy/refund, buy six tickets/start with an HTTP peer/activity recreation; and native code entry with three tickets, host-only controls, refund. All QA profiles were deleted via the service. This does not claim a cold-process test or a full round for that local fixture.
- Screenshots: `friends-ready-landscape.png`, `friends-guest-waiting.png`, `friends-create-dialog.png`, `friends-join-dialog.png`, `friends-playing.png`. They were captured before the final home-action layout refinement and version increment; friends-room behavior is the same.

Public optimized acceptance and release identity are recorded after the candidate is deployed and tested. Physical phone touch/audio/frame performance, native rival hands-on testing, cloud uptime, production signing, and app-store release remain outside these checks.

Final candidate validation: all 25 server / 37 client / 15 Android JVM tests passed, zero skipped; lint has 0 errors and 120 warnings. Eight existing native lobby/rendering tests passed in 71.286 seconds, including English, Hindi, and 1.5× text. Both friends journeys passed again on the final debug source in 29.621 seconds. `build-validation.json` records the optimized APK identity; native debug checks are not represented as optimized public checks. The final home keeps quick play and friends actions side by side so both remain visible at large Hindi text. See `game-night-welcome-hi.png` and `game-night-welcome-en.png`.
