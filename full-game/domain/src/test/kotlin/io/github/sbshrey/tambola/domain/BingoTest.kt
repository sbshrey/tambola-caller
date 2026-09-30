package io.github.sbshrey.tambola.domain

import java.util.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class BingoTest {
    private val card = BingoCardGenerator(Random(42)).generate("card", "player")
    private fun called(indices: Set<Int>) = indices.map { card.cells[it] }.filter { it != 0 }.toSet()

    @Test fun `deals six valid distinct cards across ten thousand seeds`() {
        repeat(10_000) { seed ->
            val hand = BingoCardGenerator(Random(seed.toLong())).deal("p", 6)
            assertEquals(6, hand.map { it.fingerprint }.toSet().size)
            hand.forEach {
                assertEquals(24, it.numbers.toSet().size)
                assertEquals(0, it.cells[12])
                it.cells.forEachIndexed { i, n -> assertTrue(i == 12 || n in bingoColumnRange(i % 5)) }
            }
        }
    }

    @Test fun `every line orientation wins only with its called and marked numbers`() {
        BingoPattern.ANY_LINE.masks().forEach { mask ->
            val numbers = called(mask)
            assertTrue(BingoPattern.ANY_LINE.isComplete(card, numbers))
            numbers.forEach { missing ->
                assertFalse(BingoPattern.ANY_LINE.isComplete(card, numbers - missing))
                assertFalse(BingoPattern.ANY_LINE.isComplete(card, numbers, numbers - missing))
                assertFalse(BingoPattern.ANY_LINE.isComplete(card, numbers - missing, numbers))
            }
        }
    }

    @Test fun `pattern prizes are independent and blackout requires every non-free cell`() {
        val corners = called(BingoPattern.FOUR_CORNERS.masks().single())
        assertTrue(BingoPattern.FOUR_CORNERS.isComplete(card, corners))
        assertFalse(BingoPattern.ANY_LINE.isComplete(card, corners))
        assertFalse(BingoPattern.X.isComplete(card, corners))
        val cross = called(BingoPattern.X.masks().single())
        assertTrue(BingoPattern.X.isComplete(card, cross))
        assertFalse(BingoPattern.BLACKOUT.isComplete(card, cross))
        assertTrue(BingoPattern.BLACKOUT.isComplete(card, card.numbers.toSet()))
        card.numbers.forEach { assertFalse(BingoPattern.BLACKOUT.isComplete(card, card.numbers.toSet() - it)) }
    }

    @Test fun `draw is unique exhaustive resumable and stops after seventy five calls`() {
        var draw = BingoDraw.shuffled(Random(7))
        assertNull(draw.latest)
        repeat(75) { index ->
            draw = draw.next()
            assertEquals(index + 1, draw.called.toSet().size)
            assertEquals(draw.called.last(), draw.latest)
            draw = Json.decodeFromString<BingoDraw>(Json.encodeToString(draw))
        }
        assertEquals((1..75).toSet(), draw.called.toSet())
        assertTrue(draw.finished)
        assertEquals(draw, draw.next())
    }

    @Test fun `rejects malformed cards draws and out of range calls`() {
        fun rejected(action: () -> Unit) {
            try { action(); fail("Invalid input accepted") } catch (_: IllegalArgumentException) { }
        }
        rejected { card.copy(cells = card.cells.toMutableList().also { it[12] = 40 }) }
        rejected { card.copy(cells = card.cells.toMutableList().also { it[0] = it[5] }) }
        rejected { card.copy(cells = card.cells.toMutableList().also { it[0] = 75 }) }
        rejected { BingoDraw(List(75) { 1 }) }
        rejected { BingoDraw((1..75).toList(), 76) }
        rejected { BingoPattern.ANY_LINE.isComplete(card, setOf(0)) }
        rejected { BingoCardGenerator().deal("p", 7) }
        assertEquals(listOf("B-1", "I-16", "N-31", "G-46", "O-61", "O-75"),
            listOf(1, 16, 31, 46, 61, 75).map(::bingoCallLabel))
    }
}
