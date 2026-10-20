package io.github.sbshrey.tambola.keyboard;

import android.graphics.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BogieClaimUiTest extends KeyboardScenario {
    @Before public void fundedRound() {
        store.resetPlan(); PrizeBook book = store.prizes(); TicketPlan plan = TicketPlan.forPlayers(book); int index = 0;
        for (String id : book.players.keySet()) plan = plan.with(id, index++ < 3 ? 1 : 0);
        index = 0;
        for (PrizeBook.Scheme prize : new ArrayList<>(book.schemes)) book = book.configure(prize.id, prize.name, 10, index++ < 15);
        store.plan(plan, book, true); store.save(Game.decode("1,2,3,4,5"));
        main(() -> rule.getActivity().acceptImages = true); focus();
    }
    @After public void cleanPlan() { store.resetPlan(); }
    private void prize() { choose("Quick prizes", "Early 5"); }
    private PrizeBook.Scheme saved() { return store.prizes().scheme("prize-0"); }
    private int received() { int[] count = {0}; main(() -> count[0] = rule.getActivity().receivedImages.size()); return count[0]; }
    private void picture(boolean results, String file) throws Exception {
        tap("Winners"); int before = received(); String round = store.load().encode(), book = PrizeJson.encode(store.prizes()), plan = store.planSnapshot();
        tap(results ? "Share results picture" : "Share scheme status picture");
        await("Updated picture inserted", () -> received() == before + 1);
        Uri[] uri = {null}; main(() -> uri[0] = rule.getActivity().receivedImages.get(before));
        try (InputStream input = context.getContentResolver().openInputStream(uri[0])) {
            Bitmap actual = BitmapFactory.decodeStream(input);
            Bitmap expected = results ? new ResultsImage(store.prizes(), store.load().count(), store.plan()).picture(0)
                : new SchemeStatusImage(store.prizes(), store.load(), store.plan()).picture(0);
            try (OutputStream output = context.openFileOutput(file + ".png", android.content.Context.MODE_PRIVATE)) {
                assertTrue(expected.sameAs(actual)); assertTrue(actual.compress(Bitmap.CompressFormat.PNG, 100, output));
            } finally { expected.recycle(); actual.recycle(); }
        }
        assertEquals(round, store.load().encode()); assertEquals(book, PrizeJson.encode(store.prizes())); assertEquals(plan, store.planSnapshot());
    }
    @Test public void bogieCanBeClaimedAgainAndWrongWinnerCanBeReplacedDuringPlay() throws Exception {
        prize(); name("Varsha"); name("Rekha"); name("Kamlesh Bhabhi"); tap("Insert Bogie announcement");
        await("Bogie saved", () -> saved().bogie()); assertTrue(draft().contains("Scheme stays open")); send();
        assertTrue(new GameStore(context, false).prizes().scheme("prize-0").open());
        assertEquals(15, new SchemeStatus(store.prizes(), store.plan()).remaining.size()); assertEquals(0, new PrizeSummary(store.prizes()).totalPaise);
        picture(false, "bogie-still-open");
        tap("Call"); draw(); send(); prize();
        assertTrue(visible(By.text("Claim for Varsha")).isEnabled());
        assertFalse(visible(By.text("Insert winner announcement")).isEnabled());
        assertNotNull(visible(By.desc("Varsha, choose claimant"))); screenshot("bogie-new-claim");
        tap("Claim for Varsha"); await("Fresh host claim inserted", () -> draft().contains("Varsha claims Early 5"));
        assertTrue(saved().bogie()); send(); prize(); name("Varsha"); tap("Insert winner announcement");
        await("New claim wins", () -> saved().winners.equals(Collections.singletonList("family-varsha")));
        assertFalse(saved().bogie()); assertEquals(6, saved().callCount); assertEquals(1000, saved().eachPaise()); send();
        tap("Call"); draw(); send(); prize();
        tap("Choose different winners"); assertFalse(visible(By.text("Update winner announcement")).isEnabled());
        name("Rekha"); visible(By.desc("Winner announcement preview")); screenshot("correct-winner-preview");
        assertEquals(Collections.singletonList("family-varsha"), saved().winners);
        tap("Update winner announcement"); await("Wrong winner replaced", () -> saved().winners.equals(Collections.singletonList("family-rekha")));
        assertEquals("🏆 Early 5: Rekha\n₹10 prize", draft()); assertEquals(6, saved().callCount); assertEquals(7, store.load().count()); send();
        ResultsTable table = new ResultsTable(store.prizes(), store.plan());
        assertEquals(0, table.players.get(0).totalPaise); assertEquals(1000, table.players.get(1).totalPaise); assertEquals(1000, table.summary.totalPaise);
        assertTrue(table.summary.bogies.isEmpty()); assertEquals(14, table.unawarded);
        picture(true, "corrected-winner-results"); picture(false, "corrected-winner-schemes");
        store.undo(); assertEquals(Collections.singletonList("family-rekha"), saved().winners);
        store.undo(); assertTrue(saved().open()); assertEquals(0, new PrizeSummary(store.prizes()).totalPaise);
    }
    @Test public void bogieAndCorrectionControlsStayReachable() {
        List<String> firstThree = new ArrayList<>(store.prizes().players.keySet()).subList(0, 3);
        store.prizes(store.prizes().resolve("prize-0", firstThree, 5)); focus(); prize();
        assertTrue(visible(By.text("Claim for Varsha")).isEnabled());
        assertFalse(visible(By.text("Insert winner announcement")).isEnabled());
        visible(By.text("Use previous Bogie claimants")); screenshot("bogie-open-controls");
        tap("Use previous Bogie claimants"); assertTrue(visible(By.text("Insert Bogie announcement")).isEnabled());
        main(() -> store.prizes(store.prizes().resolve("prize-0", Collections.singletonList("family-varsha"), 5)));
        focus(); prize(); tap("Choose different winners"); name("Rekha");
        visible(By.text("Update winner announcement")); screenshot("winner-correction-controls");
        tap("Update winner announcement");
        await("Corrected winner saved and inserted", () -> saved().winners.equals(Collections.singletonList("family-rekha")) && draft().equals("🏆 Early 5: Rekha\n₹10 prize"));
        assertEquals(1000, new PrizeSummary(store.prizes()).totalPaise); assertEquals(5, store.load().count());
    }
}
