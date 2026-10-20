package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.os.SystemClock;
import android.view.inputmethod.InputMethodManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import static org.junit.Assert.*;

/** Exercises the real IME against a local editor. Send only records text in the fixture. */
public abstract class KeyboardScenario {
    @Rule public ActivityTestRule<KeyboardEditorActivity> rule = new ActivityTestRule<>(KeyboardEditorActivity.class, false, false);
    protected final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    protected final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    protected GameStore store;
    private long drewAt;
    @Rule public org.junit.rules.TestWatcher failures = new org.junit.rules.TestWatcher() {
        @Override protected void failed(Throwable error, org.junit.runner.Description description) { screenshot("failed-" + description.getMethodName()); }
    };
    protected void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    @Before public void prepareScenario() throws Exception {
        device.executeShellCommand("ime enable io.github.sbshrey.tambola.keyboard/.TambolaInputMethod");
        device.executeShellCommand("ime set io.github.sbshrey.tambola.keyboard/.TambolaInputMethod");
        store = new GameStore(context, false); store.save(Game.empty()); store.prizes(FamilySetup.addTo(PrizeBook.defaults()));
        GameStore practice = new GameStore(context, true); practice.save(Game.empty()); practice.prizes(PrizeBook.defaults());
        new Preferences(context).voice(false);
        if (landscape()) device.setOrientationLeft();
        rule.launchActivity(new Intent()); device.waitForIdle(); focus();
        if (landscape()) assertTrue("Expected a landscape display", device.getDisplayWidth() > device.getDisplayHeight());
    }
    private boolean landscape() { return "true".equals(InstrumentationRegistry.getArguments().getString("landscape")); }
    @After public void restoreOrientation() throws Exception { if (landscape()) { device.setOrientationNatural(); device.unfreezeRotation(); } }
    protected void focus() {
        main(() -> {
            rule.getActivity().draft.requestFocus();
            ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(rule.getActivity().draft);
        });
        device.waitForIdle();
        main(() -> ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(rule.getActivity().draft, InputMethodManager.SHOW_IMPLICIT));
        assertNotNull(device.wait(Until.findObject(By.text("Call")), 10000)); device.waitForIdle();
    }
    protected UiObject2 visible(BySelector selector) {
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            for (int i = 0; i < 20; i++) {
                UiObject2 found = device.findObject(selector);
                if (found != null && found.getVisibleBounds().height() >= Ui.dp(context, 44)) return found;
                UiObject2 panel = device.findObject(By.desc("Tambola controls"));
                if (panel == null) panel = device.wait(Until.findObject(By.clazz(android.widget.ScrollView.class)), 5000);
                if (panel == null) screenshot("missing-scroll-panel");
                assertNotNull("No scroll panel while looking for " + selector, panel);
                boolean more = panel.scroll(direction, 0.7f); device.waitForIdle(); if (!more) break;
            }
        }
        UiObject2 found = device.findObject(selector);
        if (found == null) {
            screenshot("missing-control");
            try { device.dumpWindowHierarchy(new java.io.File(context.getFilesDir(), "missing-control.xml")); }
            catch (java.io.IOException error) { throw new AssertionError(error); }
        }
        assertNotNull(selector.toString(), found); return found;
    }
    protected void tap(String text) { tap(By.text(text)); }
    protected void tap(BySelector selector) { visible(selector).click(); device.waitForIdle(); }
    protected String draft() { final String[] text = {null}; main(() -> text[0] = rule.getActivity().draft.getText().toString()); return text[0]; }
    protected void await(String label, BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25);
        boolean complete = condition.getAsBoolean();
        if (!complete) {
            screenshot("await-failure");
            try { device.dumpWindowHierarchy(new java.io.File(context.getFilesDir(), "await-failure.xml")); }
            catch (java.io.IOException error) { throw new AssertionError(error); }
        }
        assertTrue(label, complete);
        main(() -> {}); device.waitForIdle();
    }
    protected void expect(String text) { assertTrue(text, device.wait(Until.hasObject(By.textContains(text)), 5000)); }
    protected void screenshot(String name) { assertTrue(device.takeScreenshot(new java.io.File(context.getFilesDir(), name + ".png"))); }
    protected void send() {
        String expected = draft(); assertFalse("Nothing to send", expected.isEmpty());
        assertTrue("Test editor is visible after the IME transition", device.wait(Until.hasObject(By.desc("Send test message")), 5000));
        tap(By.desc("Send test message")); await("Send cleared draft", () -> draft().isEmpty());
        main(() -> assertEquals(expected, rule.getActivity().sentMessages.get(rule.getActivity().sentMessages.size() - 1)));
    }
    protected int draw() {
        int count = store.load().count();
        // Real calling has a 650 ms double-tap guard, including on fast test devices.
        long remaining = 680 - (SystemClock.elapsedRealtime() - drewAt);
        if (remaining > 0) SystemClock.sleep(remaining);
        tap(By.desc("Next number"));
        await("Call " + (count + 1) + " persisted and inserted", () -> store.load().count() == count + 1
            && draft().equals(Game.emoji(store.load().latest())));
        drewAt = SystemClock.elapsedRealtime(); return store.load().latest();
    }
    protected void choose(String group, String prize) {
        tap("Winners"); tap("Tap prize + winner"); tap(By.desc("Prize group: " + group)); tap(By.desc("Choose prize: " + prize));
    }
    protected void name(String name) { tap(By.desc(name + ", choose claimant")); }
}
