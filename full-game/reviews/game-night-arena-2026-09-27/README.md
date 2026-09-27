# Game night ticket table and prize picker — 27 September 2026

The online coin table now continues the welcome/lobby's indigo, cream and coral design. The header contains the current ball with its deadline ring, four recent numbers and a compact round summary. Owned tickets keep large numbers, distinct blank cells, warm dab colour, a local coral Claim button and explicit page arrows. One or two tickets remain visible according to the available height; calls never move the selected page.

The left rail retains all eight possible prizes and opens the existing details. Pattern icons remain recognizable after a prize is taken, while struck-through labels/amounts show settled status. Larger text uses pattern icons and 1/2/3 house ranks. The compact player card opens the complete roster, including computer labels, without changing marks or the selected ticket page.

The ticket-specific prize picker is now a separate composable with clear prize names and coin amounts. On the tested landscape window, all eight choices fit without scrolling at normal and 150% text. Smaller windows retain a scroll fallback. Existing eligibility, same-call ties, house ordering and selected-ticket submission rules are preserved; the server remains authoritative. The broader compatibility UI keeps its existing palette.

## Iteration evidence

The first extended theme-contrast check failed: coral text on an inherited high surface had 4.18:1 contrast. The Game night palette now defines the complete surface family, and its foreground/background pairs pass the existing 4.5:1 text checks. Ticket ink is also checked on cream, blank, unmarked and dabbed fills. The original failing XML and build transcript are retained.

An initial native pass completed 17 tests at each text size, but screenshot review showed that claimed prizes became indistinguishable ticks with larger text. `initial-layout/ambiguous-prizes-hi.png` records that state. The final revision retains the pattern icons and house ranks. The manual selected-claim regression now renders the actual coin-game styling instead of the older compatibility table.

## Final validation

- Exec **30760 exited 0**. Gradle assembled the isolated review app/test pair and completed JVM tests and lint in **45 seconds**.
- **17 native tests passed at system font 1.0 (83.090 seconds), then the same 17 passed at 1.5 (85.353 seconds)** on the owned API 30 emulator. System font returned to 1.0. The suite includes actual Activity navigation/preference recreation, lobby/result controls, roster arrivals, ring expiry/recomposition, manual dabs, retained pages, chosen-ticket claims and the full player list.
- Enhanced layout assertions verify that all eight prize choices are displayed, remain inside the picker and retain at least 48 dp touch targets. English/Hindi screenshots at both system text sizes were retained; final normal and enlarged layouts were visually inspected.
- **15 JVM tests passed**, including contrast for all three palettes and every ticket fill. Lint reports **0 errors and 116 warnings**, with the same issue-category counts as the preceding checkpoint. See `validation-summary.json`, `junit/`, `lint-results-debug.xml` and `gradle-validation.txt` for the exact source and result identities.
- The installed alpha22 APK hash was identical before and after both native runs. No installed host or main-app update occurred.

| Artifact | SHA-256 |
| --- | --- |
| Isolated `.uireview` app | `3dfcfb5b2edac94df40b6c74e3956586f3ab398b5a47fbd1c60b4ec1c8d3dd08` |
| Isolated `.uireview.test` | `3145624692f682607aa225fadf0d2c495434e3e1c2d8d855884188b12e5fa70f` |
| Preserved alpha22 | `05dfaa405629fec0a5ab57234941eca1229b9f2cb13034a496e3549759bdb4b4` |

[Native ticket table](normal/manual-tickets-selected-claim.png), [prize picker](normal/manual-prize-picker.png), [eight Hindi choices at enlarged system text](font150/manual-coin-prize-picker-hi.png).

## Reproduction and scope

Build from `full-game` with the isolated `tools/ui-review.init.gradle` override, assembling the debug app and Android tests and running `:app:testDebugUnitTest :app:lintDebug`. Run the archived `run-native-review.mjs` from that directory, then repeat with `--large-text`. The runner guards the owned emulator and exact `.uireview` packages, compares the installed alpha22 hash and restores system font scale.

The review package is separate from the installed alpha22 app. These are fixture-driven native UI checks and actual Activity preference/navigation checks, not a new live multiplayer or optimized-APK acceptance run. Server code, game economy, persistence and installed host were not changed in this checkpoint. The existing Figma v2 design remains the visual reference; the screenshots here show the actual adaptive native layout.

Next: package an optimized Wi-Fi candidate from the completed design, repeat the relevant real-round purchase/claim/recovery/result checks, and then validate physical phone input, audio, Wi-Fi and frame timing. Purchase-burst latency, host reboot behavior, production signing/hosting and release materials remain open. This checkpoint does not establish production readiness.
