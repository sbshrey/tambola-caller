package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NewGameNavigationTest {
    @Rule public ActivityTestRule<KeyboardEditorActivity> rule = new ActivityTestRule<>(KeyboardEditorActivity.class, false, false);
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private void tap(BySelector selector) {
        UiObject2 item = device.findObject(selector);
        for (int i = 0; item == null && i < 8; i++) {
            UiObject2 scroll = device.findObject(By.clazz(ScrollView.class)); assertNotNull(scroll);
            scroll.scroll(Direction.DOWN, 0.7f); device.waitForIdle(); item = device.findObject(selector);
        }
        assertNotNull(selector.toString(), item); item.click(); device.waitForIdle();
    }
    @Test public void completedRoundCanResetInAppAndResumeTheSameChatEditor() {
        GameStore store = new GameStore(context, false); store.prizes(PrizeBook.defaults());
        StringBuilder all = new StringBuilder(); for (int i = 1; i <= 90; i++) { if (i > 1) all.append(','); all.append(i); }
        store.save(Game.decode(all.toString())); new Preferences(context).voice(false);
        rule.launchActivity(new Intent()); device.waitForIdle();
        main(() -> { rule.getActivity().draft.requestFocus(); ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(rule.getActivity().draft, InputMethodManager.SHOW_IMPLICIT); });
        assertNotNull(device.wait(Until.findObject(By.desc("Next number")), 10000));
        assertFalse(device.findObject(By.desc("Next number")).isEnabled());
        tap(By.text("Board")); tap(By.text("Undo / Full board"));
        assertNotNull(device.wait(Until.findObject(By.text("Your live board")), 10000));
        tap(By.text("Start a new live game"));
        assertNotNull(device.wait(Until.findObject(By.res("android:id/button1")), 5000));
        tap(By.res("android:id/button1"));
        assertTrue(device.wait(Until.hasObject(By.text("0 of 90 called")), 5000)); assertEquals(0, store.load().count());
        device.pressBack(); assertNotNull(device.wait(Until.findObject(By.text("Test chat editor")), 10000));
        tap(By.desc("Test chat draft"));
        assertTrue(device.wait(Until.hasObject(By.desc("Next number").enabled(true)), 10000));
        tap(By.desc("Next number")); assertTrue(device.wait(Until.hasObject(By.text("1 / 90")), 5000)); assertEquals(1, store.load().count());
        main(() -> assertEquals(Game.emoji(store.load().latest()), rule.getActivity().draft.getText().toString()));
    }
}
