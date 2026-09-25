# Tambola Keyboard 1.3.1: actual speech-service testing

Validated on 25 September 2026, version code 6. Testing the published v1.3.0 APK through the real Android Google speech service found two command-matching problems: “Early five” was transcribed as **“only 5”**, and “Top line” as **“Topline”**. Both left the award unchanged and displayed a retry message. v1.3.1 accepts joined scheme words and the observed “only 5” variant when followed by an explicit winner marker. Collisions with a custom Only 5 prize still require clarification; unknown prizes are not guessed.

**24 JVM tests passed**, including new regressions using those exact transcripts, partial-name rejection and ambiguity protection. Release lint passed. The signed v1.3.1 APK was installed on the dedicated Android 11/API 30 emulator, and all three audio-to-editor cases passed using the real Google speech recognition service:

| Spoken sample | Inserted result |
| --- | --- |
| Early five winner Asha Sharma | Early 5: Asha Sharma — ₹10 prize |
| Top line winners Asha and Bina | Top line: Asha & BINA — ₹5 each |
| अर्ली फाइव की विनर आशा शर्मा और बीना | Early 5: आशा शर्मा & बिना — ₹5 each |

These tests used Windows Indian English/Hindi synthesized WAV samples injected into the emulator's virtual microphone through authenticated localhost gRPC, with the host microphone explicitly disabled. The speech service was **not mocked**. A separate test-chat APK hosted the message field; the caller APK retained its release signature and production permissions. The original installed v1.3.0 APK hash matched the published asset exactly. The SDK's old emulator lacked audio injection, so a newer emulator was extracted into the ignored test workspace; the shared SDK installation was not replaced.

This establishes audio recognition → enabled-prize matching → winner persistence → prize-text insertion for these samples. Reopening the test editor and reinserting the saved Hindi award also passed, with five calls and the ₹5-each split retained. Initial test-harness checks needed to wait for keyboard focus and asynchronous text insertion. It does **not** establish perfect transcription: Hindi **बीना** was recognized as **बिना**, so name spelling still needs review before Send. These are synthesized samples, not a person's voice on a physical phone. No WhatsApp account, group or delivery was used. The earlier 27 instrumented tests below cover the unchanged keyboard and permission flows; they are separate from these three new production-APK audio tests.

Test harness, WAVs and baseline logs: ignored `releases/1.3.0/retest/`. Candidate APK and successful audio-test logs/screenshots: ignored `releases/1.3.1/`.

---

# Previous version: 1.3.0 validation

Validated on 25 September 2026. Package version code 5; Android-only update.

- **Speak prize + winner** uses Android's speech service from the keyboard, automatically assigns one or two names to one enabled prize, and inserts the announcement into the current empty editor. Exact existing names reuse their IDs; new names are added. The enabled prize's configured amount is used, including equal two-winner splits. Sending remains a separate WhatsApp action.
- **22 JVM tests passed**, including all 41 scheme labels, custom schemes, Hindi/Hinglish examples, exact player reuse, ₹10/₹5 and odd-rupee splits, disabled/unknown/ambiguous prizes, duplicate or excess names, already-awarded prizes and minimum calls. “King ki winner Mona” correctly treats Mona as a person even when the Mona scheme is enabled. A final parser refinement was checked by all JVM tests and the focused large-text voice UI test.
- **27 Android instrumented tests passed** on the dedicated Android 11/API 30 emulator: a 26-test combined suite plus one separately prepared microphone-permission test. Ten new speech tests exercise `SpeechRecognizer` through a debug-only provider in a separate process: text recognition results → persistent award → actual editor insertion, Hindi names, existing-draft protection, cancel, editor restart, changed draft/game/prizes, network error/retry, Done speaking and repeat-award rejection. Sixteen existing number, new-game, prize, media and catalog tests also passed.
- The permission test denied the system prompt, returned to the same editor and called another number, then granted microphone permission and returned without changing winners. Permission refusal does not block normal calling. The speech tests use synthetic text and **do not record or recognize real microphone audio**. Initial fixture failures were resolved by selecting the IME after runner startup and moving the test provider to another process, as required by Android's calling-permission check.
- The automatic two-winner insertion flow also passed at **130% font size and 360dp width**. Normal and large-text keyboard screenshots were visually inspected; secondary controls scroll. Debug and release lint found no issues.
- Signed release uses the existing certificate. Signed v1.3.0 installed over signed v1.2.0, reported version code 5 and launched successfully; this verifies update compatibility, not physical-device data retention. Release DEX checks confirm that the test editor, speech provider and fixture configuration provider are absent. The package requests optional `RECORD_AUDIO` plus the two existing media-playback permissions; it has no Internet or contacts permission. Android's speech provider may nevertheless process audio online. The website was unchanged; its tests were not rerun.

**No physical-phone speech recognition or WhatsApp session was tested for this update.** Recognition quality, Hindi/Latin spelling, speech-provider availability, connectivity and Android/OEM differences need a phone check. The app does not validate paper tickets or infer whether a spoken claim is true. The host checks the ticket and the inserted text before sending.

Phone acceptance: install over the existing app, enable a prize, and try in your own WhatsApp chat. Test “Early five winner Asha Sharma”, two winners joined by “aur”, and Hindi mode. Verify the configured amount, name spelling, cancellation, existing-draft protection, correction through the prize button, and a new round. If insertion fails after saving, use **Insert [prize] announcement** rather than awarding again.

APK, guide, checksum and local test logs are in ignored `releases/1.3.0/`.

---

# Previous version: 1.2.0 validation

Validated on 25 September 2026. Package version code 4; this update changes the Android keyboard only.

- Added all **41 host-supplied prize names** as optional presets in five groups, plus a confirmed **Add all 41 schemes** action. Existing matching entries are reused; recorded winners and prize amounts stay. The limit is now 60 schemes. Five original defaults plus the full list produce 45 entries because Early 5 is shared and the four other originals remain.
- **14 JVM tests passed**, covering the complete catalog, repeated additions without duplicates, existing custom/renamed prize reuse, preserved payouts, Early 10/House minimum calls, two-winner splitting, new-round preservation, invalid selections and the 60-scheme boundary.
- **16 Android instrumented tests passed** on the dedicated Android 11/API 30 emulator. New UI tests cover cancel/confirm, choosing individual schemes, protecting active prizes, adding all 41, persisted JSON round trips beyond the former 20-scheme limit, and keeping calls/players/winners. Existing keyboard, new-game, media and winner tests also passed.
- A focused scheme-selection/cancel test passed at **130% text size and 360dp width**; the selection dialog was visually inspected. An initial existing practice-test timing failure was addressed by waiting for the rendered call count before asserting, followed by the passing full run.
- Debug and release lint: **no issues found**. Release signature verified with the existing certificate. Signed v1.2.0 installed over signed v1.1.1, reported version code 4 and launched successfully. This installation check verifies upgrade compatibility; it does not independently prove user-data retention across a physical-device upgrade.
- Minimum SDK 26, target SDK 35, and the two existing media-playback permissions are unchanged. The website code was unchanged and its tests were not rerun for this release.

The scheme names come from the user, with obvious spelling/spacing corrections. They are manual tracking options, not inferred regional rules or automatic ticket verification. See [SCHEMES.md](SCHEMES.md) for all names and minimum-call behavior. **No physical-phone WhatsApp session was tested for this update.** APK, guide, checksum and local test logs are in ignored `releases/1.2.0/`.

---

# Previous version: 1.1.1 validation

Validated on 24 September 2026. Package version code 3; the website remains unchanged.

## Reset fix and checks

- Reproduced on v1.1.0: after all 90 numbers were called, resetting the shared round while retaining the visible keyboard left Next number disabled. The added regression failed before the fix and passes with it.
- The keyboard now observes saved-round changes and refreshes on editor/window lifecycle callbacks. A reset restores the Call panel and enabled Next number, cancels an old pending media handoff, and clears the draw debounce.
- Added **Board → Start a new game** with confirmation inside the keyboard. Added **Clear unsent message** with a separate confirmation when an existing draft blocks calling. Neither action sends a message or deletes sent messages.
- **14 Android instrumented tests passed** on the dedicated Android 11/API 30 emulator, including the retained-view regression, cancellation and confirmation of a new round, selected-draft preservation and explicit clearing, first call after reset, player/prize preservation, practice isolation, and a trip from a separate-task editor to the full app's New game and back. Existing winner, image, audio, privacy and persistence checks also passed.
- The new-game/draft-recovery flow also passed at **130% system font size on a 360dp-wide emulator**. Its resulting keyboard screen was visually inspected.
- **10 JVM tests passed**; debug and release lint reported **no issues found**. The signed v1.1.1 APK verifies with the existing signing certificate and installs over signed v1.1.0 successfully. The updated activity launched successfully. This update-install check does not independently prove saved-data retention; shared-store regression tests cover preservation of player/prize setup on reset.
- Release inspection confirmed version `1.1.1`, version code `3`, minimum SDK 26, target SDK 35, unchanged permissions, and no debug editor fixture in the release DEX files.

Release files and SHA-256 checksum are in the ignored `releases/1.1.1/` directory. Website code was unchanged, so its prior 69-test result below was not rerun for this Android-only fix.

The user reported that v1.1.0 worked on their phone before the reset problem. **The exact failing WhatsApp/phone state and v1.1.1 on that phone have not been independently tested.** The emulator regression establishes the retained-view bug and recovery flows, not the root cause of every possible failed call. Try the update in your own chat: call/send, start a new round, call/send again. If an unsent draft remains, send it or explicitly clear it first.

---

# Previous version: 1.1.0 validation

Validated on 24 September 2026. Java 17, Gradle 8.9, AGP 8.7.3, compile/target SDK 35, minimum SDK 26. Website gameplay remains v1.6.

## Current checks

- **10 JVM tests passed:** no-repeat draws, save/undo, exact digit emojis, invalid save rejection, optional prize tracking, two-winner limits, exact ₹10/₹5 and odd-rupee splits, stable winner IDs, prize snapshots and award invalidation on undo/new game.
- **11 Android instrumented tests passed** on a dedicated Android 11/API 30 Google APIs x86 emulator. These cover actual keyboard text insertion, existing/selected draft protection, saved progress, practice isolation/clearing, password protection, all-90 exhaustion, corrupt-save blocking, both winner dropdowns inside the keyboard, results insertion without drawing, shared store/JSON round trips, picture dimensions, read-only sharing URIs/path rejection, recipient MIME negotiation, all 270 packaged recordings, decoding sample clips in each language, and the playback service starting and stopping after a clip.
- Debug and release Android lint: **no issues found**. Signed release builds succeeded. APK signatures verified with the existing v1.0 certificate.
- **69 existing website tests passed**, including all voice-pack hashes; `npm run build` succeeded. The Android build independently checks every bundled clip against its manifest SHA-256.
- A focused practice-flow test also passed at **130% system font size on a 360dp-wide emulator**. Portrait keyboard, winner selections and each PNG type were visually inspected. Secondary controls scroll; Call returns to the main button.
- Installed signed v1.0, generated practice number **29**, upgraded in place to signed v1.1, and verified that **29 / 1 of 90** was retained. The live/practice CSV keys remain unchanged; new prize data is additive.

## Release and privacy

Package `io.github.sbshrey.tambola.keyboard`, version name `1.1.0`, version code `2`. Install over the existing app to preserve rounds. Exported installers, checksums, guide and preview files are kept outside Git in `android-keyboard/releases/1.1.0/`. The checksum identifies the exact built APK; a local build does not imply publication of a GitHub release.

The manifest requests only `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, both for the short-lived voice playback service. It requests no Internet, microphone, contacts, storage or accessibility permissions. The IME is protected by the system's `BIND_INPUT_METHOD`; playback service and file provider are non-exported. Shared files receive individual read-only URI grants. No signing secrets or test APKs are part of the release.

The playback service supports [Android 15 audio-focus requirements](https://developer.android.com/media/optimize/audio-focus). Android documents an [exception for the current input method](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) to background foreground-service start restrictions. This path was exercised on API 30; Android 15 device behavior has not been runtime-tested here.

## Remaining phone acceptance checks

**No physical phone or WhatsApp delivery was tested.** Rich-content tests use an instrumented editor fixture and verify attachment negotiation, not a WhatsApp-specific handoff. The emulator had audio output disabled: audio decoding and playback lifecycle passed, but speaker volume/accent quality were not auditioned in this run. Android 8–10, Android 12+, landscape use, and OEM differences remain outside this runtime validation.

1. Install the APK, enable/select the keyboard and try Practice safely.
2. In your own WhatsApp chat, call two numbers and tap Send after each. Confirm the expected digit emojis arrive.
3. Try Hear again, all three spoken languages, each picture type and a voice clip. If the share chooser opens, select WhatsApp and the chat. Verify the received files.
4. Add two fictional players, award a ₹10 prize to both, and insert the result. Confirm ₹5 each. Switch keyboards and reopen the app to check saved progress.
5. Start a new live game before group play. Keep player names/prizes only if they are the real setup you want.

The keyboard cannot choose a group, confirm delivery or validate a paper ticket. Generation, local playback, attachment preparation and sending are separate actions. Playing a voice clip aloud does not transmit it to WhatsApp.

---

# Previous version: 1.0.0 validation

Validated on 24 September 2026 using Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3 and SDK/build tools 35.

## Automated checks

- **5 JVM tests passed:** all 90 numbers drawn exactly once; round persistence/undo; exact keycap emoji encoding; corrupt-save rejection; immutable snapshots and the last ten calls.
- **4 instrumented tests passed** on a dedicated Android 11 (API 30) Google APIs x86 emulator: insertion into a real Android text editor; nonempty/selected draft protection; saved live calls after restarting the editor; reinsert without drawing; separate practice/live histories; password-field protection; exhaustion at 90; corrupt saves stay blocked with an explanation.
- Debug and signed release builds succeeded. Android lint reported **zero errors**. Dependency-update suggestions for test libraries are nonblocking.
- Existing website: **69 tests passed** and `npm run build` succeeded. Website gameplay and version remain v1.6.

The test suite resets the app's stores. Use a dedicated emulator, never a phone containing an active game. The practice test writes a screenshot to the debug app's private files directory for inspection.

## Release checks

- Signed APK verified using Android `apksigner`; APK Signature Scheme v2 covers the supported Android versions.
- Package `io.github.sbshrey.tambola.keyboard`, version name `1.0.0`, version code `1`, minimum SDK `26`, target SDK `35`.
- Manifest inspection confirmed no requested permissions and no debuggable flag. The IME service requires the system-only `BIND_INPUT_METHOD` permission.
- Installed the signed release APK on the dedicated emulator, enabled/selected the keyboard, opened the practice field, and successfully inserted an emoji call. Visually inspected portrait layout and verified all keyboard controls and the inserted number were visible.
- The release's `SHA256SUMS.txt` identifies its exact APK bytes. Signing material is outside Git; published assets contain only the app, guide, checksum and an emulator screenshot.

Signing certificate SHA-256:

```text
626a0e6adb3f764b80a6d70202599175f81d24b8f930ced4d7415c27ac74110b
```

## Phone acceptance check

WhatsApp itself and a physical phone were **not tested**. An Android text-editor test verifies keyboard insertion, not WhatsApp delivery. Android 8 through 10 and Android 12+ have not been exercised. Device-specific keyboard setup, large system font settings, landscape use, and OEM behavior still need a phone check.

1. Install and enable the keyboard on the WhatsApp phone. Try the app's practice box.
2. Open your own WhatsApp chat, tap its empty message box, select Tambola Keyboard, then tap **Next number → WhatsApp Send**. Confirm only the expected digit emojis arrive.
3. Call a second number; switch to the normal keyboard with **ABC ↔** and back. Confirm the count and latest call survive.
4. Open **Board & setup**, start a new live game, and return to the group before real play.

The keyboard does not observe delivery, choose a group, or send a message. Its called count records generated numbers. Use **Insert again** if insertion fails; avoid drawing a replacement. Website rounds, recordings, PNG sharing and prize tracking remain separate from this keyboard.
