# Multi-game app direction

Status: active implementation goal. The 75-ball Bingo domain, native practice lifecycle, shared home/profile navigation, card paging, local save/resume and results flow are implemented and emulator-tested. The online backend includes isolated matchmaking, persisted rooms, private snapshots and shared-wallet settlement. Native online lobby/play and saved-session integration now pass an emulator-to-PostgreSQL purchase, mark and restore test. Remaining online results/failure/accessibility acceptance and multi-game deployment/release gates are still outstanding. Existing shipped branding remains Tambola Jalsa under AGENTS.md.

## Product and home

Recommended umbrella name: **Jalsa Games** (Hindi: **जलसा गेम्स**). Keep Tambola and Bingo as game names. This is a naming proposal, not a claim of store or trademark availability.

The home screen contains the shared player/avatar, coin balance, settings, and two large game choices: Tambola and Bingo. Each choice leads directly to that game's compact lobby. Remember the last game and ticket/card count separately. Do not add explanatory paragraphs, empty coming-soon tiles, or a scrolling game catalogue for two games. Preserve readable controls at large accessibility font sizes, allowing an accessibility fallback if necessary.

Use bottom previous/next arrows with a visible card/page count during play. Support 1–6 cards, but choose the number visible per page from usable screen size; a 5×5 Bingo card must retain comfortable number targets. Do not squeeze six cards onto one screen. Retain the results list sorted by total prize won, highlight and scroll to the current player, and provide Play again and Home actions.

## Variant decision

Selected first variant: 75-ball Bingo, 5×5 B-I-N-G-O cards, free centre. The game tile and lobby identify it as 75-ball. One line includes rows, columns and either diagonal; additional patterns are four corners, X and blackout. Cards use the standard 15-number range per column. One to six cards per player; cards are individually shuffled and distinct within a hand.

For 75-ball, start with clearly displayed winning patterns; show each prize's remaining/total claims. Pattern order, prize allocation and tie rules must be defined before online play. Do not automatically apply Tambola's early-five or row prizes to Bingo.

Implemented practice rules: each pattern targets two distinct winning players; one player can claim each category only once, regardless of card count. The call that fills a category's quota remains open to all same-call ties until the next draw. The final 75th call has a full claim window before completion on the following timer tick. Practice points total 100: Line 20, Four Corners 20, X 20, Blackout 40. Each category's pool is shared exactly among its winners with deterministic integer remainders. These points never directly credit the online wallet. The server integration must define and validate coin settlement separately.

Practice opponents use generated fictional personas with the computer flag retained, varied 30–50-player populations and one to six cards per opponent. Saves include the entire shuffled draw, position, marks and accepted claims; decoding rejects invalid ownership, out-of-range marks and impossible historical claims. Native AtomicFile save/restore is integrated. Restored rounds pause until the player resumes; corrupt saves require explicit confirmation before resetting practice.

## Code boundaries

The existing domain Round, Ticket and Prize types and server RoomRecord are Tambola-specific. Add separate Bingo card generation, draw validation and pattern evaluation. Share application navigation, identity, wallet ledger, update delivery, audio/settings and reusable presentation components without rewriting working Tambola rules.

Add an explicit game/variant identifier to new matchmaking, room creation, invitations, persistence and result records. Missing identifiers on existing records/clients mean Tambola. Matchmaking must never mix games. Old clients must not receive Bingo rooms. Bind prize claims and wallet transactions to the correct room and variant; the server validates marks, wins, ties and exactly-once settlement.

Keep the requested varying 30–50 round population and 1–6 ticket/card assignments as game-specific matchmaking settings. Revalidate timing and prize economics for Bingo instead of copying Tambola assumptions.

## Delivery and compatibility

Continue all work on `shrey/tambola-jalsa`. Keep Android application IDs, signing certificates, profiles, wallet balances, repository identity and `tambola-beta-v<versionCode>.apk` asset names unchanged. A display-name change must upgrade the installed app normally.

Implement and validate the chosen Bingo rules, then practice play, then server-backed rooms and the shared home. Release the renamed app when both choices lead to playable games. Tests must cover card validity, winning patterns, invalid claims, cross-game isolation, ties, refunds, reconnect/resume, old Tambola saves/clients, six-card controls and APK upgrade data preservation.

Commit and push each validated slice to GitHub. Publish a new versioned APK only after end-to-end acceptance; this document is not an APK or server release.

## Goal acceptance checklist

- [x] Bounded home with immediately playable Tambola and Bingo choices, shared profile/settings, clear Home/Play again navigation, and separate remembered card counts.
- [x] Improved native play layouts with readable 5×5 Bingo cards, one to six cards with arrows, current call/history, pattern previews and available/total prize claims.
- [x] Complete Bingo practice rounds, save/resume, mark validation, prizes and ranked results with current-player highlighting.
- [ ] Server-authoritative Bingo rooms sharing authenticated identity and wallet, variant-isolated matchmaking/invitations, progressive 30–50-player rosters with 1–6 cards, persisted state, reconnect and exactly-once purchase/refund/settlement.
- [ ] Old Tambola saves, rooms, invitations and v42 clients remain compatible; no Bingo state leaks into Tambola rooms.
- [ ] Rules tests, persistence/migration tests, server transaction/isolation tests, native six-card and navigation acceptance, and upgraded profile preservation all pass.
- [ ] Deploy validated server, publish same-signer versioned GitHub APK, verify existing in-app updater installs it, and record physical-device versus emulator validation boundaries.

Ludo, Snakes and Ladders, and Poker are later additions, not nonfunctional tiles or completion requirements for this first multi-game release. No live-release change is implied by a source-only checkpoint.

## Native practice checkpoint

See `reviews/bingo-native/VALIDATION.md` for emulator evidence. This is source progress only; the published v43 APK still contains Tambola only. Remaining unchecked delivery gates must be verified for the eventual multi-game release.

See `reviews/bingo-server/VALIDATION.md` for the backend checkpoint and remaining integration boundaries. The backend source checkpoint does not deploy migration 010 or make online Bingo available in installed apps.

See `reviews/bingo-online-native/VALIDATION.md` for native online purchase, six-card paging, marked-card restoration and reconnect evidence. Public v43 remains unchanged until release acceptance is complete.
