# Tambola Jalsa v57 playtest follow-up

The Game Studio playtest checklist was applied to the native Android app: first playable action, waiting-room feedback, playfield visibility, called-number marking, claim overlay, results, and navigation between the two games. Browser-specific tooling was not used for this native app. The base phone/server evidence and unresolved physical-device gates remain in the [v54 review](../jalsa-physical-beta-v54-2026-10-01/VALIDATION.md).

## Findings and changes

- Both quick waiting rooms displayed incorrect singular counts (`1 players`, `1 cards`). Tambola and Bingo now use localized plurals. Ticket counts in the Tambola waiting summary, friends entry, and replay confirmation use the same singular/plural resources.
- Entering Bingo while a Tambola table was active produced a generic service failure, even though the server correctly rejected a second game. The client now checks the active game before attempting a new purchase and explains how to return to it. A matching fallback handles the server's `other_game_active` response. The reverse Bingo-to-Tambola path has the same guidance.
- The v54 live Tambola playfield kept the ticket and call strip visible together. Two called numbers were dabbed; the displayed marked count rose to two and Early Five progress to 40%. The claim picker opened over the ticket and left the current call strip visible. The completed round later reached results with rank and winnings.
- A v56 emulator build reproduced the corrected `1 player` countdown and showed the specific cross-game message when Bingo was attempted during a new Tambola round. The displayed wallet stayed at 50,100 coins on that blocked action. v55 and v56 were local unpublished candidates; the final signed follow-up is v57.

## v57 validation and distribution

- English/Hindi key, placeholder, plural and Unicode parity passed for 987 resources. Debug app unit tests passed. Public beta lint and the optimized publicBeta APK build passed with `-PtambolaFirebase=true` and collection off by default. The root physical-ticket caller's 71 tests and static build also passed after the invite-link update.
- The v57 APK installed over v56 on an emulator. Android reported versionCode 57 and the QA guest wallet remained 50,100 coins after launch.
- Prepared asset: `tambola-beta-v57.apk`, 31,678,178 bytes, SHA-256 `efd81ed293bec4322d61f21e069b6992e7b245df3740599d9d606ef4d0f3091b`. The signer SHA-256 remains `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`.
- The intended published tag is [`full-game-alpha57-gameplay-polish`](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha57-gameplay-polish). Verify its uploaded asset digest against the prepared APK before treating it as distributed.

The physical OnePlus remains locked and reported v51 during this follow-up. v57 play and update acceptance on that device, a two-human round, and unattended host availability are still open.
