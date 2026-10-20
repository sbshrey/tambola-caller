package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResultsTableTest {
    private PrizeBook people(String... values) {
        Map<String, String> names = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) names.put(values[i], values[i + 1]);
        return new PrizeBook(names, PrizeBook.defaults().schemes);
    }
    @Test public void playerColumnsContainOnlyTheirWinsAndKeepBogiesSeparate() {
        PrizeBook book = people("v", "Varsha", "r", "Rekha", "s", "Suman", "n", "Nirmala");
        book = book.resolve("prize-0", Arrays.asList("v", "r"), 5).resolve("prize-1", Collections.singletonList("v"), 5)
            .resolve("prize-2", Arrays.asList("v", "r", "s"), 5).configure("prize-3", "Bottom line", 10, false);
        ResultsTable table = new ResultsTable(book, null);
        assertEquals(4, table.players.size()); assertEquals("Varsha", table.players.get(0).name);
        assertEquals(2, table.players.get(0).wins.size()); assertEquals(1, table.players.get(1).wins.size());
        assertTrue(table.players.get(2).wins.isEmpty()); assertTrue(table.players.get(3).wins.isEmpty());
        ResultsTable.Win shared = table.players.get(0).wins.get(0);
        assertEquals("prize-0", shared.id); assertTrue(shared.shared); assertEquals(500, shared.paise);
        assertEquals("Early 5\n₹5 · shared", shared.label()); assertEquals(1, table.summary.bogies.size()); assertEquals(1, table.unawarded);
        assertEquals(1500, table.players.get(0).totalPaise); assertEquals(500, table.players.get(1).totalPaise); assertEquals(0, table.players.get(3).totalPaise);
        assertEquals(2000, table.summary.totalPaise);
    }
    @Test public void manyWinsStayInOnePlayerColumnWithExactTotals() {
        PrizeBook book = people("v", "Varsha");
        for (int i = 5; i < 110; i++) book = book.configure(null, "Prize " + i, 10, true);
        for (PrizeBook.Scheme prize : new ArrayList<>(book.schemes)) book = book.resolve(prize.id, Collections.singletonList("v"), 90);
        ResultsTable table = new ResultsTable(book, null); assertEquals(110, table.players.get(0).wins.size()); assertEquals(1, table.players.size()); assertEquals(110000, table.players.get(0).totalPaise);
        for (ResultsTable.Win win : table.players.get(0).wins) assertEquals(1000, win.paise);
    }
    @Test public void fundedBalanceSeparatesUnpaidPrizesAndPurchases() {
        PrizeBook book = people("v", "Varsha"); TicketPlan plan = TicketPlan.forPlayers(book).approve(book);
        ResultsTable table = new ResultsTable(book.resolve("prize-0", Collections.singletonList("v"), 5), plan);
        assertEquals("Varsha\n1 set", table.players.get(0).label()); assertEquals("Collected ₹50 · Unallocated ₹0 · Unpaid prizes ₹40", table.balance());
    }
}
