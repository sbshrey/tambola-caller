package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*

data class CoinWin(val prize: CoinPrize, val shared: Boolean)

/** Finished-round display uses owned ticket IDs; it never needs another player's ticket grid. */
fun ownCoinWins(prizes: List<CoinPrize>, awards: List<Award>, ownTicketIds: Set<String>): List<CoinWin> =
    prizes.mapNotNull { slot ->
        val award = awards.firstOrNull { it.prize == slot.prize } ?: return@mapNotNull null
        if (award.ticketIds.none { it in ownTicketIds }) return@mapNotNull null
        val amount = coinShares(slot.coins, award.ticketIds).filterKeys { it in ownTicketIds }.values.sum()
        CoinWin(slot.copy(coins = amount), award.ticketIds.any { it !in ownTicketIds })
    }
