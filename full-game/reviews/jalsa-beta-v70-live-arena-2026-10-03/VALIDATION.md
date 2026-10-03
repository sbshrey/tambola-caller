# Tambola Jalsa Beta v70: live arena validation

## Change

- The arena now counts connected human members from server presence separately from computer seats. A reconnecting client shows seated people rather than an unverified live count. The roster labels people Live or Away and keeps computer players explicit.
- Quick Tambola shows the remaining Early Five and Any Line places in the arena. Prize details show each confirmed quota as `remaining/total`; a same-call tie window stays labelled as open. A recent confirmed claim identifies the winner, with the Computer label where applicable.
- The ticket bounds and claim controls remain fixed while the counts, calls and roster update.

## Candidate and checks

- Branch: `shrey/tambola-jalsa`; source commit is recorded in the release after validation.
- Build: `./gradlew.bat :app:lintPublicBeta :app:assemblePublicBeta -PtambolaFirebase=true --offline` succeeded. The signed beta package remains `io.github.sbshrey.tambola.game.beta`, versionCode 70. The update preparation tool compared v69 and v70 signatures and version codes.
- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --offline` succeeded. All 34 Android unit tests passed. Four `CoinRoundLayoutTest` emulator UI tests passed, including the new count change, computer claim activity and remaining place check.
- Prepared asset: `tambola-beta-v70.apk`, 31,717,754 bytes, SHA-256 `f609ed6892db73c9f43006d339a0ab226a1f67649497e20c7edd44cdbdbdf069`.
- Public readiness during play: `{"status":"ready","protocolVersion":10}` at `https://play.thefinxperts.com/health/ready`. This is an app-only update; no server deployment was needed.

## Installed emulator play

- Installed v70 over v69 on emulator-5554 with `adb install -r`; Android reported Success and versionCode 70. The fictional profile and wallet remained present.
- Two emulators joined the same hosted Quick Tambola round within the ten-second wait. The [arena](live-two.png) showed `Live 2`, `Computers 13`, and five open places in each goal. Six owned tickets fit on three stable pages; the ticket cells and claim control remained aligned during calls.
- The [prize detail](prizes-detail.png) showed `5/5 left` for each category. After confirmed computer claims, the header fell to four, then fewer remaining places, and the activity line named a winner as Computer. The v70 player claimed Early Five and Any Line with one tap each; the [second confirmed claim](line-claim.png) shows Any Line reduced to four places and the win feedback.
- The [roster](live-roster.png) listed both fictional test profiles as Live, with all other seats labelled Computer. After force-stopping the second emulator and waiting for server presence expiry, the [arena](live-one.png) changed to `Live 1` while retaining 13 computer seats. The player remained in the round.

## Boundaries

- This is a monitored beta. v70 was verified on emulators and the hosted Windows server; this pass did not install it on a physical phone or prove recovery after a computer reboot.
- The first two Early Five taps crossed a five-second call boundary while the client was waiting for confirmation. A later tap was confirmed and reduced the remaining count. The existing pending-action/retry path therefore remains important to monitor in beta.
- Firebase diagnostics remain opt-in, and live rewarded ads remain disabled. The computer labels must remain visible to beta users.
