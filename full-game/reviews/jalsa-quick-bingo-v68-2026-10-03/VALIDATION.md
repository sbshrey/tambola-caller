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

## Beta decision

This remains a monitored beta. A PC-hosted game service, physical-device
coverage beyond the earlier v65 check, and measured win rates are still
limits for wider launch. Live rewarded ads remain disabled; diagnostics are
opt-in.
