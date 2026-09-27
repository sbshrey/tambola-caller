package io.github.sbshrey.tambola.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ExpandedPrizeTest {
    private fun game(players: Int = 4, winners: Int = 2, tickets: Int = 1): Round {
        val pool = CoinPool(players * tickets, 2)
        return Round.create(List(players) { Player("p$it", "Player $it") },
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = tickets, manualClaims = true,
                prizes = pool.prizes.map { it.prize }, winnersPerPrize = winners), Random(88), now = 1).start()
    }
    private fun claim(round: Round, player: String, prize: Prize = Prize.EARLY_FIVE): Round {
        val ticket = round.tickets.first { it.playerId == player }
        return round.claim(player, ticket.numbers.filter { it in round.called }.toSet(), ClaimSelection(ticket.id, prize.name))
    }
    @Test fun `category stays open across calls then includes all final-call ties`() {
        var round = game(tickets = 2)
        while (round.players.any { p -> round.tickets.first { it.playerId == p.id }.numbers.count { it in round.called } < 5 }) round = round.draw()
        round = claim(round, "p0")
        val pool = CoinPool(round.tickets.size, 2)
        assertTrue(pool.allocations(round).isEmpty())
        round = round.draw()
        assertTrue(pool.allocations(round).isEmpty())
        // Buying more tickets cannot take a second winner's share of this category.
        val second = round.tickets.filter { it.playerId == "p0" }[1]
        assertEquals(round, round.claim("p0", second.numbers.filter { it in round.called }.toSet(), ClaimSelection(second.id, Prize.EARLY_FIVE.name)))
        round = claim(round, "p1")
        round = claim(round, "p2")
        assertEquals(3, round.awards.single().playerIds.size)
        assertTrue(pool.allocations(round).isEmpty())
        round = round.draw()
        val closed = round
        assertEquals(closed, claim(round, "p3"))
        val paid = pool.allocations(round)
        assertEquals(pool.prizes.first().coins, paid.sumOf { it.coins })
        assertTrue(paid.maxOf { it.coins } - paid.minOf { it.coins } <= 1)
        assertEquals(round, RoundCodec.decode(RoundCodec.encode(round)))
        assertEquals(paid, pool.allocations(round.draw()))
    }
    @Test fun `fifty players have six fixed categories and conserve the entire pool`() {
        var round = game(players = 50, winners = 5, tickets = 6)
        val pool = CoinPool(300, 2)
        assertEquals(6, pool.prizes.size)
        assertEquals(30_000L, pool.prizes.sumOf { it.coins })
        val settled = mutableMapOf<String, CoinAllocation>()
        while (!round.finished) {
            round = round.draw()
            if (!round.finished) round.players.forEach { player ->
                pool.prizes.forEach { slot -> round = claim(round, player.id, slot.prize) }
            }
            pool.allocations(round).forEach { allocation ->
                settled[allocation.key]?.let { assertEquals(it, allocation) }
                settled[allocation.key] = allocation
            }
        }
        assertEquals(6, round.awards.size)
        assertTrue(round.awards.all { it.playerIds.size >= 5 && it.playerIds.size == it.ticketIds.size })
        assertEquals(pool.coins, settled.values.sumOf { it.coins })
        assertEquals(round, RoundCodec.decode(RoundCodec.encode(round)))
    }
    @Test fun `house needs several winners and cancellation refunds unawarded coins`() {
        var round = game()
        repeat(90) { round = round.draw() }
        round = claim(round, "p0", Prize.FULL_HOUSE)
        assertFalse(round.finished)
        val cancelled = round.cancel()
        assertEquals(400L, CoinPool(4, 2).allocations(cancelled).sumOf { it.coins })
    }
}
