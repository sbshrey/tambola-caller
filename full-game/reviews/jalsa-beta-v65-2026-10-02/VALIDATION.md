# Tambola Jalsa v65 monitored beta review

## Scope

This follow-up closes two issues found while playing the signed v63 APK on a OnePlus CPH2487 at 2772 × 1240 landscape, 560 dpi. The quick Tambola waiting screen put its computer-player disclosure and Cancel below the short viewport. The live roster and completed rankings showed computer personas by name without identifying them. v65 keeps the waiting scene compact, moves Cancel & refund into its top bar, and labels computer players in the roster, rankings, and other Tambola results. The v64 APK was an unpublished phone candidate used to check the waiting layout; v65 is the release candidate.

## Validation

- The v63 phone round joined a 34-player quick table after roughly ten seconds. Its ticket, call strip, 1–90 board, power control, and all six claim cards were visually inspected. Two called numbers were marked; the ticket and prize progress changed. The saved profile and wallet survived the v63 install.
- The v64 phone candidate showed its 10-second countdown, People: 1, the explicit computer-fill disclosure, and Cancel & refund together. The live 38-player roster showed names with `· Computer`. Installing v64 in place kept the saved profile and wallet. A later in-place v65 installation reported versionCode 65 and resumed the same live room with its ticket, calls, and balance intact.
- `CoinLobbyTest.quickTableShowsTenSecondCountdownAndComputerIdentity` passed on an emulator sized to the phone's short landscape viewport at 200% text scale. It checks that the disclosure and Cancel are inside the lobby bounds. `CoinRoundLayoutTest.singleTicketUsesTheStageAndKeepsClaimVisible` passed with a computer-player roster assertion. `RoundSummaryUiTest` passed both its English and Hindi 50-player cases, including a computer label and replay controls. Its previous confirmation-text expectation was corrected to use the same ticket plural as the app.
- `:app:testDebugUnitTest` passed. `:app:assemblePublicBeta :app:lintPublicBeta -PtambolaFirebase=true` passed. Opt-in diagnostics remain default off. The update preparation script accepted v64-to-v65 package, signature, and version continuity: `io.github.sbshrey.tambola.game.beta`, versionCode 65, 31,694,562 bytes, SHA-256 `4591f601305524a41fb4811adfe596cf7d46ad6cf7430c71e7ee1cf9f226ee5a`, signer SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`.
- The public `https://play.thefinxperts.com/health/ready` returned HTTP 200 with protocol 9. The phone reported `KEEP_SCREEN_ON` in the foreground app and retained its USB charging stay-awake setting. Android's device-admin screen timeout applies outside the game.

The [v61 physical review](../jalsa-phone-beta-v61-2026-10-01/VALIDATION.md) contains two-independent-client Tambola and Bingo friends rounds, accepted Bingo prize settlement, wallet preservation, audio/tap feedback from the user, and mid-round recovery. The [v63 review](../jalsa-claim-layout-v63-2026-10-02/VALIDATION.md) records the six-card claim layout and the v63 physical finding.

## Beta decision

Tambola Jalsa has a clear route into both online games, 10-second quick matching with visibly identified computer seats, tickets/cards and prices, friends tables with two tested independent clients, live calls, marking, claims, ranked results, an in-app update flow, and profile/wallet persistence across signed updates. The phone arena fits the playfield and controls. The user confirmed the audio and tap targets on the physical device. This is suitable for a **small, monitored beta** with the operator-hosted server kept online.

It is **not ready for an unattended public production launch**. The server still runs on the operator's Windows PC; sustained availability, off-host backup and restore, account recovery after uninstall or device loss, and broader physical-device coverage are not proven. The visuals and long-term engagement have not been measured with beta users. Live rewarded ads remain disabled until consent and server-verified reward settlement are complete. Virtual coins have no cash value. No private phone screenshot is included in this review.
