package io.github.sbshrey.tambola.keyboard;

import android.app.*;
import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import androidx.test.uiautomator.By;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GameMessagesUiTest extends KeyboardScenario {
    @Before public void clearOldPlan() { store.resetPlan(); focus(); }
    @After public void cleanup() {
        main(() -> {
            ActivityLifecycleMonitor monitor = ActivityLifecycleMonitorRegistry.getInstance();
            for (Stage stage : Stage.values()) for (Activity activity : new ArrayList<>(monitor.getActivitiesInStage(stage)))
                if (activity instanceof MainActivity) activity.finish();
        });
        device.waitForIdle(); store.resetPlan();
    }
    private void approve() {
        PrizeBook book = store.prizes().configure("prize-4", "Full house", 10, false);
        TicketPlan plan = TicketPlan.forPlayers(book).with("family-varsha", 2).with("family-suman", 0);
        while (TicketPlan.selected(book) < plan.totalSets() * 5) book = book.configure(null, "Extra " + book.schemes.size(), 10, true);
        store.plan(plan, book, true); focus();
    }
    private void choose(GameMessages.Kind kind) { tap(By.desc("Game message: " + kind.label)); }
    private String expected(GameMessages.Kind kind) { return GameMessages.text(kind, store.prizes(), store.plan(), store.load()); }
    @Test public void changeParticipantsPurchasesAndSchemesAfterStartMessageBeforeFirstCall() throws Exception {
        Map<String, String> names = new LinkedHashMap<>(); names.put("v", "Varsha"); names.put("r", "Rekha"); names.put("s", "Suman");
        PrizeBook book = new PrizeBook(names, PrizeBook.defaults().schemes).addPresets(Collections.singletonList("catalog-king")).configure("catalog-king", "King", 10, false);
        for (int i = 0; i < 10; i++) book = book.configure(null, "Extra " + i, 10, i < 5);
        store.plan(TicketPlan.forPlayers(book).with("s", 0), book, true);
        main(() -> rule.getActivity().acceptImages = true); focus();
        tap("Game messages"); choose(GameMessages.Kind.START); tap("Insert start message");
        await("Original start message inserted", () -> draft().equals(expected(GameMessages.Kind.START))); send();
        tap("Change players & schemes"); expect("1. Purchased ticket sets");
        tap(By.desc("Playing this game: Rekha"));
        await("Rekha sitting out without deleting her name", () -> store.plan().sets("r") == 0);
        assertEquals("Rekha", store.prizes().players.get("r")); assertFalse(store.plan().approved);
        tap(By.desc("Playing this game: Suman")); await("Suman joins", () -> store.plan().sets("s") == 1);
        tap(By.desc("One more set for Varsha")); await("Varsha buys two sets", () -> store.plan().sets("v") == 2);
        assertEquals(2, store.plan().players()); assertEquals(3, store.plan().totalSets()); assertEquals(150, store.plan().collection());
        assertThrows(IllegalArgumentException.class, store::requireReady);
        assertThrows(IllegalArgumentException.class, () -> expected(GameMessages.Kind.PLAYERS));
        assertThrows(IllegalArgumentException.class, () -> new SchemeStatus(store.prizes(), store.plan()));
        visible(By.desc("Playing this game: Varsha")); screenshot("current-game-players");
        tap("Choose ₹10 schemes →"); assertFalse(visible(By.text("Confirm setup · ready to call")).isEnabled());
        tap("Select all"); await("All 16 schemes selected", () -> TicketPlan.selected(store.prizes()) == 16);
        assertFalse(visible(By.text("Confirm setup · ready to call")).isEnabled());
        tap(By.desc("Use scheme: Early 5")); await("15 schemes allocated", () -> TicketPlan.selected(store.prizes()) == 15);
        assertTrue(store.prizes().scheme("catalog-king").enabled); assertFalse(store.prizes().scheme("prize-0").enabled);
        tap("Confirm setup · ready to call"); expect("Test chat editor"); focus(); assertEquals(0, store.load().count());
        tap("Game messages");
        for (GameMessages.Kind kind : GameMessages.Kind.values()) {
            choose(kind); tap("Insert " + kind.action); await("Revised message inserted", () -> draft().equals(expected(kind)));
            if (kind == GameMessages.Kind.PLAYERS) {
                assertTrue(draft().contains("Varsha — 2 sets (12 tickets) · ₹100")); assertTrue(draft().contains("Suman — 1 set")); assertFalse(draft().contains("Rekha"));
            } else if (kind == GameMessages.Kind.SCHEMES) { assertTrue(draft().contains("King — ₹10")); assertFalse(draft().contains("Early 5")); }
            else { assertTrue(draft().contains("2 players · 3 sets · 18 tickets")); assertTrue(draft().contains("₹150 · 15 schemes")); }
            send();
        }
        tap("Winners"); tap("Share scheme status picture");
        await("Revised scheme image inserted", () -> { int[] count = {0}; main(() -> count[0] = rule.getActivity().receivedImages.size()); return count[0] == 1; });
        android.net.Uri[] uri = {null}; main(() -> uri[0] = rule.getActivity().receivedImages.get(0));
        try (java.io.InputStream input = context.getContentResolver().openInputStream(uri[0])) {
            android.graphics.Bitmap actual = android.graphics.BitmapFactory.decodeStream(input), expected = new SchemeStatusImage(store.prizes(), store.load(), store.plan()).picture(0);
            try { assertTrue(expected.sameAs(actual)); } finally { expected.recycle(); actual.recycle(); }
        }
        assertEquals(15, new SchemeStatus(store.prizes(), store.plan()).remaining.size());
        ResultsTable results = new ResultsTable(store.prizes(), store.plan()); assertEquals(2, results.players.size());
        assertEquals("Varsha", results.players.get(0).name); assertEquals("Suman", results.players.get(1).name);
        tap("Call"); draw(); send(); tap("Share…"); tap("Game messages");
        assertFalse(device.hasObject(By.text("Change players & schemes")));
        assertThrows(IllegalArgumentException.class, () -> store.plan(store.plan().with("r", 1), store.prizes(), false));
        store.newRound(); assertFalse(store.plan().approved); assertEquals(0, store.load().count());
        assertEquals(0, store.plan().sets("r")); assertEquals(2, store.plan().sets("v")); assertEquals(15, TicketPlan.selected(store.prizes()));
        focus(); tap("Review tickets & schemes"); expect("1. Purchased ticket sets"); tap(By.desc("Playing this game: Rekha"));
        await("Rekha rejoins the next game", () -> store.plan().sets("r") == 1);
        assertEquals(3, store.prizes().players.size()); assertEquals(200, store.plan().collection());
    }
    @Test public void insertAllThreeMessagesThenCallWithoutChangingSetupOrOverwritingDrafts() {
        approve(); String plan = store.planSnapshot(), prizes = PrizeJson.encode(store.prizes()); tap("Game messages");
        for (GameMessages.Kind kind : GameMessages.Kind.values()) {
            choose(kind); String expected = expected(kind); tap("Insert " + kind.action);
            await("Whole announcement inserted", () -> draft().equals(expected));
            assertEquals(0, store.load().count()); assertEquals(plan, store.planSnapshot()); assertEquals(prizes, PrizeJson.encode(store.prizes()));
            tap("Insert " + kind.action); expect("Send or clear the current message first."); assertEquals(expected, draft());
            if (kind == GameMessages.Kind.START) screenshot("game-start-message");
            send();
        }
        tap("‹ Back to call"); draw(); send();
        tap("Share…"); tap("Game messages"); choose(GameMessages.Kind.START); visible(By.textContains("This game has already started."));
        assertFalse(device.hasObject(By.text("Insert start message")));
        choose(GameMessages.Kind.PLAYERS); tap("Insert players & tickets"); await("Lists can be reshared during play", () -> draft().equals(expected(GameMessages.Kind.PLAYERS)));
        assertEquals(1, store.load().count());
    }
    @Test public void missingUnapprovedAndNewRoundPlansRequireConfirmation() {
        tap("Game messages"); expect("Confirm tickets & schemes before sharing game messages.");
        for (GameMessages.Kind kind : GameMessages.Kind.values()) assertFalse(device.hasObject(By.text("Insert " + kind.action)));
        approve(); store.plan(store.plan(), store.prizes(), false); focus(); tap("Game messages");
        choose(GameMessages.Kind.START); expect("Confirm tickets & schemes before sharing game messages."); assertEquals("", draft());
        approve(); store.save(Game.decode("1")); store.newRound(); focus(); tap("Game messages");
        expect("Confirm tickets & schemes before sharing game messages."); assertFalse(store.plan().approved);
        assertEquals(2, store.plan().sets("family-varsha")); assertEquals(0, store.load().count());
    }
    @Test public void rejectedInsertionAndChangedPreviewKeepStateAndRequireReview() {
        approve(); tap("Game messages"); choose(GameMessages.Kind.PLAYERS);
        final int[] attempts = {0}; main(() -> { rule.getActivity().rejectCommit = true; attempts[0] = rule.getActivity().commitAttempts; });
        tap("Insert players & tickets");
        // Android's remote connection can acknowledge queuing before the editor rejects text.
        await("Editor received the insertion attempt", () -> { final boolean[] received = {false}; main(() -> received[0] = rule.getActivity().commitAttempts > attempts[0]); return received[0]; });
        assertEquals("", draft()); assertTrue(store.plan().approved); assertEquals(0, store.load().count());
        main(() -> rule.getActivity().rejectCommit = false);
        // A rename during an active game does not change the plan or trigger a plan refresh.
        store.save(Game.decode("1")); tap("Call"); tap("Share…"); tap("Game messages"); choose(GameMessages.Kind.PLAYERS);
        main(() -> store.prizes(store.prizes().player("family-varsha", "Varsha ji")));
        tap("Insert players & tickets"); expect("Setup changed. Review the updated message"); assertEquals("", draft());
        tap("Insert players & tickets"); await("Reviewed name used", () -> draft().equals(expected(GameMessages.Kind.PLAYERS)));
        assertTrue(draft().contains("Varsha ji — 2 sets")); assertEquals(1, store.load().count());
    }
    @Test public void appSharesEachPreviewThroughTheChooserWithoutSendingOrStartingGame() {
        approve();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation(); AtomicReference<Intent> shared = new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_CHOOSER.equals(intent.getAction())) return null;
                shared.set(new Intent(intent)); return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        instrumentation.addMonitor(monitor);
        try {
            store.plan(store.plan(), store.prizes(), false);
            context.startActivity(new Intent(context, MainActivity.class).putExtra("section", "plan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            tap("Choose ₹10 schemes →"); tap("Confirm setup · ready to call"); expect("Game messages"); assertTrue(store.plan().approved);
            for (GameMessages.Kind kind : GameMessages.Kind.values()) {
                choose(kind); String expected = expected(kind); shared.set(null); tap("Share " + kind.action);
                await("Share chooser opened", () -> shared.get() != null);
                Intent send = shared.get().getParcelableExtra(Intent.EXTRA_INTENT, Intent.class); assertNotNull(send);
                assertEquals(Intent.ACTION_SEND, send.getAction()); assertEquals("text/plain", send.getType());
                assertEquals(expected, send.getStringExtra(Intent.EXTRA_TEXT)); assertEquals(0, store.load().count()); assertTrue(store.plan().approved);
            }
            choose(GameMessages.Kind.PLAYERS); visible(By.desc("Game message preview")); screenshot("players-tickets-preview");
        } finally { instrumentation.removeMonitor(monitor); }
    }
}
