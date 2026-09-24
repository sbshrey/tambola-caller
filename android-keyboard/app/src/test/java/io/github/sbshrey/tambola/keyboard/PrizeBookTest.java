package io.github.sbshrey.tambola.keyboard;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PrizeBookTest {
    private PrizeBook people() { return new PrizeBook(new LinkedHashMap<String, String>() {{ put("a", "Asha"); put("b", "Bina"); put("c", "Charu"); }}, PrizeBook.defaults().schemes); }
    @Test public void trackingIsOptionalAndDefaultPrizeSplitsExactly() {
        assertTrue(PrizeBook.defaults().players.isEmpty()); PrizeBook book = people();
        for (PrizeBook.Scheme item : book.schemes) assertEquals(10, item.rupees);
        book = book.award("prize-0", Arrays.asList("a"), 10, 5); assertEquals(1000, book.scheme("prize-0").eachPaise());
        book = book.award("prize-0", Arrays.asList("a", "b"), 10, 5); assertEquals(500, book.scheme("prize-0").eachPaise());
        book = book.award("prize-0", Arrays.asList("a", "b"), 11, 5); assertEquals("₹5.50", PrizeBook.money(book.scheme("prize-0").eachPaise()));
    }
    @Test public void rejectsDuplicateUnknownAndMoreThanTwoWinnersAndEarlyClaims() {
        PrizeBook book = people();
        for (List<String> ids : Arrays.asList(Arrays.asList("a", "a"), Arrays.asList("missing"), Arrays.asList("a", "b", "c"))) assertThrows(IllegalArgumentException.class, () -> book.award("prize-0", ids, 10, 5));
        assertThrows(IllegalArgumentException.class, () -> book.award("prize-0", Arrays.asList("a"), 10, 4));
        assertThrows(IllegalArgumentException.class, () -> book.award("prize-4", Arrays.asList("a"), 10, 14));
        assertThrows(IllegalArgumentException.class, () -> book.award("prize-0", Arrays.asList("a"), -1, 5));
    }
    @Test public void setupEditsKeepRecordedPayoutsAndRenamesFollowWinnerIds() {
        PrizeBook book = people().award("prize-0", Arrays.asList("a", "b"), 10, 5).configure("prize-0", "Quick five", 30, true).player("a", "Asha Sharma");
        assertEquals(30, book.scheme("prize-0").rupees); assertEquals(10, book.scheme("prize-0").awardedRupees);
        assertTrue(book.results(8).contains("Asha Sharma & Bina")); assertTrue(book.results(8).contains("Asha Sharma: ₹5"));
        assertThrows(IllegalArgumentException.class, () -> book.removePlayer("a"));
        assertThrows(IllegalArgumentException.class, () -> book.configure("prize-0", "Quick five", 30, false));
    }
    @Test public void undoClearsOnlyAwardsOnRemovedCallsAndNewGameRetainsSetup() {
        PrizeBook book = people().award("prize-0", Arrays.asList("a"), 10, 5).award("prize-1", Arrays.asList("b"), 20, 9);
        PrizeBook undone = book.afterUndo(8); assertFalse(undone.scheme("prize-0").winners.isEmpty()); assertTrue(undone.scheme("prize-1").winners.isEmpty());
        PrizeBook fresh = book.newRound(); assertFalse(fresh.hasWinners()); assertEquals(book.players, fresh.players); assertEquals(5, fresh.schemes.size());
    }
    @Test public void customPrizesAndUniqueNamesHaveBounds() {
        PrizeBook book = people().configure(null, "Lucky corner", 0, true); assertEquals(6, book.schemes.size());
        assertThrows(IllegalArgumentException.class, () -> book.player(null, "  ASHA "));
        assertThrows(IllegalArgumentException.class, () -> book.player(null, ""));
        assertThrows(IllegalArgumentException.class, () -> book.configure(null, "Too much", 100001, true));
    }
}
