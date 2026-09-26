package io.github.sbshrey.tambola.game.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class CoinPurchaseTest {
    @Test fun `whole ticket affordability never overspends or overflows and preserves smaller choices`() {
        assertEquals(6, affordableTickets(6, null))
        assertEquals(6, affordableTickets(6, Long.MAX_VALUE))
        assertEquals(2, affordableTickets(6, 299))
        assertEquals(3, affordableTickets(6, 300))
        assertEquals(1, affordableTickets(1, 1500))
        assertEquals(1, affordableTickets(6, 99)) // Refill replaces Play below one ticket.
        assertEquals(5, affordableTickets(6, 500))
    }
}
