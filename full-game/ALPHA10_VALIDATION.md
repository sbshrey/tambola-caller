# Alpha10: database lifecycle reliability

Rapidly closing and reopening the game could close an offline database while a pause save was still releasing its connection. Alpha10 keeps one Application-owned Room database and propagates normal coroutine cancellation. It preserves the database schema, round format, protocol, rules and UI. See [the reproduction and design](STORAGE_LIFECYCLE.md).

## Candidate

Version **0.10.0-alpha10**, code **10**, package `io.github.sbshrey.tambola.game`, min SDK **26**, target SDK **36**. The APK is **42,279,171 bytes**, SHA-256 `69ce9cacdb4bc95496fedc61749c857dd4583b18dd5111b9938846ef0583553b`. Installed readback matches. The unchanged regression-test APK is `f787199db54aa2610da2c90f5b7ad717f173c99ebf2a2d6285b4632216b476d2` for both the failed diagnostic candidate and passing alpha10 stress run.

V2 signing verifies with the existing debug certificate, SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`. This remains an internal alpha using the loopback online endpoint. It is not production-signed or connected to hosted rooms.

The bounded scan of 747 APK entries found no matching credential/private-key patterns or isolated database fixture password. All 270 voice clips match their packaged manifests; all five WAVs match source and remain uncompressed. All eight native libraries are byte-identical to alpha09, and the exact APK passes 16 KB `zipalign` verification. This does not extend alpha09's runtime checks to untested alpha10 devices or ARM64.

## Executed native checks

The dedicated API 35 AOSP x86_64 emulator reports `Android/sdk_phone64_x86_64/emu64x:15/AE3A.240806.019/12368160:userdebug/test-keys`, 4 KB pages, 1080×1920, density 420 and font scale 1.0. Online cases use the real isolated PostgreSQL/room service and lost-response proxy on this workstation.

| Check against the exact candidate | Result |
| --- | --- |
| Repeated save/close/reopen/delete/create, 40 Activity cycles | 1/1, 60.391 s |
| Complete ordinary native suite | 28/28, 319.831 s |
| Hindi interface after actual process termination | 1/1, 5.280 s |
| Platform-selected English while the app is stopped | 1/1, 5.252 s |
| Tablet portrait: family tickets / custom-prize preview and inspection | 1/1 in 7.621 s; 1/1 in 11.514 s |
| Tablet landscape: same two journeys | 1/1 in 7.834 s; 1/1 in 10.965 s |

The stress fixture failed on its 40th launch with alpha09 behavior plus diagnostics, then passed unchanged against alpha10. Its fixed run logged no storage failures. The complete suite passes both original failing API 35 journeys, plus full offline/custom/online rounds, appearance, audio, tutorial/badges, localization, motion, recovery and encrypted storage. Its diagnostic log contains the deliberately invalid `missing-ticket` action from `GameAudioTest`; that expected domain rejection is not a database fault. No SQLite or cancellation error was observed in that run.

Cold-locale run `43478c12-e6a4-47fc-8e9f-d2d47086cf5e` verifies seed PID 10217 disappears and new Hindi/English processes 10385/10455 preserve the entire marked round (hash `62a1fc188d7665293a8751257a7808b0fc546c58217dc77c94517e7a5477c4bb`) and independent Hinglish caller selection. Original requested locale and all animation settings were restored and checked.

Tablet profiles use 1200×1920 and 1920×1200 at density 240, with normal text. Six screenshots of the marking dialog, custom editor and claim details were reviewed. Content scrolls, dialog actions remain reachable, and fixed caller/Save controls remain visible. The profiles are emulator layout evidence, not physical-tablet, foldable or large-text acceptance. Original screen metrics were restored and verified.

## Builds and provenance

The debug APK/test APK, six app JVM cases and debug lint pass in **1m 59s**. Debug lint has zero errors and two existing warnings: the locale attribute is ignored below API 33 (AppCompat supplies compatibility) and the KAPT-to-KSP advisory. No dependency changes were made. The unchanged domain/client/server test evidence remains scoped to alpha09; this slice does not claim a fresh execution of those 66 cases.

The optimized unsigned release APK/AAB and release lint pass in **2m 30s**, with zero lint errors and the same two warnings. Release compilation uses `https://rooms.example` solely to verify release configuration. It is not a deployed service or signed production artifact. Normal dependency verification remains enabled.

The [alpha09 matrix](DEVICE_MATRIX_VALIDATION.md) retains its exact original binaries and results: API 26/30/36 successes, API 35 storage failures, and the 16 KB fixture-mapping failure plus focused rerun. It is not rewritten as an alpha10 matrix. The diagnostic failed run, sanitized exception locations and exact SQLite binary line mapping are retained with alpha10 evidence. Earlier alpha packages remain unchanged.

## Repeating the checks

With the exact APKs installed on a dedicated emulator and the isolated service/proxy already available:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
adb -s emulator-5592 shell am instrument -w -e tambolaStorageLifecycle true -e class io.github.sbshrey.tambola.game.StorageLifecycleTest io.github.sbshrey.tambola.game.test/androidx.test.runner.AndroidJUnitRunner
node tools/android-smoke.mjs --serial emulator-5592 --online --fault-proxy --label alpha10-full-api35
node tools/android-locale-recovery.mjs --serial emulator-5592 --label alpha10-locale-api35 --apk app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease -PtambolaApiUrl=https://rooms.example --no-daemon --console=plain
```

The smoke runner now verifies ADB reverse mappings for ports 8080/8082 before online/fault tests. The missing-mapping rejection was checked before instrumentation on API 35. External stress/cold-locale/native-loader fixtures are explicitly excluded from its ordinary 28-case suite.

## Remaining production work

Hosted rooms/TLS and operations, production signing, physical phones on independent networks, ARM64/16 KB acceptance, TalkBack and Hindi editorial/listening review, performance/long-session tests, backup/deletion-suppression and restore/rollback drills remain gates in the [full plan](../docs/FULL_GAME_PLAN.md). Optimized build success and emulator passes do not satisfy them. No cloud provisioning, paid generation or public release occurred in this slice.
