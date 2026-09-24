package io.github.sbshrey.tambola.keyboard;

import android.content.Intent;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.security.SecureRandom;

public final class TambolaInputMethod extends InputMethodService {
    static final String PRACTICE = "tambola-practice";
    private final SecureRandom random = new SecureRandom();
    private TextView number, progress, recent, status;
    private Button next, again;
    private GameStore store;
    private boolean practice, textField;
    private long lastDraw;

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public View onCreateInputView() {
        boolean compact = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.PAPER);
        int padding = Ui.dp(this, compact ? 6 : 10); root.setPadding(padding, padding, padding, padding);
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        number = Ui.text(this, "—", compact ? 28 : 42); number.setGravity(Gravity.CENTER); number.setContentDescription("Latest Tambola number");
        heading.addView(number, new LinearLayout.LayoutParams(0, -2, 1));
        progress = Ui.text(this, "0 / 90", 15); progress.setGravity(Gravity.CENTER);
        heading.addView(progress, new LinearLayout.LayoutParams(0, -2, 1));
        Button switcher = Ui.button(this, "ABC ↔", Ui.PINK, this::chooseKeyboard);
        switcher.setContentDescription("Switch to your regular keyboard");
        heading.addView(switcher, new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 48)));
        root.addView(heading);
        recent = Ui.text(this, "No numbers yet", 14); recent.setSingleLine(true); recent.setGravity(Gravity.CENTER);
        Ui.add(root, recent, -2);
        next = Ui.button(this, "Next number", Ui.GREEN, () -> insert(true));
        next.setContentDescription("Next number"); Ui.add(root, next, compact ? 48 : 56);
        LinearLayout actions = new LinearLayout(this);
        again = Ui.button(this, "Insert again", Ui.PINK, () -> insert(false));
        again.setContentDescription("Insert current number again");
        Ui.weighted(actions, again);
        Ui.weighted(actions, Ui.button(this, "Board & setup", Ui.PAPER, () -> startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
        Ui.add(root, actions, 48);
        status = Ui.text(this, "Tap WhatsApp Send after each call", 14);
        status.setGravity(Gravity.CENTER); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Ui.add(root, status, -2);
        store = new GameStore(this, practice); render();
        return root;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        practice = getPackageName().equals(info.packageName) && PRACTICE.equals(info.privateImeOptions);
        store = new GameStore(this, practice);
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        textField = (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
            && variation != InputType.TYPE_TEXT_VARIATION_PASSWORD
            && variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            && variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        if (!render()) return;
        if (!textField) say("Use your regular keyboard for this field.");
        else say(practice ? "Practice round · use Clear to try the next call" : "Tap Next number, then tap WhatsApp Send.");
    }

    private void chooseKeyboard() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); }

    private boolean render() {
        if (number == null || store == null) return false;
        try {
            Game game = store.load();
            number.setText(game.latest() == 0 ? "—" : Game.emoji(game.latest()));
            progress.setText(getString(R.string.keyboard_progress, practice ? "PRACTICE" : "LIVE ROUND", game.count()));
            recent.setText(getString(R.string.recent_numbers, game.recent()));
            next.setEnabled(textField && game.count() < Game.TOTAL);
            next.setText(game.count() == Game.TOTAL ? "All 90 called" : "Next number");
            again.setEnabled(textField && game.latest() != 0);
            return true;
        } catch (IllegalArgumentException error) {
            next.setEnabled(false); again.setEnabled(false); number.setText("?");
            say("Saved round is unreadable. Open Board & setup.");
            return false;
        }
    }

    private void say(String message) { if (status != null) status.setText(message); }

    private void insert(boolean draw) {
        if (!textField) return;
        InputConnection editor = getCurrentInputConnection();
        if (editor == null) { say("Tap the empty message box, then try again."); return; }
        // Inspect only the immediate draft boundary/selection; never store editor text.
        CharSequence before = editor.getTextBeforeCursor(1, 0);
        CharSequence after = editor.getTextAfterCursor(1, 0);
        CharSequence selected = editor.getSelectedText(0);
        if (before == null || after == null) { say("Tap the empty message box, then try again."); return; }
        if (before.length() > 0 || after.length() > 0 || (selected != null && selected.length() > 0)) {
            say("Send or clear the current message first."); return;
        }
        if (draw && SystemClock.elapsedRealtime() - lastDraw < 650) return;
        try {
            Game game = store.load();
            if (draw) {
                if (game.count() == Game.TOTAL) { say("All 90 called. Start a new round in Board & setup."); return; }
                game = game.draw(random);
                store.save(game);
                lastDraw = SystemClock.elapsedRealtime();
            }
            if (game.latest() == 0) return;
            boolean inserted = editor.commitText(Game.emoji(game.latest()), 1);
            render();
            say(inserted ? (practice ? "Practice only · Clear the box to try again" : "Number ready · tap WhatsApp Send")
                : "Number saved. Tap the message box and Insert again.");
        } catch (IllegalArgumentException | IllegalStateException error) {
            render(); say(error instanceof IllegalArgumentException ? "Saved round is unreadable. Open Board & setup." : error.getMessage());
        }
    }
}
