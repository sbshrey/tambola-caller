package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class CoinResultsTest {
    private val pool = CoinPool(4)

    @Test fun `results omit other players wins and unclaimed prizes`() {
        val awards = listOf(Award(Prize.TOP_LINE, 12, listOf("theirs"), listOf("other")))
        assertTrue(ownCoinWins(pool.prizes, awards, setOf("mine")).isEmpty())
        assertTrue(ownCoinWins(pool.prizes, emptyList(), setOf("mine")).isEmpty())
        assertTrue(ownCoinWins(pool.prizes, awards, emptySet()).isEmpty())
    }

    @Test fun `uneven shared prize shows combined own ticket shares not advertised total`() {
        val award = Award(Prize.EARLY_FIVE, 12, listOf("c", "a", "b"), listOf("me", "other"))
        val expected = listOf(CoinWin(CoinPrize(Prize.EARLY_FIVE, 27), shared = true))
        assertEquals(expected, ownCoinWins(pool.prizes, listOf(award), setOf("a", "c")))
        assertEquals(expected, ownCoinWins(pool.prizes, listOf(award.copy(ticketIds = award.ticketIds.reversed())), setOf("c", "a")))
        assertEquals(13L, ownCoinWins(pool.prizes, listOf(award), setOf("b")).single().prize.coins)
    }

    @Test fun `multiple winning tickets owned by one player show the full prize without sharing label`() {
        val award = Award(Prize.CORNERS, 12, listOf("b", "a"), listOf("me"))
        assertEquals(listOf(CoinWin(CoinPrize(Prize.CORNERS, 40), shared = false)),
            ownCoinWins(pool.prizes, listOf(award), setOf("a", "b")))
    }

    @Test fun `a valid winner with no remainder coin still sees their shared prize`() {
        val award = Award(Prize.EARLY_FIVE, 12, listOf("a", "b", "c"), listOf("me", "other"))
        assertEquals(listOf(CoinWin(CoinPrize(Prize.EARLY_FIVE, 0), shared = true)),
            ownCoinWins(listOf(CoinPrize(Prize.EARLY_FIVE, 1)), listOf(award), setOf("c")))
    }

    @Test fun `display totals equal restored engine winnings for each player without counting returned coins`() {
        val plan = CoinPool(8)
        var round = Round.create((1..4).map { Player("p$it", "Player $it") },
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 2, manualClaims = true,
                playAllNumbers = true, prizes = plan.prizes.map { it.prize }), Random(53)).start()
        repeat(90) { round = round.draw() }
        val winners = round.tickets.filter { it.playerId == "p1" } + round.tickets.first { it.playerId == "p2" }
        for (ticket in winners) round = round.claim(ticket.playerId, ticket.numbers.toSet(), ClaimSelection(ticket.id, Prize.EARLY_FIVE.name))
        val other = round.tickets.first { it.playerId == "p3" }
        round = round.claim(other.playerId, other.numbers.toSet(), ClaimSelection(other.id, Prize.TOP_LINE.name)).draw()
        round = RoundCodec.decode(RoundCodec.encode(round))
        val settled = plan.allocations(round)
        assertTrue(settled.any { it.prize == null })
        for (player in round.players) {
            val shown = ownCoinWins(plan.prizes, round.awards, round.tickets.filter { it.playerId == player.id }.map { it.id }.toSet())
            assertEquals(settled.filter { it.playerId == player.id && it.prize != null }.sumOf { it.coins }, shown.sumOf { it.prize.coins })
        }
    }
}
