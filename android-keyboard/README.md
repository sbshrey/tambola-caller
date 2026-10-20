# Tambola Keyboard 1.4.4 for Android

**One phone. Next number → WhatsApp Send.** Only the host installs the app. Other players read the WhatsApp messages and mark their paper tickets.

**Last published release:** [Download Tambola Keyboard 1.3.1](https://github.com/sbshrey/tambola-caller/releases/download/keyboard-v1.3.1/Tambola-Keyboard-1.3.1.apk) — signed APK, Android 8.0 or newer, approximately 24 MB. Install over the existing app to keep the saved game; do not uninstall first. [Release notes and checksums](https://github.com/sbshrey/tambola-caller/releases/tag/keyboard-v1.3.1).

The 1.4.4 changes below are built from this checkout. A production update must be signed with the existing release key; the debug APK is for a dedicated test device and cannot update the published app. Do not uninstall a live installation to work around a signature mismatch.

## Set up once

1. Open the APK on the phone used for WhatsApp and install it. If Android asks, allow the browser/files app to install it; this permission can be turned off afterward.
2. Open **Tambola Keyboard → Help me set it up**. Tap **Enable Tambola Keyboard**, turn it on in Android settings, then return. Android displays its usual keyboard warning.
3. Tap **Choose Tambola Keyboard** and select it.
4. Tap **Practice safely**, then the practice box. Try **Next number**, then **Clear practice message**. Nothing is sent and the live game stays unchanged.
5. Try your own WhatsApp chat first, then start a new live game before group play.

## Before a game: purchased sets and prize budget

Tap **Set tickets & schemes** on the empty Call screen or in the app. **One set contains 6 tickets and costs ₹50**: one set is ₹50, two sets/12 tickets are ₹100. Use the large −/+ buttons for each player; 0 means that person is not playing. New setup starts at one set per saved player so the host can adjust purchases. All participating players are eligible for every selected scheme, regardless of buying one or two sets. A player still counts once when choosing claimants for a prize.

Tap **Choose ₹10 schemes**. Every selected scheme is fixed at **₹10**. Select **five schemes per purchased set** to allocate all ticket sales. There are no amount-edit or equal-split controls. The screen shows the selected/required count and how many more prizes to select or remove. Use **Add extra ₹10 prizes** to enter distinct names, one per line, when you need more choices than the catalog provides. Extra prizes appear in **Other prizes**; no prizes are invented automatically.

The collected total and planned prize total must match exactly before **Confirm setup · ready to call** becomes available. For example, twelve purchased sets collect ₹600, so select 60 prizes at ₹10 each. Eleven sets need 55 prizes; two sets for each of eleven players need 110 prizes. Returning to the chat enables the first call. Tickets and scheme amounts stay fixed during that game; players with 0 sets cannot claim. Editing names is still allowed. Existing installations can keep calling without ticket tracking until the host opens this optional planner.

Each new round keeps the previous purchased-set counts and schemes, clears results, and requires a fresh review/confirmation before calling. Adjust purchases and the number of selected schemes for the new game. The ₹50 price and six-ticket set size are fixed for this group's rules. Up to 20 sets per player and 100 sets total (500 prize schemes) are supported. A game already in progress from an older version retains its recorded amounts; new rounds use ₹10 for every scheme.

The full collection is allocated **before play**. A Bogie still pays ₹0 under your rule. Money for Bogies or schemes not awarded remains unpaid; the results image shows that balance and does not silently redistribute it.

## Share the setup and start message

After confirming tickets and schemes, open **Game messages** on the keyboard's Call screen or in the app's main menu. The app opens this screen after confirming setup; setup opened from the keyboard returns to the chat.

- **Players & tickets** lists participating players, purchased sets, individual ticket counts and each purchase amount, plus totals. A two-set purchase is shown as **2 sets (12 tickets) · ₹100**. Players with zero sets are omitted.
- **Selected schemes** lists every enabled scheme and its prize, the full prize budget, eligibility and shared/Bogie rules. Disabled schemes are omitted.
- **Start message** announces the game, participant/ticket totals, prize pool and claim rules. It does not draw a number. Send it, then return to **Call → Next number** when the group is ready.

Check the preview. **Insert** fills an empty chat draft; tap **WhatsApp Send** yourself. Existing drafts are protected, and a changed preview must be reviewed again. The app's **Share** buttons open Android's chooser so you can choose WhatsApp and the group.

These messages require a confirmed setup with the full collection allocated. During play, **Share… → Game messages** still offers the player and scheme lists; the start announcement is unavailable after the first call. A new round requires confirmation again before sharing. Practice does not offer live game messages.

## Play the game

Open the right WhatsApp group, tap its message box, and select Tambola Keyboard. Tap the large green **Next number** button. A number such as 47 appears as **4️⃣7️⃣**. Tap **WhatsApp Send**. Repeat.

- **Call** returns to the main button. Swipe up inside the keyboard to reach lower controls, especially with larger system text.
- **Hear again** plays the current number on this phone. **Insert again** puts the same number into an empty message box without drawing another.
- **ABC** opens Android's keyboard picker for normal typing.
- **Board** shows all 90 numbers. Pink means called; dark pink marks the latest.
- **Voice** chooses English, Hindi or Hinglish, and turns automatic speech on/off. All 270 AI-generated recordings are bundled for offline use. No API key or new audio generation is needed. Button labels remain in English.
- **Clear message…** is always available below Next number. Confirm **Yes, clear message** to remove an unsent draft, including selected text, while keeping the call and saved prizes. Use **Insert again** to put that same number back.
- **Undo last number → Yes, undo number** corrects an accidental call inside the keyboard. It returns the number to the pool and clears its unsent number text. Other draft text is preserved. Awards and Bogies recorded on that call are cleared; earlier results stay.
- If a draft already contains text, the keyboard says **Send or clear the current message first** and does not overwrite it or draw another number. Send it, or use **Clear message…**. Clearing and undoing never delete sent messages.

The keyboard and its setup app use one saved live round. Practice is separate. The website still runs its own independent game; use one caller for the whole round. Saved progress records generated calls, not confirmed delivery.

## Share pictures, recent numbers or audio

After sending the current number, tap **Share** in the keyboard:

- **Insert recent numbers** adds the last ten calls, latest first, as emoji text to the open chat.
- **Number picture**, **Recent numbers picture** and **Board picture** create separate PNGs. These contain numbers, not player names.
- **Voice clip** shares the current recording in the selected language as an MP3 attachment. It is not guaranteed to appear as a WhatsApp voice note.

The keyboard first checks whether the focused app accepts that attachment type. Where supported, it inserts the attachment into the current editor. Otherwise Android's share chooser opens: **WhatsApp → your group → Send**. Check the destination and preview. A chooser may require selecting the group again. The app cannot confirm delivery. File sharing is disabled in practice.

Pictures and audio can also be shared from the app's **See board / New game** screen. Media is prepared off the UI thread; an editor change cancels a pending keyboard handoff. Shared files use temporary, individually granted read-only content URIs. Files older than 48 hours are cleaned up when preparing another share; receiving apps may retain their own copies.

## Players and prizes are optional

### Tap the prize and winner — no typing needed

In your chat, tap **Winners → Tap prize + winner → Add family players & schemes** once. This adds Varsha, Rekha, Kamlesh Bhabhi, Laxmi Bhabhi, Leela jiji, Nirmala, Rekha totla, Sandhya, Sangita jiji, Saroj and Suman, plus all 42 catalog schemes including **T**. Existing players, calls and winners are preserved. An older game already in progress retains its amounts. Matching names are reused without changing their spelling; Rekha and Rekha totla remain separate players. Repeating setup preserves renamed family entries and re-enables catalog prizes.

1. Choose a group (Quick prizes, Lines, Boxes, Special prizes, Houses or Other prizes), then a prize.
2. If Varsha is claiming for herself, tap **Claim for Varsha**, then WhatsApp Send. This inserts `🙋 Varsha claims Early 5` with `Please check my ticket.` It does not save a winner or pay a prize.
3. After other players claim in the chat and the host checks their tickets, reopen the scheme and tap **every confirmed claimant**, including Varsha when applicable. A tick marks each selected name; tap it again to deselect. Scroll to reach every name.
4. Review the announcement and prize split. Tap **Review announcement ↑** at the bottom or scroll back to **Insert winner announcement** (one or two claimants) or **Insert Bogie announcement** (three or more). This saves the award and fills the empty chat draft, for example `🏆 Early 5: Varsha` with `₹10 prize`.
5. Check the message, then tap **WhatsApp Send**.

**One claimant receives ₹10; two receive ₹5 each; three or more means Bogie with no payout.** Bogie results list the claimants, appear in shared results, and contribute nothing to prize totals. The app does not read other players’ WhatsApp messages or verify tickets; Varsha selects the confirmed claimants.

No microphone, speech service, Internet or keyboard switch is needed for this flow. Existing drafts are preserved, and a blocked insertion does not save a new award. If an editor rejects text after the award is saved, use **Insert [prize] announcement** to retry. Android can acknowledge queuing text before an editor accepts it, so always check the draft. Choosing names or leaving the screen alone does not save anything. An editor restart, new round or changed prize setup invalidates a pending selection.

Reopen a resolved prize to correct its claimants with **Update winner announcement** or **Insert Bogie announcement**. Correcting paid winners keeps their recorded amount. Reducing a Bogie to one/two valid claimants restores a payout using the configured prize total. Both corrections keep the original call position. Use the existing manual prize controls to clear an award. New games keep the family list and scheme choices and clear both awards and Bogies. Undo clears results recorded on the removed call.

The line/box shorthand is interpreted as individual First–Ninth prizes, allowing games with six or nine of each. Ulti and Seedhi family planning are separate schemes; saved entries using the old Siddi spelling are reused. “Pati patni vah”, “Ilove you” and “10 early” map to the existing Pati patni or vo, I love you and Early 10 names. Turn unused prizes off in Players & prizes.

### Speak the prize and winner

In your WhatsApp group, leave the message box empty. Tap **Winners → Speak prize + winner**. Allow microphone access once, return to your chat, and tap **Speak prize + winner** again. Say:

- **“Early five ki winner Asha Sharma”** → saves Asha as Early 5 winner and inserts `🏆 Early 5: Asha Sharma` with `₹10 prize`.
- **“King ke winners Asha aur Bina”** → saves both and inserts their names with `₹5 each` for a ₹10 prize.
- For Hindi, tap **Speech: English / Hinglish · change** before speaking: **“अर्ली फाइव की विनर आशा शर्मा और बीना”**.

The announcement goes into the current chat draft automatically. **Check the spelling, then tap WhatsApp Send.** There is no automatic sending or delivery confirmation. Recognition can finish when you stop speaking, or tap **Done speaking**. **Cancel listening** discards the request.

Enable the prize first under **Choose prize schemes**. In new games the command uses ₹10 per scheme; speak the scheme and names, not a different amount. Separate claimant names with **and / aur / और**. One/two names create a paid result; three or more create a Bogie with no payout. Exact existing names are reused. Without a ticket plan, new names are added automatically; with a ticket plan, only saved participating players can win. Similar spellings or Hindi/Latin versions are not guessed to be the same person. Stay with one spelling/script or correct names under Players & prize amounts.

All 42 catalog names, the original defaults and custom prize names can be spoken. Common Hindi/Hinglish aliases are included, with number words such as Early five and House one. v1.3.1 also handles joined words such as “Topline” and the speech service's observed “only 5 winner” transcription of “Early five winner”. This is a small command vocabulary, not unrestricted conversational AI. An unclear/ambiguous prize, an already-awarded prize or too few calls leaves awards unchanged. Correct existing awards using the prize's manual dropdowns. If a winner saves but insertion fails, use **Insert [prize] announcement**; do not award the prize again.

Voice typing requires an enabled Android speech recognition service and may require Internet through that service. It is optional; manual winner tracking and number calls still work without microphone permission. Speech stops when the keyboard closes, the editor changes, you navigate away or cancel; a changed round, prize setup or nonempty draft prevents a pending award. Dictation is disabled in practice. No microphone recordings are stored by this app, and no OpenAI key is needed. Phone/provider recognition quality needs a real-device check.

### Choose winners manually

Tap **Winners → Players & prize amounts**, or open that screen from the app. Add names one per line or separated by commas. Use **ABC** / the normal keyboard picker to type names, then choose Tambola Keyboard again when returning to WhatsApp.

- **Winners → Choose prize schemes** offers [all 42 supplied prize names](SCHEMES.md), grouped into quick prizes, lines, boxes, special prizes and houses. Tick choices and tap **Add selected**, or use **Add all 42 schemes** and confirm. Current calls and winners stay. Already-active choices cannot be accidentally unticked here; turn unwanted prizes off under Players & prizes after clearing any winners. Existing matching prizes reuse their IDs and awards. A game already in progress retains its saved amounts.
- Early 5, Top line, Middle line, Bottom line and Full house start at **₹10 total**. Add custom ₹10 prizes, change names, or turn unused prizes off. With a ticket plan, choose schemes before the first call; they stay fixed during play.
- After checking a paper ticket, tap a prize and choose one or two players from the dropdowns. Use **Tap prize + winner** for three or more claimants and Bogie. The split is shown before **Save winners**: one winner receives ₹10; two receive ₹5 each.
- Standard line/Early 5 prizes require at least five calls; Full house requires fifteen. Custom prizes require one. These limits do not verify a physical ticket: the host checks it.
- Tap **Insert [prize] announcement** or **Insert all results** in the keyboard, then WhatsApp Send. Alternatively, the app's **Results to share** screen opens the share chooser.
- Rename players without losing their awards. Clear wins before removing a winning player or turning off an awarded prize. Each scheme stays at ₹10 in new games. Existing older games retain their saved amounts until the next round.
- Player names are typed or dictated; the app does not read contacts or WhatsApp group members. Limits: 100 players, 500 prize schemes, 2 paid winners per scheme (3+ claimants = Bogie), ₹10 per scheme in new games. No payments are made.

## Share the end-of-game results image

In the keyboard, tap **Winners → Share results picture**, check the image, then WhatsApp Send. The same button appears on **Call** after all 90 numbers and on **Board** when results exist. Before resetting, **Share results picture first** is also available on the new-game confirmation screen. You can share results whenever your game finishes, even before 90 calls.

The screen and image group **player names into columns, with only that player's won schemes underneath and their total at the bottom**. Three columns form a group. A shared ₹10 prize appears under both winners with ₹5 and “shared”. Players without a win show “No prizes won” and ₹0. Bogies are listed separately with their claimants and ₹0. The header counts selected schemes not awarded. Ticket-funded games also show purchased sets per player, collections and unpaid prizes. Totals include only recorded payouts.

Swipe the on-screen columns sideways to see all three names. **More wins** and **More players** handle large lists. Exported pictures pack several groups onto a page and split long winning lists onto numbered pages. Each won scheme appears exactly once per winner. Continued columns repeat the player's name and full-game total, with “Continued” above the remaining wins.

A small result fits one PNG. Large player lists or many prizes produce numbered pages to keep text readable. A supported chat editor receives a single image directly; otherwise choose WhatsApp and your group in Android's share chooser. For multiple pages the chooser shares them together. Nothing is sent automatically. An existing text draft must be sent or cleared first. Sharing does not draw a number or reset results. The full app also offers **Results to share → Share results picture**.

## Undo and new game

**Board → Start a new game → Yes, start new game** resets the round inside the keyboard and returns to **Next number**, keeping you in the chat. New game clears numbers and winners while keeping players, prize settings and voice preferences. An unsent draft is kept until you send it or explicitly clear it. Messages already sent to WhatsApp remain unchanged. Share results before starting another game.

If ticket planning is enabled, **Review tickets & schemes** and confirm the new round's purchases/budget before Next number becomes available again.

**Call → Undo last number** asks for confirmation inside the keyboard. Its exact unsent emoji number is removed, whether selected or with the cursor in the middle. Other text stays. If the editor cannot remove the number draft, the call is kept so the draft and board cannot silently disagree. If the game or prizes change while confirmation is open, review the latest number and start Undo again. If you already sent the call, tell the group about the correction; the sent message stays.

**Board → Undo / Full board** still opens the app's full board and controls. The keyboard refreshes when saved calls change and when the editor reopens, including after all 90 numbers were called. Practice undo/reset changes only practice. **Board → Reset practice round** resets only practice.

## Privacy and compatibility

No accounts, ads, analytics, Internet, contacts, external-storage or accessibility permissions in this app. Optional winner dictation requests `RECORD_AUDIO` at runtime. Android's [speech recognition service](https://developer.android.com/reference/android/speech/SpeechRecognizer) may process speech on remote servers using its own connectivity; offline recognition is not guaranteed. The app does not save microphone recordings or a transcript history. Recognized winner names are saved in the local prize book. The keyboard checks the immediate draft boundary and selected text to avoid overwriting a message; chat text is not stored. Password, non-text and the app's setup fields use the regular keyboard. Private game/settings data is excluded from cloud backup and device transfer. Uninstalling or clearing app data removes saved rounds.

The system-only `BIND_INPUT_METHOD` service permission lets Android bind the keyboard. The non-exported sharing provider grants read access only to a file the host chose to share. Local audio playback does not transmit sound to a group.

Two normal Android permissions (`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) support a short-lived playback service while WhatsApp is open. Playback has a Stop control and ends when the clip finishes, loses audio focus, or the keyboard closes. Number playback uses bundled recordings and needs no microphone. No extra runtime permission prompt is needed for these service permissions.

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
adb -s <emulator-serial> shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.PrizeJsonTest,io.github.sbshrey.tambola.keyboard.QuickWinnerTest,io.github.sbshrey.tambola.keyboard.WinnerSpeechTest,io.github.sbshrey.tambola.keyboard.FeaturesTest,io.github.sbshrey.tambola.keyboard.KeyboardTest,io.github.sbshrey.tambola.keyboard.NewGameNavigationTest,io.github.sbshrey.tambola.keyboard.PrizeCatalogUiTest io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

Run the call-correction suite and the continuous three-game scenario on that same dedicated emulator:

```text
adb -s <emulator-serial> shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.CallCorrectionTest,io.github.sbshrey.tambola.keyboard.FullGameTest io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

`FullGameTest` taps Next number for every draw in three complete 90-number games, corrects one accidental draw in each game, claims and awards prizes, shares a results image, inserts Bogies/results, reopens the editor halfway through, resets, and starts a fourth round. The debug editor's Send button records and clears local test messages; it does not use WhatsApp. Progress is logged under `TambolaFullGame`. `CallCorrectionTest` also supports `-e landscape true` with fixed user rotation enabled, as described in VALIDATION.md. Run `ResultsImageTest` for family totals, image readability, large/Hindi-name pagination, read-only sharing and draft protection.

Run `TicketPlanUiTest` for purchased sets, selection, extra named ₹10 prizes, exact budget approval, eligibility and next-round review. Pass `-e funded true` to `FullGameTest` to exercise three games with mixed one/two-set purchases and a ₹600 budget, including all three Houses and a Bogie. The long test supplies the plan data through the store; the planner UI is covered separately. Ticket-plan JSON is a separate version-1 preference; existing prize records keep their version-2 format.

Run `GameMessagesUiTest` for confirmed player/ticket and scheme messages, start announcements, protected drafts, changed-preview review, rejected insertion, and native text-share intents. It captures sharing locally without sending to another app. The insertion and app-share cases also run with large text and `-e landscape true`; the debug chat editor puts Send beside the composer in landscape. `GameMessagesTest` covers exact totals, disabled schemes/nonparticipants, old amounts, new rounds and maximum-size Hindi lists.

Run the microphone permission UI test separately after revoking the permission on that **dedicated emulator** (revoking a permission can stop the app):

```text
adb -s <emulator-serial> shell pm revoke io.github.sbshrey.tambola.keyboard android.permission.RECORD_AUDIO
adb -s <emulator-serial> shell pm clear-permission-flags io.github.sbshrey.tambola.keyboard android.permission.RECORD_AUDIO user-set user-fixed
adb -s <emulator-serial> shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.VoicePermissionTest io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

Screenshots are written to the debug app's private files directory. Prize storage upgrades version 1 records to version 2 on save while keeping players, amounts and awards; version 2 adds Bogie claimants. Older APKs cannot read version 2, so keep the upgraded app.

Core logic is in `Game`, `PrizeBook`, `FamilySetup`, `GameMessages` and `WinnerCommand`; persistence in `GameStore`/`PrizeJson`; recordings in `CallAudio`; optional recognition in `WinnerSpeech`; PNGs and attachment handoff in `Sharing`/`ShareProvider`; `PrizeViews` is shared by the keyboard and full-screen app. `WinnerSpeechTest` selects a debug-only speech provider and restores the previous provider after each test. It tests Android's recognition callback/binder path without recording audio; the fixture is excluded from the release APK.
