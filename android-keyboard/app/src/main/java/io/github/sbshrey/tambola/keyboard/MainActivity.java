package io.github.sbshrey.tambola.keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private GameStore store;
    private TextView setupStatus, gameStatus, recent;
    private LinearLayout board;
    private EditText practice;
    private Button undo;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new GameStore(this, false);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Ui.PAPER);
        LinearLayout page = Ui.column(this); int padding = Ui.dp(this, 20); page.setPadding(padding, padding, padding, padding);
        page.setFocusableInTouchMode(true);
        // Preserve room for system bars when target 35 enables edge-to-edge on Android 15.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.addView(page); setContentView(scroll);
        Ui.add(page, Ui.text(this, "Tambola Keyboard", 30), -2);
        Ui.add(page, Ui.text(this, "Stay in your WhatsApp group. Tap Next number, then WhatsApp Send.", 18), -2);
        setupStatus = Ui.text(this, "", 15); Ui.add(page, setupStatus, -2);
        Ui.add(page, Ui.button(this, "1. Enable Tambola Keyboard", Ui.PINK, () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))), 54);
        Ui.add(page, Ui.button(this, "2. Choose Tambola Keyboard", Ui.GREEN, this::chooseKeyboard), 54);
        Ui.add(page, Ui.text(this, "Android shows its standard keyboard warning. This keyboard has no Internet permission and does not store message text. Tap ABC ↔ to switch back anytime.", 15), -2);
        Ui.add(page, Ui.text(this, "3. Try it here first", 23), -2);
        Ui.add(page, Ui.text(this, "Practice uses a separate round. Nothing here is sent to WhatsApp or added to your live game.", 15), -2);
        practice = new EditText(this); practice.setId(View.generateViewId()); practice.setHint("Tap here, then Next number on the keyboard");
        practice.setContentDescription("Practice message"); practice.setTextSize(24); practice.setMinHeight(Ui.dp(this, 64));
        practice.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        practice.setPrivateImeOptions(TambolaInputMethod.PRACTICE); Ui.add(page, practice, -2);
        Ui.add(page, Ui.button(this, "Clear practice message", Ui.PAPER, () -> { practice.setText(""); practice.requestFocus(); }), 48);
        Ui.add(page, Ui.button(this, "Reset practice round", Ui.PAPER, () -> {
            try { new GameStore(this, true).save(Game.empty()); practice.setText(""); hideKeyboard(); toast("Practice cleared. Tap the box to try again."); }
            catch (IllegalStateException error) { toast(error.getMessage()); }
        }), 48);
        Ui.add(page, Ui.text(this, "Your live round", 25), -2);
        Ui.add(page, Ui.text(this, "In WhatsApp: open the correct group, tap its message box, and choose Tambola Keyboard. It calls its own round; use this keyboard as the caller for the whole game. Website games are separate.", 16), -2);
        gameStatus = Ui.text(this, "", 20); Ui.add(page, gameStatus, -2);
        recent = Ui.text(this, "", 16); Ui.add(page, recent, -2);
        board = Ui.column(this); Ui.add(page, board, -2);
        undo = Ui.button(this, "Undo last live number", Ui.PAPER, () -> confirm("Undo the last number?", "This returns it to the pool. Messages already sent in WhatsApp stay there; tell your group about any correction.", () -> store.save(store.load().undo())));
        Ui.add(page, undo, 48);
        Ui.add(page, Ui.button(this, "Start a new live game", Ui.PINK, () -> confirm("Start a new live game?", "Clear this keyboard's called numbers and start with all 90 available. WhatsApp messages stay unchanged.", () -> store.save(Game.empty()))), 54);
        Ui.add(page, Ui.text(this, "Numbers are saved on this phone. Called means generated, not confirmed sent. If insertion fails, use Insert again. No account, network, ads, contacts access, or automatic sending. v" + BuildConfig.VERSION_NAME, 14), -2);
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    private void chooseKeyboard() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); }
    private void hideKeyboard() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(practice.getWindowToken(), 0); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void confirm(String title, String message, Runnable action) {
        hideKeyboard();
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Keep playing", null).setPositiveButton("Confirm", (dialog, which) -> {
            try { action.run(); refresh(); } catch (IllegalArgumentException | IllegalStateException error) { toast(error.getMessage()); }
        }).show();
    }
    private void refresh() {
        if (setupStatus == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean enabled = false;
        for (InputMethodInfo info : manager.getEnabledInputMethodList()) if (info.getPackageName().equals(getPackageName())) enabled = true;
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        setupStatus.setText(current != null && current.startsWith(getPackageName() + "/") ? "✓ Tambola Keyboard is selected" : enabled ? "✓ Enabled · choose it in step 2" : "Enable the keyboard once to get started");
        try {
            Game game = store.load();
            gameStatus.setText(getString(R.string.live_progress, game.count(), game.latest() > 0 ? getString(R.string.latest_number, Game.emoji(game.latest())) : getString(R.string.ready)));
            recent.setText(getString(R.string.recent_latest_first, game.recent())); undo.setEnabled(game.count() > 0);
            board.removeAllViews();
            for (int row = 0; row < 9; row++) {
                LinearLayout line = new LinearLayout(this);
                for (int col = 0; col < 10; col++) {
                    int value = row * 10 + col + 1;
                    TextView cell = Ui.text(this, Integer.toString(value), 14); cell.setGravity(Gravity.CENTER);
                    boolean latest = value == game.latest(), called = game.called().contains(value);
                    cell.setBackgroundColor(latest ? Ui.PINK : called ? Color.rgb(247, 213, 227) : Color.WHITE);
                    cell.setTextColor(latest ? Color.WHITE : Ui.INK); cell.setContentDescription(value + (latest ? ", latest" : called ? ", called" : ", waiting"));
                    LinearLayout.LayoutParams cellSize = new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1); cellSize.setMargins(1, 1, 1, 1); line.addView(cell, cellSize);
                }
                board.addView(line);
            }
        } catch (IllegalArgumentException error) {
            gameStatus.setText(R.string.unreadable_live); recent.setText(""); board.removeAllViews(); undo.setEnabled(false);
        }
    }
}
