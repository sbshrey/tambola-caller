package io.github.sbshrey.tambola.domain

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class CustomPrizeTest {
    private fun rule(pattern: TicketPattern, minimumTickets: Int = 1, ordinals: List<Int> = emptyList()) =
        CustomPrize("custom_pattern", "Our pattern", 25, pattern, minimumTickets = minimumTickets, ticketOrdinals = ordinals)
    private fun pattern(vararg clauses: RuleCondition) = TicketPattern(listOf(clauses.toList()))
    private val ticket = TicketGenerator(Random(22)).generate("p-1", "p")

    @Test fun `empty ranges never win and selected rows columns positions are exact`() {
        val absent = (1..90).first { it !in ticket.numbers }
        assertFalse(RuleCondition(NumberSelection.Range(absent, absent)).matches(ticket, (1..90).toSet()))
        assertEquals(ticket.row(1), NumberSelection.Row(1).numbers(ticket))
        assertEquals(ticket.numbers.filter { it in 40..49 }, NumberSelection.Column(4).numbers(ticket))
        assertEquals(ticket.corners, NumberSelection.Positions(listOf(0, 4, 10, 14)).numbers(ticket))
        val condition = RuleCondition(NumberSelection.Positions(listOf(0, 4, 10, 14)))
        assertTrue(condition.matches(ticket, ticket.corners.toSet()))
        ticket.corners.forEach { assertFalse(condition.matches(ticket, ticket.corners.toSet() - it)) }
    }
    @Test fun `AND and OR groups have explicit nonvacuous behavior`() {
        val top = RuleCondition(NumberSelection.Row(0))
        val middle = RuleCondition(NumberSelection.Row(1))
        val bottom = RuleCondition(NumberSelection.Row(2))
        val rule = TicketPattern(listOf(listOf(top, middle), listOf(bottom)))
        assertFalse(rule.matches(ticket, ticket.row(0).toSet()))
        assertTrue(rule.matches(ticket, (ticket.row(0) + ticket.row(1)).toSet()))
        assertTrue(rule.matches(ticket, ticket.row(2).toSet()))
        assertFalse(RuleCondition(NumberSelection.All, 5).matches(ticket, ticket.numbers.take(4).toSet()))
    }
    @Test fun `multi ticket rules restrict the selected owned ordinals`() {
        val tickets = TicketGenerator(Random(7)).deal(listOf(Player("p", "Player")), 3)
        val prize = rule(pattern(RuleCondition(NumberSelection.All)), minimumTickets = 2, ordinals = listOf(1, 3))
        assertTrue(prize.eligibleTickets(tickets, (tickets[0].numbers + tickets[1].numbers).toSet()).isEmpty())
        assertEquals(listOf(tickets[0], tickets[2]), prize.eligibleTickets(tickets, (tickets[0].numbers + tickets[2].numbers).toSet()))
    }
    @Test fun `custom awards score once per player survive saves and undo on their call`() {
        val prize = rule(pattern(RuleCondition(NumberSelection.All, 1)))
        var round = Round.create(listOf(Player("a", "Asha"), Player("b", "Bina")), RoundSettings(ticketsPerPlayer = 6, customPrizes = listOf(prize)), Random(19), 1).start()
        while (round.customAwards.isEmpty()) round = round.draw()
        val award = round.customAwards.single()
        award.playerIds.forEach { assertEquals(25, round.score(it)) }
        assertEquals(round, RoundCodec.decode(RoundCodec.encode(round)))
        assertTrue(round.undo().customAwards.isEmpty())
        assertEquals(award, round.undo().start().draw().customAwards.single())
        assertThrows(IllegalArgumentException::class.java) { round.copy(customAwards = listOf(award.copy(ruleVersion = 2))).validated() }
    }
    @Test fun `malformed or unbounded custom rules fail closed`() {
        assertThrows(IllegalArgumentException::class.java) { TicketPattern(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { TicketPattern(listOf(emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { TicketPattern(List(5) { listOf(RuleCondition(NumberSelection.All)) }) }
        assertThrows(IllegalArgumentException::class.java) { NumberSelection.Positions(listOf(1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { NumberSelection.Range(50, 1) }
        assertThrows(IllegalArgumentException::class.java) { RuleCondition(NumberSelection.All, 0) }
        assertThrows(IllegalArgumentException::class.java) { RoundSettings(customPrizes = listOf(rule(pattern(RuleCondition(NumberSelection.All)), 2))) }
    }
    @Test fun `version one save without new fields migrates without changing calls or marks`() {
        val raw = checkNotNull(javaClass.getResource("/round-v1.json")).readText()
        val round = RoundCodec.decode(raw)
        assertEquals(3, round.version)
        assertEquals(listOf(1, 2, 3, 4), round.called)
        assertEquals(setOf(1), round.marks["p0-1"])
        assertTrue(round.settings.customPrizes.isEmpty())
        assertEquals(RoundStatus.PAUSED, round.status)
        assertEquals(round, RoundCodec.decode(RoundCodec.encode(round)))
    }
}
