package io.github.sbshrey.tambola.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class MatchPowersTest {
    @Test fun `preview grants the shown power only on fifth unique mark and replaces it once`() {
        var rolls = 0
        var powers = MatchPowers(nextPower = MatchPower.SHIELD)
        val numbers = ticket.numbers
        numbers.take(4).forEach { powers = powers.mark(ticket, it, numbers) { error("Too early") } }
        assertEquals(MatchPower.SHIELD, powers.nextPower)
        assertTrue(powers.inventory.isEmpty())
        powers = powers.mark(ticket, numbers[4], numbers) { rolls++; MatchPower.AUTO_DAB }
        assertEquals(listOf(MatchPower.SHIELD), powers.inventory)
        assertEquals(MatchPower.AUTO_DAB, powers.nextPower)
        assertEquals(powers, powers.mark(ticket, numbers[4], numbers) { error("Duplicate rerolled") })
        assertEquals(1, rolls)
        val full = powers.copy(inventory = listOf(MatchPower.SHIELD, MatchPower.SHIELD))
        var blocked = full
        numbers.drop(5).take(5).forEach { blocked = blocked.mark(ticket, it, numbers) { error("Full inventory rerolled") } }
        assertEquals(MatchPower.AUTO_DAB, blocked.nextPower)
        assertEquals(PowerNotice.FULL, blocked.notice)
    }

    @Test fun `new shield must be armed and protects its own ticket exactly once`() {
        val ready = MatchPowers(nextPower = MatchPower.AUTO_DAB, inventory = listOf(MatchPower.SHIELD))
        assertEquals(setOf(ticket.id), ready.falseClaim(ticket.id).discarded)
        val armed = ready.activate(ticket, MatchPower.SHIELD, emptyList(), 1)
        assertEquals(setOf(ticket.id), armed.armedShield)
        assertTrue(armed.inventory.isEmpty())
        assertEquals(setOf(tickets.last().id), armed.falseClaim(tickets.last().id).discarded)
        val protected = armed.falseClaim(ticket.id)
        assertTrue(protected.discarded.isEmpty())
        assertTrue(protected.armedShield.isEmpty())
        assertEquals(PowerNotice.SHIELD_SAVED, protected.notice)
        assertEquals(setOf(ticket.id), protected.falseClaim(ticket.id).discarded)
        assertThrows(IllegalArgumentException::class.java) {
            protected.copy(inventory = listOf(MatchPower.SHIELD)).activate(ticket, MatchPower.SHIELD, emptyList(), 2)
        }
    }
    private val tickets = Round.create(listOf(Player("p", "Mira")), RoundSettings(ticketsPerPlayer = 2, manualClaims = true), Random(51)).tickets
    private val ticket = tickets.first()
    @Test fun `five unique correct dabs drop once and a full inventory skips a milestone`() {
        var powers = MatchPowers()
        var rolls = 0
        val drop = { rolls++; MatchPower.SHIELD }
        ticket.numbers.forEach { number ->
            powers = powers.mark(ticket, number, ticket.numbers.toList(), drop)
            assertEquals(powers, powers.mark(ticket, number, ticket.numbers.toList(), drop))
        }
        assertEquals(15, powers.correctMarks); assertEquals(2, rolls)
        assertEquals(2, powers.inventory.size); assertEquals(PowerNotice.FULL, powers.notice)
        assertThrows(IllegalArgumentException::class.java) { MatchPowers().mark(ticket, ticket.numbers.first(), emptyList(), drop) }
    }
    @Test fun `shield saves once per ticket and later false claims discard only that ticket`() {
        val shield = MatchPowers(inventory = listOf(MatchPower.SHIELD, MatchPower.SHIELD)).falseClaim(ticket.id)
        assertTrue(shield.discarded.isEmpty()); assertEquals(1, shield.inventory.size)
        val discarded = shield.falseClaim(ticket.id)
        assertEquals(setOf(ticket.id), discarded.discarded)
        assertEquals(1, discarded.inventory.size)
        assertTrue(discarded.falseClaim(tickets.last().id).discarded == setOf(ticket.id))
        assertThrows(IllegalArgumentException::class.java) { discarded.mark(ticket, ticket.numbers.first(), ticket.numbers.toList()) { MatchPower.SHIELD } }
    }
    @Test fun `auto dab expires at exact server deadline and never grants manual progress`() {
        val numbers = ticket.numbers.toList()
        val active = MatchPowers(inventory = listOf(MatchPower.AUTO_DAB)).activate(ticket, MatchPower.AUTO_DAB, numbers.take(3), 100_000)
        assertEquals(3, active.marks.getValue(ticket.id).size)
        val before = active.autoMark(tickets, numbers.take(7), 134_999)
        assertEquals(7, before.marks.getValue(ticket.id).size)
        assertEquals(before, before.autoMark(tickets, numbers, 135_000))
        assertEquals(before, before.mark(ticket, numbers.first(), numbers) { error("Auto marks must not drop powers") })
        assertEquals(0, before.correctMarks)
        assertThrows(IllegalArgumentException::class.java) { before.copy(inventory = listOf(MatchPower.AUTO_DAB)).activate(ticket, MatchPower.AUTO_DAB, numbers, 200_000) }
    }
    @Test fun `bonus pays final ticket share only once without changing the fixed pool`() {
        val powers = MatchPowers(inventory = listOf(MatchPower.PRIZE_BONUS)).activate(ticket, MatchPower.PRIZE_BONUS, emptyList(), 1)
            .won(ticket.id, Prize.TOP_LINE).won(ticket.id, Prize.FULL_HOUSE)
        assertEquals(mapOf(Prize.TOP_LINE to ticket.id), powers.bonusPrizes)
        val awards = listOf(Award(Prize.TOP_LINE, 30, listOf(ticket.id, "other"), listOf("p", "q")))
        val prizes = listOf(CoinPrize(Prize.TOP_LINE, 100))
        assertEquals(0L, matchPowerBonus(powers, awards, prizes, false))
        assertEquals(12L, matchPowerBonus(powers, awards, prizes, true))
        assertEquals(100L, coinShares(100, awards.single().ticketIds).values.sum())
    }
}
