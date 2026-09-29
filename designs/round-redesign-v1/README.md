# Playing round redesign — design review v1

Status: concept and motion preview only. Native gameplay is unchanged. The full goal remains active: Figma and video first, then validation, implementation, tests, server/client compatibility and GitHub release.

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
7. The preview distinguishes claim sent from later server confirmation. The remaining-prize count stays at six while pending and decreases only at confirmation.

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

`validate_preview.py` passes five groups of checks: legal ticket structure; isolated fifth-cell change with the entire second ticket pixel-identical; persistent claim geometry across the new call at eight seconds; prize count only changing after confirmation; Shield/bonus arming without Auto-Dab behavior; and six decoded-video snapshots matching the intended scenes. See `validation.json` and `validated-contact-sheet.png`. These prove the illustrative design, not native performance.

Figma access was rechecked on the next goal continuation and still returns the same Starter-plan tool-limit error. No retry loop or alternative account was used. Native implementation remains behind the user's explicit Figma-and-video-first sequence. Restoring Figma tool allowance is the outstanding external dependency.
