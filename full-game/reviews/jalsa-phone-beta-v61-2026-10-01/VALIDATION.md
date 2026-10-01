# Tambola Jalsa v61 physical beta playtest

## Scope and result

The signed publicBeta APK was played on a OnePlus CPH2487 (Android 36, 2772 x 1240 landscape, 560 dpi) and an Android emulator against the public protocol-9 server. This review extends the [v54](../jalsa-physical-beta-v54-2026-10-01/VALIDATION.md) and [v57](../jalsa-playtest-v57-2026-10-01/VALIDATION.md) checks. It supports a limited, monitored beta; it is not a claim of unattended production availability.

## Playing evidence

- On the phone, the existing guest identity and wallet survived in-place signed updates from v57 through v61. A completed quick Bingo round settled to results and left the expected 51,075-coin balance after its 100-coin entry. Backgrounding and reopening mid-round preserved the marked card and current live call.
- At the phone's short landscape height, the previous Bingo setup hid Friends, the waiting table hid Leave, the live arena clipped the pattern chase, and the claim sheet hid the fourth pattern. The v61 source puts Play and Friends side by side, compacts waiting without hiding computer-fill disclosure, removes a one-card pager, fits all four chase progress bars, and presents all four claim options in two columns. These states were visually checked in the v60 candidate on the phone or on an emulator set to the short landscape height; the same UI is in v61.
- The emulator hosted a Bingo friends table. The phone joined its code using a separate saved profile. Both clients showed two people and two cards; the live round showed **People: 2 · Computer: 0** on each device. The phone's one-card entry deducted 100 coins once. The emulator started the shared round, and both clients received the live calls.
- The phone marked a called Bingo number, then retained that mark and the active round after the v60 in-place update. Its middle row reached 5/5; the One line option became enabled. The server accepted the claim, and reopening the sheet showed that the same prize had already been claimed. The two-column sheet displayed all four options on the physical screen. The shared round settled with the phone ranked first for One line, paid 40 coins, and showed 51,095 coins in its wallet.
- The earlier physical Tambola round accepted called-number marks, displayed live progress, and reached ranked results. The user reported audible number calls and sound effects and comfortable tap targets on the phone. A public two-independent-client server check for both games is recorded in the v57 review.
- v61 fixed a navigation issue found during this playtest: Tambola's Lobby action from a completed coin round left the finished room attached, so reopening Tambola returned to old results. The phone and emulator both returned to the setup screen after the fix, with their profiles and wallets intact.
- The emulator created a Tambola friends table and the phone joined it as a separate player. Both showed two friends; the host started the shared round. The phone showed two complete tickets, the call board, claim controls, and live player/prize counts in its landscape arena. A called number was marked successfully and remained marked; another called number became ready to mark. The two-device round was still active when this review was written, so its final settlement is not claimed here.

## Build and service checks

- The v61 debug app unit tests passed. The optimized publicBeta APK and lint passed with `-PtambolaFirebase=true`; diagnostics collection remains opt-in by default. The signed APK kept the package `io.github.sbshrey.tambola.game.beta` and signer SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`. Local preparation recorded versionCode 61, 31,694,562 bytes, and SHA-256 `8803098b04bb84182c8afcd2a3e9ce943cb3faa729a50b0ede4f66c09f6fb6ec`. ADB in-place installation on the phone and emulator showed versionCode 61; the phone retained its profile, wallet, and earlier results.
- The public `https://play.thefinxperts.com/health/ready` returned HTTP 200, protocol 9 during the phone playtest. The server was upgraded and public two-client quick-table behavior checked in the v54/v57 reviews. Quick rooms wait ten seconds, then identify any computer-filled seats explicitly; a friends table can contain only people.
- The phone's stay-awake-while-charging setting was set to 15 and its prior 30-second screen timeout was raised to 2147483647 at the user's request. The screen stayed unlocked through extended play and APK updates. This is a device setting, not an app change.

## First-beta readiness assessment

| User need | Evidence and status |
| --- | --- |
| Find and enter either game | The home screen presents separate large Tambola and Bingo cards. Both routes were used on the phone; the completed Tambola room navigation issue was fixed and retested in v61. |
| Choose a ticket and join a live table | Ticket/card count, price, Play, and Friends are visible in short landscape. Quick Bingo started after ten seconds with explicitly counted computer seats. Separate phone and emulator profiles joined friends tables in both games. |
| Play and understand progress | Phone Tambola tickets, board, call strip, player count and claim controls fit together. Phone Bingo shows the card, live call, claim action and all four pattern progress bars. Called-number marks persisted in both games. The user confirmed audio and tap comfort. |
| Claim and see a result | A two-person Bingo One line claim was accepted once and settled with a 40-coin prize; both clients reached results. A previous quick Tambola round reached ranked results; the v61 two-person Tambola round was still active at review time. |
| Keep profile and coins through updates | Signed v57-to-v61 in-place updates retained the phone's guest profile and wallet. Friend entry debited once, and Bingo prize settlement updated the wallet. The v61 APK's signature and published digest match local preparation. |
| Privacy and recovery | The app has an explicit data disclosure, deletion flow, opt-in diagnostics and an in-app update mechanism in the public beta. Live rewarded ads remain off. Profile and wallet cannot be recovered after uninstall/device loss, so testers need this limitation in onboarding. |

The app is suitable for a **small, monitored beta** with the host kept online and a support channel for testers. The two games have usable end-to-end paths and feel more like playable games than the earlier clipped arena. Their visual style and reward feedback are still simpler than leading commercial gaming apps; broader art direction, social engagement and long-term retention have not been validated with beta users. It is not ready for an unattended, broad public launch.

## Remaining beta boundaries

- The game server runs on the operator's Windows PC. Shutdown, network loss, unattended restart, longer endurance, and broad phone coverage are still availability gates for a public production launch.
- The v61 [GitHub prerelease](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha61-phone-playtest) carries the immutable `tambola-beta-v61.apk` asset. GitHub reported the same 31,694,562 bytes and SHA-256 `8803098b04bb84182c8afcd2a3e9ce943cb3faa729a50b0ede4f66c09f6fb6ec` as local preparation. The in-app updater's new-release prompt and user-confirmed installer have not yet been exercised end to end on this asset; ADB in-place updates and the release preparation identity/signature checks were completed.
- Live ads remain gated pending consent and server-verified reward settlement. The app can be beta tested with the virtual-coin gameplay without live ad rewards.
- Only one physical handset was tested. Accessibility at large font scale has emulator/instrumented coverage, but not a full physical-device matrix.

The v58, v59, and v60 local candidates were never published. v61 is the final release candidate from this playtest.
