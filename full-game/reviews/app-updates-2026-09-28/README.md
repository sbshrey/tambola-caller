# In-app APK updater validation

Version 40 adds a direct-distribution Internet Beta updater. Implementation and release procedure: [APP_UPDATES.md](../../APP_UPDATES.md).

- Debug and Internet Beta builds pass; release vital lint and full debug lint pass.
- 29 Android local unit tests pass, including eight new update-feed and APK identity policy tests. These cover opted-in asset selection, newer-version ordering, drafts/legacy assets, malformed metadata, invalid digest formats, download size, repository/origin restrictions, package/version/SDK/signing mismatches.
- English/Hindi validation passes for 885 resources.
- Three native API30 tests pass: separate explicit download/install actions, Later without downloading, and the installed Internet Beta opening Settings and successfully checking the real public GitHub feed. The feed currently has no newer opted-in asset. See `native-tests.txt`.
- The actual beta APK upgraded the emulator's version 39 installation to version 40 using `adb install -r`; Android retained firstInstallTime `2026-09-28 17:53:11`. The preparation tool verified both complete APK signatures and the matching signer certificate. This establishes an in-place package upgrade, not a physical-phone profile migration test.
- `git diff --check` passes (line-ending conversion warnings only).

Artifact: `full-game/releases/ota-v40/tambola-beta-v40.apk`; SHA-256 `7b1f7f157375e7e54bcf939722011b70f3ce95e3d43dadd8724d077e97f62d18`. See `artifact.json` for package and certificate metadata.

No public release or game-server process was changed. Install this APK manually once to enable future in-app offers. Subsequent updates are offered when their versioned APK asset is attached to a published GitHub release. This validation did not publish a synthetic future update or perform a full download-to-system-installer upgrade between two updater-enabled versions. Physical-device permission prompts, cancellation and profile preservation across that complete flow remain release acceptance checks.

The dedicated emulator was stopped after verification.
