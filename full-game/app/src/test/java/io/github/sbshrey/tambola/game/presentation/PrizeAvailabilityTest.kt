package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*
import org.junit.Assert.*
import org.junit.Test

class PrizeAvailabilityTest {
    private val settings = RoundSettings(manualClaims = true, winnersPerPrize = 3)
    private fun award(count: Int) = Award(Prize.EARLY_FIVE, 12, (1..count).map { "t$it" }, (1..count).map { "p$it" })

    @Test fun `unclaimed and partially claimed prizes retain remaining places across calls`() {
        assertEquals(PrizeAvailability(3, 3, PrizePlaceState.OPEN), prizeAvailability(null, settings, 12, false))
        assertEquals(PrizeAvailability(2, 3, PrizePlaceState.OPEN), prizeAvailability(award(1), settings, 13, false))
    }

    @Test fun `quota and oversubscribed ties close only on the next call`() {
        for (count in 3..5) {
            assertEquals(PrizeAvailability(0, 3, PrizePlaceState.TIES_OPEN), prizeAvailability(award(count), settings, 12, false))
            assertEquals(PrizeAvailability(0, 3, PrizePlaceState.FULL), prizeAvailability(award(count), settings, 13, false))
        }
    }

    @Test fun `own win takes precedence over open ties and closed prize`() {
        for (draw in 12..13) assertEquals(PrizePlaceState.OWNED, prizeAvailability(award(3), settings, draw, true).state)
    }

    @Test fun `multiple winning tickets belonging to same player do not consume extra places`() {
        val shared = award(2).copy(playerIds = listOf("p1", "p1"))
        assertEquals(2, prizeAvailability(shared, settings, 12, false).remaining)
    }
}
