package io.github.sbshrey.tambola.domain

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class BingoCoinPoolTest {
    private fun fixture(): BingoRound {
        val card = BingoCardGenerator(Random(42)).generate("a-card", "a")
        val players = listOf(Player("a", "A"), Player("b", "B"), Player("c", "C"))
        val line = card.cells.take(5)
        var round = BingoRound(id = "coins", createdAt = 1, players = players,
            cards = players.map { card.copy(id = "${it.id}-card", playerId = it.id) },
            draw = BingoDraw(line + (1..75).filter { it !in line })).start()
        repeat(5) { round = round.next() }
        round.cards.forEach { own -> line.forEach { round = round.mark(own.playerId, own.id, it) } }
        return round
    }

    @Test fun `same-call ties remain unpaid until closure and settle exactly once per player`() {
        val pool = BingoCoinPool(3)
        var round = fixture().claim("a", "a-card", BingoPattern.ANY_LINE)
            .claim("b", "b-card", BingoPattern.ANY_LINE)
        assertTrue(pool.allocations(round).isEmpty())
        round = round.claim("c", "c-card", BingoPattern.ANY_LINE)
        assertTrue(pool.allocations(round).isEmpty())
        round = round.next()
        val awards = pool.allocations(round)
        assertEquals(listOf(20L, 20L, 20L), awards.map { it.coins })
        assertEquals(awards, pool.allocations(round.next()))
        val final = pool.allocations(round.cancel())
        assertEquals(300L, final.sumOf { it.coins })
        assertEquals(240L, final.filter { it.pattern == null }.sumOf { it.coins })
        assertEquals(awards, final.filter { it.pattern != null })
    }

    @Test fun `unclaimed cancellation refunds every purchased card`() {
        val round = BingoRound.practice(Player("me", "Me"), 6, 1, Random(13)).cancel()
        val pool = BingoCoinPool(round.cards.size)
        val refunds = pool.allocations(round)
        assertEquals(pool.coins, refunds.sumOf { it.coins })
        round.players.forEach { player ->
            assertEquals(round.cards.count { it.playerId == player.id } * 100L,
                refunds.single { it.playerId == player.id }.coins)
        }
    }

    @Test fun `policy rejects mismatched card inventory and unknown versions`() {
        assertThrows(IllegalArgumentException::class.java) { BingoCoinPool(2).allocations(fixture()) }
        assertThrows(IllegalArgumentException::class.java) { BingoCoinPool(3, 3) }
        assertThrows(IllegalArgumentException::class.java) { BingoCoinPool(3, 2).allocations(fixture()) }
        assertThrows(IllegalArgumentException::class.java) { BingoCoinPool(301) }
    }

    @Test fun `quick prizes split the full pool across two goals`() {
        val round = fixture().copy(version = 2, winnersPerPattern = 5)
        val pool = BingoCoinPool(3, 2)
        assertEquals(listOf(BingoPattern.ANY_LINE, BingoPattern.FOUR_CORNERS), pool.prizes.map { it.pattern })
        assertEquals(300L, pool.prizes.sumOf { it.coins })
        val claimed = round.claim("a", "a-card", BingoPattern.ANY_LINE).cancel()
        assertEquals(300L, pool.allocations(claimed).sumOf { it.coins })
    }
}
