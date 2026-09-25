package io.github.sbshrey.tambola.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class RuleInspectionTest {
    @Test fun `positive examples satisfy compound and multi ticket rules on varied deals`() {
        val pattern = TicketPattern(listOf(
            listOf(RuleCondition(NumberSelection.Row(0), 2), RuleCondition(NumberSelection.Positions(listOf(0, 4, 10, 14)))),
            listOf(RuleCondition(NumberSelection.Range(60, 90), 3)),
        ))
        val prize = CustomPrize("custom_example", "Example", 25, pattern, minimumTickets = 2, ticketOrdinals = listOf(1, 3))
        repeat(250) { seed ->
            val tickets = TicketGenerator(Random(seed.toLong())).deal(listOf(Player("p", "Player")), 3)
            val example = prize.exampleCalls(tickets)
            assertNotNull(example)
            assertEquals(2, prize.eligibleTickets(tickets, example!!).size)
            assertTrue(prize.eligibleTickets(tickets, emptySet()).isEmpty())
        }
    }

    @Test fun `example refuses impossible sample and progress identifies empty or undersized selection`() {
        val ticket = TicketGenerator(Random(1)).generate("t", "p")
        val absent = (1..90).first { it !in ticket.numbers }
        val condition = RuleCondition(NumberSelection.Range(absent, absent))
        val detail = condition.inspect(ticket, emptySet())
        assertFalse(detail.matches); assertFalse(detail.possible)
        val prize = CustomPrize("custom_absent", "Absent", 10, TicketPattern(listOf(listOf(condition))))
        assertNull(prize.exampleCalls(listOf(ticket)))
        val count = RuleCondition(NumberSelection.Row(0), 6).inspect(ticket, ticket.numbers.toSet())
        assertFalse(count.possible); assertFalse(count.matches); assertEquals(1, count.remaining)
    }

    @Test fun `rule inspection shows exact corners and outstanding count without using marks`() {
        val ticket = TicketGenerator(Random(7)).generate("t", "p")
        val corner = Prize.CORNERS.condition().inspect(ticket, ticket.corners.take(3).toSet())
        assertEquals(ticket.corners, corner.selected)
        assertEquals(listOf(ticket.corners.last()), corner.uncalled)
        assertEquals(1, corner.remaining); assertFalse(corner.matches)
        val five = Prize.EARLY_FIVE.condition().inspect(ticket, ticket.numbers.take(7).toSet())
        assertTrue(five.matches); assertEquals(0, five.remaining); assertEquals(5, five.required)
    }
}
