package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TicketPlanUiTest extends KeyboardScenario {
    private PrizeBook funded(TicketPlan plan) {
        PrizeBook book = store.prizes();
        while (TicketPlan.selected(book) < plan.totalSets() * 5) book = book.configure(null, "Extra " + book.schemes.size(), 10, true);
        return book;
    }
    @Before public void clearOldPlan() { store.resetPlan(); focus(); }
    @After public void removePlan() {
        main(() -> {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitor monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance();
            for (androidx.test.runner.lifecycle.Stage stage : androidx.test.runner.lifecycle.Stage.values())
                for (android.app.Activity activity : new ArrayList<>(monitor.getActivitiesInStage(stage))) if (activity instanceof MainActivity) activity.finish();
        });
        device.waitForIdle(); store.resetPlan();
    }
    @Test public void chooseMixedSetsSelectTenRupeeSchemesAndStartFromTheKeyboard() {
        tap("Set tickets & schemes"); expect("1. Purchased ticket sets");
        assertEquals(550, store.plan().collection()); tap(By.desc("One more set for Varsha"));
        await("Varsha's second set saved", () -> store.plan().collection() == 600); tap(By.desc("One fewer set for Suman"));
        await("Suman marked not playing", () -> store.plan().sets("family-suman") == 0);
        assertEquals(2, store.plan().sets("family-varsha")); assertEquals(0, store.plan().sets("family-suman"));
        assertEquals(550, store.plan().collection()); screenshot("ticket-purchases");
        tap("Choose ₹10 schemes →"); tap("Clear selection"); await("No schemes selected", () -> TicketPlan.selected(store.prizes()) == 0);
        tap(By.desc("Use scheme: Early 5")); await("Early 5 selected", () -> store.prizes().scheme("prize-0").enabled);
        tap(By.desc("Use scheme: King")); await("King selected", () -> store.prizes().scheme("catalog-king").enabled);
        tap(By.desc("Setup prize group: Houses")); tap(By.desc("Use scheme: House 1"));
        await("Three schemes selected", () -> TicketPlan.selected(store.prizes()) == 3);
        assertFalse(visible(By.text("Confirm setup · ready to call")).isEnabled());
        tap("Select all"); await("All catalog prizes selected", () -> TicketPlan.selected(store.prizes()) == 46);
        assertFalse(visible(By.text("Confirm setup · ready to call")).isEnabled());
        tap("Add extra ₹10 prizes"); visible(By.desc("Extra prize names")).setText("Extra 1\nExtra 2\nExtra 3\nExtra 4\nExtra 5\nExtra 6\nExtra 7\nExtra 8\nExtra 9"); tap(By.res("android:id/button1"));
        await("All 55 prizes allocated", () -> TicketPlan.selected(store.prizes()) == 55);
        assertTrue(device.wait(androidx.test.uiautomator.Until.gone(By.res("android:id/button1")), 5000));
        for (PrizeBook.Scheme prize : store.prizes().schemes) assertEquals(10, prize.rupees);
        assertTrue(visible(By.text("Confirm setup · ready to call")).isEnabled());
        screenshot("ticket-ten-rupee-budget"); tap("Confirm setup · ready to call");
        expect("Test chat editor"); focus(); assertTrue(store.plan().approved); draw(); assertEquals(1, store.load().count());
    }
    @Test public void onlyParticipatingPlayersCanWinAndAmountsStayFixedDuringPlay() {
        TicketPlan plan = TicketPlan.forPlayers(store.prizes()).with("family-varsha", 2).with("family-suman", 0);
        PrizeBook book = funded(plan); store.plan(plan, book, true); store.save(Game.decode("1,2,3,4,5")); focus();
        choose("Quick prizes", "Early 5"); assertFalse(visible(By.desc("Suman, choose claimant")).isEnabled());
        name("Varsha"); name("Rekha"); tap("Insert winner announcement"); expect("Winner saved");
        assertEquals(2, store.prizes().scheme("prize-0").winners.size()); assertEquals(book.scheme("prize-0").rupees * 50, store.prizes().scheme("prize-0").eachPaise());
        PrizeBook current = store.prizes();
        assertThrows(IllegalArgumentException.class, () -> store.prizes(current.configure("prize-0", "Early 5", 999, true)));
        assertThrows(IllegalArgumentException.class, () -> store.plan(plan, current, true));
        assertEquals(book.scheme("prize-0").rupees, store.prizes().scheme("prize-0").rupees);
        tap("Board"); tap("Tickets & schemes"); expect("Game in progress."); assertFalse(device.hasObject(By.desc("One more set for Varsha")));
    }
    @Test public void nextRoundKeepsPurchasesButBlocksCallsUntilReviewAndApproval() {
        TicketPlan plan = TicketPlan.forPlayers(store.prizes()).with("family-varsha", 2); PrizeBook book = funded(plan); store.plan(plan, book, true);
        draw(); send(); tap("Board"); tap("Start a new game"); tap("Yes, start new game");
        await("New round", () -> store.load().count() == 0); assertFalse(store.plan().approved); assertEquals(2, store.plan().sets("family-varsha"));
        assertFalse(visible(By.desc("Next number")).isEnabled()); tap("Review tickets & schemes");
        tap("Choose ₹10 schemes →"); tap("Confirm setup · ready to call"); expect("Test chat editor"); focus();
        draw(); assertEquals(1, store.load().count()); assertEquals(600, TicketPlan.budget(store.prizes()));
    }
    @Test public void ticketPlanPersistsAndOldInstallationsHaveNoRequiredPlan() {
        assertNull(store.plan()); TicketPlan plan = TicketPlan.forPlayers(store.prizes()).with("family-varsha", 2); PrizeBook book = funded(plan);
        store.plan(plan, book, true); TicketPlan loaded = new GameStore(context, false).plan(); assertTrue(loaded.approved);
        assertEquals(2, loaded.sets("family-varsha")); loaded.validateLive(new GameStore(context, false).prizes());
        assertEquals(loaded.sets, TicketPlanJson.decode(TicketPlanJson.encode(loaded)).sets);
        assertThrows(IllegalArgumentException.class, () -> TicketPlanJson.decode("{\"version\":1,\"approved\":false,\"sets\":{\"x\":1.5},\"prizes\":{}}"));
        assertThrows(IllegalArgumentException.class, () -> TicketPlanJson.decode("{\"version\":9}"));
    }
    @Test public void upgradingPreservesLivePayoutsButRequiresTenRupeeSetupForANewGame() {
        PrizeBook old = store.prizes(); Map<String, Integer> amounts = new LinkedHashMap<>(); int index = 0;
        for (PrizeBook.Scheme prize : new ArrayList<>(old.schemes)) {
            int amount = index++ < 44 ? 12 : 11;
            old = old.configure(prize.id, prize.name, amount, true); amounts.put(prize.id, amount);
        }
        TicketPlan saved = new TicketPlan(TicketPlan.forPlayers(old).sets, amounts, true);
        String oldJson = PrizeJson.encode(old), planJson = TicketPlanJson.encode(saved);
        context.getSharedPreferences("tambola-round", Context.MODE_PRIVATE).edit().putString("prizes-v1", oldJson).putString("ticket-plan-v1", planJson).commit();
        store.save(Game.decode("1,2,3,4,5")); store.requireReady();
        store.prizes(store.prizes().resolve("prize-0", Collections.singletonList("family-varsha"), 5));
        assertEquals(1200, store.prizes().scheme("prize-0").eachPaise());
        store.newRound(); assertFalse(store.plan().approved); assertFalse(store.prizes().hasWinners());
        for (PrizeBook.Scheme prize : store.prizes().schemes) assertEquals(10, prize.rupees);
        assertThrows(IllegalArgumentException.class, store::requireReady);
        // An approved older setup with no calls also needs review before its first draw.
        context.getSharedPreferences("tambola-round", Context.MODE_PRIVATE).edit().putString("prizes-v1", oldJson).putString("ticket-plan-v1", planJson).commit();
        assertThrows(IllegalArgumentException.class, store::requireReady); assertFalse(store.plan().approved); assertEquals(460, TicketPlan.budget(store.prizes()));
    }
    @Test public void unreadablePrizesCanBeResetWithTheirBudgetAfterConfirmation() {
        TicketPlan plan = TicketPlan.forPlayers(store.prizes()); store.plan(plan, funded(plan), true);
        store.save(Game.decode("1,2,3,4,5"));
        context.getSharedPreferences("tambola-round", Context.MODE_PRIVATE).edit().putString("prizes-v1", "broken").commit();
        context.startActivity(new android.content.Intent(context, MainActivity.class).putExtra("section", "players").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        tap("Reset unreadable prize details"); expect("ticket budget"); tap(By.res("android:id/button2"));
        assertTrue(device.wait(androidx.test.uiautomator.Until.gone(By.res("android:id/button2")), 5000));
        assertNotNull(store.plan()); assertThrows(IllegalArgumentException.class, () -> store.prizes());
        tap("Reset unreadable prize details"); tap(By.res("android:id/button1"));
        await("Recovery clears the old ticket plan", () -> store.plan() == null);
        assertEquals(PrizeJson.encode(PrizeBook.defaults()), PrizeJson.encode(store.prizes())); assertEquals("1,2,3,4,5", store.load().encode());
    }
}
