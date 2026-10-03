# Tambola Jalsa v68 monitored beta follow-up

The [v67 gameplay and release review](../jalsa-quick-bingo-v67-2026-10-03/VALIDATION.md)
records the Quick Bingo redesign, live play, rapid-dab recovery, Tambola
layout, tests and the public updater trial. During that live Tambola run,
the Prize Boost action text clipped the ticket number on a landscape screen.
v68 puts the target ticket first and shortens the English and Hindi actions.
The power behavior and game server are unchanged.

## Validation

- Android debug unit tests passed. The v68 signed public beta build and lint
  passed with `-PtambolaFirebase=true`; other game tests from the v67 review
  remain applicable because only strings and version metadata changed.
- The prepared APK is `tambola-beta-v68.apk`, package
  `io.github.sbshrey.tambola.game.beta`, versionCode 68, 31,697,806 bytes,
  SHA-256 `253572d357a780863893bc2594b6d53c76d5b604f2b59ed2c0bf58815a5e95c8`.
  Its signer SHA-256 matches v67:
  `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`.
- The signed v68 APK installed over v67 on the main emulator with Android
  versionCode 68 and the 51,450-coin beta profile intact. The public server
  remains at protocol 9 and requires no upgrade for this string change.
- The published prerelease is [Tambola Jalsa Beta v68](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha68-quick-bingo),
  tagged at source commit `66346ef7cbb78d15554e05ff641bfb2ecd672c3e`.
  GitHub reports the exact prepared asset name, 31,697,806-byte size and
  SHA-256 digest. Public readiness returned HTTP 200 and protocol 9 after
  publication.
- On the separate Android 30 emulator, the v67 app found v68 through its
  Settings update check, downloaded it, and opened Android's install
  confirmation. After accepting, Android reported versionCode 68 and the
  fictional `UpdateQA67` profile still had 50,500 coins. This repeated the
  in-app update path after the v65-to-v67 trial.
- A new six-ticket Tambola table on signed v68 filled after the ten-second
  wait. Two tickets stayed aligned per landscape page and the power header
  followed the visible target ticket. Five correct manual marks earned an
  Auto-Dab, and another five earned a second. Activating one on ticket 6
  immediately marked called numbers on that ticket and showed `Power
  activated.` The observed random drops were both Auto-Dab, so the shorter
  Prize Boost caption was validated by resource compilation and target-first
  wording, not by a second live Prize Boost screenshot.

## Beta decision

This remains a monitored beta. A PC-hosted game service, physical-device
coverage beyond the earlier v65 check, and measured win rates are still
limits for wider launch. Live rewarded ads remain disabled; diagnostics are
opt-in.
