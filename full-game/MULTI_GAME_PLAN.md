# Multi-game app direction

Status: proposed expansion; Bingo is not implemented or released. The Bingo variant question is pending. Existing shipped branding remains Tambola Jalsa until the multi-game release.

## Product and home

Recommended umbrella name: **Jalsa Games** (Hindi: **जलसा गेम्स**). Keep Tambola and Bingo as game names. This is a naming proposal, not a claim of store or trademark availability.

The home screen contains the shared player/avatar, coin balance, settings, and two large game choices: Tambola and Bingo. Each choice leads directly to that game's compact lobby. Remember the last game and ticket/card count separately. Do not add explanatory paragraphs, empty coming-soon tiles, or a scrolling game catalogue for two games. Preserve readable controls at large accessibility font sizes, allowing an accessibility fallback if necessary.

Use bottom previous/next arrows with a visible card/page count during play. Support 1–6 cards, but choose the number visible per page from usable screen size; a 5×5 Bingo card must retain comfortable number targets. Do not squeeze six cards onto one screen. Retain the results list sorted by total prize won, highlight and scroll to the current player, and provide Play again and Home actions.

## Variant decision

Recommended first variant: 75-ball Bingo, 5×5 B-I-N-G-O cards, free centre. Alternative requested for clarification: 1–25 Bingo with five completed lines. These require different draw and win rules, so the implementation must explicitly name its variant.

For 75-ball, start with clearly displayed winning patterns; show each prize's remaining/total claims. Pattern order, prize allocation and tie rules must be defined before online play. Do not automatically apply Tambola's early-five or row prizes to Bingo.

## Code boundaries

The existing domain Round, Ticket and Prize types and server RoomRecord are Tambola-specific. Add separate Bingo card generation, draw validation and pattern evaluation. Share application navigation, identity, wallet ledger, update delivery, audio/settings and reusable presentation components without rewriting working Tambola rules.

Add an explicit game/variant identifier to new matchmaking, room creation, invitations, persistence and result records. Missing identifiers on existing records/clients mean Tambola. Matchmaking must never mix games. Old clients must not receive Bingo rooms. Bind prize claims and wallet transactions to the correct room and variant; the server validates marks, wins, ties and exactly-once settlement.

Keep the requested varying 30–50 round population and 1–6 ticket/card assignments as game-specific matchmaking settings. Revalidate timing and prize economics for Bingo instead of copying Tambola assumptions.

## Delivery and compatibility

Continue all work on `shrey/tambola-jalsa`. Keep Android application IDs, signing certificates, profiles, wallet balances, repository identity and `tambola-beta-v<versionCode>.apk` asset names unchanged. A display-name change must upgrade the installed app normally.

Implement and validate the chosen Bingo rules, then practice play, then server-backed rooms and the shared home. Release the renamed app when both choices lead to playable games. Tests must cover card validity, winning patterns, invalid claims, cross-game isolation, ties, refunds, reconnect/resume, old Tambola saves/clients, six-card controls and APK upgrade data preservation.

Commit and push each validated slice to GitHub. Publish a new versioned APK only after end-to-end acceptance; this document is not an APK or server release.
