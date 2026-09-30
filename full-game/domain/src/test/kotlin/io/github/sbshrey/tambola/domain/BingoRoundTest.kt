package io.github.sbshrey.tambola.domain

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class BingoRoundTest {
    private val template = BingoCardGenerator(Random(42)).generate("a-card", "a")
    private val players = listOf(Player("a", "A"), Player("b", "B"), Player("c", "C"))
    private val cards = players.map { template.copy(id = "${it.id}-card", playerId = it.id) }
    private val line = (0..4).map { template.cells[it] }
    private fun round() = BingoRound(id = "test", createdAt = 1, players = players, cards = cards,
        draw = BingoDraw(line + (1..75).filter { it !in line }), winnersPerPattern = 2).start()
    private fun lineRound(): BingoRound {
        var round = round()
        repeat(5) { round = round.next() }
        cards.forEach { card -> line.forEach { round = round.mark(card.playerId, card.id, it) } }
        return round
    }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Invalid action accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun `claims enforce called marks ownership and once per player per category`() {
        var round = round()
        rejected { round.mark("a", "a-card", line.first()) }
        round = round.next()
        rejected { round.mark("b", "a-card", line.first()) }
        rejected { round.claim("a", "a-card", BingoPattern.ANY_LINE) }
        round = lineRound()
        rejected { round.claim("b", "a-card", BingoPattern.ANY_LINE) }
        round = round.claim("a", "a-card", BingoPattern.ANY_LINE)
        assertEquals(round, round.claim("a", "a-card", BingoPattern.ANY_LINE))
        assertEquals(1, round.remaining(BingoPattern.ANY_LINE))
    }

    @Test fun `quota closes on next draw and ties share an exact point pool`() {
        var round = lineRound()
        players.forEach { round = round.claim(it.id, "${it.id}-card", BingoPattern.ANY_LINE) }
        assertEquals(0, round.remaining(BingoPattern.ANY_LINE))
        assertFalse(round.closed(BingoPattern.ANY_LINE))
        assertEquals(listOf(7, 7, 6), players.map { round.points(it.id) })
        assertEquals(20, players.sumOf { round.points(it.id) })
        round = round.next()
        assertTrue(round.closed(BingoPattern.ANY_LINE))
        val late = lineRound().claim("a", "a-card", BingoPattern.ANY_LINE)
            .claim("b", "b-card", BingoPattern.ANY_LINE).next()
        rejected { late.claim("c", "c-card", BingoPattern.ANY_LINE) }
    }

    @Test fun `pause resume and serialization preserve marks awards and remaining draw`() {
        val round = lineRound().claim("a", "a-card", BingoPattern.ANY_LINE).pause()
        assertEquals(round, round.next())
        rejected { round.mark("a", "a-card", line.first()) }
        val restored = BingoRoundCodec.decode(BingoRoundCodec.encode(round))
        assertEquals(round, restored)
        assertEquals(round.draw.count + 1, restored.start().next().draw.count)
        assertEquals(round.claims, restored.claims)
    }

    @Test fun `last call retains claim window until next tick`() {
        var round = round()
        repeat(75) { round = round.next() }
        assertFalse(round.finished)
        template.numbers.forEach { round = round.mark("a", "a-card", it) }
        round = round.claim("a", "a-card", BingoPattern.BLACKOUT)
        assertEquals(40, round.points("a"))
        round = round.next()
        assertTrue(round.finished)
        assertEquals(round, round.next())
        rejected { round.claim("b", "b-card", BingoPattern.BLACKOUT) }
    }

    @Test fun `practice populations vary and complete with ranked conserved awards`() {
        val sizes = mutableSetOf<Int>()
        val counts = mutableSetOf<Int>()
        repeat(20) { seed ->
            var round = BingoRound.practice(Player("me", "Me"), 6, 1, Random(seed.toLong())).start()
            sizes += round.players.size
            assertTrue(round.players.size in 30..50)
            assertEquals(6, round.cards.count { it.playerId == "me" })
            round.players.forEach { counts += round.cards.count { c -> c.playerId == it.id } }
            repeat(76) { round = round.next().playComputers() }
            assertTrue(round.finished)
            assertTrue(round.marks.keys.none { key -> round.cards.single { it.id == key }.playerId == "me" })
            assertEquals(100, round.players.sumOf { round.points(it.id) })
            val scores = round.ranking().map { round.points(it.id) }
            assertEquals(scores.sortedDescending(), scores)
            assertEquals(round, BingoRoundCodec.decode(BingoRoundCodec.encode(round)))
        }
        assertTrue(sizes.size > 1)
        assertEquals((1..6).toSet(), counts)
    }

    @Test fun `tampered saves cannot forge marks or historical awards`() {
        val round = lineRound()
        rejected { round.copy(marks = round.marks + ("a-card" to setOf(99))) }
        rejected { round.copy(claims = listOf(BingoClaim("a", "b-card", BingoPattern.ANY_LINE, 5))) }
        rejected { round.copy(claims = listOf(BingoClaim("a", "a-card", BingoPattern.BLACKOUT, 5))) }
        rejected { round.copy(version = 2) }
    }
}
