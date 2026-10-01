# Tambola Jalsa v51 large-text correction

The v50 arena review found a real v49 Bingo overflow and introduced square cards, call highlighting and a pattern chase. After v50 was published, additional screenshot review at 200% Hindi text found a cropped current-call label in landscape. A new portrait fixture then exposed a more serious layout fault: the status text squeezed into a narrow column and the card disappeared below the viewport. The released v50 APK was preserved and v51 increments the version for these fixes.

The corrected [landscape screenshot](bingo-hi-landscape-200.png) shows the complete current-call label inside its ball. The corrected [portrait screenshot](bingo-hi-portrait-200.png) shows the current call, status, History, Claim and all 25 card cells in the same viewport. The portrait controls now use separate status and action rows; the claim action has a short localized label at large text sizes.

All eight emulator tests in `CoinRoundLayoutTest` and `BingoOnlineAccessibilityTest` passed after the fix. The 34 Android debug unit tests, debug lint, publicBeta lint and optimized publicBeta build passed. English/Hindi parity passed for 983 resources.

The prepared v51 APK retains package `io.github.sbshrey.tambola.game.beta` and signing certificate SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`. It is 31,675,054 bytes with SHA-256 `6d274e27e8480123e053bfbb8bb102de6b2e4c3dcae9a98a1bef5e2cdd8e515c`.

Record the GitHub asset digest and v49-to-v51 updater installation here after publication. Physical-phone acceptance remains separate from emulator evidence.
