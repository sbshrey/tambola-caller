# Tambola Keyboard 1.1.1 for Android

**One phone. Next number → WhatsApp Send.** Only the host installs the app. Other players read the WhatsApp messages and mark their paper tickets.

**[Download Tambola Keyboard 1.1.1](https://github.com/sbshrey/tambola-caller/releases/download/keyboard-v1.1.1/Tambola-Keyboard-1.1.1.apk)** — signed APK, Android 8.0 or newer, approximately 24 MB. Install over the existing app to keep the saved game; do not uninstall first. [Release notes and checksums](https://github.com/sbshrey/tambola-caller/releases/tag/keyboard-v1.1.1).

## Set up once

1. Open the APK on the phone used for WhatsApp and install it. If Android asks, allow the browser/files app to install it; this permission can be turned off afterward.
2. Open **Tambola Keyboard → Help me set it up**. Tap **Enable Tambola Keyboard**, turn it on in Android settings, then return. Android displays its usual keyboard warning.
3. Tap **Choose Tambola Keyboard** and select it.
4. Tap **Practice safely**, then the practice box. Try **Next number**, then **Clear practice message**. Nothing is sent and the live game stays unchanged.
5. Try your own WhatsApp chat first, then start a new live game before group play.

## Play the game

Open the right WhatsApp group, tap its message box, and select Tambola Keyboard. Tap the large green **Next number** button. A number such as 47 appears as **4️⃣7️⃣**. Tap **WhatsApp Send**. Repeat.

- **Call** returns to the main button. Swipe up inside the keyboard to reach lower controls, especially with larger system text.
- **Hear again** plays the current number on this phone. **Insert again** puts the same number into an empty message box without drawing another.
- **ABC** opens Android's keyboard picker for normal typing.
- **Board** shows all 90 numbers. Pink means called; dark pink marks the latest.
- **Voice** chooses English, Hindi or Hinglish, and turns automatic speech on/off. All 270 AI-generated recordings are bundled for offline use. No API key or new audio generation is needed. Button labels remain in English.
- If a draft already contains text, the keyboard says **Send or clear the current message first** and does not overwrite it or draw another number. Send it, or tap **Clear unsent message… → Yes, clear message**, then **Next number**. Clearing asks for confirmation and does not change called numbers or sent messages.

The keyboard and its setup app use one saved live round. Practice is separate. The website still runs its own independent game; use one caller for the whole round. Saved progress records generated calls, not confirmed delivery.

## Share pictures, recent numbers or audio

After sending the current number, tap **Share** in the keyboard:

- **Insert recent numbers** adds the last ten calls, latest first, as emoji text to the open chat.
- **Number picture**, **Recent numbers picture** and **Board picture** create separate PNGs. These contain numbers, not player names.
- **Voice clip** shares the current recording in the selected language as an MP3 attachment. It is not guaranteed to appear as a WhatsApp voice note.

The keyboard first checks whether the focused app accepts that attachment type. Where supported, it inserts the attachment into the current editor. Otherwise Android's share chooser opens: **WhatsApp → your group → Send**. Check the destination and preview. A chooser may require selecting the group again. The app cannot confirm delivery. File sharing is disabled in practice.

Pictures and audio can also be shared from the app's **See board / New game** screen. Media is prepared off the UI thread; an editor change cancels a pending keyboard handoff. Shared files use temporary, individually granted read-only content URIs. Files older than 48 hours are cleaned up when preparing another share; receiving apps may retain their own copies.

## Players and prizes are optional

Tap **Winners → Players & prize amounts**, or open that screen from the app. Add names one per line or separated by commas. Use **ABC** / the normal keyboard picker to type names, then choose Tambola Keyboard again when returning to WhatsApp.

- Early 5, Top line, Middle line, Bottom line and Full house start at **₹10 total**. Add custom prizes, change names/amounts, or turn unused prizes off before or during the game.
- After checking a paper ticket, tap a prize and choose one or two players from the dropdowns. The split is shown before **Save winners**: one winner receives ₹10; two receive ₹5 each. Odd amounts split to paise, for example ₹11 gives ₹5.50 each.
- Standard line/Early 5 prizes require at least five calls; Full house requires fifteen. Custom prizes require one. These limits do not verify a physical ticket: the host checks it.
- Tap **Insert [prize] announcement** or **Insert all results** in the keyboard, then WhatsApp Send. Alternatively, the app's **Results to share** screen opens the share chooser.
- Rename players without losing their awards. Clear wins before removing a winning player or turning off an awarded prize. Editing a default prize changes future awards; edit its winners in the full app to change a recorded payout.
- Player names are entered manually; the app does not read contacts or WhatsApp group members. Limits: 100 players, 20 prize schemes, 2 winners per scheme, ₹0–₹100000 per prize. No payments are made.

## Undo and new game

**Board → Start a new game → Yes, start new game** resets the round inside the keyboard and returns to **Next number**, keeping you in the chat. New game clears numbers and winners while keeping players, prize settings and voice preferences. An unsent draft is kept until you send it or explicitly clear it. Messages already sent to WhatsApp remain unchanged. Share results before starting another game.

**Board → Undo / Full board** opens the app. Undo asks for confirmation, returns the last number to the pool and clears awards recorded on that call. You can also start a new live game here. Return to your chat and tap its message box to continue. The keyboard refreshes when saved calls change and when the editor reopens, including after all 90 numbers were called. In practice, **Board → Reset practice round** resets only practice.

## Privacy and compatibility

No accounts, ads, analytics, Internet, microphone, contacts, external-storage or accessibility permissions. The keyboard checks the immediate draft boundary and selected text to avoid overwriting a message; message text is not stored. Password, non-text and the app's setup fields use the regular keyboard. Private game/settings data is excluded from cloud backup and device transfer. Uninstalling or clearing app data removes saved rounds.

The system-only `BIND_INPUT_METHOD` service permission lets Android bind the keyboard. The non-exported sharing provider grants read access only to a file the host chose to share. Local audio playback does not transmit sound to a group.

Two normal Android permissions (`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) support a short-lived playback service while WhatsApp is open. Playback has a Stop control and ends when the clip finishes, loses audio focus, or the keyboard closes. This meets Android 15's audio-focus requirement without microphone access. No extra runtime permission prompt is needed for these service permissions.

WhatsApp and a physical phone still need an acceptance check. Direct attachment insertion varies by the receiving app. See [VALIDATION.md](VALIDATION.md) for tested scope and a short phone checklist.

## Build and test

Java 17, Android SDK 35, AGP 8.7.3, Gradle 8.9. Set `ANDROID_HOME` or an ignored `local.properties`. No production libraries; AndroidX/JUnit are test-only. Gradle copies the existing website voice packs to generated assets and verifies all 270 file hashes against their manifests. It never calls OpenAI.

```powershell
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./build-release.ps1
```

The release helper reuses `%LOCALAPPDATA%\TambolaKeyboard\signing\tambola-release.jks`, alias `tambola`. Its password is protected with Windows DPAPI for this user and passed only through temporary environment variables. Keep the same signing key for Android updates. Back up key/password securely; the DPAPI password file alone is not portable. Never commit signing files.

The helper exports the signed APK, `INSTALL.txt` and `SHA256SUMS.txt` into the ignored `releases/<version>/` directory, so a Gradle clean does not remove the installer.

To use another signing key, set `TAMBOLA_KEYSTORE_PATH` and `TAMBOLA_KEYSTORE_PASSWORD` and run `./gradlew.bat assembleRelease lintRelease`. Without signing variables, Gradle produces an unsigned release: do not distribute it.

Instrumented tests reset both game stores. Use a dedicated emulator, never a phone with an active game. Install debug and test APKs, enable/select the IME, then run:

```text
adb -s <emulator-serial> shell am instrument -w -r io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

Screenshots are written to the debug app's private files directory. Core logic is in `Game` and `PrizeBook`; persistence in `GameStore`/`PrizeJson`; recordings in `CallAudio`; PNGs and attachment handoff in `Sharing`/`ShareProvider`; `PrizeViews` is shared by the keyboard and full-screen app.
