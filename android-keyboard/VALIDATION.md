# Tambola Keyboard 1.4.7: open Bogies and correct winners during play

Validated on 20 October 2026. Version code 14, local branch `feat/tap-winner-announcements`.

## Behavior covered

- Three or more claimants records **Bogie with no payout**, but the scheme stays open at its full prize amount. The next attempt starts with no names selected. Varsha can claim again, and spoken winner entry accepts the open scheme. **Use previous Bogie claimants** reloads the prior names if the host wants to correct that decision.
- During play, reopen **Winners → Tap prize + winner → the scheme**, remove the wrong name or tap **Choose different winners**, select the correct names, then **Update winner announcement**. Corrections replace the saved award and recalculate player totals and future images without counting the prize twice. The manual dropdowns follow the same rules. Already-sent messages and pictures remain unchanged.
- Correcting a paid award preserves its recorded amount and original call position. A new award after Bogie uses the scheme amount and current call position. Undoing that later call clears that award; undoing a later unrelated call keeps a corrected earlier award. Changing a paid result to Bogie removes its payout and reopens the scheme.
- Progress images count Bogies within **Remaining**, with previous claimants and the amount still available. A later winner moves the scheme to **Won**. Final results count open Bogies as unawarded and pay them ₹0. Results show the latest decision, not a history of every attempt. Existing stored Bogies become claimable without a data-format migration.
- Number calling, draft clearing, undo, claims, winner corrections and image sharing remain inside the keyboard. Pre-game player/ticket/scheme setup opens the larger Tambola setup screen from the keyboard and returns to the chat. The host still checks and sends prepared messages in WhatsApp.

## Checks

All **62 JVM tests** passed with no failures, errors or skips. Debug, test and release builds passed. Debug/release lint passed with one existing `SetTextI18n` warning for the “Playing this game” label and no errors. Release inspection confirmed version 1.4.7/code 14, no debug editor/speech fixtures, and a valid debug APK signature.

All **18 distinct Android scenarios** passed: 13 in `validation/android.log` (204.733 seconds) and five in `validation/android-corrections.log` (180.018 seconds). The coverage manifest lists each passing case. The real keyboard flow uses a confirmed ₹150 plan with three players and 15 prizes: three claims → Bogie → next call → fresh Varsha claim → Varsha win → next call → replace Varsha with Rekha. It verifies ₹10 total, Varsha ₹0, Rekha ₹10, the original award call position, persistence and undo. Actual PNG handoffs are compared pixel for pixel with the current saved state. Choosing replacement names alone does not change the saved award.

Additional coverage includes two-winner splits, repeat Bogies, reopening a paid result, manual dropdown correction, spoken Bogie entry, older JSON records, invalid data, draft protection, pre-game sharing gates, next-game reset, 500 schemes and long Hindi names. The mixed **42-scheme image remains one 1200 × 3866 PNG**, with 31 remaining (including two Bogies), 11 won and ₹110 paid. This synthetic layout fixture is separate from the funded game scenario. The one-page assertion passed again in `android-layout.log` (1.014 seconds).

The Bogie/correction controls also passed at **160% font scale (65.668 seconds)** and in **landscape (125.475 seconds)**. Screenshots were inspected for readable labels and reachable selection/announcement controls; both runs saved and inserted the corrected award. Default display settings were restored.

There were no failed test runs for this update. The three full 90-number games under 1.4.2 remain prior evidence; they were not repeated for this change. Physical-phone speech recognition and real WhatsApp delivery remain unverified. The debug editor records locally; chooser tests intercept/cancel sharing without sending to another person.

## Reproduce and artifacts

Use a dedicated API 35 ARM64 emulator after boot and unlock complete. From `android-keyboard`:

```sh
ANDROID_HOME=/path/to/android/sdk bash ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug lintRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.BogieClaimUiTest,io.github.sbshrey.tambola.keyboard.SchemeStatusImageTest,io.github.sbshrey.tambola.keyboard.SchemeStatusUiTest,io.github.sbshrey.tambola.keyboard.ResultsImageTest#fortyTwoSchemeResultsFitReadablePlayerColumnsWithExactShares,io.github.sbshrey.tambola.keyboard.ResultsImageTest#bogieOnlyGameKeepsNamesWithZeroTotalsAndNoWinningEntries,io.github.sbshrey.tambola.keyboard.PrizeJsonTest,io.github.sbshrey.tambola.keyboard.QuickWinnerTest#reinsertAndCorrectAwardKeepRecordedPayoutAndCallPosition,io.github.sbshrey.tambola.keyboard.QuickWinnerTest#threeOrMoreClaimsAnnounceBogieAndCanBeCorrected,io.github.sbshrey.tambola.keyboard.WinnerSpeechTest#threeSpokenClaimantsSaveBogieWithNoPrizeMoney,io.github.sbshrey.tambola.keyboard.FeaturesTest#twoWinnerDropdownsShowSplitAndSaveToSharedStore io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

`releases/1.4.7/` contains the newly signed release APK, debug-signed test APK, unsigned release APK, checksums, installation notes, public certificate and source commit. `validation/` contains build/test logs, the Android coverage manifest, JVM/lint XML, screenshots and generated pictures. `run-displays.py` repeats the Bogie and correction controls at 160% text and in landscape, restoring defaults afterward; adjust its SDK path on another machine.

At the user’s explicit request, a new RSA-3072 release key was created outside the repository in a password-protected PKCS12 keystore, alias `tambola`, with 30-year certificate validity. Its directory is owner-only (700), and files are owner-only (600). The generated password is in a separate plaintext file protected by filesystem permissions; the private folder includes encrypted-backup and reuse instructions. No signing secret is in Git. The APK verifies with signature schemes v2 and v3. Every non-signature ZIP entry matches the tested unsigned release byte for byte; package alignment also verifies.

The signed release passed a fresh install, launch, same-key reinstall and second launch on the dedicated API 35 emulator (`validation/release-install.log`). Its launch screenshot was inspected. Only disposable debug test data was removed from that emulator. This checks package installation and launch; it does not establish migration from the original signing key or retention of a real saved game.

Nothing was pushed or published. This new signing key cannot update the existing installation signed with the original key. The new APK is for fresh installations; subsequent updates to those installations must reuse this new key. Keep Mom’s live installation intact.

---

# Tambola Keyboard 1.4.6: choose this round's players and revise setup

Validated on 20 October 2026. Version code 13, local branch `feat/tap-winner-announcements`.

## Behavior covered

- **Playing this game** includes or excludes a saved player without deleting their name. Checking starts at one purchased set; −/+ adjusts purchases. **Add players** is available directly in setup. Zero participants cannot confirm or start.
- Before the first number, **Game messages → Change players & schemes** reopens setup, including after confirmation or preparing/sending a start announcement. Changes invalidate confirmation; selected ₹10 prizes must again match the full ticket collection. The host reconfirms and shares fresh messages/images. Already-sent messages remain unchanged.
- Current participants, purchases and selected schemes drive the revised player/ticket list, scheme list, start message and status picture. Ticket-funded results omit saved players sitting out while keeping participating players with no wins. Names remain available for later games. First-call locking and next-round review remain enforced.

## Checks

All **61 JVM tests** passed without failures, errors or skips. Debug, test and release builds and debug/release lint passed. Release inspection confirmed version 1.4.6/code 13, no debug editor/speech fixtures, and a valid debug APK signature.

All **14 distinct Android scenarios** have passing final results across the logs listed in `validation/android-coverage.txt`: five game-message scenarios, seven ticket-plan scenarios and two native results-image regressions. The late-change flow prepares an original start message, swaps Rekha for Suman, changes Varsha to two sets, replaces Early 5 with King, selects 15 ₹10 prizes for ₹150, reconfirms and checks all three revised messages. It compares the handed-off PNG pixel for pixel with the current saved game, verifies participant-only results, calls the first number, checks setup locking, then rejoins a player in the following round. The separate add-player scenario verifies two sets, sitting out, rejoining with one set, and blocking a game with no players.

The add-player/participation scenario also passed at **160% font scale (66.217 seconds)** and in **landscape (69.567 seconds)**. Native screenshots were inspected for readable labels, purchase controls and the empty-roster confirmation gate. Default display settings were restored.

The initial runs exposed test-harness issues: image support was enabled after the editor advertised its accepted types; Android capitalized the dialog's SAVE NAMES label; and some assertions read preferences before the tap callback saved them. Tests now enable image support before rebinding, identify the positive dialog button by its Android ID, and await saved state. Failed runs and screenshots are retained. No assertions were removed and no production behavior was changed to make these tests pass. The final participation run passed in 39.799 seconds and the full late-change run in 129.947 seconds. Existing message, eligibility, budget, next-round, persistence, recovery, upgrade and image cases passed as recorded in the coverage manifest.

The three full 90-number games under 1.4.2 remain prior evidence; they were not repeated for this setup change. Physical-phone and real WhatsApp delivery remain unverified. The editor records locally and chooser tests intercept/cancel sharing without sending to another person.

## Reproduce and artifacts

Use the dedicated API 35 ARM64 emulator after boot/unlock completes. From `android-keyboard`:

```sh
ANDROID_HOME=/path/to/android/sdk bash ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug lintRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.GameMessagesUiTest,io.github.sbshrey.tambola.keyboard.TicketPlanUiTest,io.github.sbshrey.tambola.keyboard.ResultsImageTest#fundedResultsShowPurchasedSetsAndUnpaidBogieBalance,io.github.sbshrey.tambola.keyboard.ResultsImageTest#fortyTwoSchemeResultsFitReadablePlayerColumnsWithExactShares io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

`releases/1.4.6/` contains the debug-signed test APK, unsigned release APK, checksums, installation notes and source commit. `validation/` includes build/test logs, the Android coverage manifest, JVM/lint XML and screenshots. `run-displays.py` repeats participation testing at 160% text and in landscape, restoring defaults afterward; adjust its SDK path on another machine.

Nothing was pushed or published. Updating the existing phone installation requires the original release signing key. Do not uninstall the live app to install the debug test APK.

---

# Tambola Keyboard 1.4.5: remaining schemes and winner updates

Validated on 20 October 2026. Version code 12, local branch `feat/tap-winner-announcements`.

## Behavior covered

- **Share scheme status picture** is available near the top of Winners, under Call → Share…, and in the app's Results to share screen, including before any winner is saved.
- Each snapshot lists selected **Remaining** schemes first with amounts, **Won** schemes with winner names and individual payouts, then **Bogie** schemes with claimants and no payout. Disabled schemes are omitted. Two-column cards keep ordinary lists compact; long lists paginate with repeated counts and section headings. Exceptionally long Bogie lists continue across pages without losing text.
- A claim or an unsaved selection does not close a scheme. Only the host's saved decision moves it out of Remaining. The image does not claim to track WhatsApp delivery. Each share uses the latest saved state, so corrections, undo and new rounds are reflected in later pictures. Before the first call, all selected schemes are remaining.
- Funded games require a confirmed matching plan. Existing drafts are protected, sharing does not change calls/prizes/ticket setup, and a changed game/editor during asynchronous preparation invalidates the handoff. Single pictures can be inserted into supported editors; several pages use Android's chooser with read-only URI grants. The host chooses the recipient and sends.
- The inspected 42-scheme sample is one 1200 × 3930 PNG: 29 remaining, 11 won (including three shared awards), two Bogies, and ₹110 paid after 35 calls. It is synthetic sample data from a no-plan game, not a funded setup or a real result.

## Checks

All 10 Android cases passed in 110.096 seconds (seven new scheme-status cases and three final-results regressions). The two display scenarios passed again at 160% font scale (33.875 seconds) and in landscape (50.656 seconds); screenshots were inspected.

The JVM suite passed all 60 tests with no failures/errors/skips. Debug/test/release builds and debug/release lint passed. Release inspection confirmed version 1.4.5/code 12, min SDK 26, target 35, no debug editor/speech fixtures, and a verified debug APK signature.

New native image cases cover all 42 schemes before play, a mixed game, all schemes resolved, 500 schemes with long Hindi names, 100 maximum-length Bogie claimant names, exact coverage across pages, PNG decoding and read-only access. New UI cases exercise a real Varsha claim, draft protection, a shared winner announcement, fresh PNG equality with the saved game, a Bogie correction, new-round reset, blocked unconfirmed setup, approved pre-game sharing, and the app's single/multiple-image chooser. The chooser is intercepted and cancelled without sending to another app or person. Existing final-results image/provider/handoff cases are included as regression checks after sharing infrastructure reuse.

The three full 90-number games documented under 1.4.2 remain prior evidence; they were not repeated for this sharing feature. Physical-phone and real WhatsApp delivery remain unverified.

## Reproduce and artifacts

Use the dedicated API 35 ARM64 emulator after boot/unlock completes and no system error dialog covers it. From `android-keyboard`:

```sh
ANDROID_HOME=/path/to/android/sdk bash ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug lintRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.SchemeStatusImageTest,io.github.sbshrey.tambola.keyboard.SchemeStatusUiTest,io.github.sbshrey.tambola.keyboard.ResultsImageTest#familyResultsPaginateWithAllNamesAndGrandTotal,io.github.sbshrey.tambola.keyboard.ResultsImageTest#keyboardSharesPictureWithoutDrawingOrChangingAwardsAndProtectsDrafts,io.github.sbshrey.tambola.keyboard.ResultsImageTest#imageShareIntentsGrantOnlyReadAccessToEveryPage io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

`releases/1.4.5/` contains the debug-signed test APK, unsigned release APK, checksums, installation notes and source commit. `validation/` contains test/build logs, unit/lint XML, native PNG samples and screenshots. `run-displays.py` repeats the new keyboard entry point/plan gate and app single/multiple-image sharing at 160% font scale and in landscape, restoring default settings afterward. Adjust its SDK path on another machine.

Nothing was pushed or published. Updating the existing phone installation requires the original release signing key. The test APK cannot replace it; do not uninstall the live app to work around the signing mismatch.

---

# Tambola Keyboard 1.4.4: player columns with their winnings

Validated on 20 October 2026. Version code 11, local branch `feat/tap-winner-announcements`.

## Behavior covered

- Results on screen and in PNGs place names above individual player columns. Each column lists only that player's won schemes and actual payout amounts, with the full-game total underneath. Three columns form a group; several groups fit on an image. Players without prizes remain visible with ₹0. Bogies and their claimants appear separately with no payout.
- Shared ₹10 schemes appear under both winners as ₹5 each. Older recorded amounts retain exact paise. Selected but unawarded schemes are counted in the header. Purchased sets, collection and unpaid prizes remain visible for funded games.
- Long lists continue onto numbered images, repeating player names, a continuation label and full-game totals. Images stay at or below 4096 pixels high. The screen pages through 20 wins per player and 12 players at a time; horizontal scrolling reveals all three columns in a group.
- The sample with all 42 catalog schemes resolved across 11 players fits one 1236 × 3298 PNG: 40 paid schemes, six shared awards, two Bogies and ₹400 total paid. Its ₹550 setup includes 13 additional selected prizes not awarded; ₹150 remains unpaid. This is synthetic sample data, not a real game's results.

## Checks

| Check | Result |
| --- | --- |
| JVM suite | 55 passed, no failures/errors/skips |
| Android results suite | All 9 passed in 111.844 seconds |
| Large text and landscape | Results screen and More wins navigation passed at 160% font scale (10.877 seconds) and in landscape (34.909 seconds); screenshots inspected |
| Debug, test and release builds | Passed |
| Debug/release lint | No issues |
| Release inspection | Version 1.4.4/code 11; min SDK 26, target 35; debug editor/speech fixtures excluded; debug APK signature verified |

Android coverage includes the 42-scheme sample, 100 long Hindi player names with 60 shared schemes (all 120 winning entries present exactly once), one player winning all 500 supported schemes, a Bogie-only game with zero totals, funded balances, on-screen navigation, read-only single/multiple PNG intents, keyboard image insertion, draft protection and sharing before a reset without altering calls or awards. Every generated page in the large fixtures is rendered and checked for its height bound. Native PNGs and screenshots were inspected.

Two initial runs were interrupted after keyboard-focus setup failures. The emulator was still unlocking during the first launch, and Android Messages/System UI ANR dialogs subsequently covered the test editor. Logs and a screenshot retain that evidence. The unrelated Messages process was closed on the test emulator and the System UI dialog dismissed; the unchanged 9-case suite then passed. No assertion was removed or production keyboard behavior altered to make the tests pass.

The three complete 90-number games documented under 1.4.2 remain prior evidence. They were not repeated for this results-layout-only change. Physical-phone and real WhatsApp delivery remain unverified.

## Reproduce and artifacts

Use the dedicated API 35 ARM64 emulator after boot/unlock completes and the home screen has no system error dialogs. From `android-keyboard`:

```sh
ANDROID_HOME=/path/to/android/sdk bash ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug lintRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.ResultsImageTest io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

`releases/1.4.4/` contains the test APK, unsigned release APK, checksums and installation notes. `validation/` contains logs, unit/lint XML, native PNG previews and screen captures. `run-displays.py` repeats the results screen/navigation scenario at font scale 1.6 and in landscape, restoring default settings afterward; adjust its SDK path for another machine.

Nothing was pushed or published. Updating the existing phone installation requires its original release signing key. The debug-signed test APK cannot replace it; do not uninstall the live app to work around the signing mismatch.

---

# Tambola Keyboard 1.4.3: setup and start announcements

Validated on 20 October 2026. Version code 10, local branch `feat/tap-winner-announcements`.

## Behavior covered

- **Game messages** is available before the first call in the keyboard and from its Share panel during play, plus the app's main menu. Confirming setup in the app opens Game messages; setup opened from the keyboard returns to the chat.
- **Players & tickets** lists participating names, purchased sets, individual ticket counts and each purchase amount, plus total players/sets/tickets and collection. Two sets appear as 12 tickets/₹100. Zero-set players are omitted.
- **Selected schemes** lists every enabled prize with its amount, total prize budget, eligibility and payout/Bogie rules. Disabled prizes are omitted. Tests include all 500 schemes and 100 Hindi player names without truncation.
- **Start message** uses the confirmed totals and explains how to claim. One winner gets ₹10, two ₹5 each, and three or more valid claimants are Bogie with no payout. Inserting/sharing does not draw a number or award a prize; the host uses Next number after sending. Start messages are unavailable after a number is called or a result is recorded.
- All messages require a confirmed setup with the full collection allocated. Existing approved games retain their actual amounts in lists. New rounds require confirmation again. Practice does not offer live game messages.
- Each message has a preview. The keyboard inserts into an empty draft and protects existing text; the app opens the Android text-share chooser. No automatic sending occurs. A changed preview requires another review/tap. Failed editor insertion leaves setup and game progress unchanged.

## Checks

| Check | Result |
| --- | --- |
| JVM suite | 55 passed, 0 failures/errors/skips, including 7 new announcement cases |
| Android regression | 25 passed in 279.503 seconds: all 4 new message cases plus ticket-plan, number-correction and keyboard regressions |
| Large text and landscape | Two cases passed at 160% font scale (353.779 seconds). Native app sharing passed in landscape; the final keyboard scenario passed in a focused rerun (159.091 seconds). Previews and controls inspected. |
| Debug, test and release builds | Passed |
| Debug/release lint | No issues |
| Release package | 1.4.3/code 10, min SDK 26, target 35; debug editor/speech fixtures excluded; test APK signature verified |

The message tests exercise all three insertions, duplicate-tap draft protection, the first draw afterward, resharing lists during play, the blocked start message during play, missing/unconfirmed plans, new-round review, rejected editor insertion, changed names requiring a refreshed preview, and all three native text-sharing intents. The app test confirms setup through the UI before sharing. A share-chooser monitor captures the actual ACTION_SEND payload and returns cancellation without contacting another app or recipient.

The first focused run passed 3/4 cases. One assertion expected Android to report an editor rejection synchronously. The remote InputConnection can acknowledge queuing first, so the test fixture now records actual commit attempts and verifies the draft stays empty and saved state unchanged. The corrected test passed in the clean 25-case run; production insertion behavior was unchanged. Landscape testing exposed the debug editor's vertically stacked Send button being clipped beneath the IME; a screenshot and window hierarchy confirmed this. The debug-only editor now puts Send beside its composer in landscape. Subsequent failures were in test navigation: fast swipes skipped buttons, and the visibility finder incorrectly required a one-line notice to meet the 44 dp button-height threshold. The driver now uses slow overlapping drags, checks twice before accepting a scroll boundary, and uses a text-sized visibility threshold for nonclickable text. It scrolls to the blocked-start notice and retains the assertion and subsequent list-resharing check. The affected case was rerun separately. Failure diagnostics capture the editor before orientation cleanup. No production keyboard layout change was needed.

The three complete 90-number game runs reported under 1.4.2 remain prior evidence; they were not repeated for this message-only change. This update checks the message-to-first-call transition and existing calling/reset/undo cases. Physical-phone and real WhatsApp delivery remain unverified.

## Reproduce and artifacts

Use a dedicated API 35 Android emulator; instrumentation resets its saved rounds. Validation used the existing ARM64 Pixel 4 emulator, Android build-tools 35, AGP 8.7.3, Gradle 8.9 and OpenJDK 20.0.1 (Java 17 source compatibility).

From `android-keyboard`:

```sh
ANDROID_HOME=/path/to/android/sdk bash ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug lintRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.GameMessagesUiTest,io.github.sbshrey.tambola.keyboard.TicketPlanUiTest,io.github.sbshrey.tambola.keyboard.CallCorrectionTest,io.github.sbshrey.tambola.keyboard.KeyboardTest io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

`releases/1.4.3/` contains the test APK, unsigned release APK, checksums and installation notes. `validation/` contains logs, XML unit/lint reports, screenshots and `run-displays.py` (adjust its local SDK path). Display runs repeat the insertion and native app-sharing scenarios at font scale 1.6 and in landscape. The runner restores default font size and rotation handling.

Nothing was pushed or published. Updating the existing phone installation requires the original release signing key. The test APK is debug-signed and cannot replace that installation; do not uninstall the live app to work around the signature mismatch. The website was unchanged.

---

# Tambola Keyboard 1.4.2: ₹10 schemes and transposed results

Validated on 20 October 2026. Version code 9, local branch `feat/tap-winner-announcements`. This supersedes the editable prize allocation described in the 1.4.1 report below.

## Behavior covered

- **Every new-game scheme is ₹10:** amount editors and equal allocation are removed. One winner receives ₹10, two receive ₹5 each, and three or more remain Bogie with no payout. The same rule applies to custom schemes and the next round.
- **Allocate the full collection:** each ₹50 purchased set requires five selected ₹10 prizes. Approval remains blocked for under/over allocation. The setup shows selected/required counts and lets the host add distinct extra prize names, one per line. Eleven sets need 55 schemes; two sets for every family player need 110. No extra prizes are invented automatically. Capacity is now 500 schemes and 100 total sets per new game, with 0–20 sets per player.
- **Upgrade safety:** an older round in progress retains its approved amounts and recorded payouts, including an older plan above the new set limit. A new round clears results, resets all scheme defaults to ₹10 and requires review. An older approved setup with no calls is converted and must be confirmed again. Existing no-plan calling remains available.
- **Transposed screen and PNG:** awarded schemes and Bogies are rows; saved players are columns. Cells show actual per-player payouts, shared prizes show ₹5 in each winning column, and full-game totals appear at the bottom. Players with no payout remain visible. Unawarded schemes are counted in the header. Funded games include purchased sets, collections and unpaid prizes.
- **Large results:** the screen scrolls horizontally and pages through schemes/player groups. Images repeat headings, scheme labels and full-game totals on every page, with at most 12 players per image and a 4096-pixel height limit. Tests verify that all 6,000 player/scheme cells in a 100-player, 60-scheme Hindi fixture appear exactly once. A separate fixture awards all 46 family/default schemes to one player to check the requested many-wins layout.

## Checks

**48 JVM tests and 62 distinct Android cases passed across the main and focused reruns**, including the three-game scenario. Display runs repeat the relevant cases.

| Check | Result |
| --- | --- |
| JVM tests | 48 passed, 0 failures/errors/skips |
| Planner/results focused run | 11 passed in 108.715 seconds: ₹10 selection, extra names, exact approval, eligibility, new-round review, recovery, totals, pagination, screen layout, read-only image sharing and draft protection |
| Broader regression and upgrade case | 47/50 initially passed; all 3 failing cases passed after test-driver synchronization fixes in a 55.643-second focused run |
| Three full games | Passed in 715.084 seconds: three complete 90-number games, one accidental draw undone per game, Varsha's claim, a shared award, single winners, Bogie, all three Houses, editor restart at 45, image/text results, cancel/confirm reset, and the first call of a fourth game |
| Ticket/payout accounting during those games | 12 sets collect ₹600 and select 60 ₹10 prizes. The scenario records five paid schemes (₹50), one Bogie (₹0), and leaves 54 unawarded; ₹550 remains unpaid. Varsha/Rekha switch their one/two-set purchases for the second game. This explicitly tests unpaid prize reporting; the app does not automatically award paper tickets. |
| Large text and landscape | Both cases passed at 160% font scale (48.962 seconds) and in 2280×1080 landscape (91.586 seconds); actual prize rows and page navigation were inspected. |
| Debug, test and release builds | Passed |
| Debug/release lint | No issues found |
| Release inspection | Version 1.4.2/code 9, min SDK 26, target 35; debug editor and test speech fixtures excluded |
| Test APK signature | Verified with apksigner |

An initial image-handoff check exposed that including every unawarded scheme made a small result unnecessarily span multiple pictures. The final layout uses awarded/Bogie rows and an unawarded count; all focused cases then passed. The broader suite exposed test-driver timing and partial-target problems in prize navigation, new-game confirmation and undo snapshot checks. The harness now waits for the destination/confirmation screen and saved state, and requires a larger visible tap target; assertions were retained. All three focused reruns passed. One full-game attempt stopped before its first call because Android timed out binding the IME after a new instrumentation process; the test setup now refocuses the editor with a user-equivalent tap. The subsequent complete three-game run passed. Visual inspection then shortened the results-screen instructions so actual prize rows are visible sooner at large text sizes. The final large-text and landscape runs rechecked those rows. An initial landscape test also stopped its scroll search too early after a screen transition; a final upward search finds controls above the viewport, and the entire landscape setup/results run then passed.

## Reproduce and artifacts

Use a dedicated emulator: these tests reset its saved rounds. Validation uses API 35 ARM64 (Pixel 4), Android build-tools 35, AGP 8.7.3, Gradle 8.9 and OpenJDK 20.0.1; source compatibility is Java 17.

From `android-keyboard`:

```sh
ANDROID_HOME=/path/to/android/sdk bash ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug lintRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.TicketPlanUiTest,io.github.sbshrey.tambola.keyboard.ResultsImageTest io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e funded true -e class io.github.sbshrey.tambola.keyboard.FullGameTest io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

`releases/1.4.2/` contains the debug-signed test APK, unsigned production APK, checksums, installation guide, logs and result previews. `validation/run-remaining.py` records the full-game/display sequence; adjust its SDK/repository paths for another machine. Large-text runs use font scale 1.6. Landscape uses `wm fixed-to-user-rotation enabled` and `-e landscape true`; the runner restores font scale 1.0 and default rotation afterward.

No production release was pushed or published. The original signing key is required to update the existing phone installation. The test APK cannot replace it; do not uninstall the live app to bypass the signature mismatch. No physical-phone, real WhatsApp delivery/compression, or live speech-provider test was performed in this update. The local editor exercises actual IME calls and image handoff without sending messages to anyone. The website was unchanged.

---

# Tambola Keyboard 1.4.1: ticket budgets, corrections and results pictures

Validated on 20 October 2026. Version code 8, local branch `feat/tap-winner-announcements`. This Android update builds on the 1.4.0 tap-winner flow documented below.

## Behavior covered

- **Ticket purchases:** one set is six tickets for ₹50. Each saved player can have 0–20 sets; zero means not playing. One-set and two-set players can win every selected scheme, and each person counts once in a claim.
- **Before play:** select schemes by group, edit whole-rupee amounts, or split the full collection equally. Any remainder is assigned as ₹1 to the first selected schemes. Setup can only be confirmed when the prize budget exactly matches the collection. The keyboard blocks the first draw until an existing ticket plan is confirmed. Existing installations without a plan retain their calling flow.
- **During play:** purchased sets and selected prize amounts are fixed; names can still be corrected. One winner receives the approved prize, two split it, and three or more are Bogie with ₹0 paid. Zero-set players cannot claim. A new round retains purchases and scheme choices for review, clears results, and requires confirmation again.
- **Correction inside the keyboard:** Clear message removes the unsent draft but keeps the call/results. Undo last number confirms the correction, returns the latest number to the pool, clears its exact unsent number text, and removes awards/Bogies made on that call. Other drafts and earlier awards stay. Selected text and a cursor inside the emoji are covered. A rejected editor deletion prevents undo; a changed game/prize snapshot invalidates the confirmation. Sent chat messages are never altered.
- **Results PNG:** every saved player, purchased sets when tracked, won prizes, individual shares, personal totals and grand total. Bogies appear separately with no payout. A ticket-funded game shows collected and unpaid amounts. Bogie/unawarded money is not redistributed. The family list fits one image; long Hindi names and 100 players/60 schemes paginate within 1080×4096 pixels per page without dropping text.
- **Sharing:** actual IME image handoff to the local editor, existing-draft protection, a share-before-reset action, single/multiple image intents with read-only URI grants, and production exclusion of all debug editor/speech fixtures.

## Checks

**42 JVM tests and 61 distinct Android instrumented cases passed**, including the three-funded-game scenario. Large-text/landscape runs repeat relevant cases. Final recovery changes were checked by the focused recovery test after the broader regression run.

| Check | Result |
| --- | --- |
| JVM unit tests | 42 passed, 0 failures/errors/skips |
| Ticket-planner UI and recovery | All 5 cases passed across the main run and focused recovery rerun: purchases, selection, equal allocation, manual edit, eligibility, frozen budget, persistence, next-round approval and confirmed recovery of damaged prize data |
| Full games without ticket tracking | Three 90-number games passed before the planner was added; fourth round started successfully |
| Full funded games | Three complete 90-number games plus the first call of a fourth passed in 632.926 seconds; mixed one/two-set purchases, ₹600 allocated, ₹590 paid and ₹10 Bogie unpaid per game |
| Android regression suite | 54 passed in 454.967 seconds: 41 existing cases, 8 correction cases and 5 image cases |
| Microphone denied → granted | Passed; existing calling stays available after denial and the editor returns correctly after granting |
| Large text and landscape | All 3 focused checks passed at 160% font scale; setup and undo passed at 2280×1080 landscape across the main run and focused planner rerun; screenshots inspected |
| Debug, instrumentation and release builds | Passed |
| Debug and release lint | No issues found |
| Audio packaging | All 270 bundled recording hashes verified by the build |
| Release inspection | Version 1.4.1/code 8, min SDK 26, target 35; debug editor, test speech service and provider absent |
| Test APK signature | Verified with apksigner |

The setup UI tests wait for saved data and dialog dismissal after Android dispatches taps. Code review also found that a damaged prize record could not be reset while a funded plan was present; the confirmed recovery now clears prizes and their budget atomically while retaining called numbers, with a dedicated test. Early runs exposed assumptions about immediate tap completion and activity cleanup; these are corrected in the test harness. Production budget and eligibility enforcement is also tested independently of the UI. An initial funded-run attempt could not find the test editor’s Send button during a window transition; a fresh complete run passed on the same production build. The harness now also waits for that editor control before sending. The recovery test uses stable Android dialog IDs because the theme capitalizes button labels. Permission testing required clearing stale user-set/user-fixed emulator flags and restarting editor focus; the deny-then-grant test then passed. The landscape planner initially missed an allocation tap; requiring a larger visible target and waiting for the UI thread after persisted changes resolved the test, and the complete landscape setup flow passed without changing production behavior.

## Reproduce

Use a dedicated emulator: instrumentation resets its saved rounds. This run uses API 35 ARM64 (Pixel 4), SDK/build-tools 35, AGP 8.7.3, Gradle 8.9 and local OpenJDK 20.0.1. The project targets Java 17.

```sh
ANDROID_HOME=/path/to/android/sdk bash ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug lintRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class io.github.sbshrey.tambola.keyboard.TicketPlanUiTest io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e funded true -e class io.github.sbshrey.tambola.keyboard.FullGameTest io.github.sbshrey.tambola.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

The funded full-game test supplies purchases and allocations through the store; the separate planner UI test covers setting them with taps and manual amount editing. All 90 draws, claims, winner announcements, corrections, results, resets and the next game's first call use the actual keyboard/editor connection. The fixture's Send button only records local test messages.

Run the regression classes listed in README plus `CallCorrectionTest` and `ResultsImageTest`. Run `VoicePermissionTest` separately after revoking RECORD_AUDIO and clearing its user-set/user-fixed permission flags, using the commands in README. For large text, set `adb shell settings put system font_scale 1.6`; restore 1.0 afterward. For landscape, set `adb shell wm fixed-to-user-rotation enabled` and pass `-e landscape true`; restore fixed rotation to `default` afterward. Tests assert the wide display.

## Artifacts and limits

`releases/1.4.1/` contains the debug-signed test APK, unsigned production APK, SHA-256 checksums, installation guide and test logs/screenshots. No release was pushed or published. The original production signing key is not available in this clone. Sign the release with that key to update the existing phone installation. The debug APK cannot replace it; do not uninstall the existing app to bypass the signature check.

No physical phone or WhatsApp delivery was tested. Android UI tests use the actual IME with a local test editor, and speech regression tests use the deterministic debug recognition provider. On the phone, check the setup return-to-chat flow, correction of an unsent number, image attachment/chooser and recipient before group play. The host still checks paper tickets and sends each prepared message/image.

The website was unchanged in this update; its previously passing 69-test/build result was not repeated.

---

# Tambola Keyboard 1.4.0: host claims and tap-to-announce results

Validated on 20 October 2026. Version code 7. The repository was cloned at `e3d0f21`; this is a local Android update on `feat/tap-winner-announcements`.

## Behavior verified

- One-time family setup adds the supplied 11 players and 42 catalog schemes, including T, individual First–Ninth lines/boxes, and separate Seedhi/Ulti family planning. Existing names, awards, configured amounts and renamed entries are reused. The four legacy line/house defaults remain, for 46 entries on a fresh setup.
- Varsha can insert her own claim, then separately select confirmed claimants and announce a result. Claims alone never award a prize. The app does not read incoming WhatsApp claims or validate paper tickets.
- One claimant receives the configured total (default ₹10); two split it (default ₹5 each); three or more produce **Bogie, no payout**. Bogie claimants are saved, included in results and excluded from payout totals. The same rule applies to optional winner dictation.
- Draft protection, cancellation, editor restart, changed player/prize setup, minimum call counts, failed editor insertion recovery, result corrections, undo and new rounds are covered. Correcting paid winners retains their recorded payout and call position; correcting a Bogie uses the configured prize amount and original call position.
- Version 1 prize records migrate to version 2 without losing names, awards or amounts. Bogie results survive storage reopening. Corrupt Bogie records are rejected. Version 2 is not readable by older APKs.

## Automated checks

| Check | Result |
| --- | --- |
| JVM unit suite | 35 passed; includes all 462 scheme/player combinations and claimant counts 1–11 |
| Android regression suite, API 35 ARM64 Pixel 4 emulator | All 41 cases passed across the main run and focused reruns |
| Microphone denied + permission recovery | Host claim/split flow passed with RECORD_AUDIO denied; deny-then-grant UI test passed (42 distinct Android cases total) |
| Large text and landscape | 160% font-scale selection/cancellation/insertion passed; full host-claim/shared-result flow passed at 2280×1080 landscape; screenshots inspected |
| Website tests | 69 passed |
| Website static build | Passed |
| Debug APK, instrumentation APK, release APK builds | Passed |
| Debug and release lint | No issues found |
| Bundled audio integrity | All 270 recordings verified; playback/decoding checks passed |
| Release fixture exclusion | Debug editor and recognition-provider fixtures absent from production DEX |
| Debug APK signature | Verified with apksigner |

The final regression run passed 37 cases and exposed four test synchronization assumptions (a pending tap callback, TextView restarting its editor, a stale accessibility node, and a dialog not yet rendered). Tests now await the observable state and reacquire views; all four focused reruns passed on the same production build. An earlier run also caught and fixed the tap panel reopening on the previously selected prize group.

## Reproduce

Use the documented Java 17 toolchain and Android SDK 35. This run used local OpenJDK 20.0.1, Gradle 8.9 and AGP 8.7.3. Build from `android-keyboard`:

```sh
ANDROID_HOME=/path/to/android/sdk bash ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease lintRelease
```

Install the debug and instrumentation APKs on a **dedicated emulator**; the tests reset its saved games. Enable/select the Tambola IME. Run the classes listed in README, then the separate microphone-permission test after revoking RECORD_AUDIO. Use `npm test` and `npm run build` from the repository root for the website.

For large text, set emulator `font_scale` to `1.6` and run `QuickWinnerTest#lastPlayerIsReachableAndSelectionCanBeReplacedOrCancelled`; restore it to `1.0` afterward. For landscape, enable `adb shell wm fixed-to-user-rotation enabled`, pass `-e landscape true` to the host-claim test, and restore fixed rotation to `default` afterward. The test asserts a wide display so a silent portrait fallback cannot pass as a landscape check.

## Artifacts and limits

The ignored `releases/1.4.0/` directory contains a debug-signed test APK, unsigned production APK, SHA-256 checksums, installation notes, and validation logs/screenshots. No release was pushed or published. The existing production signing key is not available in this clone. Use that original key to build/sign an update for the phone; the debug APK cannot replace the published app. Do not uninstall a live installation to work around a signature mismatch.

UI tests exercised the actual Android IME and editor connection in a local test chat. Speech tests exercised Android's recognition binder with the debug deterministic provider; no live microphone/provider transcription was revalidated in this task. No physical phone, WhatsApp account, WhatsApp Send action, or delivered group message was used. On the actual phone, confirm the recipient and draft, check long-name layout, then send a claim, two-winner result and Bogie in a private test chat before group play. The host must still verify tickets.

---

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
