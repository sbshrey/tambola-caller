package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FamilySetupTest {
    @Test public void loadsEveryNameAndSchemeAndCanBeRepeatedAfterRenaming() {
        PrizeBook book = FamilySetup.addTo(PrizeBook.defaults());
        assertEquals(FamilySetup.PLAYERS, new ArrayList<>(book.players.values()));
        assertEquals(11, book.players.size()); assertEquals(46, book.schemes.size());
        assertTrue(book.scheme("catalog-t").enabled);
        assertEquals("Seedhi family planning", book.scheme("catalog-siddi-family-planning").name);
        assertTrue(book.scheme("catalog-sixth-line").enabled); assertTrue(book.scheme("catalog-ninth-box").enabled);
        book = book.player("family-varsha", "Varsha ji").configure("catalog-t", "Our T", 25, false);
        PrizeBook again = FamilySetup.addTo(book);
        assertEquals(11, again.players.size()); assertEquals("Varsha ji", again.players.get("family-varsha"));
        assertEquals("Our T", again.scheme("catalog-t").name); assertEquals(25, again.scheme("catalog-t").rupees);
        assertTrue(again.scheme("catalog-t").enabled); assertTrue(FamilySetup.hasPlayers(again));
    }
    @Test public void reusesExistingNamesAndKeepsAwardsAndAmounts() {
        Map<String, String> names = new LinkedHashMap<>(); names.put("existing", "REKHA"); names.put("guest", "Guest");
        PrizeBook original = new PrizeBook(names, PrizeBook.defaults().schemes).configure("prize-0", "Early 5", 25, true)
            .award("prize-0", Arrays.asList("existing", "guest"), 11, 5);
        PrizeBook added = FamilySetup.addTo(original);
        assertEquals(12, added.players.size()); assertEquals("REKHA", added.players.get("existing"));
        assertFalse(added.players.containsKey("family-rekha"));
        assertEquals(550, added.scheme("prize-0").eachPaise()); assertEquals(25, added.scheme("prize-0").rupees);
        assertEquals(5, added.scheme("prize-0").callCount); assertEquals(2, original.players.size());
        assertEquals(12, added.newRound().players.size()); assertFalse(added.newRound().hasWinners());
        PrizeBook oldSpelling = original.configure(null, "Siddi family planning", 25, false);
        String oldId = oldSpelling.schemes.get(5).id;
        PrizeBook upgraded = FamilySetup.addTo(oldSpelling);
        assertEquals(46, upgraded.schemes.size()); assertEquals(25, upgraded.scheme(oldId).rupees);
        assertEquals("Siddi family planning", upgraded.scheme(oldId).name); assertTrue(upgraded.scheme(oldId).enabled);
    }
    @Test public void capacityFailureLeavesOriginalBookUntouched() {
        PrizeBook full = PrizeBook.defaults();
        for (int i = 0; i < 95; i++) full = full.player(null, "Guest " + i);
        PrizeBook snapshot = full;
        assertThrows(IllegalArgumentException.class, () -> FamilySetup.addTo(snapshot));
        assertEquals(95, snapshot.players.size()); assertEquals(5, snapshot.schemes.size());
        PrizeBook schemes = PrizeBook.defaults();
        for (int i = 5; i < 60; i++) schemes = schemes.configure(null, "Prize " + i, 10, true);
        PrizeBook crowded = schemes;
        assertThrows(IllegalArgumentException.class, () -> FamilySetup.addTo(crowded));
        assertTrue(crowded.players.isEmpty()); assertEquals(60, crowded.schemes.size());
    }
    @Test public void everyFamilyPlayerCanWinEverySuppliedPrizeWithoutCreatingNames() {
        PrizeBook book = FamilySetup.addTo(PrizeBook.defaults());
        for (PrizeCatalog.Preset preset : PrizeCatalog.ALL) for (String name : FamilySetup.PLAYERS) {
            WinnerCommand result = WinnerCommand.prepare(book, preset.name + " winner " + name, 90);
            assertEquals(11, result.book.players.size()); assertTrue(result.announcement.contains(name));
        }
        for (String command : Arrays.asList("T winner Varsha", "टी की विनर Rekha", "10 early winner Suman", "Ilove you winner Saroj", "Pati patni vah winner Nirmala"))
            assertEquals(11, WinnerCommand.prepare(book, command, 90).book.players.size());
    }
    @Test public void groupMappingIncludesRenamedCustomPrizesAndOriginalDefaults() {
        PrizeBook book = FamilySetup.addTo(PrizeBook.defaults().configure(null, "king", 15, false));
        assertEquals("Quick prizes", PrizeCatalog.group(book, book.schemes.get(5)));
        assertEquals("Lines", PrizeCatalog.group(book, book.scheme("prize-2")));
        assertEquals("Houses", PrizeCatalog.group(book, book.scheme("prize-4")));
        book = book.configure("catalog-t", "My special shape", 10, true);
        assertEquals("Special prizes", PrizeCatalog.group(book, book.scheme("catalog-t")));
    }
    @Test public void hostClaimIsSeparateFromAwardAndUsesSavedSpelling() {
        PrizeBook book = FamilySetup.addTo(PrizeBook.defaults());
        assertEquals("🙋 Varsha claims Early 5\nPlease check my ticket.", book.claim("prize-0", FamilySetup.hostId(book), 5));
        assertFalse(book.hasWinners());
        assertThrows(IllegalArgumentException.class, () -> book.claim("prize-0", FamilySetup.hostId(book), 4));
        assertThrows(IllegalArgumentException.class, () -> book.claim("prize-0", "missing", 5));
        PrizeBook renamed = book.player("family-varsha", "Varsha ji");
        assertTrue(renamed.claim("prize-0", FamilySetup.hostId(renamed), 5).contains("Varsha ji"));
        PrizeBook existing = PrizeBook.defaults().player(null, "VARSHA");
        assertNotNull(FamilySetup.hostId(existing));
        PrizeBook awarded = book.award("prize-0", Arrays.asList("family-rekha"), 10, 5);
        assertThrows(IllegalArgumentException.class, () -> awarded.claim("prize-0", FamilySetup.hostId(awarded), 5));
    }
}
