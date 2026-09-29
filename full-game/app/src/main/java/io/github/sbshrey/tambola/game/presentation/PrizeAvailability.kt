package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.Award
import io.github.sbshrey.tambola.domain.RoundSettings
import io.github.sbshrey.tambola.domain.isClosed

enum class PrizePlaceState { OPEN, TIES_OPEN, FULL, OWNED }

data class PrizeAvailability(val remaining: Int, val total: Int, val state: PrizePlaceState)

/** Confirmed awards only. A pending command never reserves a winning place. */
fun prizeAvailability(award: Award?, settings: RoundSettings, currentDraw: Int, alreadyWon: Boolean): PrizeAvailability {
    val remaining = (settings.winnersPerPrize - award?.playerIds.orEmpty().distinct().size).coerceAtLeast(0)
    val state = when {
        alreadyWon -> PrizePlaceState.OWNED
        award?.isClosed(settings, currentDraw) == true -> PrizePlaceState.FULL
        remaining == 0 -> PrizePlaceState.TIES_OPEN
        else -> PrizePlaceState.OPEN
    }
    return PrizeAvailability(remaining, settings.winnersPerPrize, state)
}
