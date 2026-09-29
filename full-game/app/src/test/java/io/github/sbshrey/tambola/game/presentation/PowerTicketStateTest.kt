package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

class PowerTicketStateTest {
    private val ticket = Round.create(listOf(Player("me", "Player123456")),
        RoundSettings(ticketsPerPlayer = 1), Random(27)).tickets.first()

    @Test fun autoDabExplainsActiveAndExpiredUseEvenWhenInventoryIsEmpty() {
        val powers = MatchPowers(inventory = listOf(MatchPower.AUTO_DAB))
            .activate(ticket, MatchPower.AUTO_DAB, emptyList(), 100_000)
        assertEquals(PowerTicketState.ACTIVE, powers.ticketState(ticket.id, MatchPower.AUTO_DAB, 15))
        assertEquals(PowerTicketState.USED, powers.ticketState(ticket.id, MatchPower.AUTO_DAB, 0))
    }

    @Test fun bonusExplainsArmedThenConsumedState() {
        val powers = MatchPowers(inventory = listOf(MatchPower.PRIZE_BONUS))
            .activate(ticket, MatchPower.PRIZE_BONUS, emptyList(), 100_000)
        assertEquals(PowerTicketState.ARMED, powers.ticketState(ticket.id, MatchPower.PRIZE_BONUS, 0))
        assertEquals(PowerTicketState.USED, powers.won(ticket.id, Prize.EARLY_FIVE)
            .ticketState(ticket.id, MatchPower.PRIZE_BONUS, 0))
    }

    @Test fun discardedTicketTakesPrecedenceAndOtherTicketsRemainAvailable() {
        val powers = MatchPowers(inventory = listOf(MatchPower.AUTO_DAB)).falseClaim(ticket.id)
        assertEquals(PowerTicketState.DISCARDED, powers.ticketState(ticket.id, MatchPower.AUTO_DAB, 15))
        assertEquals(PowerTicketState.AVAILABLE, powers.ticketState("other", MatchPower.AUTO_DAB, 0))
        assertEquals(PowerTicketState.EMPTY, powers.ticketState("other", MatchPower.PRIZE_BONUS, 0))
    }
}
