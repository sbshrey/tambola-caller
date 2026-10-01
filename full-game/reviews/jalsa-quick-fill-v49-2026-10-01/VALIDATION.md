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

Record the public host upgrade, one-human quick starts for each game, GitHub release digest and v48-to-v49 update installation after they are complete. Physical-phone acceptance remains separate from emulator and PC evidence.
