package io.github.sbshrey.tambola.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class CoinPoolTest {
    @Test fun `quick Tambola has two funded goals and finishes at its call limit`() {
        val pool = CoinPool(2, 2, quickTambola = true)
        assertEquals(listOf(Prize.EARLY_FIVE, Prize.ANY_LINE), pool.prizes.map { it.prize })
        assertEquals(pool.coins, pool.prizes.sumOf { it.coins })
        val players = listOf(Player("one", "One"), Player("two", "Two"))
        var round = Round.create(players, RoundSettings(mode = GameMode.ONLINE, manualClaims = true, assistedMarking = true,
            prizes = pool.prizes.map { it.prize }, winnersPerPrize = 2, maxCalls = 10), Random(41)).start()
        val ticket = round.tickets.first()
        val oneEach = listOf(Prize.EARLY_FIVE, Prize.ANY_LINE).map { Award(it, 1, listOf(ticket.id), listOf("one")) }
        assertFalse(round.terminalAward(oneEach, emptyList()))
        assertTrue(round.terminalAward(oneEach.map { it.copy(playerIds = listOf("one", "two")) }, emptyList()))
        assertTrue(Prize.ANY_LINE.matches(ticket, ticket.row(1).toSet()))
        assertFalse(Prize.ANY_LINE.matches(ticket, ticket.row(1).take(4).toSet()))
        repeat(10) { round = round.draw() }
        assertFalse(round.finished)
        round = round.draw()
        assertTrue(round.finished)
        assertEquals(10, round.called.size)
        assertEquals(pool.coins, pool.allocations(round).sumOf { it.coins })
        assertEquals(round, RoundCodec.decode(RoundCodec.encode(round)))
    }
    @Test fun `independent ticket purchases preserve disjoint hands and round restoration`() {
        val players = (1..6).map { Player("p$it", "Player $it") }
        val counts = players.mapIndexed { index, player -> player.id to index + 1 }.toMap()
        val plan = CoinPool(counts.values.sum())
        val round = Round.create(players, RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6,
            manualClaims = true, prizes = plan.prizes.map { it.prize }), Random(123), ticketCounts = counts)
        assertEquals(5, round.version)
        assertEquals(21, round.tickets.size)
        players.forEach { player ->
            val hand = round.tickets.filter { it.playerId == player.id }
            assertEquals(counts[player.id], hand.size)
            assertEquals(hand.size * 15, hand.flatMap { it.numbers }.distinct().size)
        }
        assertEquals(round, RoundCodec.decode(RoundCodec.encode(round)))
        assertThrows(IllegalArgumentException::class.java) { round.copy(ticketCounts = counts + ("p1" to 0)).validated() }
        assertThrows(IllegalArgumentException::class.java) { round.copy(ticketCounts = counts - "p1").validated() }
        assertThrows(IllegalArgumentException::class.java) { round.copy(version = 4).validated() }
    }
    @Test fun `every allowed ticket count allocates exactly the pool and unlocks only fixed house thresholds`() {
        for (count in 2..192) {
            val plan = CoinPool(count)
            assertEquals(count * 100L, plan.coins)
            assertEquals(plan.coins, plan.prizes.sumOf { it.coins })
            assertEquals(when { count < 12 -> 6; count < 24 -> 7; else -> 8 }, plan.prizes.size)
            assertTrue(plan.prizes.all { it.coins > 0 })
            assertEquals(plan.prizes.size, plan.prizes.map { it.prize }.distinct().size)
        }
        assertThrows(IllegalArgumentException::class.java) { CoinPool(1) }
        assertThrows(IllegalArgumentException::class.java) { CoinPool(193) }
        assertThrows(IllegalArgumentException::class.java) { CoinPool(2, 3) }
        for (count in 2..300) {
            val plan = CoinPool(count, 2)
            assertEquals(6, plan.prizes.size)
            assertEquals(plan.coins, plan.prizes.sumOf { it.coins })
        }
    }

    private fun game(tickets: Int = 2, players: Int = 3): Pair<CoinPool, Round> {
        val plan = CoinPool(tickets * players)
        var round = Round.create((1..players).map { Player("p$it", "Player $it") },
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = tickets, prizes = plan.prizes.map { it.prize },
                manualClaims = true, playAllNumbers = true), Random(27)).start()
        repeat(90) { round = round.draw() }
        return plan to round
    }
    private fun Round.choose(playerId: String, prize: Prize): Round {
        val ticket = tickets.first { it.playerId == playerId }
        return claim(playerId, ticket.numbers.toSet(), ClaimSelection(ticket.id, prize.name))
    }

    @Test fun `ties stay provisional and distribute every coin independently of claim arrival`() {
        val (plan, round) = game(tickets = 1)
        val forward = listOf("p1", "p2", "p3").fold(round) { next, id -> next.choose(id, Prize.EARLY_FIVE) }
        val reverse = listOf("p3", "p2", "p1").fold(round) { next, id -> next.choose(id, Prize.EARLY_FIVE) }
        assertTrue(plan.allocations(forward).isEmpty())
        val paid = plan.allocations(forward.draw())
        assertEquals(paid, plan.allocations(reverse.draw()))
        assertEquals(plan.coins, paid.sumOf { it.coins })
        assertEquals(30L, paid.filter { it.prize == Prize.EARLY_FIVE }.sumOf { it.coins })
        assertEquals(paid.size, paid.map { it.key }.distinct().size)
        assertEquals(paid, plan.allocations(RoundCodec.decode(RoundCodec.encode(forward.draw()))))
    }

    @Test fun `round cancellation returns only unawarded pool and never duplicates paid winnings`() {
        val (plan, round) = game()
        val claimed = round.choose("p1", Prize.TOP_LINE).choose("p2", Prize.TOP_LINE).cancel()
        val paid = plan.allocations(claimed)
        assertEquals(plan.coins, paid.sumOf { it.coins })
        assertEquals(60L, paid.filter { it.prize == Prize.TOP_LINE }.sumOf { it.coins })
        assertEquals(540L, paid.filter { it.prize == null }.sumOf { it.coins })
        assertEquals(plan.coins, plan.allocations(round.cancel()).sumOf { it.coins })
    }

    @Test fun `an uneven tie gives deterministic remainder without creating or losing coins`() {
        val (plan, round) = game(tickets = 1, players = 4)
        val claimed = listOf("p1", "p2", "p3").fold(round) { next, id -> next.choose(id, Prize.EARLY_FIVE) }.draw()
        val wins = plan.allocations(claimed).filter { it.prize == Prize.EARLY_FIVE }
        assertEquals(listOf(14L, 13L, 13L), wins.map { it.coins })
        assertEquals(plan.coins, plan.allocations(claimed).sumOf { it.coins })
    }

    @Test fun `pool cannot be reused with a different purchase count or prize schedule`() {
        val (plan, round) = game()
        assertThrows(IllegalArgumentException::class.java) { CoinPool(plan.soldTickets + 1).allocations(round) }
        assertThrows(IllegalArgumentException::class.java) { plan.allocations(round.copy(settings = round.settings.copy(prizes = listOf(Prize.FULL_HOUSE)))) }
    }
}
