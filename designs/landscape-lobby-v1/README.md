# Tambola Together — landscape design review

Created 27 September 2026. Review the design before another APK installation.

Current implementation: the Game night design is packaged in [alpha23](../../full-game/reviews/game-night-alpha23-2026-09-27/README.md), and the [fictional gamer handles are deployed](../../full-game/reviews/gamer-handles-server-2026-09-27/README.md) to the PC server. The native review sections below retain the scope of their earlier checkpoints. Browser examples remain simulated design data.

- [Editable Figma design](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=4-2696): the **Joining, timer & settings · v2** page has nine screens imported as vector and text layers. Page 1 is preserved. This is a design board, not a wired Figma prototype or production component library.
- [Clickable browser review on this computer](http://127.0.0.1:8877/review.html): simulated interactions and motion, with screen selection and a feedback download. The loopback preview must be running; it is not a public link or a multiplayer server.
- `Tambola-Landscape-Review.svg` is the portable vector board. `frames/` holds each 1280×720 screen. `build_design.py` regenerates these originals and the prototype's demo data.

## Direction

**Game night** uses deep indigo, coral actions, cream tickets, gold coins and friendly illustrated number balls. **Daylight club** is a lighter cream/mint alternative. Artwork is original SVG; no competitor assets or downloaded fonts are included.

The welcome has one primary Play action and optional personalization. Returning players go straight to the lobby. The lobby keeps the ticket count, total price and Play together; it avoids lists of unrelated game modes. A compact countdown shows the table and frozen round pool. Gameplay gives the left edge to prizes/players, the top to recent calls, and most of the screen to the player's own tickets.

| Screen | Review focus |
| --- | --- |
| 01 Welcome | Play immediately; optional name/avatar; no tutorial carousel |
| 02 Landing | One obvious destination; 1–6 tickets and visible coin cost |
| 03 Table countdown | Staggered arrivals, a draining timer, pool and prize count |
| 04 Playing | Manual dabs, two readable tickets, a Claim button per ticket |
| 05 Claim | Ticket-specific prize picker; short invalid-claim feedback |
| 06 Your win | Personal settled reward and a prominent next-round action |
| 07 Daylight direction | Compare the lighter visual treatment |
| 08 Ticket page 2 | Explicit up/down navigation; no forced scrolling or automatic page switch |
| 09 Settings | Five personal switches and one language choice |

## Interaction and implementation handoff

- Keep all screens in landscape. Android source declares `sensorLandscape` and game category, and removes the gameplay-only orientation override. [Source checks](../../full-game/reviews/landscape-source-2026-09-27/README.md) and the subsequent [native review](../../full-game/reviews/game-night-lobby-2026-09-27/README.md) passed. Launch, Settings and return to home remain landscape on the API 30 emulator at normal and 150% system text. Physical-device/OEM behavior remains unverified; the installed alpha22 APK is unchanged.
- Reference frames are 1280×720, not a requirement to scale the entire native UI uniformly. Respect device cutouts, navigation insets, large text and at least 48 dp tap targets. When two tickets cannot retain readable cells and touch targets, show one ticket per page. Never compress six tickets onto one screen.
- Persist dabs, selected ticket page and confirmed quantity. Only the owner sees their ticket numbers. A claim always carries the selected ticket and prize; actual eligibility, ties and credit remain server authoritative.
- Freeze the 6/7/8-prize schedule when sales close. Real gameplay must expose taken/closed prizes and the pending claim state, then settle winnings at the server's call boundary. The design's immediate result transition is illustrative, not an implementation of settlement timing.
- Use short ball arrival and dab animations, a restrained countdown pulse and one win celebration. Honor reduced motion. Avoid continuous particles during ticket play. Keep sound optional and avoid downloading media on the critical Play path.
- Show a brief reconnect state without discarding tickets or marks; preserve exact purchase/claim retry semantics from the existing app. These error/reconnect states still need detailed visual variants before release.
- Keep computers labelled. Coins are free virtual currency without purchases, transfers or redemption.

## What the browser preview does

Welcome → lobby → simulated countdown → tickets; 1–6 quantity selection; a valid six-ticket strip covering 1–90 exactly once; two tickets per page; manual marks restricted to revealed numbers; marks retained across page changes; per-ticket prize picker; invalid/valid claim examples; a personal result; optional player name; subtle motion/reduced-motion toggle; feedback download.

The default example has your three tickets plus three each for an example human and two computers: a 1,200-coin pool, five 120-coin small prizes, and houses of 420 and 180. Selecting one or two tickets reduces this example to six prizes. This example table cannot reach the 24-ticket threshold for eight prizes.

The 12-second joining preview introduces the fictional handle ChaiChamp, then labelled computer seats NeonNinja and LuckyMango at dealing time. New rounds begin without called numbers and reveal one every five seconds. The circular timer drains smoothly, recent numbers update, and ticket-page changes preserve the running deadline. Reduced motion uses stepped progress. Opening Playing directly retains an initial example snapshot for quicker design review.

It is not connected to the game server. Coins reset across illustrative flows, and duplicate claim/purchase/settlement behavior is not modelled. Settings switches and language selection change local demo state; they do not produce audio/haptics or translate the entire preview. Avatar selection and the data-disclosure link remain illustrative. Use the actual app/server for functional acceptance.

## Validation performed

- Generated nine SVG screens and checked the demo strip has exactly 1–90 once, five numbers in each ticket row and fifteen per ticket.
- Verified native Figma vector/text layer import, reopened the saved file in a fresh tab, and visually inspected the full board and selected 1280×720 lobby group. Saved `figma-board.png` and `figma-lobby.png` as direct browser evidence. [Open the lobby selection](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=3-1215).
- In the browser, exercised welcome/landing, six-ticket purchase selection, countdown, paging to tickets 5–6, preserved a manual dab after leaving/returning to its page, and selected a prize from ticket 1.
- An incomplete full-house claim returned “Not complete yet”; the example Early 5 accepted 120 coins and showed a 1,320-coin illustrative balance after the default purchase. No browser console errors were reported during these checks.
- One-ticket selection showed one visible ticket, a 1,000-coin pool with five 100-coin small prizes and a 500-coin house, and hid the second house.
- Claim-dialog keyboard verification: focus enters the prize options, Tab/Shift+Tab wrap inside the picker, the table behind it has no tab stops, and Escape returns focus to the same ticket's Claim button. Reducing ticket quantity also bounds the selected ticket/page to the remaining hand.
- Corrected animation transform interference and preview sizing after visual inspection. `preview-lobby.png` records the corrected landing.
- JavaScript syntax and repository whitespace checks passed. The initial Figma/browser review required no APK installation. The later isolated native review is recorded below; phone rotation, audio and frame performance remain separate checks.

## Native implementation checkpoint

Game night is the working direction while the optional visual preference remains open. The welcome and returning-player lobby now use the design's palette, original ticket/ball artwork, finite entrance animation, six ticket choices, explicit cost and one Play action. Optional player setup preserves the ticket choice; the form keeps its input and actions above the landscape keyboard. Pending purchases show receipt recovery instead of a second purchase action.

[Native evidence and screenshots](../../full-game/reviews/game-night-lobby-2026-09-27/README.md) record nine UI tests at each of two system text sizes, English/Hindi fixtures, 15 JVM tests and lint with no errors. A separate `.uireview` package coexists with alpha22; it is not a release candidate or a new multiplayer acceptance run. The subsequent arena implementation is recorded below. Continue reviewing in Figma and the browser without reinstalling the main app.

The [joining, timer and Settings checkpoint](../../full-game/reviews/table-experience-2026-09-27/README.md) adds actual roster arrival animation, a server-deadline call ring and the minimal Settings screen. Seventeen native tests pass at each of normal and 150% system text, including manual tickets/claims and preference persistence across Activity recreation. The original alpha22 APK and installed server are unchanged. The server's new fictional gaming handles compile but are not deployed.

The [native table and prize-picker review](../../full-game/reviews/game-night-arena-2026-09-27/README.md) carries Game night into live play: cream tickets, coral ticket-level Claim buttons, warm dabs, rounded page controls and a compact player card. Prize patterns remain recognizable after settlement; larger text shows explicit house ranks. The adaptive picker separates prize names from coin values and fits eight choices on the tested landscape window. Its native screenshots can be reviewed without replacing alpha22. Optimized Wi-Fi packaging and relevant real-round acceptance follow this UI checkpoint.

The v2 Figma [countdown](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=4-2696) and [Settings](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=4-3653) were visually inspected and the saved page was verified after reload. `figma-countdown-v2.png` and `figma-settings-v2.png` record these views. The browser's arrivals, advancing calls and keyboard Settings controls were checked; `preview-joining-evidence.json` retains observations and console errors. Pointer mapping was inconsistent in the narrow Codex panel, so browser pointer acceptance is not claimed. Native full-row Settings controls passed independently.

## Inspiration

Reviewed public design references for composition and interaction ideas, without importing their artwork:

- [Srinivasulu Palle — Bingo Lobby Grid view](https://dribbble.com/shots/16205118-Bingo-Lobby-Grid-view): a recognisable game lobby with a clear play destination.
- [Benny Chew — Bingo Games Platform Game Lobby](https://dribbble.com/shots/4813814-Bingo-Games-Platform-Game-Lobby): playful visual hierarchy and game identity.
- [Dribbble game onboarding](https://dribbble.com/search/game-onboarding): concise first-run presentation.
- [Figma UI kit guidance](https://help.figma.com/hc/en-us/articles/24037724065943-Start-designing-with-UI-kits): editable design workflow.

For the Android orientation handoff, consult the [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16) and [orientation/resizability guidance](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability). Device/OEM behavior still requires testing.

## Run or regenerate

From this folder:

```powershell
python -X utf8 build_design.py
python -m http.server 8877 --bind 127.0.0.1
```

Open `http://127.0.0.1:8877/review.html`. Use the screen selector to compare states; use Feedback to download local notes. Figma comments remain the primary place to review the static design. Iterate the sources, Figma board and native implementation together as feedback arrives.
