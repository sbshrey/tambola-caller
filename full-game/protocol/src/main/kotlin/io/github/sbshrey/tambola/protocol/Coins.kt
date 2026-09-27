@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.sbshrey.tambola.protocol

import io.github.sbshrey.tambola.domain.COIN_TICKET_PRICE
import io.github.sbshrey.tambola.domain.CoinPrize
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault

/** Spendable balance only; pending same-call prize shares are excluded. */
@Serializable data class WalletView(
    val balance: Long,
    val revision: Long,
    val refillAfter: Long,
    val ticketPrice: Long = COIN_TICKET_PRICE,
)
@Serializable data class RefillRequest(val id: String)
@Serializable data class MatchRequest(
    val id: String, val tickets: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val friendTable: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val friendCode: String? = null,
) {
    init {
        require(tickets in 1..6)
        require(friendCode == null || (friendTable && friendCode.matches(Regex("[A-HJ-NP-Z2-9]{8}"))))
    }
}
@Serializable data class CoinTableView(
    val tickets: Int,
    val pool: Long,
    val prizes: List<CoinPrize>,
    val ownTickets: Int,
    val startsAt: Long?,
    val settledWinnings: Long = 0,
    val returnedCoins: Long = 0,
    // Omitted for quick play so already installed protocol-v4 apps keep decoding it.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val friendTable: Boolean = false,
)
