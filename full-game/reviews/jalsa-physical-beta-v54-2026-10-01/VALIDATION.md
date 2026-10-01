# Tambola Jalsa v54 phone beta validation

## Delivery

Source commit `a32da7ca228a468578239ec7244a9f763377c788` was pushed to `shrey/tambola-jalsa`. The committed server distribution passed the private Windows host preflight with no unfinished rooms. The upgrade script retained paired database backups, applied migrations and restarted the host. Public `https://play.thefinxperts.com/health/ready` returned HTTP 200 with protocol 9 after deployment.

The signed publicBeta APK was assembled with `-PtambolaFirebase=true`, diagnostics collection off by default and rewarded ads disabled. GitHub prerelease [`full-game-alpha54-phone-beta`](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha54-phone-beta) contains `tambola-beta-v54.apk` (31,677,194 bytes). GitHub's SHA-256 asset digest matches the prepared APK: `ef54b2249e9288092be3cb0baca37419ccc1a1e705a89ba310b9fc8652efba36`. Its signing certificate SHA-256 is unchanged from v51: `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`.

The public invitation and privacy pages were copied from the canonical branch to the GitHub Pages publishing branch after the release. Pages completed its build and live HTTP reads showed the Tambola Jalsa name, v54 invite link and 10-second computer-seat disclosure.

## Checks completed

- Native client and app unit tests, debug and publicBeta lint, and optimized APK assembly passed. English/Hindi parity passed for 984 resources. Root web tests (71 cases) and production build passed.
- The short landscape home instrumented test passed at the physical phone's 1240 x 2772, 560 dpi geometry, including 200% font scaling. A v54 emulator screenshot at that geometry showed both full game cards and Join actions in the viewport.
- Server tests ran 221 cases: 220 passed on the first full run; the remaining test expected the previous progressive computer seating, so it was corrected and its entire class passed on rerun. Targeted quick-table and power tests passed (15 cases). There was no uninterrupted second full-suite run after that test-only correction.
- On the public upgraded server, a v54 emulator Bingo quick lobby showed one person and no computers during the countdown. At the 10-second start it showed one person and 48 explicitly identified computer players. A previous complete hosted Bingo round reached results; a called-number mark persisted across an in-place v52-to-v53 upgrade. The new bounded conflict retry was covered by unit tests and accepted a live v53 mark.
- On a OnePlus CPH2487 Android 36 phone, the signed v51 APK installed over v44 without clearing data. Its existing guest identity and coin balance remained. A pending purchase reconnected into a live Tambola round with 42 players; a tapped called number marked and progress updated. The user reported audible number calls/effects and comfortable taps.

## Beta acceptance still to finish

- The physical phone's screen relocked during ADB inspection. Its v54 installer session did not complete while locked, so v54 physical layout, Bingo, final Tambola settlement, updater detection and v54 profile persistence are not yet confirmed. The installed version must be checked again after the phone is unlocked.
- One real two-human round across the phone and emulator was not completed. Computer players are identified in the UI and protocol; they must not be represented as human users.
- The public game service is hosted on the operator's Windows PC. Availability during PC shutdown, Internet loss and unattended restart remains a practical beta dependency. Store distribution, long-duration endurance and broad physical-device coverage are separate release gates.

This evidence supports a limited first beta with clear monitoring and recovery, but does not certify an unattended public production launch or full physical-phone acceptance of v54.

Later ADB inspection showed that Android eventually completed the in-place v54 install while the phone stayed locked: `dumpsys package` reported versionCode 54. This confirms the update installed, but does not confirm the preserved profile or the v54 playing experience until the device is unlocked.
