package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.Player
import io.github.sbshrey.tambola.protocol.RoundWinnings
import org.junit.Assert.*
import org.junit.Test

class RoundStandingsTest {
    @Test fun bonusesCountRefundsDoNotAndEqualTotalsShareRankWithoutPinningOwner() {
        val players = listOf(Player("me", "Mira"), Player("a", "Asha"), Player("b", "Bina"), Player("c", "Noor"))
        val amounts = mapOf("me" to RoundWinnings(0, 0, 600), "a" to RoundWinnings(100, 25, 0),
            "b" to RoundWinnings(125, 0, 0), "c" to RoundWinnings(110, 0, 0))
        val rows = roundStandings(players, amounts)
        assertEquals(listOf("a", "b", "c", "me"), rows.map { it.player.id })
        assertEquals(listOf(1, 1, 3, 4), rows.map { it.rank })
        assertEquals(rows, roundStandings(players.reversed(), amounts))
        assertThrows(IllegalArgumentException::class.java) { roundStandings(players, amounts - "me") }
    }
}
