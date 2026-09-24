package io.github.sbshrey.tambola.keyboard;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashSet;
import java.util.Random;

public class GameTest {
    @Test public void drawsEveryNumberOnceAndStops() {
        Game game = Game.empty(); Random random = new Random(42);
        for (int i = 0; i < 90; i++) { game = game.draw(random); assertEquals(i + 1, game.count()); }
        assertEquals(90, new HashSet<>(game.called()).size()); assertSame(game, game.draw(random));
        assertTrue(game.called().contains(1)); assertTrue(game.called().contains(90));
    }
    @Test public void savesResumeTheExactRoundAndUndoRestoresAvailability() {
        Game game = Game.decode("7,47,90"); assertEquals("7,47,90", game.encode());
        assertEquals("90  ·  47  ·  7", game.recent()); assertEquals(90, game.latest());
        assertEquals("7,47", game.undo().encode()); assertEquals("", Game.empty().undo().encode());
    }
    @Test public void emojisMatchWhatsAppKeycapDigitsWithoutLeadingZeroes() {
        assertEquals("7\uFE0F\u20E3", Game.emoji(7));
        assertEquals("4\uFE0F\u20E37\uFE0F\u20E3", Game.emoji(47));
        assertEquals("9\uFE0F\u20E30\uFE0F\u20E3", Game.emoji(90));
        for (int i = 1; i <= 90; i++) assertEquals(i < 10 ? 3 : 6, Game.emoji(i).length());
    }
    @Test public void rejectsCorruptSavesAndOutOfRangeNumbers() {
        for (String value : new String[]{"0", "91", "1,1", "1,", "-2", "47,x", "01", " 7"}) {
            assertThrows(IllegalArgumentException.class, () -> Game.decode(value));
        }
        assertThrows(IllegalArgumentException.class, () -> Game.emoji(0));
        assertThrows(IllegalArgumentException.class, () -> Game.emoji(91));
        assertEquals(0, Game.decode(null).count());
    }
    @Test public void drawsDoNotMutatePreviousSnapshotsAndRecentStopsAtTen() {
        Game previous = Game.decode("1,2,3,4,5,6,7,8,9,10,11");
        Game next = previous.draw(new Random(5)); assertEquals(11, previous.count()); assertEquals(12, next.count());
        assertEquals("11  ·  10  ·  9  ·  8  ·  7  ·  6  ·  5  ·  4  ·  3  ·  2", previous.recent());
        assertThrows(UnsupportedOperationException.class, () -> previous.called().add(90));
    }
}
