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
    private GameMessages.Kind messageKind = GameMessages.Kind.PLAYERS;
    private int viewSession;
    private boolean sharing;
    private boolean planSchemes;
    private String planGroup = "Quick prizes";
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
    private void go(String next) { hideKeyboard(); audio.stop(); if (next.equals("plan")) { planSchemes = false; planGroup = "Quick prizes"; } section = next; render(); scroll.scrollTo(0, 0); }
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
                case "catalog": catalog(); break;
                case "voice": voice(); break;
                case "results": results(); break;
                case "plan": plan(); break;
                case "messages": gameMessages(); break;
                case "award": action("‹ All prizes", Ui.PAPER, () -> go("players")); PrizeViews.award(page, store, awardId, () -> go("players"), this::toast); break;
                default: home();
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            text(error.getMessage(), 20); action("Help", Ui.PAPER, () -> go("help"));
            if (error.getMessage().startsWith("Ticket plan cannot")) action("Reset unreadable ticket plan", Ui.PAPER, () -> confirm("Reset ticket tracking?", "Purchased-set counts and the saved budget will be removed. Called numbers, players and recorded prizes stay.", () -> { store.resetPlan(); go("board"); }));
            action("Reset unreadable prize details", Ui.PAPER, () -> confirm("Reset prize details?", "This clears player names, prizes, winners and the ticket budget. Called numbers stay saved.", () -> { store.resetPrizeDetails(); go("players"); }));
            action("Reset saved live game", Ui.PAPER, () -> confirm("Reset saved numbers and winners?", "Start again with all 90 numbers. Player names and prize settings stay. Messages sent to WhatsApp stay unchanged.", () -> { store.newRound(); go("board"); }));
        }
    }
    private void home() {
        text("Tambola Keyboard", 30); text("One phone. Two taps per number.", 22);
        text("1. Open your WhatsApp group.\n2. Tap the message box. Choose Tambola Keyboard.\n3. Tap Next number, then WhatsApp Send.", 20);
        action("Help me set it up", Ui.GREEN, () -> go("help"));
        action("Practice first", Ui.PINK, () -> go("practice"));
        action("Set tickets & schemes", Ui.GREEN, () -> go("plan"));
        action("Game messages", Ui.GREEN, () -> go("messages"));
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
        text("Before the first call", 25); text("Set tickets & schemes: choose each player's purchased sets. One set is 6 tickets for ₹50; 0 means not playing. Every scheme is ₹10. Select five prizes per purchased set and add extra named prizes if needed. Confirm when the prize total matches ticket sales. Everyone playing can win any selected scheme. Review this setup again for each new game.", 19);
        text("Tell the group", 25); text("After confirming setup, open Game messages in the keyboard or main menu. Share Players & tickets, Selected schemes, then the Start message. Preview each message, insert it, and tap WhatsApp Send. Start message does not draw a number; return to Call → Next number when everyone is ready. During play, Game messages is under Share….", 19);
        text("Useful buttons", 25); text("Clear message: remove an unsent draft.\nUndo last number: correct an accidental call.\nHear again: hear the same number.\nInsert again: put the same number in the box.\nABC: return to normal typing.\nBoard: see called numbers.\nWinners: prize tracking and results pictures.", 19);
        text("Swipe up inside the keyboard to see more buttons. Tap Call at the top to return to Next number.", 19);
        text("Tap a winner", 25); text("In the chat: Winners → Tap prize + winner → Add family players & schemes once. Choose a prize group and prize. Claim for Varsha inserts her claim without awarding it. After checking all claims, tap each claimant. Insert winner announcement fills the message box and saves the award. Check it, then WhatsApp Send. No typing or microphone needed. A Bogie stays open. To correct a winner, reopen the prize, tap Choose different winners, choose the correct names, then Update winner announcement.", 19);
        text("Speak a winner", 25); text("In the chat: Winners → Speak prize + winner. Allow microphone once and return to your chat. Say Early five winner Asha Sharma, or King winners Asha aur Bina. Use Speech · change for Hindi. The prize must be enabled. Names are added and winners saved automatically, using the configured prize amount. Check the name in the message box, then WhatsApp Send. Two winners split the prize equally; three or more means Bogie with no payout; the scheme stays open for another claim.", 19);
        text("Pictures and voice clips", 25); text("Tap Share in the keyboard. Choose a picture or voice clip. If a chooser opens, select WhatsApp and your group. Check the preview, then Send. Sound played aloud stays on this phone.", 19);
        text("Share progress during play", 25); text("Winners → Share scheme status picture lists remaining schemes, saved winners and Bogies. Only a saved winner closes a scheme. A Bogie stays open for another claim. Send or clear any announcement first, then share the picture. It reflects saved results, not WhatsApp delivery.", 19);
        text("At the end of the game", 25); text("Winners → Share results picture groups names in columns with only each player’s won schemes and their total underneath. Bogies pay nothing. Share it before starting a new game. Check the image and recipient, then Send.", 19);
        text("Your privacy", 25); text("No accounts, ads, Internet or contacts permission in this app. Optional winner dictation asks for microphone access; your phone's speech service may process audio online. The app does not save microphone recordings. It saves winner names on this phone and checks the current draft to avoid overwriting it; chat text is not saved. Number voice clips are AI-generated and included in the app.", 17);
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
        action("Tickets & schemes", Ui.PAPER, () -> go("plan"));
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
        action("Choose prize schemes", Ui.PINK, () -> go("catalog"));
        action("Add player names", Ui.GREEN, this::addPlayers); action("Use normal keyboard to type names", Ui.PAPER, this::chooseKeyboard);
        for (Map.Entry<String, String> person : book.players.entrySet()) action(person.getValue() + " · edit", Ui.PAPER, () -> editPlayer(person.getKey()));
        text("Prizes", 26); text("Each scheme pays ₹10. Two winners receive ₹5 each. Tap a prize to choose winners after checking their tickets.", 19);
        for (PrizeBook.Scheme item : book.schemes) {
            if (item.enabled) action(item.name + " · " + PrizeBook.money((item.winners.isEmpty() ? item.rupees : item.awardedRupees) * 100) + "\n" + (item.resolved() ? book.winners(item) : "Choose winners"), Ui.PINK, () -> { awardId = item.id; go("award"); });
            action("Change " + item.name + (item.enabled ? "" : " (off)"), Ui.PAPER, () -> editScheme(item.id));
        }
        action("Add another prize", Ui.PAPER, () -> editScheme(null)); action("See / share results", Ui.GREEN, () -> go("results"));
    }
    private void catalog() {
        text("Choose prize schemes", 28);
        text("Tap a group, tick the prizes you want, then Add selected. New prizes start at ₹10. Two winners share ₹5 each.", 20);
        text("Your current prizes and winners stay. Check tickets using your group's rules.", 18);
        for (String group : PrizeCatalog.GROUPS) {
            int count = 0; for (PrizeCatalog.Preset item : PrizeCatalog.ALL) if (item.group.equals(group)) count++;
            action(group + " · " + count + " choices", Ui.PAPER, () -> choosePresets(group));
        }
        action("Add all " + PrizeCatalog.ALL.size() + " schemes", Ui.PINK, () -> confirm("Add all " + PrizeCatalog.ALL.size() + " schemes?", "All listed prizes will turn on. New prizes start at ₹10 each. Existing amounts and winners stay. You can switch unused prizes off under Players & prizes.", () -> { store.prizes(store.prizes().addPresets(PrizeCatalog.allIds())); go("players"); toast("Prize schemes added. Your game continues."); }));
        action("‹ Players & prizes", Ui.PAPER, () -> go("players"));
    }
    private void choosePresets(String group) {
        PrizeBook book = store.prizes(); LinearLayout box = dialogBox(); Set<String> selected = new LinkedHashSet<>();
        Ui.add(box, Ui.text(this, "Tick prizes to add. Already active prizes are kept.", 18), -2);
        for (PrizeCatalog.Preset preset : PrizeCatalog.ALL) if (preset.group.equals(group)) {
            PrizeBook.Scheme current = PrizeCatalog.existing(book, preset); boolean active = current != null && current.enabled;
            CheckBox choice = new CheckBox(this); choice.setTextSize(20); choice.setMinHeight(Ui.dp(this, 56));
            choice.setText(active ? getString(R.string.prize_already_active, preset.name) : preset.name); choice.setContentDescription(preset.name);
            choice.setChecked(active); choice.setEnabled(!active);
            choice.setOnCheckedChangeListener((button, checked) -> { if (checked) selected.add(preset.id); else selected.remove(preset.id); });
            Ui.add(box, choice, -2);
        }
        editDialog(group, box, "Add selected", () -> {
            if (selected.isEmpty()) throw new IllegalArgumentException("Tick at least one prize to add, or tap Cancel.");
            store.prizes(store.prizes().addPresets(selected)); toast("Selected prizes added. Your game continues.");
        });
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
        int rupees = item != null && store.load().count() > 0 ? item.rupees : PrizeBook.PRIZE_RUPEES;
        Ui.add(box, Ui.text(this, "Prize: " + PrizeBook.money(rupees * 100) + " · two winners share equally", 18), -2);
        CheckBox enabled = new CheckBox(this); enabled.setText(R.string.use_prize); enabled.setTextSize(20); enabled.setChecked(item == null || item.enabled); Ui.add(box, enabled, 56);
        editDialog("Prize settings", box, "Save prize", () -> store.prizes(store.prizes().configure(id, name.getText().toString(), rupees, enabled.isChecked())));
    }
    private LinearLayout dialogBox() { LinearLayout box = Ui.column(this); int pad = Ui.dp(this, 20); box.setPadding(pad, pad, pad, pad); return box; }
    private AlertDialog editDialog(String title, LinearLayout box, String positive, Runnable save) {
        ScrollView wrapper = new ScrollView(this); wrapper.addView(box); AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(wrapper).setNegativeButton("Cancel", null).setPositiveButton(positive, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> safe(() -> { save.run(); hideKeyboard(); dialog.dismiss(); render(); }))); dialog.show(); return dialog;
    }
    private void plan() {
        text("Plan the game", 28); PrizeBook book = store.prizes();
        if (store.load().count() > 0 || book.hasWinners()) {
            text("Game in progress. Start a new game before changing purchased sets, schemes or prize amounts.", 20);
            TicketPlan saved = store.plan(); if (saved != null) TicketPlanViews.totals(page, saved, book);
            action("See board / New game", Ui.GREEN, () -> go("board")); return;
        }
        if (!FamilySetup.hasPlayers(book)) action("Add family players & schemes", Ui.PAPER, () -> safe(() -> { store.prizes(FamilySetup.addTo(store.prizes())); render(); }));
        if (!planSchemes) action("Add players", Ui.PAPER, this::addPlayers);
        if (book.players.isEmpty()) { text("Add the family list above, or add names under Players & prizes.", 20); return; }
        if (store.plan() == null || book.schemes.stream().anyMatch(prize -> prize.rupees != PrizeBook.PRIZE_RUPEES))
            store.plan(store.plan() == null ? TicketPlan.forPlayers(book) : store.plan(), book, false);
        if (!planSchemes) TicketPlanViews.tickets(page, store, this::render, () -> { planSchemes = true; render(); scroll.scrollTo(0, 0); }, this::toast);
        else TicketPlanViews.schemes(page, store, planGroup, value -> { planGroup = value; render(); },
            () -> { planSchemes = false; render(); scroll.scrollTo(0, 0); }, this::render, this::addPlanPrizes,
            () -> { toast("Setup confirmed. Game messages are ready to share."); if (getIntent().getBooleanExtra("returnToChat", false)) finish(); else go("messages"); }, this::toast);
    }
    private void gameMessages() {
        GameMessageViews.show(page, store, messageKind, kind -> { messageKind = kind; render(); scroll.scrollTo(0, 0); },
            () -> go("plan"), false, text -> {
                try { Sharing.text(this, text); }
                catch (RuntimeException error) { toast("Could not open sharing. Use Game messages in the keyboard."); }
            }, this::toast);
    }
    private void addPlanPrizes() {
        LinearLayout box = dialogBox();
        Ui.add(box, Ui.text(this, "Add one distinct prize name per line. Each is ₹10 and appears under Other prizes.", 19), -2);
        EditText names = input("Prize names, one per line", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        names.setContentDescription("Extra prize names"); names.setMinLines(3); Ui.add(box, names, -2);
        editDialog("Extra ₹10 prizes", box, "Add prizes", () -> {
            PrizeBook book = store.prizes(); Set<String> used = new HashSet<>();
            for (PrizeBook.Scheme prize : book.schemes) used.add(prize.name.toLowerCase(Locale.ROOT));
            boolean added = false;
            for (String value : names.getText().toString().split("\\n")) if (!value.trim().isEmpty()) {
                String name = PrizeBook.clean(value);
                if (!used.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Use a different name for each prize: " + name);
                book = book.configure(null, name, PrizeBook.PRIZE_RUPEES, true); added = true;
            }
            if (!added) throw new IllegalArgumentException("Enter at least one prize name.");
            store.plan(store.plan(), book, false); planGroup = "Other prizes";
        });
    }
    private void results() { PrizeBook book = store.prizes(); text("Results to share", 28); action("Share scheme status picture", Ui.GREEN, () -> sharePrizePicture(true)); if (!book.hasWinners()) { text("No winners saved yet. Add winners under Players & prizes, or keep playing.", 20); action("Players & prizes", Ui.PINK, () -> go("players")); return; }
        action("Share results picture", Ui.GREEN, this::shareResultsPicture);
        ResultsViews.table(page, new ResultsTable(book, store.plan()));
        action("Share results to WhatsApp", Ui.GREEN, () -> { try { Sharing.text(this, store.prizes().results(store.load().count())); } catch (RuntimeException error) { toast("Could not open sharing. Use Winners in the keyboard."); } }); text("Or return to your group: keyboard → Winners → Share results picture → WhatsApp Send.", 18); }
    private void shareResultsPicture() { sharePrizePicture(false); }
    private void sharePrizePicture(boolean schemes) {
        if (sharing) return; sharing = true; int started = viewSession; Game game = store.load(); PrizeBook book = store.prizes(); String prizes = PrizeJson.encode(book), planSnapshot = store.planSnapshot();
        toast(schemes ? "Preparing scheme status…" : "Preparing results picture…");
        java.util.function.Consumer<java.util.List<android.net.Uri>> ready = uris -> {
            if (isFinishing() || started != viewSession) return; sharing = false;
            if (!store.load().encode().equals(game.encode()) || !PrizeJson.encode(store.prizes()).equals(prizes) || !store.planSnapshot().equals(planSnapshot)) { toast(schemes ? "Game or prizes changed. Share scheme status again." : "Game or prizes changed. Share results again."); return; }
            try { Sharing.images(this, uris); } catch (RuntimeException error) { toast("No sharing app is available."); }
        };
        java.util.function.Consumer<String> failed = error -> { if (started == viewSession) { sharing = false; toast(error); } };
        if (schemes) Sharing.prepareSchemeStatus(this, game, book, store.plan(), ready, failed);
        else Sharing.prepareResults(this, game, book, store.plan(), ready, failed);
    }
    private void confirm(String title, String message, Runnable action) { hideKeyboard(); new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Keep playing", null).setPositiveButton("Yes, continue", (dialog, which) -> safe(action)).show(); }
    private void share(String kind) {
        if (sharing) return; sharing = true; int started = viewSession; Game snapshot = store.load(); toast("Preparing…");
        Sharing.prepare(this, snapshot, preferences.language(), kind, uri -> { if (isFinishing() || started != viewSession) return; sharing = false;
            try { Sharing.file(this, uri, Sharing.AUDIO.equals(kind) ? "audio/mpeg" : "image/png"); } catch (RuntimeException error) { toast("No sharing app is available."); }
        }, error -> { if (started == viewSession) { sharing = false; toast(error); } });
    }
}
