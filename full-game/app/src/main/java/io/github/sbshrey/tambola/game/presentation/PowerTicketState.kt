package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.MatchPower
import io.github.sbshrey.tambola.domain.MatchPowers

internal enum class PowerTicketState { AVAILABLE, DISCARDED, ACTIVE, ARMED, USED, EMPTY }

/** Explain the server-owned ticket state before the shared inventory state. */
internal fun MatchPowers.ticketState(ticketId: String, power: MatchPower, remainingSeconds: Long): PowerTicketState = when {
    ticketId in discarded -> PowerTicketState.DISCARDED
    power == MatchPower.AUTO_DAB && remainingSeconds > 0 -> PowerTicketState.ACTIVE
    power == MatchPower.PRIZE_BONUS && ticketId in armedBonus -> PowerTicketState.ARMED
    power in used[ticketId].orEmpty() -> PowerTicketState.USED
    power !in inventory -> PowerTicketState.EMPTY
    else -> PowerTicketState.AVAILABLE
}
