# Tambola Jalsa Beta v69: Quick Tambola validation

## Candidate and release

- Source: `shrey/tambola-jalsa` at `c8cb0e0779cdb4c653aa69fa4a1fbb26cb67fc28` (the Quick Tambola implementation began in `ec2db6b05137533d5f73984d6a961be834d40d7e`).
- Hosted Windows server upgraded from the committed distribution after a no-active-room preflight and fresh primary/journal database backups. Public `/health/ready` returned `{"status":"ready","protocolVersion":10}` after the upgrade.
- GitHub prerelease: <https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha69-quick-tambola>; asset: `tambola-beta-v69.apk`, 31,700,702 bytes, SHA-256 `492ec25fcca4922f7415ab4acd48e00541a53b728ef4c5231e42b6d9be02d2a8`. The published asset digest matched the prepared APK.
- The installer verified versionCode 69, beta package `io.github.sbshrey.tambola.game.beta`, and the existing signing certificate. The exact publicBeta APK used opt-in Firebase configuration with `-PtambolaFirebase=true`.

## Code checks

- Isolated PostgreSQL server regression suite: 223 tests passed before the final computer-ticket adjustment; the affected `CoinMatchTest` subset passed 18 tests after it.
- Android app unit suite: 34 tests passed after the final UI changes.
- Six Android emulator UI tests passed after the final UI changes. They cover the new lobby copy, separate goal-place counts, disabled unready claims, and a ready one-tap claim.
- `:app:lintPublicBeta :app:assemblePublicBeta -PtambolaFirebase=true` succeeded. Lint reported no errors.

## Installed play

- A v69 emulator retained its existing fictional profile and wallet after an in-place APK installation. The public Quick Tambola lobby displayed the five-second pace, two goals, automatic dabs, and ticket cost; the wait view identified computer fill after ten seconds. Fifteen labelled seats appeared in the hosted arena.
- The first exploratory hosted round exposed that two tickets per computer seat filled prize places too quickly. The final server assigns one ticket per computer seat, with one to six selectable for a person. The initial exploratory player received no prize; this was used to adjust the rule before the final APK and server deployment.
- In the final hosted round, a test player submitted an Any Line claim from ticket 6 with one tap. The result showed rank 2 and 334 coins awarded. The round terminated without user navigation or force close. Ticket alignment stayed fixed while calls and claim state updated.
- A separate emulator running v68 detected the GitHub v69 release inside the app, downloaded it, handed it to Android for update confirmation, and then ran v69 with the fictional profile and 50,500 coin wallet preserved.
- The two v69 emulators joined the same new Quick Tambola lobby with different ticket counts (six and three). Each waiting view showed two people and the other fictional profile by name before computer fill. Both entered the 15-seat arena. Force-stopping and reopening one app during the shared round restored its called board and ticket marks without a new purchase.
- Both devices then displayed the same called number sequence and completed that shared round with identical standings. Neither test profile took a prize in this particular round; each result showed rank 7 and zero payout. The [first result](same-result-a.png) and [second result](same-result-b.png) agree, including the two human profiles in the roster.
- In another hosted round the first emulator claimed both Early Five and Any Line, ending at rank 1 with 400 coins. This verifies multiple distinct goal claims and their combined result display.

The [first waiting view](same-table-a.png) and [second waiting view](same-table-b.png) show each fictional test profile seeing the other at the same table. The [winning result](winning-result.png) shows the two-goal payout. These are emulator captures with fictional test identities.

## Boundaries

- This is a monitored beta, not a public production launch. The v69 APK was verified on emulators and the hosted Windows PC, but not installed on a physical phone in this validation pass.
- The first post-upgrade purchase request on one emulator timed out. The app showed its pending-action recovery path and the retry recovered into a live round. Keep monitoring this path in beta.
- Hosting still depends on the Windows PC and its public route. The upgrade readiness check does not prove recovery after an actual reboot in this pass.
- Diagnostic collection requires opt-in. Live rewarded ads remain disabled.
