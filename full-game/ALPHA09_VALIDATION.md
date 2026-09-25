# Alpha09: English and Hindi interface

This milestone implements an independent interface-language choice for the full game, including static UI, accessibility descriptions, standard prizes, structural custom rules, errors, results and anonymous sharing. Player/custom-prize text is preserved. Caller recordings remain independently selectable. See [localization notes](LOCALIZATION.md) for architecture and editorial boundaries.

## Candidate

Version **0.9.0-alpha09**, code **9**, package `io.github.sbshrey.tambola.game`, min SDK **26**, target SDK **36**. The internal APK is **42,559,944 bytes**, SHA-256 `0b0423bf200f1a3e5dc1d06a40e10658a9822a4399284c24dbb68c92facc95ee`. Its installed readback matches exactly. The instrumentation APK hash is `cb180c7bce049e62ed619fd5b973adb55270919ec65426caf9de5fee645511d7`.

The APK verifies with v2 signing using the existing debug certificate, SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`. This is not production signing. A bounded scan of 747 APK entries found no matching OpenAI credential/private-key patterns; the five WAV assets remain byte-exact and uncompressed. This scan is not a general security audit or listening approval.

The app uses AppCompat 1.8.0 per-app locale handling and Android locale configuration. Dependency verification remains enabled; the metadata change adds hashes for the resolved official AndroidX dependencies. No game rule, protocol version, database schema or saved-round format changed. Standard live-win presentation now carries the prize enum so its title can resolve in the selected language; custom titles remain as entered.

The resource audit covers **608 resources**, comparing English/Hindi keys, plural forms, format placeholders and Unicode integrity. It runs in CI. Native Android resources and all audio remain packaged offline. No translation API call or new paid media generation occurred.

## Executed checks

The final integrated debug build completed in **2m 41s** with **72 passing JVM/service tests**: 25 domain, 11 client, 30 PostgreSQL service and six Android-module JVM cases, no failures or skips. Debug lint reports **zero errors and two warnings**: the locale attribute is ignored below API 33 (AppCompat provides the older-device path), and the existing KAPT-to-KSP advisory. Warnings are not globally suppressed.

The optimized unsigned release APK and AAB build completed in **3m 22s**. Release lint also reports **zero errors and the same two warnings**. That build uses `https://rooms.example` only to exercise release configuration; it is not a deployed service or a signed store candidate.

The exact installed candidate passed **28/28 native tests in 319.029 seconds** on the dedicated Android 11/API 30 emulator against the isolated PostgreSQL/Netty service and response-loss proxy. This includes all 24 earlier cases plus four localization cases. Existing English complete-round, custom rule, rematch, appearance, audio, badge, avatar/win, encrypted session and online recovery journeys still pass.

The new native checks cover: Hindi/English switching through Settings; retained unsaved family setup and custom editor data; unchanged caller voice; retained ticket/call/mark/player/award data; no replayed win after recreation; localized claim inspection and anonymous sharing; standard/structural rule formatting parity; and a real service-confirmed profile deletion whose response is dropped, then retried with the same saved request and credentials after changing language. Hindi player names round-trip through the local service unchanged.

The four localization tests also passed at **360dp width / 200% text in 37.764 seconds**. Ten Hindi screens were reviewed at both normal and enlarged text: Home, Settings, table, custom editor, validation, claim details, results, share dialog, online lobby and pending-action error. Text wraps and longer content scrolls; the main caller/editor/dialog actions remain reachable. Final images show the corrected dark status/navigation icons in the light full-screen editor. The online lobby capture can show its brief connection transition; the integration assertions separately wait for a live room.

Font scale, density, night mode and all animation settings were restored and read back exactly. The owned service/proxy processes were stopped and their two ADB mappings removed. This does not establish cold-process locale persistence, Android 13+ locale-settings integration, physical-device typography or TalkBack acceptance.

An earlier development APK passed both language-switch journeys: unsaved setup/editor data and an active game's identities, tickets, marks and awards survived, while the Hinglish caller preference remained independent. The resource-formatting case initially stopped because its test custom-prize ID lacked the required `custom_` prefix. The fixture was corrected without changing production rules. This earlier run is development evidence, not the final-candidate result.

Visual review of the initial eight Hindi screenshots found readable normal-size layouts and an unrelated light-theme contrast issue in the separate full-screen editor window. Both full-screen editors now synchronize status/navigation icon appearance with their actual background. The final-candidate screenshots verify the correction and enlarged-text control reachability.

## Repeatable checks

From `full-game/`, with JDK 17, SDK 36 and the isolated PostgreSQL test environment:

```powershell
python tools/check-localization.py
.\gradlew.bat :domain:test :client:test :server:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
node tools/android-smoke.mjs --online --fault-proxy --label alpha09-full-final
node tools/android-smoke.mjs --online --fault-proxy --class io.github.sbshrey.tambola.game.LocalizationTest --label alpha09-localization-large
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease -PtambolaApiUrl=https://rooms.example --no-daemon --console=plain
```

The native runner requires already installed APKs, a separately started isolated loopback service, response-loss proxy and ADB mappings. The large-text check additionally uses 360dp width/200% font and restores the prior device settings. Screenshots, build logs and resource checks do not establish physical-device, TalkBack or native-speaker acceptance.

## Production boundaries

This remains an internal debug-signed alpha using a loopback online endpoint. Public room hosting/TLS, operational monitoring/backups/deletion suppression, production signing and store/support material remain unfinished. API 33+ platform locale integration, native-speaker Hindi editing, TalkBack, physical phones/audio routing, wider network faults, performance and long-session acceptance remain gates unless separately evidenced. The optimized release build uses a placeholder HTTPS origin for build validation only.
