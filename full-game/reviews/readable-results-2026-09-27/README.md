# Readable results — 27 September 2026

The alpha26 public-round screenshot cuts off **Four corners** and **House three**. Its fixed three-column grid and single-line prize text hide part of those names. This is a finding from our own shipped app, following the [hands-on rival review](../friends-replay-2026-09-27/README.md), not a new claim about rival behavior.

The results cards now use up to three columns according to the available width and the user's font scale. Titles wrap in full, amounts retain their own line, and the shared-win label wraps too. Text is not reduced to make it fit. The existing scroll container keeps all prizes and replay controls reachable when larger text needs more space. The same cards appear in the in-game prize-details dialog. Settlement calculations, purchases and the running PC service are unchanged.

Native checks inspect the actual Compose text layout, including overflow, ellipsized lines and the last visible character. They cover all eight results in English and 150% Hindi, access to Same friends and both alternative actions, and every prize/shared label at 144, 260 and 380 dp widths with 200% Hindi text. These are emulator presentation checks, not physical-device or TalkBack acceptance.

The initial attempts failed all three new checks while the ten existing lobby checks passed. [Failure transcript](native-first-attempt.txt). Removing the equal-height constraint alone did not resolve them. A focused diagnostic then showed a 177-pixel paragraph inside a 159-pixel text box. The final layout lets each card expand and gives centered text the full card content width, keeping its paragraph and measured bounds aligned. The checks retain the original overflow assertions.

The first wrapping-layout run passed **13 tests in 71.537 seconds**. [Transcript](native-prior-spacing.txt). Its optimized APK then passed a complete public 84-call round, all eight payouts, 20 recovered marks and the same-friends/quick-play refunds. [Initial public evidence](public-first-spacing/coin-release-journey.json). Visual inspection of that run showed the bottom payouts partly below the initial viewport, despite being reachable by scrolling. That package was not published. The final refinement reduces only card padding/line spacing and adds an initial-viewport assertion for all eight payouts at normal text size.

## Extended hands-on rival review

Used the computer-use plugin to revisit [Tambola Online](https://tambolaonline.com/play) as the same fictional Guest QA. This was the existing one-person free private room, with no account, messages, money or actual prizes. It does not establish native Android, multiple-player or cross-network behavior.

- Entering the existing code in Join Game rejected the returning host because the game had started. Opening the previously observed direct room link restored the host, ticket and first call. This is a difference between two observed entry paths, not proof that reconnection is universally broken.
- The first attempt to call the next number showed an advertising interstitial and did not advance the counter. After dismissing it, normal button presses advanced the visible counter through all 90 calls. [Caller interruption](rival-caller-interruption.png).
- The old deliberately invalid full-house request remained Claim Sent during calling. At 90/90, a Game Over dialog reported no winners. Thus it was **not awarded**. No valid winning claim was submitted, so this review does not verify the rival's successful-claim path.
- Restart Game worked: the same six-character room code returned to waiting/unlocked, with a new ticket and zero called numbers. This is a useful group-replay feature. [Completed round](rival-finished.png), [restarted room](rival-restarted.png), [visible restarted state](rival-restarted-dom.txt).
- Inspected and cancelled the manual End Game confirmation. Returned to the lobby after replay; the temporary room was not deleted.

Our Same friends flow similarly avoids retyping a code for original members, but preserves the finished round separately and asks each player to confirm their own new virtual-coin purchase. Keeping ads away from ticket marking and giving explicit verified/rejected claim feedback remain product priorities. These observations are not a claim of superiority over every market competitor.

## Final alpha27 candidate

All **13 native checks passed in 61.783 seconds**, including the initial-viewport assertion, English/Hindi results, 200% narrow-card text, payout shares, affordability, and explicit replay quantities. [Final transcript](native-final.txt), [normal-text results](complete-results-en.png), [large Hindi results](complete-results-hi.png), [large Hindi replay controls](complete-results-actions-hi.png). The 15 Android JVM checks and optimized build passed; lint has **0 errors / 121 warnings**. [Build/APK metadata](build-validation.json), [build log](build.txt).

The final APK is version 27 / `0.27.0-alpha27-internet-beta`, package `io.github.sbshrey.tambola.game.beta`, Android 8+, **29,122,389 bytes**, SHA-256 **`4b376998ef917f2d15f247e3bf4cc45524b3ebd0fad33db7a6dc1896d1501ac2`**. Signature verification confirms the existing development certificate, allowing updates over alpha24–alpha26 without uninstalling. The PC service remains source `bf03af542183a8d4e39a596dad9d21449a1100b8`; no server code or database migration changed in this update.

Exact public acceptance for this final APK is running. The earlier successful public round above belongs to the previous spacing candidate and is not substituted for this APK's acceptance.

Physical-phone touch/audio/frame performance, mobile-data reliability, purchase-burst latency, native rival testing, production signing and the editable Figma replay addition remain open. Figma's tool-call limit stopped the previous attempt before writes; no new board edit was claimed or attempted here.
