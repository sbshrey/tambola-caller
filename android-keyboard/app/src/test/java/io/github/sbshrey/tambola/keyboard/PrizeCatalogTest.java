package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrizeCatalogTest {
    @Test public void all41CanBeAddedRepeatedlyWithoutLosingExistingAwardsOrDefaults() {
        Map<String, String> names = new LinkedHashMap<>(); names.put("a", "Asha"); names.put("b", "Bina");
        PrizeBook original = new PrizeBook(names, PrizeBook.defaults().schemes).award("prize-0", Arrays.asList("a", "b"), 11, 5);
        assertEquals(41, PrizeCatalog.ALL.size()); assertEquals(41, new HashSet<>(PrizeCatalog.allIds()).size());
        PrizeBook added = original.addPresets(PrizeCatalog.allIds());
        assertEquals(45, added.schemes.size()); assertEquals(5, original.schemes.size());
        assertEquals(original.players, added.players); assertEquals(550, added.scheme("prize-0").eachPaise());
        assertEquals(45, added.addPresets(PrizeCatalog.allIds()).schemes.size());
        for (PrizeCatalog.Preset preset : PrizeCatalog.ALL) { PrizeBook.Scheme scheme = PrizeCatalog.existing(added, preset); assertNotNull(scheme); assertTrue(scheme.enabled); assertEquals(10, scheme.rupees); }
    }
    @Test public void existingCustomAndRenamedPrizesAreReusedWithTheirAmounts() {
        PrizeBook book = PrizeBook.defaults().configure(null, " KING ", 25, false);
        String kingId = book.schemes.get(5).id;
        book = book.addPresets(Arrays.asList("catalog-king", "catalog-king"));
        assertEquals(6, book.schemes.size()); assertEquals(25, book.scheme(kingId).rupees); assertTrue(book.scheme(kingId).enabled);
        book = book.addPresets(Arrays.asList("catalog-queen")).configure("catalog-queen", "Our Queen", 30, false);
        book = book.addPresets(Arrays.asList("catalog-queen"));
        assertEquals(7, book.schemes.size()); assertEquals("Our Queen", book.scheme("catalog-queen").name); assertEquals(30, book.scheme("catalog-queen").rupees);
    }
    @Test public void newAwardsSplitAndNewRoundsKeepTheFullChosenList() {
        Map<String, String> names = new LinkedHashMap<>(); names.put("a", "Asha"); names.put("b", "Bina");
        PrizeBook book = new PrizeBook(names, PrizeBook.defaults().schemes).addPresets(PrizeCatalog.allIds());
        assertThrows(IllegalArgumentException.class, () -> book.award("catalog-early-10", Arrays.asList("a"), 10, 9));
        assertThrows(IllegalArgumentException.class, () -> book.award("catalog-house-3", Arrays.asList("a"), 10, 14));
        PrizeBook awarded = book.award("catalog-ninth-box", Arrays.asList("a", "b"), 10, 20).award("catalog-house-3", Arrays.asList("a"), 10, 20);
        assertEquals(500, awarded.scheme("catalog-ninth-box").eachPaise()); assertTrue(awarded.results(20).contains("Ninth box: Asha & Bina"));
        assertEquals(45, awarded.newRound().schemes.size()); assertFalse(awarded.newRound().hasWinners());
    }
    @Test public void badSelectionsAndOverLimitDoNotPartiallyMutateSetup() {
        PrizeBook base = PrizeBook.defaults();
        assertThrows(IllegalArgumentException.class, () -> base.addPresets(Arrays.asList("catalog-king", "not-a-prize"))); assertEquals(5, base.schemes.size());
        PrizeBook full = base; for (int i = 5; i < PrizeBook.MAX_SCHEMES; i++) full = full.configure(null, "Custom " + i, 10, true);
        PrizeBook limit = full; assertThrows(IllegalArgumentException.class, () -> limit.addPresets(Arrays.asList("catalog-king"))); assertEquals(60, limit.schemes.size());
    }
}
