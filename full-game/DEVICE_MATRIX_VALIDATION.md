# Alpha09 device and cold-locale validation

Date: 26 September 2026. This extends [alpha09 validation](ALPHA09_VALIDATION.md) without changing its APK or original release package. The complete production goal remains active.

## Candidate and fixtures

Application source: `7e51823532f8dea5238ffa72d5beafb8eb727b2d`. APK: `releases/0.9.0-alpha09/Tambola-Together-0.9.0-alpha09.apk`, SHA-256 `0b0423bf200f1a3e5dc1d06a40e10658a9822a4399284c24dbb68c92facc95ee`, 42,559,944 bytes. It is the same debug-signed code-9 candidate recorded in the original report.

This slice adds instrumentation and host-side verification. The current test APK SHA-256 is `5ce819a117a52cc961ce9629508c509c4c7a46ac6f4f333e4729ac732329e257`. Earlier evidence retains its actual instrumentation identity:

| Test APK SHA-256 | Evidence |
| --- | --- |
| `4e44dce25c8439ad5583abb0e2323c404753430d1d87c3776c2bcaf5bb2e8f9b` | API 30 cold locale, before the emulator-guard correction |
| `7bdfb2b4ca8193942a858340e3c350f2533fbe1910fc79359883da81682e7692` | API 36 cold locale, before ordinary localization teardown was corrected |
| `cc9511a4f3c74fbc8bb9c332647cbfc01595793ce2c3923d707839663077699b` | API 36 full 4 KB suite and 16 KB cold locale, before adding the separate native-loader fixture |
| `5ce819a117a52cc961ce9629508c509c4c7a46ac6f4f333e4729ac732329e257` | Current test build, including the opt-in native-loader fixture |

All devices are dedicated emulators with fictional fixture data. The host uses Android Emulator 37.3.1; game suites run on one AVD at a time. Service-backed cases use the isolated loopback PostgreSQL database, room service and response-loss proxy; they do not establish hosted or physical-network acceptance.

## Language persistence beyond Activity recreation

The external driver creates a practice round with a Devanagari player name, makes a real ticket mark, selects Hindi through the app UI and retains Hinglish calling. It checks the live seed PID, terminates that process, verifies its disappearance and starts a new native verification process. The private witness compares the entire round, including tickets, calls and marks; host evidence contains only its hash. Calling remains paused and no win celebration replays.

On API 33+, a second check stops the app and selects English with Android's `cmd locale set-app-locales` command. Native verification checks the platform locale, declared supported languages, rendered interface and unchanged caller/game. This checks the platform service integration; it is not automation of the Settings application's screen.

| Device | Case | Process IDs | Native verification |
| --- | --- | --- | --- |
| API 26 / Android 8 | In-app Hindi after termination | 3060 → 3232 | 1/1, 2.841 s |
| API 30 / Android 11 | In-app Hindi after termination | 10801 → 11025 | 1/1, 6.869 s |
| API 35 / Android 15 | In-app Hindi after termination | 2305 → 2516 | 1/1, 5.385 s |
| API 35 / Android 15 | Platform English while stopped | 2305 seed → 2607 | 1/1, 5.238 s |
| API 36 / Android 16 | In-app Hindi after termination | 3195 → 3352 | 1/1, 5.111 s |
| API 36 / Android 16 | Platform English while stopped | 3195 seed → 3423 | 1/1, 5.248 s |
| API 36 / Android 16, 16 KB | In-app Hindi after termination | 3096 → 4036 | 1/1, 13.316 s |
| API 36 / Android 16, 16 KB | Platform English while stopped | 3096 seed → 4480 | 1/1, 11.316 s |

The successful API 36 run ID is `0e90a85f-1632-4041-b0f3-827f0fb759be`; both cases retain round hash `93aff6a978960bd78be9c15d4ed82f453757f70cc5b6e0df64fa5e15126da28c`. Original requested locales and all three animation settings were restored and verified. Screenshots of restored Hindi on both API levels and platform-selected English on API 36 were visually reviewed.

Two earlier API 36 attempts exposed harness issues. The official AOSP image reports uppercase `SDK`, which the old case-sensitive emulator check rejected before fixture mutations. A shared check now accepts case variations while requiring `ranchu`/`goldfish` hardware. A later run passed Hindi recovery but stopped because `cmd locale help` returned 255 despite displaying valid usage. The driver now invokes the actual platform command and checks its result. Neither failed attempt is counted as a complete acceptance pass, and no app code was changed to make either pass.

The first full API 36 run stalled after the deletion/language test's assertions. A SIGQUIT thread dump placed the instrumentation thread in `LocalizationTest.resetLanguage`, waiting inside Espresso's next-frame synchronization; the main thread was in its message loop and the app showed confirmed profile deletion. That run was explicitly stopped and is not a passing suite. Teardown now closes the tested Activity before restoring the original device-language selection, then verifies the selection directly. The rebuilt test APK completed the affected case in **10.515 seconds**, including cleanup. This changes no game behavior or assertion of the deletion flow.

## Complete game suite

| Environment | Page size | Result | Instrumentation |
| --- | --- | --- | --- |
| Android 8 / API 26, AOSP x86_64 | 4 KB | 28/28, 273.388 s | `5ce819a1…` test APK |
| Android 15 / API 35, AOSP x86_64 | 4 KB | 26/28, 288.107 s; storage failures require investigation | `5ce819a1…` test APK |
| Android 11 / API 30, original alpha09 acceptance | 4 KB | 28/28, 319.029 s | Original release test APK; see original report |
| Android 16 / API 36, AOSP x86_64 | 4 KB | 28/28, 325.322 s | `cc9511a4…` test APK |
| Android 16 / API 36, Google APIs x86_64 | 16 KB | 27/28 in 354.465 s; failed case passed separately in 12.658 s after fixture repair | `5ce819a1…` test APK |

The API 36 final run covers the same 28 gameplay tests as the original alpha09 suite: appearance, avatars/wins, standard/custom complete rounds, tutorial/badges, languages, recorded audio/music/focus, number/win motion, family play, local recovery/history, private-room host/guest/reconnect/deletion and encrypted-store integrity. Specialized external process/pair/update fixtures are excluded and have their own evidence.

API 36 uses `Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1/13818094:userdebug/test-keys`, at 1080×1920, density 420 and font scale 1.0. After the final pass, installed app/test hashes were read back, original animation settings were verified, loopback mappings were removed and the owned AVD was stopped. The final instrumentation build succeeded in 35 seconds; unchanged application/JVM/release-build evidence stays in the original report.

API 26 uses `Android/sdk_phone_x86_64/generic_x86_64:8.0.0/OSR1.180418.004/4931640:userdebug/test-keys`, at 1080×1920, density 420 and default font scale. Cold restoration and the complete 28-case suite passed against the minimum supported Android version. The restored Hindi screen was visually reviewed. Installed hashes were read back, original unset animation settings restored, mappings removed and the AVD stopped. Its older shell has no `getconf`; `/proc/self/smaps` reports a 4 KB kernel page size and the metadata labels that observation method explicitly.

### Open API 35 storage finding

API 35 uses `Android/sdk_phone64_x86_64/emu64x:15/AE3A.240806.019/12368160:userdebug/test-keys`. Its cold-locale checks passed, but the 28-case suite had two failures: offline setup did not return Home after deleting saved rounds, and the profile-deletion journey could not create its prerequisite offline round. The latter's captured screenshot shows **The round could not be saved. Please try again.** The test did reach the save action; this cannot be dismissed as merely a missed UI click or missing network mapping. The app log also records a SQLite pool closing with one connection still in use; that observation is a lead, not a proven cause.

The profile-deletion case passed unchanged on its own in 14.667 seconds. That focused pass does not resolve the storage finding or turn the earlier run into a clean pass. API 35 acceptance remains open until the storage/lifecycle cause is isolated and regression-tested. No app change is part of the completed alpha09 matrix results above. A test-library update was considered during diagnosis but reverted before any build once the storage-error screenshot was inspected.

Subsequent alpha10 work reproduced a closed-connection save and locked history query during rapid Activity close/reopen, then moved database ownership to the Application. [The lifecycle investigation](STORAGE_LIFECYCLE.md) identifies its distinct diagnostic and fixed candidates. Those later results do not turn this original alpha09 run into a pass.

## 16 KB environment and native loading

The Google APIs experimental x86_64 image is `google/sdk_gphone16k_x86_64/emu64xa16k:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`. Both shell `getconf PAGE_SIZE` and the native-loader fixture's `Os.sysconf(_SC_PAGESIZE)` report **16384**. The fixture also asserts that the process is 64-bit. Before installing/testing, the owned AVD's `bionic.linker.16kb.app_compat.enabled` was set to `false` and `pm.16kb.app_compat.disabled` to `true`, following [Android's page-size testing guidance](https://developer.android.com/guide/practices/page-sizes). Original values were recorded for restoration.

The focused `NativeLibraryTest` explicitly loaded `androidx.graphics.path` and `datastore_shared_counter`: **1/1 passed, 0.052 seconds**. This validates loading their packaged x86_64 binaries; it does not exercise every native API or establish ARM64 behavior. The additional instrumentation build succeeded in 42 seconds. The ordinary game suite excludes this fixture because it requires an explicitly configured 16 KB device.

Static inspection found 16 KB LOAD alignment in all eight packaged `.so` files, and Build-Tools 36 `zipalign -v -c -P 16 4` passed. The stricter RELRO end-address formula in Android's guidance flags these prebuilts. Inspection of their actual writable LOAD ranges found no additional writable bytes between each RELRO end and the next 16 KB boundary; the x86_64 loading check above also passed. Retain that distinction: these results are not a claim that the simple RELRO formula passed or that untested ARM devices have been accepted.

The 16 KB cold-locale run ID is `57e1f825-5bcc-4289-9e2c-312552a11658`, retaining round hash `a14d1786ec83130398789106a7ac42268f826d53309dc7f153cc48a4293a1519`. An initial attempt encountered a failed settings read immediately after first boot, before fixture mutation; after verifying the settings service was ready, the recorded run passed with locale/animation cleanup verified.

The first 16 KB gameplay run reached profile registration with no ADB reverse mappings; the host service was healthy but unreachable at the app's loopback origin. That localization case timed out. The missing mappings were restored while the suite was still completing offline checks; all remaining cases, including online host/guest/deletion and encrypted storage, passed. The affected localization case then passed unchanged in a separate run. Therefore this is **27 passing cases plus a passing focused rerun**, not a clean 28/28 run. Both logs are retained. The smoke runner now checks device mappings for ports 8080/8082 before starting an online/fault-proxy run.

That preflight was checked on API 35 before creating any reverse mappings: it rejected the run with the expected missing-port-8080 message before instrumentation. The expected rejection is retained separately and is not counted as a passing game case. After mappings were created, the normal suite could start.

After these checks, installed hashes and the disabled fallback properties were read back. Original compatibility properties and animation settings were restored, owned port mappings removed and the 16 KB AVD stopped. Its source, artifacts and x86_64 scope remain distinct from physical ARM64 acceptance.

## Repeatable commands

Install the exact candidate and the test APK on a dedicated `tambola_full_game_*` AVD before running the driver. See [localization notes](LOCALIZATION.md) for fixture behavior and [the service guide](server/README.md) for the isolated database setup.

```powershell
node tools/android-locale-recovery.mjs --serial emulator-5586 --label alpha09-locale-api36-complete --apk releases/0.9.0-alpha09/Tambola-Together-0.9.0-alpha09.apk
node tools/android-smoke.mjs --serial emulator-5586 --online --fault-proxy --label alpha09-full-api36-final
```

For the explicit loader check, first configure a dedicated 16 KB emulator as described above and install the current instrumentation APK, then run `adb -s <serial> shell am instrument -w -e tambolaNative16kb true -e class io.github.sbshrey.tambola.game.NativeLibraryTest io.github.sbshrey.tambola.game.test/androidx.test.runner.AndroidJUnitRunner`. Check the JUnit result, not just the ADB exit code. Restore both compatibility properties afterward.

The ordinary smoke suite excludes the externally coordinated locale fixture. Seed instrumentation is intentionally killed and is not reported as a passing test. The locale driver restores device settings even when verification fails and records cleanup failures as failures.

## Remaining boundaries

The remaining API matrix, tablet/foldable layouts, real phones including ARM64/16 KB devices, native-speaker Hindi review, TalkBack, physical audio routing, performance, production signing and hosted operations require their own evidence. A passing emulator test or static APK check does not satisfy those gates.
