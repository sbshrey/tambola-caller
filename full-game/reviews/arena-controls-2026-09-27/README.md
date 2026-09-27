# Readable arena controls — alpha35

Large text could truncate the ticket's Claim label and collapse lower prize rows in a narrow landscape table. Alpha35 measures the claim label, gives the side action enough width, and places it below the numbers on narrow portrait tickets. The label retains the user's text scale. Recent-call numbers now have explicit line heights; the header uses compact call/ticket counts and the English ticket count uses the existing singular/plural resource.

The prize rail scrolls independently of the hand. At large text sizes, rank/pattern markers and amounts fit the compact rail, while full prize names remain in semantics and the prize dialog. A reserved bottom cue indicates more rows without covering the last row. The compact player control opens the complete player list. Neither scrolling nor inspecting either dialog changes the ticket page or bounds. Coin amounts have enough line height at normal and large text sizes. Marking rules, claims, calls, networking and server behavior are unchanged.

## Native layout evidence

The baseline reproduces a truncated Hindi Claim label and collapsed lower prize rows at 200% system text size on a narrow emulator. [Baseline](baseline/instrumentation.txt). The final matrix checks English/Hindi, portrait/landscape, 100%/200% system text, standard/narrow viewports, one/three/six-ticket hands and an eight-player pool. It checks rendered lines, 48dp claim controls, every sidebar prize, first/last ticket-specific claim selection, and returning from prize/player dialogs to the same ticket bounds and page.

The review package is isolated from the installed Wi-Fi and Internet beta packages. Fixtures use saved-state data and issue no registration, purchase or room command. Each run verifies the actual Activity font scale, restores device font/animation/density settings and checks both installed app hashes. The review APK retains version 34; normalized production source hashes are compared with optimized alpha35 before acceptance. [Source and run identities](native-summary.json), [historical runner](run-native-review.mjs), [review build setup](ui-review.init.gradle). The runner pins the installed alpha34 beta and must be adapted before reuse against another version.

**39 final native executions pass:** 28 arena cases, seven existing marking/claim cases and four existing recovery cases. Each arena run includes four language/orientation combinations for six tickets, plus English portrait with one ticket, Hindi landscape with three tickets, and Hindi landscape with eight players.

| Run | Coverage | Result |
| --- | --- | --- |
| [amount-normal](amount-normal/instrumentation.txt) | Standard viewport / 100% | 7 passed, 56.82s |
| [amount-large](amount-large/instrumentation.txt) | Standard viewport / 200% | 7 passed, 52.887s |
| [amount-narrow](amount-narrow/instrumentation.txt) | Narrow viewport / 200% | 7 passed, 143.126s |
| [amount-narrow-normal](amount-narrow-normal/instrumentation.txt) | Narrow viewport / 100% | 7 passed, 62.586s |
| [regressions](regressions/instrumentation.txt) | Existing marking/claim feedback / 100% | 7 passed, 49.126s |
| [recovery](recovery/instrumentation.txt) | Existing recovery / narrow / 200% | 4 passed, 34.198s |

Screenshots were visually inspected as well as asserted: [narrow Hindi portrait](amount-narrow/arena-amount-narrow-hi-portrait-200-6-tickets-4-players-live.png), [eight-player prizes at the end of the rail](amount-narrow/arena-amount-narrow-hi-landscape-200-6-tickets-8-players-prizes-end.png), [normal English landscape](amount-normal/arena-amount-normal-en-landscape-100-6-tickets-4-players-live.png). Run times are functional-test elapsed times, not frame-performance measurements; the narrow large-text run overlapped the optimized build.

## Corrections during verification

The first four candidate cases passed their original assertions. Visual inspection then found a bottom cue overlapping a prize row and vertically clipped recent-call numbers. The expanded suite added dialog interaction, one/three-ticket fixtures, an eight-player pool and a minimum claim touch target. It exposed pixel rounding in a 56dp container with 4dp vertical padding; reducing that padding to 3dp preserves at least 48dp for the button.

Two fixture defects were corrected separately. Coin RoomOptions requires a six-ticket allocation limit even when a player owns fewer tickets. Also, overriding only Compose's Hindi context left a dialog's Activity resources in English. The fixture now temporarily matches and then restores the Activity locale, as production AppCompat locale selection does. This was not evidence of a production language failure. The normal-size matrix then detected coin-amount line measurements outside their bounds; explicit 14sp line height and natural alignment at normal size resolved them. Intermediate failures and captures remain in this review and are excluded from final acceptance.

An initial optimized build command omitted the opt-in benchmark project flag and stopped during task selection; the corrected command includes `-PtambolaBenchmarks=true`. No app was produced by the failed command. [Initial task-selection failure](initial-build-task-selection.txt).

## Scope

This change follows the existing [hands-on rival review](../friends-replay-2026-09-27/README.md): protect the ticket area and make claims and group coordination clear. No new rival session was performed in this pass. Native UI fixtures and the public emulator journey do not establish physical-phone/mobile-data, TalkBack, frame performance, native rival gameplay, production signing or Store readiness. Android browser handoff and the Figma update remain open as recorded in the [previous review](../active-round-recovery-2026-09-27/README.md). The temporary PC host remains the availability dependency; the broader goal remains active.

## Optimized build and invitation checks

The publicBeta APK and external test driver build successfully. Eighteen Android JVM tests and 71 web tests pass. English/Hindi catalog checks pass for 802 resources. Lint reports zero errors and 124 warnings; the additional unused-resource warning is the old ticket-count copy now replaced by the existing plural resource. [Build](android-build.txt), [test summary](test-summary.json), [web checks](web-tests.txt).

The APK is version 35 / `0.35.0-alpha35-internet-beta`, 29,192,677 bytes, non-debuggable, package `io.github.sbshrey.tambola.game.beta`, with the existing development signing certificate. SHA-256: `2c8b9cbded702cbc22178f0f5f479a41968669753824fe80af58237b6a0e4636`. [Identity](apk-identity.json).

The [desktop browser fixture](browser/validation.json) passes the legacy caller-worker upgrade, retained offline caller, code validation/copy, keyboard navigation, English/Hindi at normal/200% text and exact alpha35 intent/download fallback. It does not establish Android browser handoff. The unchanged external driver requires an actual absent active network before accepting the outage; [network preflight](network-preflight.json) independently verifies disconnection and restoration before the public round.

## Exact public acceptance

The unchanged optimized APK passed `InternetBetaTest#friendsRoundRecovery`: **OK (1 test), 479.736 seconds** through the public directory and system-trusted HTTPS/WSS, with no adb reverse. One native emulator player and three passive HTTP QA peers completed **86 calls and all eight prizes**. Public winning-ticket shares accounted for the full 2,400-coin pool, the native balance finished at 3,300, and aggregate balances remained 6,000. [Journey](public-round/coin-release-journey.json), [transcript](public-round/instrumentation.txt), [exact app/driver identity](public-round/validation.json).

At call 30 the emulator had no active network. The app dismissed the picker, disabled claims, hid the deadline ring and retained ticket geometry, page and marks. Marking a saved call off and on worked offline. Explicit reconnect restored the same room, round, ticket counts, pool and mark by call 31. [Disconnected](public-round/round-network-disconnected.png), [restored](public-round/round-network-restored.png). All 20 saved marks and the ticket page survived process death; call history was checked before and after reopening.

Same friends replay placed the native player and a peer in the same next lobby with three/two tickets, verified the exact peer receipt and process recovery, then refunded both purchases. All four QA profiles were deleted. The emulator's network, font, animation and density settings were restored, the Wi-Fi APK was unchanged, and the owned emulator was stopped. [Cleanup](emulator-cleanup.json).

The installed PC service and complete runtime hashes remained unchanged; no service or bridge restart was needed. [Host verification](host-verification.json), [public endpoint check](public-entry.json). This proves a functional public round on the owned emulator, not independent remote-human, physical-phone/mobile-data or frame-performance acceptance.

## Publication

The [alpha35 prerelease](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha35-arena-controls) targets source commit `f56922968ad018a30a790a8ac1298d4cc2f722b3`. Its [APK download](https://github.com/sbshrey/tambola-caller/releases/download/full-game-alpha35-arena-controls/Tambola-Internet-Beta-0.35.0.apk) was fetched anonymously and matched the tested SHA-256 and 29,192,677-byte size. [Release notes](release-notes.md).

The guarded publisher created main commit `565e54ab3de4b14190812d9c8d3ea6ca914608ba`; its actual diff changes only `friends/index.html` and `friends/invite.js`. Pages build `1243373589` completed successfully. At **16:50 UTC**, normal public URLs for the invitation assets, stylesheet, service worker and caller homepage matched reviewed local content. A fresh Chromium session checked the code display, English/Hindi switch, alpha35 download and explicit intent fallback without page errors. [Publication record](pages-publication.json), [verification](publication-verification.json), [public invitation](published-invitation.png). These checks do not establish Android browser handoff or physical/mobile-data acceptance.
