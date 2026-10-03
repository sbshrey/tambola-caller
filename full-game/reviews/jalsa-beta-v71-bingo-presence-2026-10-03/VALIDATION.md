# Tambola Jalsa Beta v71: Bingo live seats

## Change

- Bingo's active arena now counts server-confirmed connected people separately from computer seats. While reconnecting, it reports people seated instead of presenting a stale live count. The ten-second waiting room counts connected people.
- Quick Bingo's One Line and Four Corners cards now say how many winning spots are **left**. Their counts continue to come from confirmed server claims. The Live Wins panel retains explicit Computer labels.
- v71 includes the v70 Quick Tambola prize and live-presence arena changes.

## Candidate checks

- Canonical branch: `shrey/tambola-jalsa`; implementation commit: `b4f0bf3`.
- GitHub prerelease: <https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha71-bingo-live-seats>. Its asset size and SHA-256 matched the prepared APK after publication. The public server readiness endpoint returned `{"status":"ready","protocolVersion":10}`; this is an app-only update.
- `./gradlew.bat :app:lintPublicBeta :app:assemblePublicBeta -PtambolaFirebase=true --offline` succeeded. The signed package remains `io.github.sbshrey.tambola.game.beta`, versionCode 71.
- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --offline` succeeded. All 34 Android unit tests passed. Six `BingoOnlineAccessibilityTest` emulator UI tests passed, including a two-person to one-person presence change and both goal labels.
- The v70-to-v71 APK preparation verified a higher version code and the same signing certificate. Prepared `tambola-beta-v71.apk`: 31,701,242 bytes, SHA-256 `a7fcd7ee974c78fe9b643aabda17d769ce74a7642c301a475084857c05646d13`.

## Installed play

- Installed v71 over the previous beta on both Android emulators with `adb install -r`; Android reported Success and versionCode 71. The fictional profiles and wallets remained present.
- The hosted Bingo Night wait lasted ten seconds, then filled open seats with explicitly labelled computer players. The [first arena](bingo-live-one.png) showed `Live 1 · Computers 46` beside the called-ball panel and `5 spots left` on both goals.
- After confirmed computer claims, [both goal cards](bingo-prizes-remaining.png) showed four spots left and the Live Wins panel named computer winners. Card placement, two-card tabs and controls stayed usable in landscape.
- The first hosted round reached its [result](bingo-result.png) without manual navigation. The player made no dabs in this observational pass and correctly received zero coins; computer winners retained their labels.
- Both v71 emulators entered the [same ten-second queue](bingo-two-wait.png) and the [same arena](bingo-live-two.png). The arena showed `Live 2 · Computers 33` with both goal quotas at five places. After force-stopping the second emulator and waiting for presence expiry, the [remaining client](bingo-live-one-after-disconnect.png) changed to `Live 1 · Computers 33` while its round continued.

## Boundaries

- This is a monitored beta tested on emulators and the hosted Windows server; this pass did not install v71 on a physical phone or prove host recovery after an actual reboot.
- The public route depends on the Windows host. Firebase diagnostics remain opt-in and live rewarded ads remain disabled.
