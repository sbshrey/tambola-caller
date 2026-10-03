# Tambola Jalsa v67 monitored beta review

## Gameplay decision

New online Bingo tables have two visible goals, One Line and Four Corners,
with five places each. Calls arrive five seconds apart. Both filled goals or
the completed claim window after call 45 end the round. People still get a
10-second matchmaking window before explicitly labeled computer players fill
open seats. One to six owned cards use numbered tabs with a missed-mark count.
The selected card's claim buttons show progress, reward and places remaining.
Results keep the player's rank, dabs, goals and coin outcome above the roster.

New Tambola tables use earned powers without a Classic choice. New drops are
Auto-Dab or Prize Boost. The landscape control names the power, target ticket
and effect; Auto-Dab lasts 35 seconds. Existing purchased Classic and Shield
tables remain playable under their saved rules. Bingo and Tambola remain
online, server-authoritative games with virtual coins and no cash prizes.

## Validation

- Source suites passed: 78 domain, 56 client, 222 isolated PostgreSQL server
  and 34 Android unit tests. The v67 client suite includes a test that
  persists two rapid dabs, promotes one to a retryable command and retains
  the other until confirmation. The server and domain code did not change
  after their full runs.
- Emulator UI tests passed for the short landscape Bingo card and direct
  claims, Hindi portrait at 200% text, the 10-second computer-fill message,
  Tambola power activation, Bingo card navigation, recovery and mixed-version
  friends tables. English/Hindi parity passed for 1,006 resources. Debug and
  public beta lint passed on their respective variants.
- The v65-to-v66 in-place emulator installation retained the same profile
  and 52,150-coin balance. v66 was an unpublished test candidate. On the
  public host, a two-card quick Bingo table waited ten seconds, then began
  with one person and 31 labeled computer players. The card showed the
  two goals, 45-call meter and live dabs. Repeating a dab kept it marked.
  That table finished in roughly four minutes with 32 ranked players; the
  beta profile remained available afterward.
- The server deployment preflight confirmed no unfinished rooms. The
  upgrade from commit `49da4c0a3506ec736388512a49401a69fc06eb37`
  retained a fresh primary and journal backup, migrated and restarted.
  `https://play.thefinxperts.com/health/ready` returned HTTP 200 with
  protocol 9 after the restart.
- The signed v67 candidate installed in place over v66 on the emulator,
  retained the same beta profile and 51,950-coin balance, and resumed the
  prior quick-round results. A second live two-card table bought two cards,
  waited ten seconds and began with one person and 31 computer players.
  Two glowing numbers tapped back-to-back were both confirmed as permanent
  dabs. After a force-stop and relaunch, the app resumed that active table
  with both dabs intact. The previous v66 test had dropped one of two rapid
  taps, which is why v67 adds the durable mark queue.
- That v67 Bingo table completed within the short call limit and showed rank
  7 of 32, two test dabs, zero goals and zero coins earned. Only two numbers
  were deliberately marked during this queue-focused run; it is not a
  representative win-rate trial. Returning home preserved the 51,750-coin
  balance.
- From the signed v67 home screen, Tambola opened directly to the online
  ticket selector with no Classic choice. A three-ticket live table opened
  in landscape with two aligned tickets visible, a page control for the
  third, live calls, claim progress and the free Prize Boost meter. Marking
  the called number 2 on ticket 2 immediately changed the ticket to 20%
  progress and the power meter to 1/5 correct.
- Update preparation confirmed the beta package, increasing versionCode 67,
  unchanged signer SHA-256
  `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`,
  31,697,826 bytes and APK SHA-256
  `2fae035bb14f57dfa40fd96a37ee35cac4122d92f666aa83670a779e2f349038`.
  The packaged manifest still sets Crashlytics and Performance collection off
  by default; collection requires the beta user's opt-in.
- The source for the signed APK was pushed to `shrey/tambola-jalsa` at
  `0f09f51e3625601b88097907296fc184b8ced7ca`. The published prerelease
  [Tambola Jalsa Beta v67](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha67-quick-bingo)
  contains the exact `tambola-beta-v67.apk` asset. GitHub reports the same
  31,697,826 bytes and `sha256:2fae035bb14f57dfa40fd96a37ee35cac4122d92f666aa83670a779e2f349038`
  digest as the locally verified file. A post-release public readiness check
  returned HTTP 200 and protocol 9.
- On a separate clean Android 30 emulator, the previously published v65 APK
  created the fictional `UpdateQA67` profile with 50,500 coins. Its Settings
  update check found v67 from the public release, downloaded and verified the
  APK, and opened Android's source-permission and install confirmation screens.
  After granting permission and confirming installation, Android reported
  versionCode 67. Opening the updated app retained `UpdateQA67`, its 50,500
  coins and the collected day-one reward. This exercised the actual in-app
  updater rather than only an ADB replacement install.

## Beta decision

This is a small, monitored beta candidate. The server is still hosted on the
operator's Windows PC, and availability across reboot or an extended outage
has not been demonstrated for this build. The faster timing and five-place
goals are design and emulator findings, not measured retention improvement.
Broader physical-device coverage and real-player win-rate feedback remain
needed before an unattended public launch. Live rewarded ads remain disabled.
