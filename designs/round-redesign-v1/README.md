# Playing round redesign — design review v1

Status: saved concept and motion previews, with native implementation in progress after the user resumed from the saved design. The full goal remains active: finish design, validation, implementation, tests, server/client compatibility and GitHub release. Figma publication remains externally blocked as recorded below; it is not claimed complete.

## Review artifacts

- `round-preview.mp4`: 16-second animated walkthrough, 960×540, 24 fps.
- `round.png`, `power-ready.png`, `power-active.png`, `claim.png`: stills.
- Matching `.svg` files: editable vector/text import sources for Figma.
- `video-contact-sheet.png`: frames decoded from the actual MP4 at 1, 4, 6 and 11 seconds, reviewed for the four states.
- `render_preview.py`: reproducible renderer; uses Pillow and imageio-ffmpeg. The local helper dependency is under ignored `full-game/.test-workspace/design-runtime`.

## Proposed behavior

1. Persistent number board on the left. Current call and continuously draining countdown above it, with five recent calls in a small strip.
2. Two compact tickets stacked on the right. No full-card re-entry animation after a mark. Only the touched number responds; confirmed marks and ticket positions remain stable.
3. Top-right power slot shows the next server-selected random power and progress toward five distinct correct manual marks. At five marks it glows; one tap applies it to the last-played eligible ticket, named explicitly in the button. The motion example illustrates Auto-Dab; additional Shield and bonus SVG/PNG states show ready and armed behavior. No power chooser or bottom power/HP section.
4. Header shows player and remaining-prize counts. Details stay optional.
5. Per-ticket Claim opens a compact six-choice panel with scheme patterns and amounts. The board and call clock remain visible. Claim panel state persists across calls; patterns are explanatory, not eligibility hints.
6. Proposed cadence: eight seconds per call, down from ten, with a smooth deadline-based countdown. This is a design proposal, not a change to current server timing. A reduced-motion state must retain readable numeric timing and static ready emphasis.
7. The preview distinguishes claim sent from later server confirmation. The remaining-prize count stays at six: one confirmed winner does not close a category with three winning places. Per-prize availability updates after confirmation.

## Prize recommendation

Current version-2 pool allocates 10% to each of Early Five, Corners and the three lines, and 50% to Full House. For the fictional 3,000-coin pool shown, those are 300 and 1,500 coins. Recommend retaining this split: equal 500-coin schemes would reduce the Full House reward and increase early awards. Any change must be explicit, versioned and fixed before tickets are bought, with settlement/replay tests. No economy change has been made.

## Authoritative code findings

- `ClaimArena.kt` currently places powers in the existing dock and opens the number board separately. The desired permanent board and top-right power layout require restructuring this screen.
- `MatchPowers.kt` currently draws a random power only when the fifth correct mark lands, stores up to two powers, and consumes Shield automatically on a false claim. Showing a known next power in advance and single-tap behavior require a deliberate server-authoritative rules/protocol change, not just changing the icon.
- `OnlineViewModel.mark` routes Power-room marks through `command(RoomAction.Mark(...))`. `OnlineArena` derives enabled state from broad `pending/busy` flags. In `ClaimArena` landscape, the recovery content replaces the recent-call header while a command is pending; it does not resize the ticket area. The hand and Claim controls are disabled in that interval. This header replacement and interaction interruption are plausible contributors to the perceived refresh; actual ticket remounting has not been demonstrated. Confirm with frame/recomposition and latency measurements; preserve durable retry, exactly-once marks and server authority while isolating mark feedback.

## Figma access blocker

Existing product file: https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/

The first read confirmed the existing pages and Roboto fonts. Subsequent component/library reads returned: "You've reached the Figma MCP tool call limit on the Starter plan." No new Figma nodes were created. These SVG sources are not a substitute claim that the requested Figma deliverable is finished. Resume Figma composition and motion export once access is restored; preserve the existing pages.

## Remaining completion gates

- Editable composition in Figma and design review of the concept/video.
- Resolve power targeting, queued inventory, Shield semantics, next-power privacy and version compatibility; preview all three powers.
- Validate compact/two-ticket layout on small landscape phones and at large English/Hindi text, plus safe portrait fallback and reduced motion.
- Implement stable per-cell marking and profile its behavior under latency, reconnection and rapid taps.
- Implement server-authoritative new power preview/activation and approved call cadence with replay, retry and claim fairness coverage.
- Native playtests, actual server/client integration, APK upgrade validation, and GitHub source/APK publication. Respect ongoing host sessions during any server rollout.

## Design validation follow-up

`validate_preview.py` passes five groups of checks: legal ticket structure; isolated fifth-cell change with the entire second ticket pixel-identical; persistent claim geometry across the new call at eight seconds; per-prize availability only changing after confirmation; Shield/bonus arming without Auto-Dab behavior; and six decoded-video snapshots matching the intended scenes. See `validation.json` and `validated-contact-sheet.png`. These prove the illustrative design, not native performance.

Figma access was rechecked on the next goal continuation and still returns the same Starter-plan tool-limit error. No retry loop or alternative account was used. Native implementation remains behind the user's explicit Figma-and-video-first sequence. Restoring Figma tool allowance is the outstanding external dependency.

## Resumed design: six tickets and prize availability

The user resumed from the saved design and explicitly requested easy play with six tickets, plus pagination arrows. Preferred placement: a 48-unit-wide right rail. At the concept size this preserves the full 48-unit ticket-row height, while a bottom rail would remove ticket height. Three pages show 1–2, 3–4 and 5–6 of 6; arrows are disabled at the boundaries. No calls or marks automatically change the visible page. See `six-ticket-preview.mp4`, three `six-tickets-page-N.svg` files and `six-ticket-validation.json`. The video shows next, next, previous, previous and preserved marks on return. This is a layout preview, not a released APK change.

Claim cards now show remaining winning places out of the total, e.g. `2 of 3 places left`, and label the amount as a shared coin pool. Current rules derive target winners from the room size: version 2 uses ceil(players/10), at least two and no more than players. Thus the fictional 24-player room uses three places per category. Do not hardcode two in the native implementation.

The server's current-call tie window can admit additional qualifying claimants after the nominal target is reached. Required native labels: remaining/total while below target; `Ties still open` when at/above target but not closed; `Full` only when closed; `You already claimed` for the owning player when applicable. Count unique awarded players, not ticket count or unconfirmed local submissions. The remaining-category count decreases when a category closes, not whenever any claim succeeds.

Native implementation must preserve page and ticket identity across calls, retries and marking; use server-confirmed availability, and retain the chosen ticket/prize panel during new calls. New calls must not auto-page or add off-page marking hints. Six-ticket play also needs a rapid-mark/latency check against the proposed eight-second interval before that cadence is accepted.

## Round results continuation

The user proposed an end-of-round summary with all players, their prizes, Play again and Back to lobby. `results-preview.mp4` and `results-your-rank.svg`, `results-leaders.svg`, and `results-rest.svg` preview this in the saved visual style. The user explicitly prefers a scrolling ranked list for results, superseding the paged-list proposal. Personal winnings stay visible on the left; the right list scrolls to reveal the owning player in their actual rank position, with a mint-highlighted row. Players are sorted by descending total prize winnings; equal totals share rank with stable ordering within ties. Both actions remain fixed. The example roster and amounts are fictional; these previews do not demonstrate live settlement.

Native requirements: enter the results surface when the round ends, retain a finalising state until settlement is confirmed, distinguish cancellation/refunds from winnings, show actual allocated prize shares and power bonuses separately, retain explicit computer labels, and keep every player reachable by scrolling within the ranked list. Do not pin or move the owning player out of rank order. Auto-scroll to their row once when confirmed results first appear, preserving manual scroll position on subsequent updates. Long names and multiple prizes need accessible detail without obscuring rank and total winnings. Play again retains the last ticket count but requires a visible cost confirmation before entering a paid round. Friends replay must follow existing host/room authority, not promise an unsupported automatic rematch.

`render_results.py` validates all 24 sample players are represented and personal results/actions stay pixel-identical while scrolling. Native result rendering, settlement integration and replay are still pending.

The first native slice adds per-prize availability to `TicketPrizePicker` in English and Hindi, including open ties and already-won states. It uses confirmed awards and does not modify claims, prize amounts or settlement. Unit coverage exercises partial quotas, oversubscribed same-call ties, subsequent closure and ownership. This source change is not a new APK release; native layout validation remains required before release.
