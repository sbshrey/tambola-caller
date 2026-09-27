# All-screen landscape source validation — 27 September 2026

The main activity now requests `sensorLandscape` from launch, covering onboarding, lobby, settings and gameplay in either landscape direction. The old gameplay-only orientation override is removed. The application declares its game category.

`./gradlew.bat :app:compileDebugKotlin :app:lintDebug --console=plain` completed successfully (exit 0). The merged debug manifest retains the orientation and game category. `validation.json` records the exact source hashes and parent commit; compilation ran with these source changes uncommitted.

Lint reports **0 errors and 88 warnings**: 75 unused resources, 8 plural candidates, and one each for target API, compile SDK, locale configuration on older APIs, kapt usage and fixed orientation. The fixed-orientation warning is relevant to this change and is retained rather than suppressed. The compiler also reports an existing always-true instance check in `OnlineViewModel.kt`, which this change does not edit. This is not a warning-free build.

The [Android 16 behavior documentation](https://developer.android.com/about/versions/16/behavior-changes-16) describes the game-category exception to its large-screen orientation changes. This manifest check does not establish device/OEM behavior. Verify launch, settings, gameplay, both landscape directions, cutouts and large text on supported devices after the native design work.

**No APK was assembled or installed for this validation.** The installed alpha22 app and Wi-Fi server are unchanged. The [Figma design and browser preview](../../../designs/landscape-lobby-v1/README.md) remain separate review artifacts; their visual redesign is not yet implemented in the native app. The earlier alpha22 endurance result does not cover this source change.

Evidence: `compile-lint-transcript.txt`, `lint-results-debug.xml`, `merged-debug-manifest.xml` and `validation.json`.
