package io.github.sbshrey.tambola.keyboard;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import java.util.*;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FullGameTest extends KeyboardScenario {
    private boolean funded() { return "true".equals(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("funded")); }
    @After public void removePlan() { if (funded()) context.getSharedPreferences("tambola-round", android.content.Context.MODE_PRIVATE).edit().remove("ticket-plan-v1").commit(); }
    private void house(String prize, String player) {
        choose("Houses", prize); name(player); tap("Insert winner announcement"); expect("Winner saved"); send(); tap("Call");
    }
    @Test public void threeCompleteGamesThroughKeyboardKeepSetupAndNeverLeakCallsOrAwards() throws Exception {
        if (funded()) {
            PrizeBook book = store.prizes();
            while (TicketPlan.selected(book) < 60) book = book.configure(null, "Extra " + book.schemes.size(), 10, true);
            store.prizes(book);
        }
        String setup = PrizeJson.encode(store.prizes());
        new GameStore(context, true).save(Game.decode("47"));
        main(() -> rule.getActivity().acceptImages = true); focus();
        for (int round = 1; round <= 3; round++) {
            if (funded()) {
                TicketPlan plan = TicketPlan.forPlayers(store.prizes()).with("family-varsha", round == 2 ? 1 : 2).with("family-rekha", round == 2 ? 2 : 1);
                store.plan(plan, store.prizes(), true); focus(); assertEquals(600, store.plan().collection()); assertEquals(600, TicketPlan.budget(store.prizes()));
            }
            Set<Integer> seen = new HashSet<>();
            for (int count = 1; count <= 90; count++) {
                int number = draw(); assertTrue("Round " + round + " repeated " + number, seen.add(number));
                if (count == 10) {
                    tap("Undo last number"); tap("Yes, undo number"); expect("Unsent number cleared.");
                    assertEquals(9, store.load().count()); assertEquals("", draft()); seen.remove(number);
                    number = draw(); assertTrue(seen.add(number));
                }
                send();
                if (count == 5) {
                    choose("Quick prizes", "Early 5"); tap("Claim for Varsha"); expect("Claim ready");
                    assertEquals("🙋 Varsha claims Early 5\nPlease check my ticket.", draft()); assertFalse(store.prizes().hasWinners()); send();
                    choose("Quick prizes", "Early 5"); name("Varsha"); name("Rekha"); tap("Insert winner announcement"); expect("Winner saved");
                    assertEquals("🏆 Early 5: Varsha & Rekha\n₹5 each", draft()); send(); tap("Call");
                }
                if (count == 6) {
                    choose("Quick prizes", "King"); name("Kamlesh Bhabhi"); tap("Insert winner announcement"); expect("Winner saved");
                    assertEquals("🏆 King: Kamlesh Bhabhi\n₹10 prize", draft()); send(); tap("Call");
                }
                if (count == 7) {
                    choose("Quick prizes", "Queen"); name("Varsha"); name("Rekha"); name("Suman"); tap("Insert Bogie announcement"); expect("Bogie saved");
                    assertTrue(draft().contains("3 claimants")); assertTrue(draft().contains("No payout")); send(); tap("Call");
                }
                if (count == 45) {
                    String calls = store.load().encode(), prizes = PrizeJson.encode(store.prizes());
                    device.pressHome(); rule.finishActivity(); rule.launchActivity(new android.content.Intent()); device.waitForIdle();
                    main(() -> rule.getActivity().acceptImages = true); focus();
                    assertEquals(calls, new GameStore(context, false).load().encode()); assertEquals(prizes, PrizeJson.encode(new GameStore(context, false).prizes()));
                    tap(By.desc("Insert current number again")); await("Reopened editor reinserts latest", () -> draft().equals(Game.emoji(store.load().latest())));
                    send(); assertEquals(45, store.load().count());
                    if (funded()) { assertTrue(store.plan().approved); assertEquals(600, store.plan().collection()); }
                }
                if (funded() && count == 60) house("House 1", "Varsha");
                if (funded() && count == 75) house("House 2", "Rekha");
                if (count % 15 == 0) android.util.Log.i("TambolaFullGame", "Game " + round + ": " + count + " / 90 calls verified");
            }
            assertEquals(90, seen.size()); for (int number = 1; number <= 90; number++) assertTrue(seen.contains(number));
            assertFalse(visible(By.desc("Next number")).isEnabled());
            tap(By.desc("Next number")); assertEquals(90, store.load().count()); assertEquals("", draft());
            if (funded()) house("House 3", "Suman");
            else house("House " + round, "Varsha");
            tap("Call"); tap("Share results picture"); expect("Results picture ready");
            main(() -> assertEquals(new ResultsImage(store.prizes(), 90, store.plan()).pages.size(), rule.getActivity().receivedImages.size()));
            assertEquals(funded() ? 5000 : 3000, new PrizeSummary(store.prizes()).totalPaise); assertEquals(90, store.load().count());
            if (funded() && round == 1) {
                android.graphics.Bitmap picture = new ResultsImage(store.prizes(), 90, store.plan()).picture(0);
                try (java.io.OutputStream output = context.openFileOutput("full-game-table.png", android.content.Context.MODE_PRIVATE)) { assertTrue(picture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)); }
                finally { picture.recycle(); }
            }
            tap("Winners"); tap("Insert all results"); expect("Ready · tap WhatsApp Send");
            assertTrue(draft().contains("90 numbers called")); assertTrue(draft().contains("Queen: Bogie"));
            assertTrue(draft().contains(funded() ? "Total prizes\nVarsha: ₹15\nRekha: ₹15\nKamlesh Bhabhi: ₹10\nSuman: ₹10" : "Total prizes\nVarsha: ₹15\nRekha: ₹5\nKamlesh Bhabhi: ₹10"));
            screenshot("full-game-" + round + "-results");
            // A new game keeps the old draft visible and blocks accidental calls until explicitly cleared.
            String results = draft(); tap("Board"); tap("Start a new game"); tap("Keep playing");
            assertEquals(90, store.load().count()); assertTrue(store.prizes().hasWinners());
            tap("Board"); tap("Start a new game"); tap("Yes, start new game"); expect("Send or clear the current message first.");
            assertEquals(0, store.load().count()); assertEquals(setup, PrizeJson.encode(store.prizes())); assertEquals(results, draft());
            tap(By.desc("Next number")); assertEquals(0, store.load().count());
            tap(By.desc("Clear unsent message")); tap("Yes, clear message"); expect("Message cleared.");
            assertEquals("", draft()); assertEquals(!funded(), visible(By.desc("Next number")).isEnabled());
            if (funded()) { assertFalse(store.plan().approved); assertEquals(600, store.plan().collection()); }
            assertEquals("47", new GameStore(context, true).load().encode());
            android.util.Log.i("TambolaFullGame", "Game " + round + " complete: 90 unique numbers, awards, Bogie, results, reset verified");
        }
        if (funded()) { store.plan(store.plan(), store.prizes(), true); focus(); }
        draw(); assertEquals(1, store.load().count()); assertFalse(store.prizes().hasWinners()); screenshot("fourth-game-ready");
    }
}
