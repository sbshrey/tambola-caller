package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrizeSummaryTest {
    @Test public void groupsEachPlayersPrizesWithExactSharesAndZeroForBogieAndNoWins() {
        PrizeBook book = FamilySetup.addTo(PrizeBook.defaults()).configure("prize-0", "Early 5", 11, true)
            .resolve("prize-0", Arrays.asList("family-varsha", "family-rekha"), 5)
            .resolve("catalog-king", Collections.singletonList("family-varsha"), 6)
            .resolve("catalog-queen", Arrays.asList("family-varsha", "family-rekha", "family-suman"), 7);
        PrizeSummary summary = new PrizeSummary(book);
        assertEquals(11, summary.players.size()); assertEquals(2100, summary.totalPaise); assertEquals(2, summary.paidPrizes);
        assertEquals("Varsha", summary.players.get(0).name); assertEquals(1550, summary.players.get(0).totalPaise);
        assertEquals(Arrays.asList("Early 5 · ₹5.50 (shared)", "King · ₹10"), summary.players.get(0).prizes);
        assertEquals(550, summary.players.get(1).totalPaise); assertEquals(0, summary.players.get(10).totalPaise);
        assertTrue(summary.players.get(10).prizes.isEmpty()); assertEquals("Queen", summary.bogies.get(0).prize);
        assertEquals(3, summary.bogies.get(0).names.size());
    }
    @Test public void totalsUseRecordedPayoutsAndCorrectionsAndResetRemoveOldWinnings() {
        PrizeBook book = FamilySetup.addTo(PrizeBook.defaults()).resolve("prize-0", Arrays.asList("family-varsha", "family-rekha"), 5)
            .configure("prize-0", "Early 5", 100, true);
        assertEquals(1000, new PrizeSummary(book).totalPaise);
        book = book.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha", "family-suman"), 6);
        assertEquals(0, new PrizeSummary(book).totalPaise); assertEquals(0, new PrizeSummary(book).paidPrizes);
        book = book.resolve("prize-0", Collections.singletonList("family-varsha"), 6);
        assertEquals(10000, new PrizeSummary(book).totalPaise);
        assertEquals(0, new PrizeSummary(book.afterUndo(4)).totalPaise);
        PrizeSummary reset = new PrizeSummary(book.newRound()); assertEquals(0, reset.totalPaise); assertEquals(11, reset.players.size()); assertTrue(reset.bogies.isEmpty());
    }
}
