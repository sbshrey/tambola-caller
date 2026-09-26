# Alpha11: readable game-data and deletion information

Alpha11 adds **Your game data** in Settings and on the online screen before registration. The English/Hindi explanation covers local storage, service records and retention, shared records left after deletion, the independent recovery record, sign-out/reset/retry differences, prerecorded AI voices and aggregate service health metrics. Section headings are exposed as accessibility headings; text scrolls while the close button stays fixed. Opening the view survives Activity recreation without creating a guest or changing a saved round or nickname.

The deletion confirmation now states that the separate recovery record keeps a random profile ID, one-way confirmation code and deletion timestamps, contains no display name/avatar/sign-in secret, and currently has no automatic expiry. It distinguishes that lifetime from the original request's 30-day confirmation window. The success notice also explains why recovery data remains. This documents implemented behavior; provider backup/journal retirement, public privacy/support information and store readiness remain unfinished.

## Candidate and compatibility

Version **0.11.0-alpha11**, code **11**, package `io.github.sbshrey.tambola.game`, min SDK **26**, target SDK **36**. The APK is **42,307,879 bytes**, SHA-256 `4be92c9c229e1d67e87851fae7507988167d66fa1f53184160f8a646715b5870`. Final instrumentation APK SHA-256: `21abd69c9496a66030ebc1faf28744f914a3f999251b83bfab6ba0ab8d7b9506`.

V2 signing verifies with the previous debug certificate, SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`. Protocol **2**, round format **3** and Android database schema **1** are unchanged. No dependency, backend implementation, permission, runtime AI call or paid generation was added. This remains a debug-signed internal alpha using the loopback room endpoint, not a hosted or production-signed release.

All **270 voice recordings** match packaged manifests and all **five WAVs** match source, remaining uncompressed. The eight native libraries are byte-identical to alpha10; exact-candidate 16 KB zip alignment passes. A bounded scan of **747 APK entries** found no matching OpenAI/private-key patterns or isolated database password. These checks do not establish a general security audit or physical ARM64 acceptance.

## Native checks

Dedicated API **30**, x86 emulator `tambola_full_game_api30`, fingerprint `google/sdk_gphone_x86_arm/generic_x86_arm:11/RSR1.201013.001/6903271:userdebug/dev-keys`, 4 KB pages, physical 1080 x 1920/density 420/font 1.0. Online tests use the actual loopback service JAR `fd258243ffd7a05e0a9e2e427d737642f18b8edc1649edb7dfd2c00b8256fd4e`, isolated PostgreSQL and the deliberate response-loss proxy. This app fixture explicitly uses local-development database privileges without an independent journal; journal/restore/permission evidence belongs to the separately identified [service validations](server/OPERATIONS_VALIDATION.md).

| Exact-candidate check | Result |
| --- | --- |
| Complete ordinary native suite | 30/30, **312.369 s** |
| Data view in English/Hindi at 360dp / 200% text | 2/2, **17.835 s** |
| English profile deletion, dropped response, retry and offline preservation at 360dp / 200% | 1/1, **15.421 s** |
| Hindi deletion, language change and original-request retry at 360dp / 200% | 1/1, **11.500 s** |

Twenty original screenshots at normal and 200% text were visually reviewed, with contact sheets retained. Both languages wrap within the dialogs, the explanatory content reaches its end, and close/confirm/keep actions remain visible. The normal-size Hindi confirmation fits completely without scrolling. Activity recreation preserves the data view and underlying state. Font, density and animation settings were restored and verified; installed APK and instrumentation readbacks match the hashes above.

After validation, the exact owned Java service (PID 10512), Node proxy (PID 26400) and emulator reverse mappings for 8080/8082 were removed. Their listeners are gone; the original API 30 emulator and isolated PostgreSQL cluster remain available. No physical device was modified.

The two new cases verify that Settings content and the pre-registration link survive recreation, a nickname stays editable without creating a profile, and the current round's identity/tickets/calls/marks remain unchanged. Existing live-service deletion tests assert visible fixed actions before and after scrolling the expanded confirmation; the actual committed deletion response is dropped and the original encrypted request is retried successfully. They retain peer continuation and offline-game preservation checks.

## Builds and test-fixture corrections

The debug APK/test build, all **six app JVM cases**, and debug lint pass in **2m 31s**. The localization checker passes **625 resources** with matching English/Hindi keys, plurals and formatting arguments. Debug lint has zero errors and the two existing warnings: pre-API-33 locale configuration and KAPT-to-KSP guidance.

The optimized unsigned release APK/AAB and release lint pass in **4m 25s**, with zero errors and the same two existing warnings. The build uses `https://rooms.example` only to validate HTTPS release configuration, a two-processor/1.5 GiB Gradle JVM and one build worker. Those unsigned placeholder-service artifacts are not distributed as production builds. Normal dependency verification remains enabled.

The initial native run executed 30 cases in **326.580 seconds** with **29 passes and one failure**: the new test matched identical build-status text in both Settings and its dialog. Restricting the selector to a dialog fixed the fixture without changing APK bytes. That instrumentation hash was `ad477e19bc1528aa42c7903a5d8e91116a961f1500f0278b3a0a8d6455bdd56d`.

The first large-text run then passed both data-view cases in **16.772 seconds**, but the separately launched English online fixture timed out in setup after **23.195 seconds** because AppCompat had persisted Hindi from the previous process. The saved locale file and screenshot confirmed Hindi; no deletion was attempted. The fixture now explicitly selects English before using English labels. Intermediate instrumentation hash: `824d821ca0ab14500d113ba3fa5c359977402035da544f6c29515442019327c3`. The failed run restored font/density/animation settings and remains separate evidence. The final fixture also captures the recovery paragraph itself, rather than only its heading.

An intermediate large-text run passed all four cases (17.520 / 14.539 / 11.359 seconds; instrumentation `cf5a4d7f53696db99911ea18f403660cd4ee8377d228da8357c56113833b7579`), but screenshot review showed its deletion end-images were captured before the scroll animation advanced. A wall-clock screenshot delay does not advance Compose's test clock. The helper now waits for idle and asserts the final scroll offset before capture. Its focused normal-text deletion check passes in **13.989 seconds**. The repeat ordinary suite was deliberately stopped with `am force-stop` during CustomRulesGameTest to rebuild this fixture; its reported process termination is intentional and is not an application crash finding or completed acceptance run.

With instrumentation `695ec47f01842ce48abdc1f23aafc8bd6a0ac9ad2ca5b39577f1779bc9161e22`, all four large-text cases passed with verified scroll endpoints (16.961 / 14.745 / 11.360 seconds). The next ordinary suite completed in **357.698 seconds**, with **29 passes and one fixture failure**: normal-size Hindi text already fit completely, but the new helper unnecessarily demanded a positive scroll range. Its screenshot shows the entire confirmation and both actions. The helper now accepts zero as a valid end offset when no scrolling is needed, while still requiring overflowing content to reach its maximum offset.

Corrected instrumentation/lint builds passed in **55 seconds**, **47 seconds**, **46 seconds** and **48 seconds**. The final source then passed the complete 30-case suite and all four focused large-text cases reported above. Earlier failed, intentionally stopped and incomplete screenshot runs are retained separately; they are not presented as final acceptance.

## Repeating and release limits

With the exact APKs installed on the dedicated emulator, local service/proxy running, and reverse mappings for ports 8080/8082:

```powershell
python tools/check-localization.py
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
node tools/android-smoke.mjs --serial emulator-5582 --online --fault-proxy --label alpha11-full-api30
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease -PtambolaApiUrl=https://rooms.example --no-daemon --console=plain
```

The packaged `source/run-alpha11-large.py` records its exact emulator assumptions, selects the focused cases and restores font/density/animation settings. Place it under `.test-workspace` in the checkout to repeat it. The explanation is bundled and available offline from Settings.

The [full plan](../docs/FULL_GAME_PLAN.md) retains hosting/TLS/secret/alert/backup acceptance, bounded journal retirement, large-history deletion and broader load/fault/soak testing, invite links, dependency advisory review, physical phones on independent networks, ARM64/16 KB runtime, TalkBack, Hindi editorial/listening and production signing/support/store gates. Earlier device matrices retain their original APK versions; this API 30 result does not re-label them as alpha11 acceptance. No remote CI, cloud deployment or public release occurred.
