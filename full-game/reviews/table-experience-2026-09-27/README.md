# Joining, call timer and minimal Settings — 27 September 2026

The landscape review now shows a table filling with short avatar arrivals, a circular timer and familiar fictional gaming handles. Native code animates actual server roster changes. Empty seats remain placeholders; the server still creates labelled computers only when sales close. `ChaiChamp`, `NeonNinja`, `LuckyMango`, `PixelRaja`, `DiceDiva`, `MoonMaverick`, `TurboTikka` and `LotusLegend` are invented display names, not imported third-party accounts. The shared server helper preserves computer identity and game behavior; it compiled but was not deployed.

The called-number ring uses the server deadline and monotonic elapsed time. Frequent progress reads occur in Canvas drawing, not ticket composition. Changing unrelated UI state does not restart the timer. Reduced motion/system animation controls use stepped progress. No new frame-performance acceptance is claimed.

Settings contains number voice, music, game sounds, vibration, reduced motion and English/Hindi/Hinglish. Enabling a previously zero-volume channel restores a usable default. Language selection updates caller language and UI locale; Hinglish uses English UI. The data disclosure remains accessible. Hidden legacy pace/theme/tutorial/history controls do not erase stored data.

## Exact validation

- Final exec **77726 exited 0**. Gradle completed successfully in 59 seconds with review app/test assembly, 15 JVM tests across five classes and lint. Lint has **0 errors, 116 warnings**: 103 unused resources, 8 plural candidates and 5 existing other warnings. Removing old Settings controls leaves compatibility resources unused.
- **17 native tests passed at system font 1.0 (88.018 s) and 1.5 (86.562 s)** on the owned API 30 emulator. The suite covers lobby/result controls, actual Activity landscape navigation, saved preference recreation, English/Hindi settings with full-size targets, actual CoinLobby roster updates, manual marking/paging/prize selection, and the actual arena's five-second ring through an unrelated recomposition and expiry.
- Both runs used the same app and test APKs. System font was restored to 1.0. The installed alpha22 APK hash was unchanged before and after both runs. `normal/`, `font150/` and `validation-summary.json` retain identities, screenshots and raw transcripts.
- An earlier 150% run failed the immediate orientation assertion after launch: requested landscape had been set, but configuration still reported portrait. `initial-font150-failure/` preserves that failure. The final test waits up to five seconds for configuration to settle before asserting landscape. This establishes eventual emulator orientation, not a physical-device first-frame or OEM guarantee. The earlier Android instrumentation process itself returned zero despite a JUnit failure; the runner rejected that transcript and exited nonzero.
- Server compilation passed in `initial-build-server-compile.txt`. This cosmetic handle change did not trigger a new database/load run and is not running on the installed host.

| Artifact | SHA-256 |
| --- | --- |
| Isolated `.uireview` app | `64f229225f8c82803659b70c275df3a017a63b67b0ea4427d6a5838f7e011926` |
| Isolated `.uireview.test` | `3dde465c4bad7a30524263cc36fa8a20ee21990d842216d6fc113d3ea638da2f` |
| Preserved alpha22 | `05dfaa405629fec0a5ab57234941eca1229b9f2cb13034a496e3549759bdb4b4` |

## Design review

[Figma countdown](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=4-2696) and [Settings](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=4-3653) are on **Joining, timer & settings · v2**. Nine editable vector/text groups were imported; Page 1 is preserved. The saved page and selected 1280×720 Settings group were verified after reload. This is a design board, not a wired Figma prototype. [Source and screenshots](../../../designs/landscape-lobby-v1/README.md) are retained with the design.

The [local browser review](http://127.0.0.1:8877/review.html) animates the 12-second joining sequence and reveals a number every five seconds. Direct observation recorded empty seats, ChaiChamp's arrival, a draining arc, then a new sequence reaching 4/90 calls instead of the earlier 37-call state. Settings keyboard actions update the intended switch independently. The narrow Codex panel had inconsistent pointer-coordinate mapping, so browser pointer acceptance remains unproven. All browser data is simulated; its audio, haptics, language and disclosure controls are illustrative. Native personal controls have separate test coverage above.

## Boundaries and next work

The main APK and installed Wi-Fi host were not updated. These are isolated UI checks with fixture state and actual Activity preferences, not a new live purchase/settlement or production acceptance run. The arena keeps its existing base visual treatment apart from the new timer. Finish that treatment, then repeat relevant native multiplayer acceptance before packaging. Physical Wi-Fi/touch/audio, ARM64 frame timing, server purchase-burst latency, reboot behavior, production hosting/signing and release materials remain open.

To reproduce, build with `tools/ui-review.init.gradle`, then run the archived `run-native-review.mjs` from the full-game directory; use `--large-text` for the second pass. The runner guards the owned emulator and exact review package names, checks the original APK identity and restores font scale. It never clears or replaces the main app.
