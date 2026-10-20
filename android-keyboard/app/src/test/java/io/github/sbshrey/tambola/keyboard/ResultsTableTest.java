package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResultsTableTest {
    private PrizeBook people(String... values) {
        Map<String, String> names = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) names.put(values[i], values[i + 1]);
        return new PrizeBook(names, PrizeBook.defaults().schemes);
    }
    @Test public void schemeRowsAlignSharesAndTotalsWithTheRightPlayerColumns() {
        PrizeBook book = people("v", "Varsha", "r", "Rekha", "s", "Suman", "n", "Nirmala");
        book = book.resolve("prize-0", Arrays.asList("v", "r"), 5).resolve("prize-1", Collections.singletonList("v"), 5)
            .resolve("prize-2", Arrays.asList("v", "r", "s"), 5).configure("prize-3", "Bottom line", 10, false);
        ResultsTable table = new ResultsTable(book, null);
        assertEquals(4, table.players.size()); assertEquals(3, table.rows.size()); assertEquals("Varsha", table.players.get(0).name);
        assertEquals(Arrays.asList(500, 500, 0, 0), table.rows.get(0).shares); assertEquals("Shared", table.rows.get(0).status);
        assertEquals("₹5", table.rows.get(0).share(0)); assertEquals("—", table.rows.get(0).share(2));
        assertTrue(table.rows.get(2).bogie); assertEquals(0, table.rows.get(2).totalPaise); assertEquals(1, table.unawarded);
        assertEquals(1500, table.players.get(0).totalPaise); assertEquals(500, table.players.get(1).totalPaise); assertEquals(0, table.players.get(3).totalPaise);
        assertEquals(2000, table.summary.totalPaise);
    }
    @Test public void manyWinsStayInSeparateRowsWithOnePlayerColumnAndExactTotals() {
        PrizeBook book = people("v", "Varsha");
        for (int i = 5; i < 110; i++) book = book.configure(null, "Prize " + i, 10, true);
        for (PrizeBook.Scheme prize : new ArrayList<>(book.schemes)) book = book.resolve(prize.id, Collections.singletonList("v"), 90);
        ResultsTable table = new ResultsTable(book, null); assertEquals(110, table.rows.size()); assertEquals(1, table.players.size()); assertEquals(110000, table.players.get(0).totalPaise);
        for (ResultsTable.Row row : table.rows) assertEquals(1000, row.totalPaise);
    }
    @Test public void fundedBalanceSeparatesUnpaidPrizesAndPurchases() {
        PrizeBook book = people("v", "Varsha"); TicketPlan plan = TicketPlan.forPlayers(book).approve(book);
        ResultsTable table = new ResultsTable(book.resolve("prize-0", Collections.singletonList("v"), 5), plan);
        assertEquals("Varsha\n1 set", table.players.get(0).label()); assertEquals("Collected ₹50 · Unallocated ₹0 · Unpaid prizes ₹40", table.balance());
    }
}
