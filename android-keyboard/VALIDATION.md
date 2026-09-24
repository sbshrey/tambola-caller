# Tambola Keyboard 1.0.0 validation

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
