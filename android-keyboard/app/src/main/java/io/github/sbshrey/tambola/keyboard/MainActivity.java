package io.github.sbshrey.tambola.keyboard;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private GameStore store;
    private Preferences preferences;
    private CallAudio audio;
    private LinearLayout page;
    private EditText practice;
    private ScrollView scroll;
    private String section = "home", awardId;
    private int viewSession;
    private boolean sharing;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store = new GameStore(this, false); preferences = new Preferences(this); audio = new CallAudio(this);
        section = saved == null ? getIntent().getStringExtra("section") : saved.getString("section", "home");
        if (section == null) section = enabled() ? "home" : "help";
        if (saved != null) awardId = saved.getString("awardId");
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Ui.PAPER);
        if (Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener((view, insets) -> { android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets; });
        page = Ui.column(this); int pad = Ui.dp(this, 18); page.setPadding(pad, pad, pad, pad); page.setFocusableInTouchMode(true); scroll.addView(page); setContentView(scroll); render();
    }
    @Override public void onResume() { super.onResume(); if (!section.equals("practice") || practice == null) render(); }
    @Override public void onPause() { audio.stop(); viewSession++; sharing = false; super.onPause(); }
    @Override public void onDestroy() { audio.stop(); super.onDestroy(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); String requested = intent.getStringExtra("section"); if (requested != null) section = requested; render(); }
    @Override protected void onSaveInstanceState(Bundle saved) { saved.putString("section", section); saved.putString("awardId", awardId); super.onSaveInstanceState(saved); }
    private boolean enabled() { for (InputMethodInfo info : ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).getEnabledInputMethodList()) if (info.getPackageName().equals(getPackageName())) return true; return false; }
    private void chooseKeyboard() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); }
    private void hideKeyboard() { View focus = getCurrentFocus(); if (focus != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void go(String next) { hideKeyboard(); audio.stop(); section = next; render(); scroll.scrollTo(0, 0); }
    private void text(String value, int size) { Ui.add(page, Ui.text(this, value, size), -2); }
    private void action(String label, int color, Runnable click) { Ui.action(page, label, color, click); }
    private void safe(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException error) { toast(error.getMessage()); } }
    private void render() {
        if (page == null) return; viewSession++; sharing = false; page.removeAllViews(); practice = null;
        if (!section.equals("home")) action("‹ Main menu", Ui.PAPER, () -> go("home"));
        try {
            switch (section) {
                case "help": help(); break;
                case "practice": practice(); break;
                case "board": board(); break;
                case "players": players(); break;
                case "voice": voice(); break;
                case "results": results(); break;
                case "award": action("‹ All prizes", Ui.PAPER, () -> go("players")); PrizeViews.award(page, store, awardId, true, () -> go("players"), this::toast); break;
                default: home();
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            text(error.getMessage(), 20); action("Help", Ui.PAPER, () -> go("help"));
            action("Reset unreadable prize details", Ui.PAPER, () -> confirm("Reset prize details?", "This clears player names, prizes and winners. Called numbers stay saved.", () -> { store.prizes(PrizeBook.defaults()); go("players"); }));
            action("Reset saved live game", Ui.PAPER, () -> confirm("Reset saved numbers and winners?", "Start again with all 90 numbers. Player names and prize settings stay. Messages sent to WhatsApp stay unchanged.", () -> { store.newRound(); go("board"); }));
        }
    }
    private void home() {
        text("Tambola Keyboard", 30); text("One phone. Two taps per number.", 22);
        text("1. Open your WhatsApp group.\n2. Tap the message box. Choose Tambola Keyboard.\n3. Tap Next number, then WhatsApp Send.", 20);
        action("Help me set it up", Ui.GREEN, () -> go("help"));
        action("Practice first", Ui.PINK, () -> go("practice"));
        action("See board / New game", Ui.PAPER, () -> go("board"));
        action("Voice & language", Ui.PAPER, () -> go("voice"));
        action("Players & prizes (optional)", Ui.PAPER, () -> go("players"));
        action("Results to share", Ui.PAPER, () -> go("results"));
        text("Use this keyboard for the whole game. Website games are separate. v" + BuildConfig.VERSION_NAME, 16);
    }
    private void help() {
        text("Set up once", 28);
        text(enabled() ? "✓ Keyboard is enabled" : "First, enable the keyboard on this phone.", 20);
        action("1. Enable Tambola Keyboard", Ui.PINK, () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        text("Turn on Tambola Keyboard, then come back here. Android shows its usual warning for keyboards.", 18);
        action("2. Choose Tambola Keyboard", Ui.GREEN, this::chooseKeyboard);
        action("3. Practice safely", Ui.PAPER, () -> go("practice"));
        text("To play", 25); text("Open the right WhatsApp group and tap its message box.\n\nNext number puts the emoji number into the box. Tap WhatsApp Send. Repeat for each call.", 20);
        text("Useful buttons", 25); text("Hear again: hear the same number.\nInsert again: put the same number in the box.\nABC: return to normal typing.\nBoard: see called numbers.\nWinners: optional prize tracking.", 19);
        text("Swipe up inside the keyboard to see more buttons. Tap Call at the top to return to Next number.", 19);
        text("Pictures and voice clips", 25); text("Tap Share in the keyboard. Choose a picture or voice clip. If a chooser opens, select WhatsApp and your group. Check the preview, then Send. Sound played aloud stays on this phone.", 19);
        text("Your privacy", 25); text("No accounts, ads, Internet, microphone or contacts permission. The app checks the current draft only to avoid overwriting it; message text is not saved. Voice clips are AI-generated and included in the app.", 17);
    }
    private void practice() {
        text("Practice safely", 28); text("Nothing here is sent to WhatsApp. Your live game stays unchanged.", 20);
        action("Choose Tambola Keyboard", Ui.PINK, this::chooseKeyboard);
        text("1. Tap the box below.\n2. Tap Next number on the keyboard.\n3. Tap Clear practice message to try again.", 19);
        practice = new EditText(this); practice.setId(R.id.practice_message); practice.setContentDescription("Practice message"); practice.setHint("Tap here to practise"); practice.setTextSize(28); practice.setMinHeight(Ui.dp(this, 72));
        practice.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); practice.setPrivateImeOptions(TambolaInputMethod.PRACTICE); Ui.add(page, practice, -2);
        action("Clear practice message", Ui.GREEN, () -> practice.setText(""));
        action("Reset practice round", Ui.PAPER, () -> safe(() -> { new GameStore(this, true).newRound(); practice.setText(""); hideKeyboard(); toast("Practice reset. Tap the box to try again."); }));
        text("When ready, open your WhatsApp group. Your live round starts there.", 19);
    }
    private void board() {
        Game game = store.load(); text("Your live board", 28); text(game.count() + " of 90 called" + (game.latest() > 0 ? " · latest " + game.latest() : ""), 22);
        text("Recent: " + game.recent(), 18); Ui.board(page, game); text("Pink = called · Dark pink = latest", 16);
        if (game.latest() > 0) {
            action("Hear latest number", Ui.GREEN, () -> audio.play(game.latest(), preferences.language(), this::toast));
            action("Share number picture", Ui.PAPER, () -> share(Sharing.NUMBER)); action("Share recent numbers picture", Ui.PAPER, () -> share(Sharing.RECENT));
            action("Share board picture", Ui.PAPER, () -> share(Sharing.BOARD)); action("Share latest voice clip", Ui.PAPER, () -> share(Sharing.AUDIO));
            action("Undo last number", Ui.PAPER, () -> confirm("Undo number " + game.latest() + "?", "It returns to the pool. Winners recorded on this call will be cleared. Tell the group about the correction; sent messages stay unchanged.", () -> { store.undo(); render(); }));
        }
        action("Start a new live game", Ui.PINK, () -> confirm("Start a new game?", "Called numbers and winners will be cleared. Player names and prize settings stay. Share results first if you need them. WhatsApp messages stay unchanged.", () -> { store.newRound(); render(); }));
        text("These are the same numbers used by your keyboard. Called means generated, not confirmed sent.", 17);
    }
    private void voice() {
        text("Voice & language", 28); text("Choose the spoken language. Button labels stay in English.", 19);
        for (int i = 0; i < Preferences.LANGUAGES.length; i++) { String language = Preferences.LANGUAGES[i], label = Preferences.LABELS[i]; action(label + (language.equals(preferences.language()) ? " ✓" : ""), language.equals(preferences.language()) ? Ui.GREEN : Ui.PAPER, () -> safe(() -> { preferences.language(language); render(); })); }
        action(preferences.voice() ? "Voice is ON · tap for OFF" : "Voice is OFF · tap for ON", Ui.PAPER, () -> safe(() -> { preferences.voice(!preferences.voice()); audio.stop(); render(); }));
        action("Hear a sample: number 22", Ui.PINK, () -> audio.play(22, preferences.language(), this::toast));
        text("Turn up media volume. Recordings work offline and play on this phone only. Use Share voice clip to send an audio attachment. These are AI-generated recordings.", 19);
        text("While a clip plays, Android may show a playback notification with a Stop button.", 17);
    }
    private EditText input(String hint, String initial, int type) { EditText field = new EditText(this); field.setTextSize(20); field.setHint(hint); field.setText(initial); field.setInputType(type); field.setMinHeight(Ui.dp(this, 56)); field.setPrivateImeOptions("tambola-settings"); return field; }
    private void players() {
        PrizeBook book = store.prizes(); text("Players & prizes", 28); text("Optional. You can set this up before or during a game.", 19);
        action("Add player names", Ui.GREEN, this::addPlayers); action("Use normal keyboard to type names", Ui.PAPER, this::chooseKeyboard);
        for (Map.Entry<String, String> person : book.players.entrySet()) action(person.getValue() + " · edit", Ui.PAPER, () -> editPlayer(person.getKey()));
        text("Prizes", 26); text("Default total ₹10. Two winners receive ₹5 each. Tap a prize to choose winners after checking their tickets.", 19);
        for (PrizeBook.Scheme item : book.schemes) {
            if (item.enabled) action(item.name + " · " + PrizeBook.money((item.winners.isEmpty() ? item.rupees : item.awardedRupees) * 100) + "\n" + (item.winners.isEmpty() ? "Choose winners" : book.winners(item)), Ui.PINK, () -> { awardId = item.id; go("award"); });
            action("Change " + item.name + (item.enabled ? "" : " (off)"), Ui.PAPER, () -> editScheme(item.id));
        }
        action("Add another prize", Ui.PAPER, () -> editScheme(null)); action("See / share results", Ui.GREEN, () -> go("results"));
    }
    private void addPlayers() {
        LinearLayout box = dialogBox(); Ui.add(box, Ui.text(this, "One name per line, or use commas. Names must be different.", 18), -2);
        EditText names = input("Names", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); names.setMinLines(3); Ui.add(box, names, -2);
        editDialog("Add players", box, "Save names", () -> { PrizeBook book = store.prizes(); String[] values = names.getText().toString().split("[,\\n]", -1); boolean added = false; for (String name : values) if (!name.trim().isEmpty()) { book = book.player(null, name); added = true; } if (!added) throw new IllegalArgumentException("Enter at least one name."); store.prizes(book); });
    }
    private void editPlayer(String id) {
        LinearLayout box = dialogBox(); EditText name = input("Player name", store.prizes().players.get(id), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); Ui.add(box, name, -2);
        AlertDialog dialog = editDialog("Edit player", box, "Save name", () -> store.prizes(store.prizes().player(id, name.getText().toString())));
        Ui.action(box, "Remove player", Ui.PAPER, () -> confirm("Remove this player?", "Players with a recorded win cannot be removed until their wins are cleared.", () -> { store.prizes(store.prizes().removePlayer(id)); dialog.dismiss(); render(); }));
    }
    private void editScheme(String id) {
        PrizeBook.Scheme item = id == null ? null : store.prizes().scheme(id); LinearLayout box = dialogBox();
        Ui.add(box, Ui.text(this, "Prize name", 18), -2); EditText name = input("Prize name", item == null ? "" : item.name, InputType.TYPE_CLASS_TEXT); Ui.add(box, name, -2);
        Ui.add(box, Ui.text(this, "Total prize in rupees", 18), -2); EditText amount = input("10", Integer.toString(item == null ? 10 : item.rupees), InputType.TYPE_CLASS_NUMBER); Ui.add(box, amount, -2);
        CheckBox enabled = new CheckBox(this); enabled.setText(R.string.use_prize); enabled.setTextSize(20); enabled.setChecked(item == null || item.enabled); Ui.add(box, enabled, 56);
        Ui.add(box, Ui.text(this, "This is the default for future awards. To change a recorded payout, edit its winners.", 17), -2);
        editDialog("Prize settings", box, "Save prize", () -> { int rupees; try { rupees = Integer.parseInt(amount.getText().toString()); } catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a whole-rupee prize from 0 to 100000."); } store.prizes(store.prizes().configure(id, name.getText().toString(), rupees, enabled.isChecked())); });
    }
    private LinearLayout dialogBox() { LinearLayout box = Ui.column(this); int pad = Ui.dp(this, 20); box.setPadding(pad, pad, pad, pad); return box; }
    private AlertDialog editDialog(String title, LinearLayout box, String positive, Runnable save) {
        ScrollView wrapper = new ScrollView(this); wrapper.addView(box); AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(wrapper).setNegativeButton("Cancel", null).setPositiveButton(positive, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> safe(() -> { save.run(); hideKeyboard(); dialog.dismiss(); render(); }))); dialog.show(); return dialog;
    }
    private void results() { PrizeBook book = store.prizes(); text("Results to share", 28); if (!book.hasWinners()) { text("No winners saved yet. Add winners under Players & prizes, or keep playing.", 20); action("Players & prizes", Ui.PINK, () -> go("players")); return; } text(book.results(store.load().count()), 20); action("Share results to WhatsApp", Ui.GREEN, () -> { try { Sharing.text(this, store.prizes().results(store.load().count())); } catch (RuntimeException error) { toast("Could not open sharing. Use Winners in the keyboard."); } }); text("Or return to your group: keyboard → Winners → Insert all results → WhatsApp Send.", 18); }
    private void confirm(String title, String message, Runnable action) { hideKeyboard(); new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Keep playing", null).setPositiveButton("Yes, continue", (dialog, which) -> safe(action)).show(); }
    private void share(String kind) {
        if (sharing) return; sharing = true; int started = viewSession; Game snapshot = store.load(); toast("Preparing…");
        Sharing.prepare(this, snapshot, preferences.language(), kind, uri -> { if (isFinishing() || started != viewSession) return; sharing = false;
            try { Sharing.file(this, uri, Sharing.AUDIO.equals(kind) ? "audio/mpeg" : "image/png"); } catch (RuntimeException error) { toast("No sharing app is available."); }
        }, error -> { if (started == viewSession) { sharing = false; toast(error); } });
    }
}
