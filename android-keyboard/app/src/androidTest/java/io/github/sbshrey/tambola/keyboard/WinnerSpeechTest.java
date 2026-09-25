package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.speech.SpeechRecognizer;
import android.view.inputmethod.InputMethodManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses the Android SpeechRecognizer binder with a debug-only deterministic provider. */
@RunWith(AndroidJUnit4.class)
public class WinnerSpeechTest {
    @Rule public ActivityTestRule<KeyboardEditorActivity> rule = new ActivityTestRule<>(KeyboardEditorActivity.class, false, false);
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private GameStore store;
    private String provider;
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    @Before public void setup() throws Exception {
        device.executeShellCommand("ime set io.github.sbshrey.tambola.keyboard/.TambolaInputMethod");
        provider = device.executeShellCommand("settings get secure voice_recognition_service").trim();
        device.executeShellCommand("settings put secure voice_recognition_service io.github.sbshrey.tambola.keyboard/.TestRecognitionService");
        device.executeShellCommand("pm grant io.github.sbshrey.tambola.keyboard android.permission.RECORD_AUDIO");
        main(() -> { TestRecognitionService.failure = 0; TestRecognitionService.waitForResult = false; TestRecognitionService.transcript = "Early five winner Asha Sharma"; });
        store = new GameStore(context, false); store.save(Game.decode("1,2,3,4,5")); store.prizes(PrizeBook.defaults().addPresets(PrizeCatalog.allIds()));
        new Preferences(context).voice(false); new Preferences(context).dictationLanguage("en-IN");
        rule.launchActivity(new Intent()); device.waitForIdle(); focus();
    }
    @After public void restoreProvider() throws Exception {
        device.pressBack();
        device.executeShellCommand(provider == null || provider.equals("null") || provider.isEmpty() ? "settings delete secure voice_recognition_service" : "settings put secure voice_recognition_service " + provider);
    }
    private void focus() {
        main(() -> {
            rule.getActivity().draft.requestFocus();
            InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.restartInput(rule.getActivity().draft);
        });
        device.waitForIdle();
        main(() -> ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(rule.getActivity().draft, InputMethodManager.SHOW_IMPLICIT));
        assertNotNull(device.wait(Until.findObject(By.text("Call")), 10000)); device.waitForIdle();
    }
    private void tap(String text) { UiObject2 item = device.wait(Until.findObject(By.text(text)), 5000); assertNotNull(text, item); item.click(); device.waitForIdle(); }
    private void speak() { tap("Winners"); tap("Speak prize + winner"); }
    private void expect(String text) { assertTrue(text, device.wait(Until.hasObject(By.textContains(text)), 10000)); }
    private void noAward() { assertFalse(store.prizes().hasWinners()); }
    private String draft() { final String[] text = {null}; main(() -> text[0] = rule.getActivity().draft.getText().toString()); return text[0]; }
    @Test public void assignsTwoWinnersAndInsertsWithoutManualChoice() {
        store.prizes(store.prizes().player(null, "Asha Sharma").configure("catalog-king", "King", 11, true));
        main(() -> TestRecognitionService.transcript = "King ke winners asha sharma aur Bina");
        speak(); expect("Winner saved. Check");
        assertEquals("🏆 King: Asha Sharma & Bina\n₹5.50 each", draft()); assertEquals(2, store.prizes().players.size());
        assertEquals(5, store.load().count());
        assertTrue(device.takeScreenshot(new java.io.File(context.getFilesDir(), "voice-winner-ready.png")));
        main(() -> rule.getActivity().draft.setText("")); device.waitForIdle(); speak(); expect("already has winners");
        assertEquals("", draft()); assertEquals(2, store.prizes().scheme("catalog-king").winners.size());
    }
    @Test public void hindiSpeechAddsNamesAndSplitsDefaultPrize() {
        new Preferences(context).dictationLanguage("hi-IN");
        main(() -> TestRecognitionService.transcript = "अर्ली फाइव की विनर आशा शर्मा और बीना");
        speak(); expect("Winner saved. Check"); assertEquals("🏆 Early 5: आशा शर्मा & बीना\n₹5 each", draft());
    }
    @Test public void existingDraftBlocksVoiceAndPreservesGame() {
        main(() -> rule.getActivity().draft.setText("Unsent message")); speak(); expect("Send or clear the current message first.");
        noAward(); assertEquals("Unsent message", draft());
    }
    @Test public void cancelNeverAssignsOrInserts() {
        main(() -> TestRecognitionService.waitForResult = true); speak(); expect("Listening…");
        tap("Cancel listening"); main(() -> TestRecognitionService.complete(context)); device.waitForIdle(); noAward(); assertEquals("", draft());
    }
    @Test public void editorChangeCancelsLateRecognition() {
        main(() -> TestRecognitionService.waitForResult = true); speak(); expect("Listening…");
        focus(); main(() -> TestRecognitionService.complete(context)); device.waitForIdle(); noAward(); assertEquals("", draft());
    }
    @Test public void changedDraftCancelsRecognition() {
        main(() -> TestRecognitionService.waitForResult = true); speak(); expect("Listening…");
        main(() -> rule.getActivity().draft.setText("Keep this")); device.waitForIdle();
        main(() -> TestRecognitionService.complete(context)); device.waitForIdle(); noAward(); assertEquals("Keep this", draft());
    }
    @Test public void newGameCancelsRecognitionAndCallingStillWorks() {
        main(() -> TestRecognitionService.waitForResult = true); speak(); expect("Listening…");
        main(store::newRound); expect("New game ready."); main(() -> TestRecognitionService.complete(context)); noAward(); assertEquals("", draft());
        tap("Next number"); expect("1 / 90"); assertEquals(Game.emoji(store.load().latest()), draft());
    }
    @Test public void changedPrizeSettingsDiscardPendingCommand() {
        main(() -> TestRecognitionService.waitForResult = true); speak(); expect("Listening…");
        main(() -> { store.prizes(store.prizes().configure("prize-0", "Early 5", 25, true)); TestRecognitionService.complete(context); });
        expect("Game or prizes changed"); noAward(); assertEquals("", draft());
    }
    @Test public void providerFailureCanRetryAndDoneSpeakingWorks() {
        main(() -> TestRecognitionService.failure = SpeechRecognizer.ERROR_NETWORK); speak(); expect("Check the Internet"); noAward();
        main(() -> { TestRecognitionService.failure = 0; TestRecognitionService.waitForResult = true; });
        tap("Speak again"); expect("Listening…"); tap("Done speaking"); expect("Winner saved. Check");
        assertEquals("🏆 Early 5: Asha Sharma\n₹10 prize", draft());
    }
    @Test public void unknownPrizeDoesNotCreatePlayersOrAwards() {
        main(() -> TestRecognitionService.transcript = "Unknown prize Asha Sharma"); speak(); expect("Prize not clear");
        noAward(); assertTrue(store.prizes().players.isEmpty()); assertEquals("", draft());
    }
}
