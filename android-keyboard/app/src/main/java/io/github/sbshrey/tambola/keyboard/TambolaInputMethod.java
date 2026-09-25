package io.github.sbshrey.tambola.keyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.security.SecureRandom;

public final class TambolaInputMethod extends InputMethodService {
    static final String PRACTICE = "tambola-practice";
    private final SecureRandom random = new SecureRandom();
    private LinearLayout body, tabs;
    private ScrollView scroll;
    private TextView status;
    private GameStore store, liveStore, practiceStore;
    private Preferences preferences;
    private CallAudio audio;
    private boolean practice, textField, mediaBusy, draftBlocked;
    private long lastDraw;
    private int session;
    private String panel = "Call", awardId;
    private String shownRound;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRound = this::refreshSavedRound;
    private final SharedPreferences.OnSharedPreferenceChangeListener roundListener = (preferences, key) -> {
        if ("called-v1".equals(key)) { handler.removeCallbacks(refreshRound); handler.post(refreshRound); }
    };

    @Override public void onCreate() {
        setTheme(R.style.AppTheme); super.onCreate(); preferences = new Preferences(this); audio = new CallAudio(this);
        liveStore = new GameStore(this, false); practiceStore = new GameStore(this, true);
        liveStore.observe(roundListener); practiceStore.observe(roundListener);
    }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public View onCreateInputView() {
        LinearLayout root = Ui.column(this); root.setBackgroundColor(Ui.PAPER); int pad = Ui.dp(this, 8); root.setPadding(pad, pad, pad, pad);
        tabs = new LinearLayout(this); Ui.add(root, tabs, 48);
        scroll = new ScrollView(this); scroll.setContentDescription("Tambola controls"); scroll.setFillViewport(false); body = Ui.column(this); scroll.addView(body);
        int screen = getResources().getDisplayMetrics().heightPixels;
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int height = Math.max(Ui.dp(this, 115), Math.min(Ui.dp(this, 360), screen * (landscape ? 70 : 55) / 100 - Ui.dp(this, 104)));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, height));
        status = Ui.text(this, "", 16); status.setGravity(Gravity.CENTER); status.setMaxLines(2); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); Ui.add(root, status, -2);
        store = practice ? practiceStore : liveStore; render(); return root;
    }
    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting); configureEditor(info);
    }
    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting); configureEditor(info);
        if (render()) say(!textField ? "Tap ABC to use your normal keyboard." : practice ? "Practice only · nothing is sent" : "1. Next number   2. WhatsApp Send");
        if (scroll != null) scroll.post(() -> scroll.scrollTo(0, 0));
    }
    private void configureEditor(EditorInfo info) {
        session++; mediaBusy = false; draftBlocked = false; panel = "Call"; shownRound = null;
        practice = getPackageName().equals(info.packageName) && PRACTICE.equals(info.privateImeOptions); store = practice ? practiceStore : liveStore;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        textField = (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT && variation != InputType.TYPE_TEXT_VARIATION_PASSWORD
            && variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD && variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            && !"tambola-settings".equals(info.privateImeOptions);
    }
    @Override public void onWindowShown() { super.onWindowShown(); refreshSavedRound(); }
    @Override public void onFinishInputView(boolean finishing) { session++; mediaBusy = false; audio.stop(); super.onFinishInputView(finishing); }
    @Override public void onDestroy() { handler.removeCallbacks(refreshRound); liveStore.stopObserving(roundListener); practiceStore.stopObserving(roundListener); audio.stop(); super.onDestroy(); }
    private void refreshSavedRound() {
        if (store == null || body == null || !isInputViewShown()) return;
        try {
            Game game = store.load(); if (game.encode().equals(shownRound)) return;
            boolean reset = shownRound != null && !shownRound.isEmpty() && game.count() == 0;
            if (reset) { session++; mediaBusy = false; lastDraw = 0; panel = "Call"; audio.stop(); }
            render();
            if (reset) { scroll.scrollTo(0, 0); say("New game ready. Send or clear any old draft, then Next number."); }
        } catch (IllegalArgumentException | IllegalStateException error) { render(); }
    }
    private void chooseKeyboard() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); }
    private void open(String section) { audio.stop(); startActivity(new Intent(this, MainActivity.class).putExtra("section", section).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); }
    private void navigate(String next) { panel = next; render(); scroll.scrollTo(0, 0); say(practice ? "Practice only · nothing is sent" : "You choose when to send in WhatsApp."); }
    private boolean render() {
        if (body == null || store == null) return false; body.removeAllViews(); tabs.removeAllViews();
        for (String name : new String[]{"Call", "Board", "Winners"}) Ui.weighted(tabs, Ui.button(this, name, panel.equals(name) ? Ui.PINK : Ui.PAPER, () -> navigate(name)));
        try {
            Game game = store.load();
            shownRound = game.encode();
            if (!textField) { Ui.add(body, Ui.text(this, "Use your normal keyboard here", 22), -2); Ui.action(body, "ABC · Change keyboard", Ui.GREEN, this::chooseKeyboard); return true; }
            if (panel.equals("Call")) call(game);
            else if (panel.equals("Board")) board(game);
            else if (panel.equals("Winners")) winners(game);
            else if (panel.equals("Voice")) voice();
            else if (panel.equals("Share")) share(game);
            else if (panel.equals("New game")) newGame();
            else if (panel.equals("Clear message")) confirmClearMessage();
            else if (panel.equals("Award")) { Ui.action(body, "‹ Back to winners", Ui.PAPER, () -> navigate("Winners")); PrizeViews.award(body, store, awardId, false, () -> { navigate("Winners"); say("Winners saved. Tap their announcement to insert it."); }, this::say); }
            return true;
        } catch (IllegalArgumentException | IllegalStateException error) { Ui.add(body, Ui.text(this, error.getMessage(), 18), -2); Ui.action(body, "Open help & setup", Ui.PAPER, () -> open("help")); say("Saved details need attention. Open help & setup."); return false; }
    }
    private void call(Game game) {
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView number = Ui.text(this, game.latest() == 0 ? "—" : Game.emoji(game.latest()), getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 26 : 38); number.setGravity(Gravity.CENTER); number.setContentDescription("Latest Tambola number");
        heading.addView(number, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = Ui.text(this, (practice ? "PRACTICE\n" : "") + game.count() + " / 90", 18); heading.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        Button abc = Ui.button(this, "ABC", Ui.PAPER, this::chooseKeyboard); abc.setContentDescription("Switch to your regular keyboard"); heading.addView(abc, new LinearLayout.LayoutParams(Ui.dp(this, 72), -2)); Ui.add(body, heading, -2);
        TextView phrase = Ui.text(this, CallAudio.phrase(this, preferences.language(), game.latest()), 18); phrase.setGravity(Gravity.CENTER); Ui.add(body, phrase, -2);
        Button next = Ui.button(this, game.count() == 90 ? "All 90 called" : "Next number", Ui.GREEN, () -> insertNumber(true)); next.setContentDescription("Next number"); next.setEnabled(game.count() < 90 && !mediaBusy); Ui.add(body, next, 58);
        if (practice) Ui.action(body, "Clear practice message", Ui.PINK, this::clearPractice).setContentDescription("Clear practice draft");
        else if (draftBlocked) Ui.action(body, "Clear unsent message…", Ui.PINK, () -> navigate("Clear message"));
        LinearLayout actions = new LinearLayout(this);
        Button hear = Ui.button(this, "Hear again", Ui.PAPER, () -> audio.play(game.latest(), preferences.language(), this::say)); hear.setEnabled(game.latest() > 0);
        Button again = Ui.button(this, "Insert again", Ui.PAPER, () -> insertNumber(false)); again.setContentDescription("Insert current number again"); again.setEnabled(game.latest() > 0 && !mediaBusy);
        Ui.weighted(actions, hear); Ui.weighted(actions, again); Ui.add(body, actions, 52);
        LinearLayout more = new LinearLayout(this); Ui.weighted(more, Ui.button(this, "Share…", Ui.PAPER, () -> navigate("Share"))); Ui.weighted(more, Ui.button(this, "Voice…", Ui.PAPER, () -> navigate("Voice"))); Ui.add(body, more, 52);
        Ui.add(body, Ui.text(this, "Recent: " + game.recent(), 16), -2);
    }
    private void board(Game game) {
        Ui.add(body, Ui.text(this, game.count() + " of 90 called", 22), -2);
        Ui.action(body, practice ? "Reset practice round" : "Start a new game", Ui.PINK, () -> navigate("New game"));
        Ui.action(body, "Share board picture", Ui.GREEN, () -> media(Sharing.BOARD)).setEnabled(!practice && game.latest() > 0 && !mediaBusy);
        Ui.board(body, game); Ui.add(body, Ui.text(this, "Pink = called · Dark pink = latest", 16), -2);
        Ui.action(body, "Undo / Full board", Ui.PAPER, () -> open(practice ? "practice" : "board"));
    }
    private void newGame() {
        Ui.add(body, Ui.text(this, practice ? "Reset practice round?" : "Start a new game?", 24), -2);
        Ui.add(body, Ui.text(this, practice ? "Practice numbers will be cleared. Your live game stays unchanged." : "Called numbers and winners will be cleared. Player names and prize settings stay. Share results first if needed.", 19), -2);
        Ui.add(body, Ui.text(this, "Any unsent message stays in the box. Send or clear it before calling again.", 18), -2);
        Ui.action(body, "Keep playing", Ui.PAPER, () -> navigate("Call"));
        Ui.action(body, "Yes, start new game", Ui.PINK, () -> {
            try {
                store.newRound(); session++; mediaBusy = false; lastDraw = 0; draftBlocked = false; audio.stop(); navigate("Call");
                if (emptyEditor() != null) say("New game ready. Tap Next number.");
            } catch (IllegalArgumentException | IllegalStateException error) { say(error.getMessage()); }
        });
    }
    private void confirmClearMessage() {
        Ui.add(body, Ui.text(this, "Clear the unsent message?", 24), -2);
        Ui.add(body, Ui.text(this, "This removes the text in the message box. Messages already sent and your called numbers stay unchanged.", 19), -2);
        Ui.action(body, "Keep message", Ui.PAPER, () -> navigate("Call"));
        int started = session;
        Ui.action(body, "Yes, clear message", Ui.PINK, () -> {
            if (started != session || !textField) return;
            InputConnection editor = getCurrentInputConnection(); if (editor == null) { say("Tap the message box, then try again."); return; }
            boolean cleared;
            editor.beginBatchEdit();
            try { editor.finishComposingText(); cleared = editor.setSelection(0, 0) && editor.deleteSurroundingText(0, Integer.MAX_VALUE); }
            finally { editor.endBatchEdit(); }
            draftBlocked = false; navigate("Call");
            say(cleared ? "Message cleared. Tap Next number." : "Tap ABC and clear the message with your normal keyboard.");
        });
    }
    private void voice() {
        Ui.action(body, "‹ Back to call", Ui.PAPER, () -> navigate("Call")); Ui.add(body, Ui.text(this, "Spoken language", 23), -2);
        for (int i = 0; i < Preferences.LANGUAGES.length; i++) { String language = Preferences.LANGUAGES[i], label = Preferences.LABELS[i]; Ui.action(body, label + (language.equals(preferences.language()) ? " ✓" : ""), language.equals(preferences.language()) ? Ui.GREEN : Ui.PAPER, () -> { preferences.language(language); render(); say("Voice set to " + label); }); }
        Ui.action(body, preferences.voice() ? "Voice is ON · tap for OFF" : "Voice is OFF · tap for ON", Ui.PAPER, () -> { preferences.voice(!preferences.voice()); audio.stop(); render(); });
        Ui.add(body, Ui.text(this, "Sound plays on this phone only. Turn up media volume. These are AI-generated recordings.", 17), -2);
    }
    private void share(Game game) {
        Ui.action(body, "‹ Back to call", Ui.PAPER, () -> navigate("Call")); if (game.latest() == 0) { Ui.add(body, Ui.text(this, "Call a number first.", 20), -2); return; }
        Ui.add(body, Ui.text(this, "Choose what to share", 23), -2); Ui.action(body, "Insert recent numbers", Ui.GREEN, () -> insertText(Sharing.recent(store.load())));
        for (String kind : new String[]{Sharing.NUMBER, Sharing.RECENT, Sharing.BOARD, Sharing.AUDIO}) {
            String label = kind.equals(Sharing.NUMBER) ? "Number picture" : kind.equals(Sharing.RECENT) ? "Recent numbers picture" : kind.equals(Sharing.BOARD) ? "Board picture" : "Voice clip · " + preferences.label();
            Button button = Ui.button(this, label, Ui.PAPER, () -> media(kind)); button.setEnabled(!mediaBusy && !practice); Ui.add(body, button, -2);
        }
        Ui.add(body, Ui.text(this, practice ? "File sharing is off during practice." : "Pictures go into this chat when supported. Otherwise choose WhatsApp and your group. Check the preview, then Send.", 17), -2);
    }
    private void winners(Game game) {
        if (practice) { Ui.add(body, Ui.text(this, "Winners are for your live game. Practice does not change them.", 20), -2); return; }
        PrizeBook book = store.prizes(); Ui.add(body, Ui.text(this, "Winners (optional)", 23), -2);
        Ui.action(body, "Choose prize schemes", Ui.PAPER, () -> open("catalog"));
        Ui.action(body, "Players & prize amounts", Ui.PAPER, () -> open("players"));
        if (book.players.isEmpty()) { Ui.add(body, Ui.text(this, "Add names once above. You can keep calling numbers without this.", 18), -2); return; }
        if (book.hasWinners()) Ui.action(body, "Insert all results", Ui.GREEN, () -> insertText(store.prizes().results(store.load().count())));
        for (PrizeBook.Scheme item : book.schemes) if (item.enabled) {
            Ui.action(body, item.name + " · " + PrizeBook.money((item.winners.isEmpty() ? item.rupees : item.awardedRupees) * 100) + "\n" + (item.winners.isEmpty() ? "Choose winners" : book.winners(item)), Ui.PAPER, () -> { awardId = item.id; navigate("Award"); });
            if (!item.winners.isEmpty()) Ui.action(body, "Insert " + item.name + " announcement", Ui.GREEN, () -> { PrizeBook current = store.prizes(); insertText(current.announcement(current.scheme(item.id))); });
        }
    }
    private void say(String message) { if (status != null) status.setText(message); }
    private InputConnection emptyEditor() {
        if (!textField) return null; InputConnection editor = getCurrentInputConnection(); if (editor == null) { say("Tap the empty message box, then try again."); return null; }
        CharSequence before = editor.getTextBeforeCursor(1, 0), after = editor.getTextAfterCursor(1, 0), selected = editor.getSelectedText(0);
        if (before == null || after == null) { say("Tap the empty message box, then try again."); return null; }
        if (before.length() > 0 || after.length() > 0 || (selected != null && selected.length() > 0)) {
            draftBlocked = true;
            if (panel.equals("Call")) { render(); scroll.scrollTo(0, 0); }
            say("Send or clear the current message first."); return null;
        }
        draftBlocked = false; return editor;
    }
    private void insertText(String text) { InputConnection editor = emptyEditor(); if (editor == null) return; boolean ready = editor.commitText(text, 1); say(ready ? (practice ? "Practice only · nothing is sent" : "Ready · tap WhatsApp Send") : "Could not insert. Tap the message box and try again."); }
    private void insertNumber(boolean draw) {
        if (mediaBusy) return; InputConnection editor = emptyEditor(); if (editor == null) return; if (draw && SystemClock.elapsedRealtime() - lastDraw < 650) return;
        try {
            Game game = store.load(); if (draw) { if (game.count() == 90) return; game = game.draw(random); store.save(game); lastDraw = SystemClock.elapsedRealtime(); }
            if (game.latest() == 0) return; boolean inserted = editor.commitText(Game.emoji(game.latest()), 1); render(); scroll.post(() -> scroll.scrollTo(0, 0));
            say(inserted ? practice ? "Practice only · tap Clear to try again" : "Number ready · tap WhatsApp Send" : "Number saved. Tap the message box and Insert again.");
            if (draw && preferences.voice()) audio.play(game.latest(), preferences.language(), this::say);
        } catch (IllegalArgumentException | IllegalStateException error) { render(); say(error.getMessage()); }
    }
    private void clearPractice() {
        if (!practice) return; InputConnection editor = getCurrentInputConnection(); if (editor == null) return;
        editor.beginBatchEdit(); editor.setSelection(0, 0); editor.deleteSurroundingText(0, Integer.MAX_VALUE); editor.endBatchEdit(); say("Practice box cleared. Tap Next number.");
    }
    private void media(String kind) {
        if (practice || mediaBusy || emptyEditor() == null) return; Game snapshot = store.load(); if (snapshot.latest() == 0) { say("Call a number first."); return; }
        int started = session; mediaBusy = true; render(); say("Preparing… please wait"); String mime = Sharing.AUDIO.equals(kind) ? "audio/mpeg" : "image/png";
        Sharing.prepare(this, snapshot, preferences.language(), kind, uri -> {
            if (started != session) return; mediaBusy = false; render();
            if (!store.load().encode().equals(snapshot.encode())) { say("The game changed. Please share again."); return; }
            InputConnection editor = emptyEditor(); if (editor == null) return;
            if (Sharing.insert(editor, getCurrentInputEditorInfo(), uri, mime)) say("Check the attachment, then tap Send.");
            else try { audio.stop(); Sharing.file(this, uri, mime); say("Choose WhatsApp, your group, then Send."); } catch (RuntimeException error) { say("Could not open sharing. Try from the app's Board screen."); }
        }, error -> { if (started == session) { mediaBusy = false; render(); say(error); } });
    }
}
