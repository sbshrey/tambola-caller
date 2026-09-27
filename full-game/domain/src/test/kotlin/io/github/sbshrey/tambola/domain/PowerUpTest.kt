package io.github.sbshrey.tambola.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class PowerUpTest {
    @Test fun `insurance covers only net entry loss and boost leaves prize shares unchanged`() {
        val pool = CoinPool(3, 2)
        var round = Round.create(List(3) { Player("p$it", "Player $it") }, RoundSettings(mode = GameMode.ONLINE,
            manualClaims = true, prizes = pool.prizes.map { it.prize }, winnersPerPrize = 2), Random(14)).start()
        repeat(90) { round = round.draw() }
        listOf("p0", "p1").forEach { player ->
            val ticket = round.tickets.first { it.playerId == player }
            round = round.claim(player, ticket.numbers.toSet(), ClaimSelection(ticket.id, Prize.EARLY_FIVE.name))
        }
        assertEquals(0L, pool.powerUpBonus(round, "p0", PowerUp.PRIZE_BOOST))
        assertEquals(0L, pool.powerUpBonus(round.cancel(), "p2", PowerUp.TICKET_INSURANCE))
        round = round.draw()
        val allocations = pool.allocations(round)
        assertEquals(300L, allocations.sumOf { it.coins })
        assertEquals(3L, pool.powerUpBonus(round, "p0", PowerUp.PRIZE_BOOST))
        assertEquals(0L, pool.powerUpBonus(round, "p0", PowerUp.TICKET_INSURANCE))
        assertEquals(10L, pool.powerUpBonus(round, "p2", PowerUp.TICKET_INSURANCE))
        assertEquals(0L, pool.powerUpBonus(round, "p2", PowerUp.PRIZE_BOOST))
        assertEquals(allocations, pool.allocations(round))
    }
}
