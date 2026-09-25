package io.github.sbshrey.tambola.keyboard;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class WinnerCommandTest {
    private PrizeBook all() { return PrizeBook.defaults().addPresets(PrizeCatalog.allIds()); }
    @Test public void speaksPrizeAndNewNameWithoutSetup() {
        PrizeBook original = PrizeBook.defaults(); WinnerCommand result = WinnerCommand.prepare(original, "Early five ki winner Asha Sharma.", 5);
        assertEquals("🏆 Early 5: Asha Sharma\n₹10 prize", result.announcement);
        assertTrue(original.players.isEmpty()); assertFalse(original.hasWinners());
        assertEquals(1, result.book.players.size()); assertEquals(5, result.book.scheme("prize-0").callCount);
    }
    @Test public void twoExistingWinnersReuseIdsAndConfiguredAmount() {
        Map<String, String> players = new LinkedHashMap<>(); players.put("a", "Asha Sharma"); players.put("b", "Bina");
        PrizeBook book = new PrizeBook(players, all().schemes).configure("catalog-king", "King", 11, true);
        WinnerCommand result = WinnerCommand.prepare(book, "King ke winners asha sharma aur BINA", 5);
        assertEquals(Arrays.asList("a", "b"), result.book.scheme("catalog-king").winners);
        assertEquals(2, result.book.players.size()); assertEquals("🏆 King: Asha Sharma & Bina\n₹5.50 each", result.announcement);
    }
    @Test public void supportsHindiAndPrizeAfterName() {
        assertEquals("🏆 Early 5: आशा शर्मा & बीना\n₹5 each", WinnerCommand.prepare(all(), "अर्ली फाइव की विनर आशा शर्मा और बीना", 5).announcement);
        assertEquals("🏆 House 1: Asha Sharma\n₹10 prize", WinnerCommand.prepare(all(), "Asha Sharma house one", 15).announcement);
        assertTrue(WinnerCommand.prepare(all(), "पहली लाइन विजेता आशा", 5).announcement.contains("First line: आशा"));
    }
    @Test public void everyCatalogNameCanBeSpokenAndCustomNamesWork() {
        for (PrizeCatalog.Preset preset : PrizeCatalog.ALL) {
            WinnerCommand result = WinnerCommand.prepare(all(), preset.name + " winner Asha Sharma", 90);
            assertFalse(preset.name, result.book.scheme(preset.id).winners.isEmpty());
        }
        PrizeBook custom = all().configure(null, "Lucky star", 30, true);
        assertEquals("🏆 Lucky star: Asha\n₹30 prize", WinnerCommand.prepare(custom, "Lucky star Asha", 1).announcement);
    }
    @Test public void neverOverwritesAwardsOrEnablesUnknownPrizes() {
        PrizeBook awarded = WinnerCommand.prepare(all(), "King Asha", 1).book;
        assertThrows(IllegalArgumentException.class, () -> WinnerCommand.prepare(awarded, "King Bina", 5));
        assertThrows(IllegalArgumentException.class, () -> WinnerCommand.prepare(PrizeBook.defaults(), "King Asha", 5));
        PrizeBook disabled = all().configure("catalog-king", "King", 10, false);
        assertThrows(IllegalArgumentException.class, () -> WinnerCommand.prepare(disabled, "King Asha", 5));
        assertThrows(IllegalArgumentException.class, () -> WinnerCommand.prepare(all(), "Early five Asha", 4));
    }
    @Test public void rejectsAmbiguousMissingDuplicateAndTooManyNames() {
        for (String speech : Arrays.asList("Early five", "Early five winner", "King and", "King Asha aur asha", "King Asha and Bina and Charu", "King Asha Queen", "Asha Sharma", "King winner Asha 10 rupees", "King Asha &", "King 123"))
            assertThrows(speech, IllegalArgumentException.class, () -> WinnerCommand.prepare(all(), speech, 90));
        PrizeBook duplicate = all().configure(null, "King", 10, true);
        assertThrows(IllegalArgumentException.class, () -> WinnerCommand.prepare(duplicate, "King Asha", 5));
    }
    @Test public void doesNotGuessSimilarNamesOrTranslateSavedNames() {
        PrizeBook book = all().player(null, "Asha Sharma");
        WinnerCommand result = WinnerCommand.prepare(book, "King आशा शर्मा", 1);
        assertEquals(2, result.book.players.size()); assertTrue(result.announcement.contains("आशा शर्मा"));
    }
    @Test public void explicitWinnerMarkerAllowsNamesThatAreAlsoPrizeNames() {
        assertEquals("🏆 King: Mona\n₹10 prize", WinnerCommand.prepare(all(), "King ki winner Mona", 5).announcement);
        assertEquals("🏆 Early 5: Sona & Mona\n₹5 each", WinnerCommand.prepare(all(), "Early five winners Sona and Mona", 5).announcement);
        assertEquals("🏆 King: मोना\n₹10 prize", WinnerCommand.prepare(all(), "किंग की विनर मोना।  ", 5).announcement);
    }
    @Test public void acceptsJoinedSchemeWordsFromActualSpeechRecognition() {
        assertEquals("🏆 Top line: Asha & BINA\n₹5 each", WinnerCommand.prepare(all(), "Topline winners Asha and BINA", 5).announcement);
        assertEquals("🏆 Early 5: Asha\n₹10 prize", WinnerCommand.prepare(all(), "Early5 winner Asha", 5).announcement);
        assertEquals("🏆 Photo frame: Asha\n₹10 prize", WinnerCommand.prepare(all(), "Photoframe winner Asha", 5).announcement);
        assertThrows(IllegalArgumentException.class, () -> WinnerCommand.prepare(all(), "Toplines winner Asha", 5));
    }
    @Test public void acceptsObservedOnlyFiveTranscriptOnlyWithWinnerMarkerAndNoCollision() {
        assertEquals("🏆 Early 5: Asha Sharma\n₹10 prize", WinnerCommand.prepare(all(), "only 5 winner Asha Sharma", 5).announcement);
        assertThrows(IllegalArgumentException.class, () -> WinnerCommand.prepare(all(), "only 5 Asha Sharma", 5));
        PrizeBook collision = all().configure(null, "Only 5", 20, true);
        assertThrows(IllegalArgumentException.class, () -> WinnerCommand.prepare(collision, "only 5 winner Asha", 5));
    }
}
