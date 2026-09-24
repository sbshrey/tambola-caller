# Tambola Keyboard for Android

An offline keyboard for a host who calls Tambola numbers in a WhatsApp group. Open the group, tap **Next number**, then tap **WhatsApp Send**. A call such as 47 is inserted as **4️⃣7️⃣**. Players stay in WhatsApp and mark their paper tickets.

Requires **Android 8.0 or newer**. The keyboard is a separate Android app; its game does not sync with the website. Use one caller for the whole round. This first version focuses on emoji numbers, recent calls and the board; website voice recordings, PNG sharing and prize tracking are not in the keyboard.

## Install and try it

1. Download the signed APK from [GitHub Releases](https://github.com/sbshrey/tambola-caller/releases/tag/keyboard-v1.0.0) on the phone used for WhatsApp.
2. Open the download. If Android asks, allow this browser/files app to install this APK, then install **Tambola Keyboard**. You can turn that installation permission off afterward.
3. Open **Tambola Keyboard**. Tap **1. Enable Tambola Keyboard**, turn it on in Android settings, then return. Android displays its standard keyboard warning.
4. Tap **2. Choose Tambola Keyboard** and select it.
5. Tap the **practice message box**, then **Next number** on the keyboard. An emoji number appears. Tap **Clear practice message** to try the next one. Practice has its own saved round; nothing is sent or added to the live game.
6. Open the correct WhatsApp group, tap its message box, and choose Tambola Keyboard if needed. **Next number → WhatsApp Send** is the live-game flow. Test with your own chat first, then start a new live game before the group plays.

## During the game

- Each of 1–90 is drawn once. Progress survives closing the keyboard and restarting the app.
- **Insert again** inserts the latest number without drawing. Use it if the number was generated but did not appear, or if you need to repeat it.
- **Send or clear the current message first** means the draft already has text. Next will not draw or overwrite that draft, including selected text.
- **ABC ↔** opens Android's keyboard picker so you can type a normal message with your usual keyboard.
- **Board & setup** opens the saved board, recent calls, Undo and New game. Return to WhatsApp to continue.
- Undo and New game ask for confirmation. They change local game state; WhatsApp messages already sent remain in the group.
- The keyboard inserts into the focused text field. Your mom chooses the group and taps Send. It does not read group history, detect delivery, switch groups, or send automatically.

The live and practice rounds are independent. Keyboard settings/website choices do not import an existing website round. Reinstalling after uninstall, clearing app data, or moving to another phone removes the local rounds. Install updates over the existing app to preserve them.

## Privacy

The release APK requests no Internet, contacts, microphone, external-storage or accessibility permissions and has no runtime dependencies, analytics or accounts. It does not save message text. Before insertion, it checks one character on either side of the cursor and any selected draft text only to avoid replacing or appending to an existing message. Password fields and non-text fields disable its game buttons. Game history is stored privately on the phone and excluded from cloud backup/device transfer.

The system-only `BIND_INPUT_METHOD` service permission allows Android to bind the keyboard; it is not an Internet or messaging permission. Android's general keyboard warning still appears during setup.

## Build and test

The app uses Java 17, Android SDK 35, Android Gradle Plugin 8.7.3 and Gradle 8.9. The official Gradle wrapper and its distribution checksum are included. Set `ANDROID_HOME` to your SDK path (or use an ignored `local.properties`). Runtime dependencies: none. AndroidX/JUnit dependencies are test-only.

```powershell
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./build-release.ps1
```

`build-release.ps1` creates/reuses the signing key outside the repository under `%LOCALAPPDATA%\TambolaKeyboard\signing`. Its password is protected with Windows DPAPI for the current Windows user and is passed to tools through temporary environment variables, never command-line literals. Keep that signing key: Android requires the same certificate for updates. Back it up securely, with a recoverable password backup if moving Windows accounts or machines; the DPAPI file alone is not portable. Do not commit signing files.

To supply your own signing key, set `TAMBOLA_KEYSTORE_PATH` and `TAMBOLA_KEYSTORE_PASSWORD`, use key alias `tambola`, and run `./gradlew.bat assembleRelease lintRelease` directly. A release build without those variables produces an unsigned APK; do not distribute it.

Instrumented tests use a **dedicated emulator** and reset both game stores. Enable/select this IME on that emulator, then run the test APK with `adb -s <emulator-serial> shell am instrument -w -r io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner`. Never run the test suite against a phone containing a real game. See [VALIDATION.md](VALIDATION.md) for the tested scope and remaining phone/WhatsApp checks.
