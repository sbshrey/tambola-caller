package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SchemeStatusTest {
    private PrizeBook book() {
        Map<String, String> people = new LinkedHashMap<>(); people.put("v", "Varsha"); people.put("r", "Rekha"); people.put("s", "Suman");
        return new PrizeBook(people, PrizeBook.defaults().schemes);
    }
    @Test public void selectedSchemesArePartitionedAndSharedNamesHaveTheirOwnPayouts() {
        PrizeBook book = book().resolve("prize-0", Arrays.asList("v", "r"), 5)
            .resolve("prize-1", Collections.singletonList("s"), 5).resolve("prize-2", Arrays.asList("v", "r", "s"), 5)
            .configure("prize-3", "Bottom line", 10, false);
        SchemeStatus status = new SchemeStatus(book, null);
        assertEquals(4, status.selected); assertEquals(1, status.remaining.size()); assertEquals(2, status.won.size()); assertEquals(1, status.bogies.size());
        assertEquals("Full house", status.remaining.get(0).name); assertEquals("Still open · ₹10", status.remaining.get(0).detail);
        assertEquals("Shared prize\nVarsha · ₹5\nRekha · ₹5", status.won.get(0).detail);
        assertEquals("Suman · ₹10", status.won.get(1).detail); assertEquals(2000, status.paidPaise);
        assertEquals("No payout · ₹0\nVarsha, Rekha, Suman", status.bogies.get(0).detail);
    }
    @Test public void claimAloneDoesNotCloseAPrizeButSavedAnnouncementDoes() {
        PrizeBook book = book(); String claim = book.claim("prize-0", "v", 5); assertTrue(claim.contains("claims Early 5"));
        assertEquals(5, new SchemeStatus(book, null).remaining.size());
        PrizeBook saved = book.resolve("prize-0", Collections.singletonList("v"), 5);
        assertTrue(saved.announcement(saved.scheme("prize-0")).contains("Varsha"));
        assertEquals(4, new SchemeStatus(saved, null).remaining.size()); assertEquals(1, new SchemeStatus(saved, null).won.size());
    }
    @Test public void correctionsUndoAndNewRoundImmediatelyChangeTheSnapshot() {
        PrizeBook initial = book().resolve("prize-0", Collections.singletonList("v"), 5);
        SchemeStatus previous = new SchemeStatus(initial, null);
        PrizeBook changed = initial.resolve("prize-0", Arrays.asList("v", "r", "s"), 5);
        assertEquals(1, new SchemeStatus(changed, null).bogies.size()); assertEquals(0, new SchemeStatus(changed, null).won.size());
        assertEquals(1, previous.won.size()); assertEquals(1000, previous.paidPaise);
        assertEquals(5, new SchemeStatus(changed.afterUndo(4), null).remaining.size());
        SchemeStatus next = new SchemeStatus(changed.newRound(), null); assertEquals(5, next.remaining.size()); assertEquals(0, next.paidPaise); assertTrue(next.bogies.isEmpty());
    }
    @Test public void recordedLegacyAmountsAreNotReplacedWithCurrentDefaults() {
        PrizeBook book = book().configure("prize-0", "Early 5", 11, true).resolve("prize-0", Arrays.asList("v", "r"), 5);
        SchemeStatus status = new SchemeStatus(book, null); assertEquals(1100, status.paidPaise);
        assertEquals("Shared prize\nVarsha · ₹5.50\nRekha · ₹5.50", status.won.get(0).detail);
    }
    @Test public void fundedSetupMustBeConfirmedAndStillMatchTheGame() {
        PrizeBook book = book().removePlayer("r").removePlayer("s"); TicketPlan plan = TicketPlan.forPlayers(book);
        assertThrows(IllegalArgumentException.class, () -> new SchemeStatus(book, plan));
        TicketPlan approved = plan.approve(book); assertEquals(5, new SchemeStatus(book, approved).remaining.size());
        PrizeBook changed = book.configure("prize-1", "Top line", 10, false);
        assertThrows(IllegalArgumentException.class, () -> new SchemeStatus(changed, approved));
        assertThrows(IllegalArgumentException.class, () -> new SchemeStatus(new PrizeBook(book.players, Collections.emptyList()), null));
    }
}
