package io.github.sbshrey.tambola.protocol

import io.github.sbshrey.tambola.domain.COIN_TICKET_PRICE
import kotlinx.serialization.Serializable

/** Spendable balance only; pending same-call prize shares are excluded. */
@Serializable data class WalletView(
    val balance: Long,
    val revision: Long,
    val refillAfter: Long,
    val ticketPrice: Long = COIN_TICKET_PRICE,
)
@Serializable data class RefillRequest(val id: String)
