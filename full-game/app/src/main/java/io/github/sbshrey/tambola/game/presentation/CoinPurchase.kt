package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.COIN_TICKET_PRICE

/** Keep the player's preference while offering only whole tickets their known wallet can buy. */
fun affordableTickets(preferred: Int, balance: Long?): Int = minOf(preferred.coerceIn(1, 6),
    balance?.let { (it / COIN_TICKET_PRICE).coerceIn(1, 6).toInt() } ?: 6)
