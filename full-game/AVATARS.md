# Avatar identity and verified wins

Eight original Canvas illustrations use stable numeric IDs: 0 Sun, 1 Mango, 2 Chai, 3 Peacock, 4 Lotus, 5 Ladoo, 6 Kite and 7 Moon. They are local artwork with no photo upload, generated-image request, license dependency or runtime download. Labels and selection marks accompany the art; choices remain editable before dealing.

Offline setup keeps a choice for each seat. Rematches copy the round's human choices, and explicitly labelled computer opponents have their own avatars. Online registration sets a profile avatar. A lobby change updates only the authenticated member, resets that member's ready flag and changes the profile used for future rooms. Current rounds and their results retain the avatars at the deal, including after a member leaves.

The server locks the profile before the room for an avatar command, updates both in one PostgreSQL transaction, and stores the ordinary idempotent command receipt. Duplicate/old receipts cannot roll the latest avatar back. Changing an avatar in an active round is rejected. Profile deletion redacts both names and avatars from members, current/finished rounds and stored command receipts. A visible celebration refreshes its identities on an accepted update without creating another celebration.

## Save and protocol compatibility

- New offline/engine saves use round format 3. Readers accept formats 1, 2 and 3; older missing avatar fields become Sun (0). Tickets, calls, marks and rules are preserved. A later save writes version 3. No SQLite schema change is required.
- New service snapshots use protocol envelope 2, including round-player avatars and the avatar command. HTTP route names remain `/v1/...`. Existing stored rounds are read and normalized without changing their game result; no PostgreSQL schema migration is needed.
- The new client accepts legacy envelope-1 cached snapshots and receipts. A stale receipt still cannot overwrite newer state. Avatar changes are available only for envelope-2 lobbies. Use the alpha08 app and service together for the full feature.
- Earlier APKs/services are not forward-compatible with the new serialized fields. Do not roll back to alpha07 over a version-3 save or connect alpha07 clients to an alpha08 service. Preserve a pre-upgrade backup when conducting rollback drills. Public deployment and a production version-negotiation/upgrade policy remain release gates.

## Celebration event rules

`WinMoment` derives its display entirely from already verified standard/custom awards at the latest committed draw. It cannot change winners, points, ticket eligibility or draw order. Ties remain ties; multiple prizes on one call are grouped. The card provides ticket inspection and dismissal, remains readable after its finite decoration ends, and does not cover the fixed caller controls.

Offline calls must finish saving while the game table is foregrounded. Online calls must pass the existing new-live-call acceptance gate. First reconnect snapshots, catch-up, stale receipts, restored rounds and Hear again do not create another win. Pause/cancel/navigation clear transient celebration state; it is not persisted. Background transitions cancel sound separately through the shared audio owner.

Confetti uses 18 noninteractive particles and a 1.5-second animation. Reduced motion/system motion-off show the still message immediately; slower system scales are capped at approximately the same wall duration. The component-clock check verifies completion, not frame timing on physical hardware. Exact device observations and remaining acceptance belong in `ALPHA08_VALIDATION.md`.
