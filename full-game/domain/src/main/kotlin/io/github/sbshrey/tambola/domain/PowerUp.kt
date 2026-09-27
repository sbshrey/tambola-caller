package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable

@Serializable enum class PowerUp { NONE, TICKET_INSURANCE, PRIZE_BOOST }

/** Promotional coins are separate from the shared prize pool and paid only once at completion. */
fun CoinPool.powerUpBonus(round: Round, playerId: String, powerUp: PowerUp): Long {
    if (version != 2 || round.status != RoundStatus.COMPLETED || powerUp == PowerUp.NONE) return 0
    require(round.players.any { it.id == playerId && !it.computer })
    val paid = allocations(round).filter { it.playerId == playerId }
    val winnings = paid.filter { it.prize != null }.sumOf { it.coins }
    return when (powerUp) {
        PowerUp.NONE -> 0
        PowerUp.PRIZE_BOOST -> winnings / 4
        PowerUp.TICKET_INSURANCE -> if (winnings > 0) 0 else
            (round.tickets.count { it.playerId == playerId } * COIN_TICKET_PRICE - paid.filter { it.prize == null }.sumOf { it.coins }).coerceAtLeast(0)
    }
}
