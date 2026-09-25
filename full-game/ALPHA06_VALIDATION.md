# Alpha 06 validation: appearance and number motion

Date: 26 September 2026. Branch: `shrey/full-tambola-game`. This is a debug-signed internal alpha. Hosted play, remaining presentation work and the production release gates remain open.

## Delivered behavior

- Saved System/Light/Dark appearance, including page surfaces, dialogs, full-screen custom/room editors, buttons, badge/number states and system-bar icon contrast. Theme changes preserve the current game. Existing preference files without an appearance value use System.
- An original native home illustration, with scalable ticket/ball geometry and no external image dependency. Decorative artwork is excluded from screen-reader traversal. Printed tickets and calling balls retain their fixed ink/paper colors.
- A finite 420 ms number-ball settle. The committed number is accessible immediately; the animation never controls a draw. Reduced motion displays the final state immediately, and Android's animation duration setting is respected.
- Recent-number and board cells grow with system text size; recent calls wrap to avoid crowding. Screenshot review also prompted proper singular counts and the default player's “Your ticket” title, using Android string/plural resources as a start toward the planned localization work.

See [presentation and asset provenance](PRESENTATION.md) for implementation details and remaining music, effects, celebrations, avatars and Hindi interface work.

## Candidate and build evidence

Candidate `Tambola-Together-0.6.0-alpha06.apk`: **39,729,110 bytes**, SHA-256 `62859367c692b8f44a50912723fb5ea408590e8086b967a9e5eebb11fb73d5f3`. Package `io.github.sbshrey.tambola.game`, version code 6, minimum API 26, target API 36. Installed APK readback matches. Signature Scheme v2 verifies with the existing Android debug certificate, SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`.

The final debug APK, instrumentation and lint build passed in 1 minute 27 seconds. Five Android JVM tests pass: the existing four setup cases and a new palette contrast case. It checks 68 screen text/foreground pairs at 4.5:1, six outline pairs at 3:1 and three fixed ticket-text pairs. This is numeric palette evidence, not a complete accessibility audit. Debug lint reports zero errors and one unsuppressed KAPT-to-KSP warning.

The final optimized release APK/AAB and release lint build passed in 2 minutes 43 seconds. Release lint also reports zero errors and one KAPT-to-KSP warning. These builds use a placeholder HTTPS origin for packaging/R8 validation and are not distributed as a hosted or signed production app. Normal dependency SHA-256 verification remains enabled.

## Device acceptance

The exact candidate passed **17/17 instrumented tests in 329.62 seconds** on the dedicated Android 11/API 30 emulator: eight offline/custom-rule journeys, two tutorial/badge journeys, three online journeys against real local Netty/PostgreSQL, two encrypted-store checks, appearance preservation and number motion. The final test source includes the duration-scale correction described below. System animation settings were restored and verified by the runner.

The appearance journey creates a real solo round and manually marks a called ticket number. Native theme controls change Light/Dark/System, screenshot pixels and system-bar flags verify the selected palette, and activity recreation retains the selected theme and the round's ID, cards, calls and marks. It opens ticket and number-board dialogs in both themes and scrolls to number 90. Ten normal-size screenshots were captured and visually reviewed. System appearance resolves to light in the ordinary run.

Focused final-candidate motion checks passed separately at scale **0 in 2.247 seconds** and scale **1 in 3.176 seconds**. The latter additionally verifies the app's reduced-motion switch while system animations are enabled. The full suite also passes the scale-0 case; these focused runs are repetitions of one case, not extra unique tests.

The same appearance journey passed at **360dp width / 200% text in 25.973 seconds**. It includes readable scrollable ticket/board dialogs, board access through number 90, selected-theme persistence and unchanged ID/cards/calls/manual marks. Ten additional screenshots were captured and visually reviewed. Android night mode was enabled for this run, so the final System selection was verified as dark; the ordinary run verified System as light. Font size, density, night mode and animation settings were restored and checked. These emulator checks do not replace a TalkBack walk-through or physical-device acceptance.

The temporary local service and response-loss proxy ran in separately tracked tool sessions and were stopped afterward. The created ADB mappings were removed. The original emulator and isolated PostgreSQL cluster remain available. The release folder contains the APK, source/build identity, reports, final test logs, 20 screenshots, previews and checksums; it contains no database/session files or credentials.

## Repeatable checks

From `full-game/`, with JDK 17 and the installed API 36 SDK:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease -PtambolaApiUrl=https://rooms.example --no-daemon --console=plain
node tools/android-smoke.mjs --online --fault-proxy --label alpha06-full-verified
node tools/android-smoke.mjs --animations --class io.github.sbshrey.tambola.game.NumberMotionTest --label alpha06-motion-final
node tools/android-smoke.mjs --class io.github.sbshrey.tambola.game.AppearanceTest --label alpha06-appearance-large-final
```

The full suite needs the documented isolated PostgreSQL service/proxy and dedicated emulator mappings. `--animations` uses animator scale 1 while disabling only platform window/transition animations; the runner restores all original values afterward. The motion test compares rendered frames before/after settlement, checks that the number is already exposed, checks that motion stops, and checks the app's reduced-motion setting. The ordinary full suite repeats it with duration scale 0. Large-text acceptance requires separately setting 360dp width and 200% text and restoring both settings afterward.

An initial full run passed 16 of 17 cases in 280.924 seconds. Its motion-off assertion exposed a harness assumption: Compose's test recomposer does not automatically inherit the global Android animation setting. The test now explicitly supplies that setting through `MotionDurationScale`, as supported by [the AndroidX test API](https://android.googlesource.com/platform/frameworks/support/+/fd5938364d07a81899bc1afa6d7477fd53315567). This verifies the component at the configured scale; it is not a measurement of the real device compositor. The application APK was unchanged by the test correction. The failed run is retained separately and excluded from passing acceptance.

## Evidence boundaries

The service, protocol, ticket generation, rules and encrypted-persistence implementation are unchanged from alpha05. Their earlier 60 domain/client/server test results and separate cold-process/two-native-client evidence remain historical evidence; they were not all rerun for the appearance change. Android gameplay regression exercises the new UI against that actual local service. Emulator results do not establish physical-phone motion smoothness, TalkBack usability, audio focus/device routing, mobile network switching, hosted TLS/capacity/restore or production-signed update behavior.

Final music/effects/celebrations, avatars, Hindi resources, wider API/device/performance acceptance, large-history deletion and backup suppression, hosting/operations and production signing remain in the [full plan](../docs/FULL_GAME_PLAN.md). No new paid generation, cloud provisioning, remote CI or public release occurred.
