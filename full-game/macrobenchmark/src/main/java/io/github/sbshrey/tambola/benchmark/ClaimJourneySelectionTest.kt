package io.github.sbshrey.tambola.benchmark

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test

class ClaimJourneySelectionTest {
    private val house = Award(Prize.HOUSE_ONE, 70, listOf("own-2", "peer-4"), listOf("me", "peer"))
    private fun snapshot(calls: Int, awards: List<Award> = listOf(house)) = PublicRound(
        id = "fixture", status = RoundStatus.PLAYING, called = (1..calls).toList(), ownTickets = emptyList(),
        awards = awards, customAwards = emptyList(), scores = emptyMap(), drawCommitment = "fixture",
        winningTickets = listOf(WinningTicket("own-2", "me", 2), WinningTicket("peer-4", "peer", 4), WinningTicket("own-5", "me", 5)))

    @Test fun delayedHouseReceiptCannotReuseThePreviousWinningTicket() {
        val beforeReceipt = snapshot(71, emptyList())
        assertEquals(2, listOf(2, 3).first { it !in closedHouseOrdinals(beforeReceipt, "me") })
        // Same call, later snapshot: the previous claim has now settled. No inline
        // receipt callback is available, but the public identities must exclude ticket 2.
        assertEquals(3, listOf(2, 3).first { it !in closedHouseOrdinals(snapshot(71), "me") })
    }

    @Test fun currentCallHouseRemainsOpenUntilTheNextCall() {
        assertTrue(closedHouseOrdinals(snapshot(70), "me").isEmpty())
        assertEquals(setOf(2), closedHouseOrdinals(snapshot(71), "me"))
    }

    @Test fun otherPlayersAndNonHousePrizesDoNotExcludeTickets() {
        val early = Award(Prize.EARLY_FIVE, 20, listOf("own-5"), listOf("me"))
        assertEquals(setOf(2), closedHouseOrdinals(snapshot(71, listOf(house, early)), "me"))
        assertEquals(setOf(4), closedHouseOrdinals(snapshot(71, listOf(house, early)), "peer"))
    }
}
