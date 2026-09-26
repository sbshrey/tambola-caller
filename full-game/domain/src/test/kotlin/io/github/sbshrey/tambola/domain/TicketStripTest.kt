package io.github.sbshrey.tambola.domain

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class TicketStripTest {
    @Test fun `ten thousand physical strips cover ninety numbers exactly once`() {
        repeat(10_000) { seed ->
            val tickets = TicketGenerator(Random(seed.toLong())).deal(listOf(Player("p", "Player")), 6)
            assertEquals((1..90).toList(), tickets.flatMap { it.numbers }.sorted())
            tickets.forEach { ticket ->
                assertEquals(listOf(5, 5, 5), (0..2).map { ticket.row(it).size })
                (0..8).forEach { col ->
                    val numbers = (0..2).map { ticket.cells[it * 9 + col] }.filter { it != 0 }
                    assertTrue(numbers.size in 1..3)
                    assertTrue(numbers.all { it in columnRange(col) })
                    assertEquals(numbers.sorted(), numbers)
                }
            }
        }
    }

    @Test fun `every supported hand size has disjoint numbers for every player`() {
        val players = (1..32).map { Player("p$it", "Player $it") }
        for (count in 1..6) repeat(25) { seed ->
            val tickets = TicketGenerator(Random(seed.toLong())).deal(players, count)
            assertEquals(32 * count, tickets.map { it.fingerprint }.toSet().size)
            players.forEach { player ->
                val hand = tickets.filter { it.playerId == player.id }
                assertEquals(count, hand.size)
                assertEquals(15 * count, hand.flatMap { it.numbers }.toSet().size)
                assertEquals((1..count).map { "${player.id}-$it" }, hand.map { it.id })
            }
        }
    }

    @Test fun `seeded dealing is repeatable but fresh seeds give fresh strips`() {
        val players = listOf(Player("p", "Player"))
        fun deal(seed: Long) = TicketGenerator(Random(seed)).deal(players, 6)
        assertEquals(deal(91), deal(91))
        assertNotEquals(deal(91), deal(92))
        assertEquals(deal(91).take(2), TicketGenerator(Random(91)).deal(players, 2))
    }

    @Test fun `legacy rounds still round trip without redealing tickets`() {
        val old = RoundCodec.decode(javaClass.getResource("/round-v1.json")!!.readText())
        assertEquals(old.tickets, RoundCodec.decode(RoundCodec.encode(old)).tickets)
    }
}
