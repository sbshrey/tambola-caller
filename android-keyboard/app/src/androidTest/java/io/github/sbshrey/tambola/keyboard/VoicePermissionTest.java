package io.github.sbshrey.tambola.keyboard;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.view.inputmethod.InputMethodManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run separately on the emulator after revoking RECORD_AUDIO; revocation kills a running app. */
@RunWith(AndroidJUnit4.class)
public class VoicePermissionTest {
    @Rule public ActivityTestRule<KeyboardEditorActivity> rule = new ActivityTestRule<>(KeyboardEditorActivity.class, false, false);
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private void tap(BySelector selector) { UiObject2 item = device.wait(Until.findObject(selector), 5000); assertNotNull(selector.toString(), item); item.click(); device.waitForIdle(); }
    private void focus() {
        main(() -> rule.getActivity().draft.requestFocus()); device.waitForIdle();
        main(() -> ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(rule.getActivity().draft, InputMethodManager.SHOW_IMPLICIT));
        assertNotNull(device.wait(Until.findObject(By.text("Call")), 10000));
    }
    @Test public void denyThenGrantReturnsToChatAndKeepsCallingAvailable() throws Exception {
        Assume.assumeTrue("Run separately after revoking microphone permission", context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED);
        device.executeShellCommand("ime set io.github.sbshrey.tambola.keyboard/.TambolaInputMethod");
        GameStore store = new GameStore(context, false); store.save(Game.decode("1,2,3,4,5")); store.prizes(PrizeBook.defaults()); new Preferences(context).voice(false);
        rule.launchActivity(new Intent()); device.waitForIdle(); focus();
        tap(By.text("Winners")); tap(By.text("Speak prize + winner")); tap(By.text("Allow microphone"));
        tap(By.res("com.android.permissioncontroller:id/permission_deny_button"));
        tap(By.text("Return to chat")); tap(By.desc("Test chat draft")); focus();
        assertFalse(store.prizes().hasWinners()); tap(By.text("Next number"));
        assertTrue(device.wait(Until.hasObject(By.text("6 / 90")), 5000)); assertEquals(6, store.load().count());
        main(() -> rule.getActivity().draft.setText(""));
        tap(By.text("Winners")); tap(By.text("Speak prize + winner")); tap(By.text("Allow microphone"));
        tap(By.res("com.android.permissioncontroller:id/permission_allow_foreground_only_button"));
        assertTrue(device.wait(Until.hasObject(By.textContains("Microphone is ready")), 5000));
        tap(By.text("Return to chat")); tap(By.desc("Test chat draft")); focus();
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        assertFalse(store.prizes().hasWinners()); main(() -> assertEquals("", rule.getActivity().draft.getText().toString()));
    }
}
