package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.view.inputmethod.InputMethodManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class QuickWinnerTest {
    @Rule public ActivityTestRule<KeyboardEditorActivity> rule = new ActivityTestRule<>(KeyboardEditorActivity.class, false, false);
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private GameStore store;
    @Rule public org.junit.rules.TestWatcher failures = new org.junit.rules.TestWatcher() {
        @Override protected void failed(Throwable error, org.junit.runner.Description description) { screenshot("failed-" + description.getMethodName()); }
    };
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    @Before public void setup() throws Exception {
        device.executeShellCommand("ime set io.github.sbshrey.tambola.keyboard/.TambolaInputMethod");
        store = new GameStore(context, false); store.save(Game.decode("1,2,3,4,5")); store.prizes(FamilySetup.addTo(PrizeBook.defaults()));
        new Preferences(context).voice(false);
        if (landscape()) device.setOrientationLeft();
        rule.launchActivity(new Intent()); device.waitForIdle(); focus();
        if (landscape()) assertTrue("Landscape test must run on a wide display", device.getDisplayWidth() > device.getDisplayHeight());
    }
    private boolean landscape() { return "true".equals(InstrumentationRegistry.getArguments().getString("landscape")); }
    @After public void restoreOrientation() throws Exception { if (landscape()) { device.setOrientationNatural(); device.unfreezeRotation(); } }
    private void focus() {
        main(() -> {
            rule.getActivity().draft.requestFocus();
            ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(rule.getActivity().draft);
        });
        device.waitForIdle();
        main(() -> ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(rule.getActivity().draft, InputMethodManager.SHOW_IMPLICIT));
        assertNotNull(device.wait(Until.findObject(By.text("Call")), 10000)); device.waitForIdle();
    }
    private UiObject2 visible(BySelector selector) {
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            for (int i = 0; i < 18; i++) {
                UiObject2 found = device.findObject(selector); if (found != null && found.getVisibleBounds().height() >= Ui.dp(context, 44)) return found;
                UiObject2 panel = device.findObject(By.desc("Tambola controls")); assertNotNull(panel);
                boolean more = panel.scroll(direction, 0.7f); device.waitForIdle();
                if (!more) break;
            }
        }
        UiObject2 found = device.findObject(selector); assertNotNull(selector.toString(), found); return found;
    }
    private void tap(String text) { visible(By.text(text)).click(); main(() -> {}); device.waitForIdle(); if (text.equals("‹ Choose another prize") || text.equals("Tap prize + winner")) expect("1. Choose a prize"); }
    private void choose(String prize) { visible(By.desc("Choose prize: " + prize)).click(); device.waitForIdle(); }
    private void name(String name, boolean selected) { visible(By.desc(name + (selected ? ", selected claimant" : ", choose claimant"))).click(); device.waitForIdle(); }
    private void quick() { tap("Winners"); tap("Tap prize + winner"); }
    private String draft() { final String[] value = {null}; main(() -> value[0] = rule.getActivity().draft.getText().toString()); return value[0]; }
    private void expect(String text) { assertTrue(text, device.wait(Until.hasObject(By.textContains(text)), 5000)); }
    private void screenshot(String name) { assertTrue(device.takeScreenshot(new java.io.File(context.getFilesDir(), name + ".png"))); }

    @Test public void familySetupWorksInsideKeyboardAndKeepsSavedRound() {
        store.prizes(PrizeBook.defaults().player(null, "REKHA")); quick(); tap("Add family players & schemes"); expect("Family list ready.");
        PrizeBook saved = new GameStore(context, false).prizes(); assertEquals(11, saved.players.size()); assertEquals(46, saved.schemes.size());
        assertTrue(saved.players.containsValue("REKHA")); assertEquals("1,2,3,4,5", store.load().encode());
        choose("Early 5"); name("Varsha", false); tap("Insert winner announcement");
        expect("Winner saved · check"); assertEquals("🏆 Early 5: Varsha\n₹10 prize", draft());
        assertEquals(5, store.load().count()); screenshot("tap-winner-ready");
    }
    @Test public void twoWinnersPreviewSplitWithoutSavingUntilInsert() {
        store.prizes(store.prizes().configure("catalog-king", "King", 11, true));
        quick(); choose("King"); assertFalse(visible(By.text("Insert winner announcement")).isEnabled());
        name("Rekha", false); name("Rekha totla", false); 
         assertFalse(store.prizes().hasWinners()); assertEquals("", draft());
        visible(By.desc("Winner announcement preview")); expect("₹5.50 each"); screenshot("tap-two-winners-preview");
        tap("Insert winner announcement"); expect("Winner saved · check");
        assertEquals("🏆 King: Rekha & Rekha totla\n₹5.50 each", draft());
    }
    @Test public void lastPlayerIsReachableAndSelectionCanBeReplacedOrCancelled() {
        quick(); choose("Queen"); name("Varsha", false); name("Varsha", true);
        assertFalse(visible(By.text("Insert winner announcement")).isEnabled());
        name("Suman", false); screenshot("tap-player-list"); tap("‹ Choose another prize"); assertFalse(store.prizes().hasWinners());
        choose("Queen"); assertFalse(visible(By.text("Insert winner announcement")).isEnabled());
        name("Suman", false); tap("Insert winner announcement"); expect("Winner saved · check"); assertTrue(draft().contains("Queen: Suman"));
    }
    @Test public void existingAndSelectedDraftsBlockSavingAndInsertion() {
        quick(); choose("Early 5"); name("Varsha", false);
        main(() -> { rule.getActivity().draft.setText("Keep my draft"); rule.getActivity().draft.selectAll(); });
        // TextView can restart the editor after setText; pending choices must be discarded.
        focus(); assertFalse(store.prizes().hasWinners()); quick(); choose("Early 5"); name("Varsha", false);
        tap("Insert winner announcement"); expect("Send or clear the current message first.");
        assertEquals("Keep my draft", draft()); assertFalse(store.prizes().hasWinners());
        main(() -> rule.getActivity().draft.setText("")); device.waitForIdle();
        focus(); quick(); choose("Early 5"); name("Varsha", false);
        tap("Insert winner announcement"); expect("Winner saved · check"); assertTrue(draft().contains("Varsha"));
    }
    @Test public void editorRestartDropsPendingSelectionAndNewGameKeepsFamilyList() {
        quick(); choose("Early 5"); name("Varsha", false); focus();
        assertEquals("", draft()); assertFalse(store.prizes().hasWinners());
        quick(); choose("Early 5"); assertFalse(visible(By.text("Insert winner announcement")).isEnabled());
        name("Varsha", false); main(store::newRound); expect("New game ready.");
        assertEquals(11, store.prizes().players.size()); assertEquals(46, store.prizes().schemes.size()); assertFalse(store.prizes().hasWinners());
        assertEquals("", draft()); tap("Next number"); expect("1 / 90"); assertEquals(Game.emoji(store.load().latest()), draft());
    }
    @Test public void changingPrizeOrPlayerDuringSelectionRequiresStartingAgain() {
        quick(); choose("Early 5"); name("Varsha", false);
        store.prizes(store.prizes().player("family-varsha", "Varsha ji"));
        tap("Insert winner announcement"); expect("Game or prizes changed.");
        assertEquals("", draft()); assertFalse(store.prizes().hasWinners());
        choose("Early 5"); name("Varsha ji", false); tap("Insert winner announcement"); expect("Winner saved · check");
        assertTrue(draft().contains("Varsha ji"));
    }
    @Test public void everyGroupIsReachableAndCallMinimumsAreEnforced() {
        quick();
        for (String[] sample : new String[][]{{"Lines", "Ninth line"}, {"Boxes", "Ninth box"}, {"Special prizes", "T"}}) {
            visible(By.desc("Prize group: " + sample[0])).click(); device.waitForIdle(); choose(sample[1]);
            assertNotNull(visible(By.desc("Varsha, choose claimant"))); tap("‹ Choose another prize");
        }
        visible(By.desc("Prize group: Houses")).click(); device.waitForIdle(); choose("House 3");
        expect("Call at least 15 numbers"); assertFalse(device.hasObject(By.text("Insert winner announcement"))); assertFalse(store.prizes().hasWinners());
    }
    @Test public void reinsertAndCorrectAwardKeepRecordedPayoutAndCallPosition() {
        store.prizes(store.prizes().award("prize-0", Collections.singletonList("family-varsha"), 11, 5).configure("prize-0", "Early 5", 25, true));
        store.save(Game.decode("1,2,3,4,5,6")); focus(); quick(); choose("Early 5");
        name("Rekha", false); tap("Update winner announcement"); expect("Winner saved · check");
        assertEquals("🏆 Early 5: Varsha & Rekha\n₹5.50 each", draft());
        assertEquals(5, store.prizes().scheme("prize-0").callCount); assertEquals(25, store.prizes().scheme("prize-0").rupees);
        main(() -> rule.getActivity().draft.setText("")); device.waitForIdle(); focus();
        tap("Winners"); tap("Insert Early 5 announcement"); expect("Ready · tap WhatsApp Send");
        assertTrue(draft().contains("₹5.50 each")); store.undo(); assertTrue(store.prizes().hasWinners());
    }
    @Test public void rejectedEditorInsertionLeavesAwardRecoverable() {
        main(() -> rule.getActivity().rejectCommit = true); quick(); choose("Early 5"); name("Varsha", false);
        tap("Insert winner announcement"); expect("saved");
        // Remote input connections may acknowledge queuing even if the editor later rejects the text.
        assertTrue(store.prizes().hasWinners()); assertEquals("", draft());
        main(() -> rule.getActivity().rejectCommit = false); tap("Insert Early 5 announcement"); expect("Ready · tap WhatsApp Send");
        assertEquals("🏆 Early 5: Varsha\n₹10 prize", draft()); assertEquals(5, store.load().count());
    }
    @Test public void varshaCanClaimBeforeHostConfirmsTwoWinners() {
        quick(); choose("Early 5"); tap("Claim for Varsha"); expect("Claim ready");
        assertEquals("🙋 Varsha claims Early 5\nPlease check my ticket.", draft());
        assertFalse(store.prizes().hasWinners()); assertEquals(5, store.load().count()); screenshot("host-claim-ready");
        quick(); choose("Early 5"); name("Varsha", false); name("Rekha", false); tap("Insert winner announcement");
        expect("Send or clear the current message first."); assertFalse(store.prizes().hasWinners());
        main(() -> rule.getActivity().draft.setText("")); device.waitForIdle(); focus(); quick(); choose("Early 5");
        name("Varsha", false); name("Rekha", false); tap("Insert winner announcement"); expect("Winner saved · check");
        assertEquals("🏆 Early 5: Varsha & Rekha\n₹5 each", draft()); screenshot("host-shared-prize-ready");
    }
    @Test public void threeOrMoreClaimsAnnounceBogieAndCanBeCorrected() {
        quick(); choose("Early 5"); name("Varsha", false); name("Rekha", false); name("Suman", false);
        visible(By.desc("Winner announcement preview")); expect("3 claimants"); expect("No payout");
        assertFalse(store.prizes().hasWinners()); screenshot("bogie-preview");
        tap("Insert Bogie announcement"); expect("Bogie saved");
        assertTrue(draft().contains("Early 5: Bogie")); assertTrue(draft().contains("No payout"));
        assertTrue(new GameStore(context, false).prizes().scheme("prize-0").bogie()); assertEquals(0, store.prizes().scheme("prize-0").eachPaise());
        main(() -> rule.getActivity().draft.setText("")); device.waitForIdle(); focus(); tap("Winners"); tap("Insert Early 5 announcement");
        expect("Ready · tap WhatsApp Send"); assertTrue(draft().contains("3 claimants"));
        main(() -> rule.getActivity().draft.setText("")); device.waitForIdle(); quick(); choose("Early 5");
        name("Saroj", false); tap("Insert Bogie announcement"); expect("Bogie saved"); assertTrue(draft().contains("4 claimants"));
        main(() -> rule.getActivity().draft.setText("")); device.waitForIdle(); quick(); choose("Early 5");
        name("Saroj", true); name("Suman", true); tap("Update winner announcement"); expect("Winner saved · check");
        assertEquals("🏆 Early 5: Varsha & Rekha\n₹5 each", draft()); assertFalse(store.prizes().scheme("prize-0").bogie());
    }
}
