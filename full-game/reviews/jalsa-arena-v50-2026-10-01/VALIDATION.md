# Tambola Jalsa v50 arena review

## Visual review

The v49 public beta was played on the emulator with a one-person Tambola quick room. Its two-ticket arena showed readable numbers and live calls, but an empty recent-call area and little feedback about ticket progress. A one-ticket page in the same layout would reserve half its height as blank space. A one-person Bingo quick room was opened after the Tambola room finished, with 44 identified computer players. A called number was marked and visibly changed state. Its 5×5 card stretched across a wide rectangular area. By call 42, the fifth recent-call chip wrapped and pushed the claim button below the visible arena; see the [v49 screenshot](bingo-v49-overflow.png).

The v50 review fixtures use fictional QA data. They show the revised [single-ticket Tambola stage](tambola-single-ticket.png) and [Bingo card with pattern chase](bingo-live-card.png). The new Bingo card has square cells and a gold outline for a called but unmarked number. The claim action remains visible beside the card. In the Tambola fixture, the one ticket fills the short stage, with its claim progress adjacent to the claim ring and a marked/ready summary below it. On taller screens the same one-ticket layout adds a recent-call stage.

## Checks

- Seven emulator tests in `CoinRoundLayoutTest` and `BingoOnlineAccessibilityTest` passed, covering six-ticket navigation, large Hindi text, board/claim visibility, one-ticket alignment and square Bingo cells. The first one-ticket assertion incorrectly required a separate recent-call panel even on a short display; it was corrected to verify the full-height ticket there.
- The first screenshot pass found that the Tambola progress caption could be clipped and that Bingo's pattern panel hid the claim action. Both layouts were revised, retested and visually inspected.
- All 34 Android debug unit tests, debug lint, publicBeta lint and the optimized publicBeta build passed. English/Hindi parity passed for 983 resources.
- The prepared v50 APK has package `io.github.sbshrey.tambola.game.beta`, versionCode 50, size 31,675,054 bytes and SHA-256 `65a6bc26bedb715aa87b03eccd53383c87485b8cf757064e2787004b5a7bbf2a`. Its signing certificate SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c` matches v49.

Record the GitHub asset digest and v49-to-v50 update result here after publication. Physical-phone acceptance remains separate from emulator evidence.
