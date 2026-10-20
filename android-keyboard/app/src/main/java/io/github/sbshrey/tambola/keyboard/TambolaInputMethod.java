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
import java.util.*;

public final class TambolaInputMethod extends InputMethodService {
    static final String PRACTICE = "tambola-practice";
    private final SecureRandom random = new SecureRandom();
    private LinearLayout body, tabs;
    private ScrollView scroll;
    private TextView status;
    private GameStore store, liveStore, practiceStore;
    private Preferences preferences;
    private CallAudio audio;
    private boolean practice, textField, mediaBusy;
    private long lastDraw;
    private int session;
    private String panel = "Call", awardId;
    private GameMessages.Kind messageKind = GameMessages.Kind.PLAYERS;
    private String shownRound, shownPlan;
    private String undoRound, undoPrizes;
    private int undoSession;
    private String quickGroup = "Quick prizes", quickId, quickRound, quickPrizes;
    private int quickSession;
    private WinnerSpeech winnerSpeech;
    private int speechSession;
    private String speechMessage = "Say the prize, then the claimant names. Three or more means Bogie.";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRound = this::refreshSavedRound;
    private final SharedPreferences.OnSharedPreferenceChangeListener roundListener = (preferences, key) -> {
        if ("called-v1".equals(key) || "ticket-plan-v1".equals(key)) { handler.removeCallbacks(refreshRound); handler.post(refreshRound); }
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
        if (render()) say(!textField ? "Tap ABC to use your normal keyboard." : practice ? "Practice only · nothing is sent" : store.plan() != null && !store.plan().approved ? "Review tickets & schemes before calling." : "1. Next number   2. WhatsApp Send");
        if (scroll != null) scroll.post(() -> scroll.scrollTo(0, 0));
    }
    private void configureEditor(EditorInfo info) {
        cancelWinnerSpeech();
        session++; mediaBusy = false; panel = "Call"; shownRound = null;
        practice = getPackageName().equals(info.packageName) && PRACTICE.equals(info.privateImeOptions); store = practice ? practiceStore : liveStore;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        textField = (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT && variation != InputType.TYPE_TEXT_VARIATION_PASSWORD
            && variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD && variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            && !"tambola-settings".equals(info.privateImeOptions);
    }
    @Override public void onWindowShown() { super.onWindowShown(); refreshSavedRound(); }
    @Override public void onFinishInputView(boolean finishing) { cancelWinnerSpeech(); session++; mediaBusy = false; audio.stop(); super.onFinishInputView(finishing); }
    @Override public void onFinishInput() { cancelWinnerSpeech(); super.onFinishInput(); }
    @Override public void onWindowHidden() { cancelWinnerSpeech(); super.onWindowHidden(); }
    @Override public void onDestroy() { cancelWinnerSpeech(); handler.removeCallbacks(refreshRound); liveStore.stopObserving(roundListener); practiceStore.stopObserving(roundListener); audio.stop(); super.onDestroy(); }
    @Override public void onUpdateSelection(int oldStart, int oldEnd, int newStart, int newEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
        if (winnerSpeech != null && (newStart != 0 || newEnd != 0)) { cancelWinnerSpeech(); speechMessage = "Message changed. Send or clear it before speaking again."; render(); }
    }
    private void refreshSavedRound() {
        if (store == null || body == null || !isInputViewShown()) return;
        try {
            Game game = store.load(); if (game.encode().equals(shownRound) && store.planSnapshot().equals(shownPlan)) return;
            if (winnerSpeech != null) { cancelWinnerSpeech(); speechMessage = "Game changed. Tap Speak again."; }
            boolean reset = shownRound != null && !shownRound.isEmpty() && game.count() == 0;
            if (reset) { session++; mediaBusy = false; lastDraw = 0; panel = "Call"; audio.stop(); }
            render();
            if (reset) { scroll.scrollTo(0, 0); say(!practice && store.plan() != null ? "New game. Review tickets & schemes before calling." : "New game ready. Send or clear any old draft, then Next number."); }
        } catch (IllegalArgumentException | IllegalStateException error) { render(); }
    }
    private void chooseKeyboard() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); }
    private void open(String section) { cancelWinnerSpeech(); audio.stop(); startActivity(new Intent(this, MainActivity.class).putExtra("section", section).putExtra("returnToChat", section.equals("plan")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); }
    private void navigate(String next) { cancelWinnerSpeech(); panel = next; render(); scroll.scrollTo(0, 0); say(practice ? "Practice only · nothing is sent" : "You choose when to send in WhatsApp."); }
    private boolean render() {
        if (body == null || store == null) return false; body.removeAllViews(); tabs.removeAllViews();
        for (String name : new String[]{"Call", "Board", "Winners"}) Ui.weighted(tabs, Ui.button(this, name, panel.equals(name) ? Ui.PINK : Ui.PAPER, () -> navigate(name)));
        try {
            Game game = store.load();
            shownRound = game.encode();
            shownPlan = store.planSnapshot();
            if (!textField) { Ui.add(body, Ui.text(this, "Use your normal keyboard here", 22), -2); Ui.action(body, "ABC · Change keyboard", Ui.GREEN, this::chooseKeyboard); return true; }
            if (panel.equals("Call")) call(game);
            else if (panel.equals("Board")) board(game);
            else if (panel.equals("Winners")) winners(game);
            else if (panel.equals("Tap winners")) quickSchemes();
            else if (panel.equals("Tap names")) quickNames(game);
            else if (panel.equals("Voice")) voice();
            else if (panel.equals("Speak winner")) speakWinnerPanel();
            else if (panel.equals("Share")) share(game);
            else if (panel.equals("Game messages")) gameMessages();
            else if (panel.equals("New game")) newGame();
            else if (panel.equals("Clear message")) confirmClearMessage();
            else if (panel.equals("Undo number")) confirmUndo();
            else if (panel.equals("Award")) { Ui.action(body, "‹ Back to winners", Ui.PAPER, () -> navigate("Winners")); PrizeViews.award(body, store, awardId, () -> { navigate("Winners"); say("Winners saved. Tap their announcement to insert it."); }, this::say); }
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
        TicketPlan plan = practice ? null : store.plan();
        if (!practice && game.count() == 0) {
            Ui.action(body, plan == null ? "Set tickets & schemes" : "Review tickets & schemes", Ui.PAPER, () -> open("plan"));
            if (plan != null) Ui.add(body, Ui.text(this, plan.approved ? "Ready · " + plan.totalSets() + " sets · " + PrizeBook.money(plan.collection() * 100) + " prize budget" : "Confirm ticket sales and prize budget before calling.", 17), -2);
            Ui.action(body, "Game messages", Ui.GREEN, () -> navigate("Game messages"));
        }
        Button next = Ui.button(this, game.count() == 90 ? "All 90 called" : "Next number", Ui.GREEN, () -> insertNumber(true)); next.setContentDescription("Next number"); next.setEnabled(game.count() < 90 && !mediaBusy && (plan == null || plan.approved)); Ui.add(body, next, 58);
        if (!practice && game.count() == 90 && store.prizes().hasWinners()) Ui.action(body, "Share results picture", Ui.GREEN, this::resultsPicture).setEnabled(!mediaBusy);
        LinearLayout corrections = new LinearLayout(this);
        Button clear = Ui.button(this, practice ? "Clear practice message" : "Clear message…", Ui.PAPER,
            practice ? this::clearPractice : () -> navigate("Clear message"));
        clear.setContentDescription(practice ? "Clear practice draft" : "Clear unsent message");
        Button undo = Ui.button(this, "Undo last number", Ui.PINK, this::startUndo);
        undo.setEnabled(game.count() > 0 && !mediaBusy);
        Ui.weighted(corrections, clear); Ui.weighted(corrections, undo); Ui.add(body, corrections, 58);
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
        if (!practice) Ui.action(body, "Tickets & schemes", Ui.PAPER, () -> open("plan"));
        if (!practice && store.prizes().hasWinners()) Ui.action(body, "Share results picture", Ui.GREEN, this::resultsPicture).setEnabled(!mediaBusy);
        Ui.action(body, "Share board picture", Ui.GREEN, () -> media(Sharing.BOARD)).setEnabled(!practice && game.latest() > 0 && !mediaBusy);
        Ui.board(body, game); Ui.add(body, Ui.text(this, "Pink = called · Dark pink = latest", 16), -2);
        Ui.action(body, "Undo / Full board", Ui.PAPER, () -> open(practice ? "practice" : "board"));
    }
    private void newGame() {
        Ui.add(body, Ui.text(this, practice ? "Reset practice round?" : "Start a new game?", 24), -2);
        Ui.add(body, Ui.text(this, practice ? "Practice numbers will be cleared. Your live game stays unchanged." : "Called numbers and winners will be cleared. Player names and prize settings stay. Share results first if needed.", 19), -2);
        Ui.add(body, Ui.text(this, "Any unsent message stays in the box. Send or clear it before calling again.", 18), -2);
        if (!practice && store.prizes().hasWinners()) Ui.action(body, "Share results picture first", Ui.GREEN, this::resultsPicture).setEnabled(!mediaBusy);
        Ui.action(body, "Keep playing", Ui.PAPER, () -> navigate("Call"));
        Ui.action(body, "Yes, start new game", Ui.PINK, () -> {
            try {
                store.newRound(); session++; mediaBusy = false; lastDraw = 0; audio.stop(); navigate("Call");
                if (emptyEditor() != null) say(!practice && store.plan() != null ? "New game. Review tickets & schemes before calling." : "New game ready. Tap Next number.");
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
            boolean cleared = clearEditor(editor);
            navigate("Call");
            say(cleared ? (!practice && store.plan() != null && !store.plan().approved ? "Message cleared. Review tickets & schemes." : "Message cleared. Tap Next number.") : "Tap ABC and clear the message with your normal keyboard.");
        });
    }
    private void startUndo() {
        if (mediaBusy || !textField) return;
        undoRound = store.load().encode(); undoPrizes = PrizeJson.encode(store.prizes()); undoSession = session;
        if (!undoRound.isEmpty()) navigate("Undo number");
    }
    private void confirmUndo() {
        Game snapshot = Game.decode(undoRound);
        Ui.add(body, Ui.text(this, "Undo number " + snapshot.latest() + "?", 24), -2);
        Ui.add(body, Ui.text(this, "Return it to the pool and remove its unsent number message. Other draft text stays. Winners and Bogies recorded on this call will be cleared.", 19), -2);
        if (!practice) Ui.add(body, Ui.text(this, "Already sent it? Tell the group about the correction. Sent messages stay unchanged.", 18), -2);
        Ui.action(body, "Keep number", Ui.PAPER, () -> navigate("Call"));
        Ui.action(body, "Yes, undo number", Ui.PINK, () -> {
            try {
                if (undoSession != session || !textField || !store.load().encode().equals(undoRound)
                    || !PrizeJson.encode(store.prizes()).equals(undoPrizes)) {
                    navigate("Call"); say("Game or prizes changed. Review the latest number, then try Undo again."); return;
                }
                InputConnection editor = getCurrentInputConnection();
                String draft = editor == null ? null : readDraft(editor);
                if (draft == null) { say("Could not check the draft. Tap the message box, then try Undo again."); return; }
                boolean numberDraft = draft.equals(Game.emoji(snapshot.latest()));
                if (numberDraft && !clearEditor(editor)) {
                    say("Could not clear the number message. The number is still called. Try Clear message first."); return;
                }
                store.undo(); lastDraw = 0; audio.stop(); navigate("Call");
                say("Number " + snapshot.latest() + " undone. " + (numberDraft ? "Unsent number cleared." : draft.isEmpty() ? "Ready to continue." : "Other draft text kept."));
            } catch (IllegalArgumentException | IllegalStateException error) { navigate("Call"); say(error.getMessage()); }
        });
    }
    // More than either number's six UTF-16 units prevents a longer draft matching a truncated suffix.
    private String readDraft(InputConnection editor) {
        CharSequence before = editor.getTextBeforeCursor(8, 0), after = editor.getTextAfterCursor(8, 0), selected = editor.getSelectedText(0);
        return before == null || after == null ? null : before.toString() + (selected == null ? "" : selected.toString()) + after;
    }
    private boolean clearEditor(InputConnection editor) {
        boolean accepted;
        editor.beginBatchEdit();
        try { editor.finishComposingText(); accepted = editor.setSelection(0, 0) && editor.deleteSurroundingText(0, Integer.MAX_VALUE); }
        finally { editor.endBatchEdit(); }
        // A remote connection may accept the request even when the editor rejects the deletion.
        return accepted && "".equals(readDraft(editor));
    }
    private void voice() {
        Ui.action(body, "‹ Back to call", Ui.PAPER, () -> navigate("Call")); Ui.add(body, Ui.text(this, "Spoken language", 23), -2);
        for (int i = 0; i < Preferences.LANGUAGES.length; i++) { String language = Preferences.LANGUAGES[i], label = Preferences.LABELS[i]; Ui.action(body, label + (language.equals(preferences.language()) ? " ✓" : ""), language.equals(preferences.language()) ? Ui.GREEN : Ui.PAPER, () -> { preferences.language(language); render(); say("Voice set to " + label); }); }
        Ui.action(body, preferences.voice() ? "Voice is ON · tap for OFF" : "Voice is OFF · tap for ON", Ui.PAPER, () -> { preferences.voice(!preferences.voice()); audio.stop(); render(); });
        Ui.add(body, Ui.text(this, "Sound plays on this phone only. Turn up media volume. These are AI-generated recordings.", 17), -2);
    }
    private void share(Game game) {
        Ui.action(body, "‹ Back to call", Ui.PAPER, () -> navigate("Call"));
        if (!practice) Ui.action(body, "Game messages", Ui.GREEN, () -> navigate("Game messages"));
        if (game.latest() == 0) { Ui.add(body, Ui.text(this, "Call a number first to share numbers, pictures or voice clips.", 20), -2); return; }
        Ui.add(body, Ui.text(this, "Choose what to share", 23), -2); Ui.action(body, "Insert recent numbers", Ui.GREEN, () -> insertText(Sharing.recent(store.load())));
        for (String kind : new String[]{Sharing.NUMBER, Sharing.RECENT, Sharing.BOARD, Sharing.AUDIO}) {
            String label = kind.equals(Sharing.NUMBER) ? "Number picture" : kind.equals(Sharing.RECENT) ? "Recent numbers picture" : kind.equals(Sharing.BOARD) ? "Board picture" : "Voice clip · " + preferences.label();
            Button button = Ui.button(this, label, Ui.PAPER, () -> media(kind)); button.setEnabled(!mediaBusy && !practice); Ui.add(body, button, -2);
        }
        Ui.add(body, Ui.text(this, practice ? "File sharing is off during practice." : "Pictures go into this chat when supported. Otherwise choose WhatsApp and your group. Check the preview, then Send.", 17), -2);
    }
    private void gameMessages() {
        Ui.action(body, "‹ Back to call", Ui.PAPER, () -> navigate("Call"));
        if (practice) return;
        GameMessageViews.show(body, store, messageKind, kind -> { messageKind = kind; navigate("Game messages"); },
            () -> open("plan"), true, this::insertText, this::say);
        Ui.action(body, "Clear message…", Ui.PAPER, () -> navigate("Clear message"));
    }
    private void winners(Game game) {
        if (practice) { Ui.add(body, Ui.text(this, "Winners are for your live game. Practice does not change them.", 20), -2); return; }
        PrizeBook book = store.prizes(); Ui.add(body, Ui.text(this, "Winners (optional)", 23), -2);
        Ui.action(body, "Tap prize + winner", Ui.GREEN, () -> { quickGroup = "Quick prizes"; navigate("Tap winners"); }).setEnabled(!mediaBusy);
        Ui.action(body, "Speak prize + winner", Ui.GREEN, this::startWinnerSpeech).setEnabled(!mediaBusy);
        Ui.action(body, "Speech: " + (preferences.dictationLanguage().equals("hi-IN") ? "Hindi" : "English / Hinglish") + " · change", Ui.PAPER, () -> { preferences.dictationLanguage(preferences.dictationLanguage().equals("hi-IN") ? "en-IN" : "hi-IN"); render(); });
        Ui.add(body, Ui.text(this, "Say: Early five winner Asha Sharma. We save the winner and fill the message. Check it, then Send.", 18), -2);
        Ui.action(body, "Choose prize schemes", Ui.PAPER, () -> open("catalog"));
        Ui.action(body, "Players & prize amounts", Ui.PAPER, () -> open("players"));
        Ui.action(body, "Add family players & schemes", Ui.PAPER, this::addFamilySetup);
        if (book.players.isEmpty()) Ui.add(body, Ui.text(this, "Speaking adds new names automatically. Or add names above to choose manually.", 18), -2);
        if (book.hasWinners()) {
            Ui.action(body, "Share results picture", Ui.GREEN, this::resultsPicture).setEnabled(!mediaBusy);
            Ui.action(body, "Insert all results", Ui.GREEN, () -> insertText(store.prizes().results(store.load().count())));
        }
        for (PrizeBook.Scheme item : book.schemes) if (item.enabled) {
            Ui.action(body, item.name + " · " + PrizeBook.money((item.resolved() ? item.awardedRupees : item.rupees) * 100) + "\n" + (item.resolved() ? book.winners(item) : "Choose winners"), Ui.PAPER, () -> { awardId = item.id; navigate("Award"); });
            if (item.resolved()) Ui.action(body, "Insert " + item.name + " announcement", Ui.GREEN, () -> { PrizeBook current = store.prizes(); insertText(current.announcement(current.scheme(item.id))); });
        }
    }
    private void addFamilySetup() {
        try {
            store.prizes(FamilySetup.addTo(store.prizes()));
            quickGroup = "Quick prizes";
            navigate("Tap winners");
            say("Family list ready. Choose a prize, then tap the winner.");
        } catch (IllegalArgumentException | IllegalStateException error) { say(error.getMessage()); }
    }
    private void quickSchemes() {
        if (practice) return;
        PrizeBook book = store.prizes();
        Ui.add(body, Ui.text(this, "1. Choose a prize", 23), -2);
        if (!FamilySetup.hasPlayers(book)) {
            Ui.action(body, "Add family players & schemes", Ui.GREEN, this::addFamilySetup);
            Ui.add(body, Ui.text(this, "Adds your 11 players and " + PrizeCatalog.ALL.size() + " schemes. Existing names, amounts and winners stay.", 17), -2);
        }
        List<String> groups = new ArrayList<>(PrizeCatalog.GROUPS); groups.add("Other prizes");
        for (int i = 0; i < groups.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            for (int j = i; j < Math.min(i + 2, groups.size()); j++) {
                String group = groups.get(j);
                Button button = Ui.button(this, group + (quickGroup.equals(group) ? " ✓" : ""), quickGroup.equals(group) ? Ui.PINK : Ui.PAPER,
                    () -> { quickGroup = group; navigate("Tap winners"); });
                button.setContentDescription("Prize group: " + group);
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
                size.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0); row.addView(button, size);
            }
            Ui.add(body, row, -2);
        }
        int available = 0;
        for (PrizeBook.Scheme scheme : book.schemes) if (scheme.enabled && quickGroup.equals(PrizeCatalog.group(book, scheme))) {
            available++;
            Ui.action(body, scheme.name + (scheme.resolved() ? " · " + book.winners(scheme) : ""), Ui.PAPER, () -> {
                quickId = scheme.id; quickSession = session; quickRound = store.load().encode(); quickPrizes = PrizeJson.encode(store.prizes());
                navigate("Tap names");
            }).setContentDescription("Choose prize: " + scheme.name);
        }
        if (available == 0) Ui.add(body, Ui.text(this, "No enabled prizes in this group. Add the family list or choose prize schemes.", 18), -2);
        Ui.action(body, "‹ More winner options", Ui.PAPER, () -> navigate("Winners"));
    }
    private void quickNames(Game game) {
        if (practice) return;
        Ui.action(body, "‹ Choose another prize", Ui.PAPER, () -> navigate("Tap winners"));
        if (quickSession != session || !game.encode().equals(quickRound) || !PrizeJson.encode(store.prizes()).equals(quickPrizes)) {
            Ui.add(body, Ui.text(this, "Game or prizes changed. Choose the prize again.", 20), -2); return;
        }
        PrizeBook book = store.prizes(); PrizeBook.Scheme scheme = book.scheme(quickId); TicketPlan ticketPlan = store.plan();
        String host = FamilySetup.hostId(book);
        if (host != null && !scheme.resolved()) {
            Ui.action(body, "Claim for " + book.players.get(host), Ui.PINK, () -> {
                try {
                    if (quickSession != session || !store.load().encode().equals(quickRound) || !PrizeJson.encode(store.prizes()).equals(quickPrizes)) {
                        navigate("Tap winners"); say("Game or prizes changed. Choose the prize again."); return;
                    }
                    String claim = book.claim(scheme.id, host, game.count());
                    InputConnection editor = emptyEditor(); if (editor == null) return;
                    boolean inserted = editor.commitText(claim, 1);
                    if (inserted) { navigate("Winners"); say("Claim ready · WhatsApp Send. Confirm winners after checking claims."); }
                    else say("Could not insert claim. Tap the empty message box and try again.");
                } catch (IllegalArgumentException | IllegalStateException error) { say(error.getMessage()); }
            }).setEnabled(game.count() >= scheme.minimumCalls && (ticketPlan == null || ticketPlan.sets(host) > 0));
        }
        QuickWinnerViews.choose(body, book, scheme, game.count(), ticketPlan, selected -> {
            try {
                if (quickSession != session || !isInputViewShown() || !store.load().encode().equals(quickRound)
                    || !PrizeJson.encode(store.prizes()).equals(quickPrizes)) {
                    navigate("Tap winners"); say("Game or prizes changed. Choose the prize again."); return;
                }
                InputConnection editor = emptyEditor(); if (editor == null) return;
                PrizeBook updated = book.resolve(scheme.id, selected, game.count());
                store.prizes(updated); // Recoverable via the existing Insert announcement action if the editor rejects text.
                boolean inserted = editor.commitText(updated.announcement(updated.scheme(scheme.id)), 1);
                navigate("Winners");
                say(inserted ? (selected.size() >= 3 ? "Bogie saved · no payout. Check the message, then Send" : "Winner saved · check the message, then WhatsApp Send") : "Result saved. Tap Insert announcement to try again.");
            } catch (IllegalArgumentException | IllegalStateException error) { say(error.getMessage()); }
        }, () -> scroll.scrollTo(0, 0));
    }
    private void cancelWinnerSpeech() {
        speechSession++;
        WinnerSpeech previous = winnerSpeech; winnerSpeech = null;
        if (previous != null) previous.cancel();
    }
    private void speakWinnerPanel() {
        Ui.add(body, Ui.text(this, "Speak prize + winner", 23), -2);
        Ui.add(body, Ui.text(this, speechMessage, 20), -2);
        if (winnerSpeech != null) {
            Ui.action(body, "Done speaking", Ui.GREEN, () -> { if (winnerSpeech != null) winnerSpeech.stop(); });
            Ui.action(body, "Cancel listening", Ui.PINK, () -> navigate("Winners"));
        } else {
            Ui.action(body, "Speak again", Ui.GREEN, this::startWinnerSpeech);
            Ui.action(body, "‹ Back to winners", Ui.PAPER, () -> navigate("Winners"));
        }
        Ui.add(body, Ui.text(this, "Early five winner Asha Sharma\nKing winners Asha aur Bina\nअर्ली फाइव की विनर आशा शर्मा", 18), -2);
        if (winnerSpeech == null) for (String code : new String[]{"en-IN", "hi-IN"}) {
            String label = code.equals("hi-IN") ? "Hindi · हिन्दी" : "English / Hinglish";
            Ui.action(body, label + (code.equals(preferences.dictationLanguage()) ? " ✓" : ""), Ui.PAPER, () -> { preferences.dictationLanguage(code); render(); });
        }
        Ui.add(body, Ui.text(this, "Uses the enabled prize's amount. Two names split it equally; three or more means Bogie with no payout. Your phone's speech service may need Internet. To change a saved award, use its prize button.", 17), -2);
    }
    private void startWinnerSpeech() {
        if (practice || mediaBusy || winnerSpeech != null) return;
        InputConnection editor = emptyEditor(); if (editor == null) return;
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            audio.stop(); startActivity(new Intent(this, VoicePermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)); return;
        }
        panel = "Speak winner";
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) { speechMessage = "Voice typing is not available on this phone. Enable a speech recognition service in Android settings, or choose winners manually."; render(); return; }
        audio.stop(); cancelWinnerSpeech();
        int request = speechSession, editorSession = session;
        String round = store.load().encode(), prizes = PrizeJson.encode(store.prizes());
        speechMessage = "Starting microphone…";
        try {
            winnerSpeech = new WinnerSpeech(this, spoken -> {
                if (request != speechSession) return;
                winnerSpeech = null;
                if (editorSession != session || !isInputViewShown() || editor != getCurrentInputConnection()) return;
                try {
                    if (!round.equals(store.load().encode()) || !prizes.equals(PrizeJson.encode(store.prizes()))) throw new IllegalArgumentException("Game or prizes changed. Tap Speak again.");
                    if (emptyEditor() == null) { speechMessage = "Send or clear the current message, then speak again. No winner saved."; render(); return; }
                    WinnerCommand command = WinnerCommand.prepare(store.prizes(), spoken, store.load().count());
                    // Persist before handing off; a failed insertion remains recoverable from Winners.
                    store.prizes(command.book);
                    boolean inserted = editor.commitText(command.announcement, 1);
                    speechMessage = inserted ? (command.bogie ? "Bogie saved. Check the claimants in the message box, then WhatsApp Send."
                        : "Winner saved. Check the name in the message box, then WhatsApp Send.")
                        : "Result saved. Return to Winners and tap Insert announcement.";
                    render(); scroll.scrollTo(0, 0); say(inserted ? "Ready · check name, then WhatsApp Send" : "Winner saved; message could not be inserted.");
                } catch (IllegalArgumentException | IllegalStateException error) { speechMessage = "Heard: " + spoken + "\n\n" + error.getMessage(); render(); scroll.scrollTo(0, 0); }
            }, error -> {
                if (request != speechSession) return; winnerSpeech = null; speechMessage = error; render(); scroll.scrollTo(0, 0);
            }, message -> {
                if (request != speechSession) return; speechMessage = message; render();
            });
            render(); scroll.scrollTo(0, 0); winnerSpeech.start(preferences.dictationLanguage());
        } catch (RuntimeException error) { cancelWinnerSpeech(); speechMessage = "Voice typing could not start. Try again or choose winners manually."; render(); }
    }
    private void say(String message) { if (status != null) status.setText(message); }
    private InputConnection emptyEditor() {
        if (!textField) return null; InputConnection editor = getCurrentInputConnection(); if (editor == null) { say("Tap the empty message box, then try again."); return null; }
        CharSequence before = editor.getTextBeforeCursor(1, 0), after = editor.getTextAfterCursor(1, 0), selected = editor.getSelectedText(0);
        if (before == null || after == null) { say("Tap the empty message box, then try again."); return null; }
        if (before.length() > 0 || after.length() > 0 || (selected != null && selected.length() > 0)) {
            if (panel.equals("Call")) { render(); scroll.scrollTo(0, 0); }
            say("Send or clear the current message first."); return null;
        }
        return editor;
    }
    private void insertText(String text) { InputConnection editor = emptyEditor(); if (editor == null) return; boolean ready = editor.commitText(text, 1); say(ready ? (practice ? "Practice only · nothing is sent" : "Ready · tap WhatsApp Send") : "Could not insert. Tap the message box and try again."); }
    private void insertNumber(boolean draw) {
        if (mediaBusy) return; InputConnection editor = emptyEditor(); if (editor == null) return; if (draw && SystemClock.elapsedRealtime() - lastDraw < 650) return;
        try {
            Game game = store.load(); if (draw) { if (game.count() == 90) return; if (!practice) store.requireReady(); game = game.draw(random); store.save(game); lastDraw = SystemClock.elapsedRealtime(); }
            if (game.latest() == 0) return; boolean inserted = editor.commitText(Game.emoji(game.latest()), 1); render(); scroll.post(() -> scroll.scrollTo(0, 0));
            say(inserted ? practice ? "Practice only · tap Clear to try again" : "Number ready · tap WhatsApp Send" : "Number saved. Tap the message box and Insert again.");
            if (draw && preferences.voice()) audio.play(game.latest(), preferences.language(), this::say);
        } catch (IllegalArgumentException | IllegalStateException error) { render(); say(error.getMessage()); }
    }
    private void clearPractice() {
        if (!practice) return; InputConnection editor = getCurrentInputConnection(); if (editor == null) return;
        say(clearEditor(editor) ? "Practice box cleared. Tap Next number." : "Could not clear the practice box. Try again.");
    }
    private void resultsPicture() {
        if (practice || mediaBusy || emptyEditor() == null) return;
        Game snapshot = store.load(); PrizeBook prizes = store.prizes(); String prizeSnapshot = PrizeJson.encode(prizes), planSnapshot = store.planSnapshot();
        int started = session; mediaBusy = true; render(); say("Preparing results picture…");
        Sharing.prepareResults(this, snapshot, prizes, store.plan(), uris -> {
            if (started != session) return; mediaBusy = false; render();
            if (!store.load().encode().equals(snapshot.encode()) || !PrizeJson.encode(store.prizes()).equals(prizeSnapshot) || !store.planSnapshot().equals(planSnapshot)) {
                say("Game or prizes changed. Share results again."); return;
            }
            InputConnection editor = emptyEditor(); if (editor == null) return;
            if (uris.size() == 1 && Sharing.insert(editor, getCurrentInputEditorInfo(), uris.get(0), "image/png")) say("Results picture ready · check it, then Send.");
            else try { audio.stop(); Sharing.images(this, uris); say("Choose WhatsApp, your group, then Send."); }
            catch (RuntimeException error) { say("Could not open sharing. Try Results to share in the app."); }
        }, error -> { if (started == session) { mediaBusy = false; render(); say(error); } });
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
