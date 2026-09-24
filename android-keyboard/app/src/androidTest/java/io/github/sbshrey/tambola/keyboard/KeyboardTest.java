package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class KeyboardTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class, false, false);
    private Context context;
    private UiDevice device;
    private EditText input;

    @Before public void prepare() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        new GameStore(context, false).save(Game.empty()); new GameStore(context, true).save(Game.empty());
        rule.launchActivity(new Intent());
        onMain(() -> input = (EditText) findInput(rule.getActivity().findViewById(android.R.id.content)));
        focus(true, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    }
    private android.view.View findInput(android.view.View view) {
        if (view instanceof EditText) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { android.view.View found = findInput(group.getChildAt(i)); if (found != null) return found; }
        }
        return null;
    }
    private void onMain(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private void focus(boolean practice, int type) {
        onMain(() -> {
            input.setPrivateImeOptions(practice ? TambolaInputMethod.PRACTICE : null); input.setInputType(type); input.requestFocus();
            InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.restartInput(input); manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        assertNotNull(device.wait(Until.findObject(By.desc("Next number")), 10000));
        device.waitForIdle();
    }
    private void clear() throws Exception { onMain(() -> input.setText("")); Thread.sleep(750); }
    private String message() { final String[] value = {null}; onMain(() -> value[0] = input.getText().toString()); return value[0]; }
    private void tap(String description) {
        UiObject2 button = device.wait(Until.findObject(By.desc(description)), 5000); assertNotNull(button); button.click(); device.waitForIdle();
    }
    @Test public void practiceInsertsKeycapsBlocksDraftsAndNeverChangesLiveRound() throws Exception {
        tap("Next number");
        Game game = new GameStore(context, true).load(); assertEquals(1, game.count()); assertEquals(Game.emoji(game.latest()), message());
        assertTrue(device.takeScreenshot(new java.io.File(context.getFilesDir(), "keyboard-practice.png")));
        tap("Next number"); assertEquals(1, new GameStore(context, true).load().count()); assertEquals(Game.emoji(game.latest()), message());
        assertTrue(device.hasObject(By.text("Send or clear the current message first.")));
        clear(); tap("Insert current number again"); assertEquals(Game.emoji(game.latest()), message());
        assertEquals(1, new GameStore(context, true).load().count()); assertEquals(0, new GameStore(context, false).load().count());
    }
    @Test public void liveCallsPersistAcrossEditorRestartAndRespectExistingSelection() throws Exception {
        focus(false, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        tap("Next number"); Game first = new GameStore(context, false).load(); assertEquals(1, first.count()); assertEquals(Game.emoji(first.latest()), message());
        clear(); focus(false, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        tap("Insert current number again"); assertEquals(Game.emoji(first.latest()), message()); assertEquals(1, new GameStore(context, false).load().count());
        onMain(() -> { input.setText("Do not replace this draft"); input.selectAll(); });
        tap("Next number"); assertEquals("Do not replace this draft", message()); assertEquals(1, new GameStore(context, false).load().count());
        clear(); tap("Next number"); Game second = new GameStore(context, false).load(); assertEquals(2, second.count()); assertNotEquals(first.latest(), second.latest());
        assertEquals(Game.emoji(second.latest()), message());
    }
    @Test public void passwordFieldsDisableGameButtons() {
        focus(false, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        assertFalse(device.findObject(By.desc("Next number")).isEnabled());
        assertFalse(device.findObject(By.desc("Insert current number again")).isEnabled());
        assertEquals(0, new GameStore(context, false).load().count());
    }
    @Test public void completedRoundStopsNextButCanReinsertAndCorruptionDoesNotReset() throws Exception {
        StringBuilder all = new StringBuilder(); for (int i = 1; i <= 90; i++) { if (i > 1) all.append(','); all.append(i); }
        new GameStore(context, false).save(Game.decode(all.toString()));
        focus(false, InputType.TYPE_CLASS_TEXT);
        assertFalse(device.findObject(By.desc("Next number")).isEnabled());
        tap("Insert current number again"); assertEquals(Game.emoji(90), message());
        context.getSharedPreferences("tambola-round", Context.MODE_PRIVATE).edit().putString("called-v1", "47,47").commit();
        clear(); focus(false, InputType.TYPE_CLASS_TEXT);
        assertFalse(device.findObject(By.desc("Next number")).isEnabled());
        assertFalse(device.findObject(By.desc("Insert current number again")).isEnabled());
        assertEquals("47,47", context.getSharedPreferences("tambola-round", Context.MODE_PRIVATE).getString("called-v1", ""));
        assertTrue(device.hasObject(By.text("Saved round is unreadable. Open Board & setup.")));
    }
}
