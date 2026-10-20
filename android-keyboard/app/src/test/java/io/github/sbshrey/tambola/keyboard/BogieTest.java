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
    @Test public void bogiePersistsThroughSetupEditsAndPreventsAccidentalReaward() {
        PrizeBook book = setup().resolve("prize-0", three(), 5).player("family-varsha", "Varsha ji").configure("prize-0", "Early 5", 25, true);
        assertTrue(book.announcement(book.scheme("prize-0")).contains("Varsha ji"));
        assertTrue(FamilySetup.addTo(book).scheme("prize-0").bogie());
        assertThrows(IllegalArgumentException.class, () -> book.claim("prize-0", "family-rekha", 5));
        assertThrows(IllegalArgumentException.class, () -> WinnerCommand.prepare(book, "Early five winner Rekha", 5));
        assertThrows(IllegalArgumentException.class, () -> book.removePlayer("family-rekha"));
        assertThrows(IllegalArgumentException.class, () -> book.configure("prize-0", "Early 5", 25, false));
    }
    @Test public void correctingClearingUndoAndNewGameHandleBogieAndKeepSetup() {
        PrizeBook book = setup().resolve("prize-0", three(), 5);
        PrizeBook corrected = book.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha"), 10);
        assertFalse(corrected.scheme("prize-0").bogie()); assertEquals(500, corrected.scheme("prize-0").eachPaise());
        assertEquals(5, corrected.scheme("prize-0").callCount);
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
}
