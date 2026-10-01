# Tambola Jalsa v49 quick-fill review

## Scope

- New quick purchases for both games join server-authoritative rooms for 10 seconds, then fill open seats with computer players. Existing saved v47/v48 human-only rooms and friends rooms retain their previous behavior.
- Computer identity remains explicit in protocol `Player.computer`, waiting-room roster, Bingo result rows and Tambola winner labels. Personas keep varied fictional names and avatars for a lively table without representing them as people.
- Home and waiting-room copy now describes the actual quick-match flow in English and Hindi.

## Validation

- The full 221-test PostgreSQL server suite passed, including new one-person quick-start tests for Tambola and Bingo. An earlier run failed one large-match fixture that expected a 12-second lobby; after moving its human-join checks inside the new 10-second window, the focused tests and full suite passed.
- Two native emulator waiting-room checks passed with Hindi at 200% text size. They confirmed the 10-second timer, visible computer identity and the Bingo leave action. The first large-text Tambola run found the explanatory copy below the viewport; the compact waiting layout was corrected before the passing rerun.
- English/Hindi parity passed for 976 resources.
- All 34 Android debug unit tests, debug lint and publicBeta lint passed. The optimized v49 publicBeta APK built successfully. The combined build/lint invocation initially hit a generated-source file race in Android lint; running packaging and lint sequentially passed.
- The prepared 31,674,126-byte APK retains package `io.github.sbshrey.tambola.game.beta` and signer SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`, matching v48. APK SHA-256: `d4ac5c9082c2488fe95460ccc045c5e3b71921bfd4278a66bd54d183e3850b8a`.

- Host preflight found no unfinished rooms. The Windows host upgrade applied source commit `01140d2f53bb9f8cf09a3daee0ce01cd26cdaed8` and returned ready. The public TLS verification passed readiness and one-human quick starts for both Tambola and Bingo after the ten-second window, with `computer` flags on the added players. Temporary QA profiles were deleted. See `host-verification.json`.
- GitHub prerelease [`full-game-alpha49-quick-computer-fill`](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha49-quick-computer-fill) publishes the exact `tambola-beta-v49.apk` asset at 31,674,126 bytes. Its reported SHA-256 digest matches the prepared APK: `d4ac5c9082c2488fe95460ccc045c5e3b71921bfd4278a66bd54d183e3850b8a`.
- The installed v48 emulator beta found v49 through Settings > Check for updates, downloaded it in-app, and Android installed it over the existing package. `dumpsys package` reports versionCode 49 and versionName `0.49.0-alpha49-internet-beta`; the updated app opened to the Tambola Jalsa home screen. This emulator had no saved player profile, so profile preservation was not exercised.
- Physical-phone acceptance and full hosted round settlement remain untested in this release pass.
