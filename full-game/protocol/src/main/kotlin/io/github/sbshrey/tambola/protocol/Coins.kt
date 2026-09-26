package io.github.sbshrey.tambola.protocol

import io.github.sbshrey.tambola.domain.COIN_TICKET_PRICE
import io.github.sbshrey.tambola.domain.CoinPrize
import kotlinx.serialization.Serializable

/** Spendable balance only; pending same-call prize shares are excluded. */
@Serializable data class WalletView(
    val balance: Long,
    val revision: Long,
    val refillAfter: Long,
    val ticketPrice: Long = COIN_TICKET_PRICE,
)
@Serializable data class RefillRequest(val id: String)
@Serializable data class MatchRequest(val id: String, val tickets: Int) {
    init { require(tickets in 1..6) }
}
@Serializable data class CoinTableView(
    val tickets: Int,
    val pool: Long,
    val prizes: List<CoinPrize>,
    val ownTickets: Int,
    val startsAt: Long?,
    val settledWinnings: Long = 0,
    val returnedCoins: Long = 0,
)
