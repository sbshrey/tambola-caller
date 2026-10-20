package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BogieTest {
    private PrizeBook setup() { return FamilySetup.addTo(PrizeBook.defaults()); }
    private List<String> three() { return Arrays.asList("family-varsha", "family-rekha", "family-suman"); }
    @Test public void oneGetsTenTwoGetFiveAndThreeOrMoreGetNoPayout() {
        PrizeBook book = setup();
        for (int count = 1; count <= 11; count++) {
            List<String> claimants = new ArrayList<>(book.players.keySet()).subList(0, count);
            PrizeBook result = book.resolve("prize-0", claimants, 5); PrizeBook.Scheme prize = result.scheme("prize-0");
            assertEquals(count >= 3, prize.bogie()); assertTrue(prize.resolved());
            assertEquals(count == 1 ? 1000 : count == 2 ? 500 : 0, prize.eachPaise());
            assertEquals(count >= 3 ? 0 : 10, prize.awardedRupees);
            if (count >= 3) { assertTrue(result.announcement(prize).contains("No payout")); assertFalse(result.results(5).contains("Total prizes")); }
        }
        assertFalse(book.hasWinners());
    }
    @Test public void bogiePersistsThroughSetupEditsAndAcceptsAnotherClaim() {
        PrizeBook book = setup().resolve("prize-0", three(), 5).player("family-varsha", "Varsha ji").configure("prize-0", "Early 5", 25, true);
        assertTrue(book.announcement(book.scheme("prize-0")).contains("Varsha ji"));
        assertTrue(FamilySetup.addTo(book).scheme("prize-0").bogie());
        assertTrue(book.scheme("prize-0").open());
        assertTrue(book.claim("prize-0", "family-rekha", 8).contains("claims Early 5"));
        WinnerCommand next = WinnerCommand.prepare(book, "Early five winner Rekha", 8);
        assertEquals(2500, next.book.scheme("prize-0").eachPaise()); assertEquals(8, next.book.scheme("prize-0").callCount);
        assertFalse(next.book.scheme("prize-0").open()); assertFalse(next.book.scheme("prize-0").bogie());
        assertThrows(IllegalArgumentException.class, () -> next.book.claim("prize-0", "family-rekha", 8));
        assertThrows(IllegalArgumentException.class, () -> book.removePlayer("family-rekha"));
        assertThrows(IllegalArgumentException.class, () -> book.configure("prize-0", "Early 5", 25, false));
    }
    @Test public void correctingClearingUndoAndNewGameHandleBogieAndKeepSetup() {
        PrizeBook book = setup().resolve("prize-0", three(), 5);
        PrizeBook corrected = book.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha"), 10);
        assertFalse(corrected.scheme("prize-0").bogie()); assertEquals(500, corrected.scheme("prize-0").eachPaise());
        assertEquals(10, corrected.scheme("prize-0").callCount);
        assertFalse(corrected.afterUndo(9).hasWinners());
        assertTrue(book.afterUndo(5).hasWinners()); assertFalse(book.afterUndo(4).hasWinners());
        assertFalse(book.award("prize-0", Collections.emptyList(), 0, 0).scheme("prize-0").bogie());
        assertEquals(11, book.newRound().players.size()); assertEquals(46, book.newRound().schemes.size()); assertFalse(book.newRound().hasWinners());
    }
    @Test public void invalidClaimsCannotCreateBogie() {
        PrizeBook book = setup();
        for (List<String> claimants : Arrays.asList(Collections.<String>emptyList(), Arrays.asList("family-varsha", "family-varsha", "family-rekha"), Arrays.asList("family-varsha", "family-rekha", "missing")))
            assertThrows(IllegalArgumentException.class, () -> book.resolve("prize-0", claimants, 5));
        assertThrows(IllegalArgumentException.class, () -> book.resolve("prize-0", three(), 4));
        PrizeBook disabled = book.configure("prize-0", "Early 5", 10, false);
        assertThrows(IllegalArgumentException.class, () -> disabled.resolve("prize-0", three(), 5));
        assertThrows(IllegalArgumentException.class, () -> new PrizeBook.Scheme("bad", "Bad", 10, true, 1, three().subList(0, 1), 10, 5, three()));
    }
    @Test public void laterClaimsAndWinnerCorrectionsReplacePayoutsWithoutDoubleCounting() {
        PrizeBook bogie = setup().resolve("prize-0", three(), 5);
        PrizeBook repeated = bogie.resolve("prize-0", three(), 6);
        assertTrue(repeated.scheme("prize-0").open()); assertEquals(6, repeated.scheme("prize-0").callCount);
        PrizeBook awarded = repeated.resolve("prize-0", Collections.singletonList("family-varsha"), 7)
            .resolve("catalog-king", Collections.singletonList("family-varsha"), 7);
        PrizeBook corrected = awarded.resolve("prize-0", Collections.singletonList("family-rekha"), 9);
        assertEquals(7, corrected.scheme("prize-0").callCount); assertEquals(2000, new PrizeSummary(corrected).totalPaise);
        ResultsTable results = new ResultsTable(corrected, null);
        assertEquals(1000, results.players.get(0).totalPaise); assertEquals(1000, results.players.get(1).totalPaise);
        PrizeBook split = corrected.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha"), 10);
        assertEquals(500, split.scheme("prize-0").eachPaise()); assertEquals(2000, new PrizeSummary(split).totalPaise);
        PrizeBook reopened = split.resolve("prize-0", three(), 10);
        assertTrue(reopened.scheme("prize-0").open()); assertEquals(1000, new PrizeSummary(reopened).totalPaise);
        assertEquals(45, new SchemeStatus(reopened, null).remaining.size());
        PrizeBook finalClaim = reopened.resolve("prize-0", Collections.singletonList("family-suman"), 11);
        assertEquals(11, finalClaim.scheme("prize-0").callCount); assertEquals(2000, new PrizeSummary(finalClaim).totalPaise);
        assertEquals(Collections.singletonList("family-suman"), finalClaim.scheme("prize-0").winners);
        assertFalse(finalClaim.afterUndo(10).scheme("prize-0").resolved());
        assertEquals(1000, new PrizeSummary(finalClaim.afterUndo(10)).totalPaise);
    }
}
