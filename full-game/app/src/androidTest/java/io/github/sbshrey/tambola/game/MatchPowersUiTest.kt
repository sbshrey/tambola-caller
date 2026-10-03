package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.CoinTableView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Random

class MatchPowersUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun powerPickerExplainsUnavailableTicketsAndUpdatesAfterAWin() {
        val round = Round.create(listOf(Player("me", "Player123456")),
            RoundSettings(ticketsPerPlayer = 6), Random(27)).start()
        val hand = round.tickets
        var powers by mutableStateOf(MatchPowers(
            inventory = listOf(MatchPower.PRIZE_BONUS),
            discarded = setOf(hand[0].id),
            used = mapOf(hand[1].id to setOf(MatchPower.PRIZE_BONUS), hand[2].id to setOf(MatchPower.PRIZE_BONUS)),
            armedBonus = setOf(hand[1].id)))
        var activated: String? = null
        compose.setContent { TambolaTheme {
            PowerDock(round.toTable().copy(powers = powers), true, false) { ticket, _ -> activated = ticket }
        } }
        compose.onNodeWithTag("match-power-PRIZE_BONUS").performClick()
        compose.onNodeWithTag("use-power-ticket-1").performScrollTo().assertIsNotEnabled()
            .assertTextEquals("Ticket 1 · discarded")
        compose.onNodeWithTag("use-power-ticket-2").performScrollTo().assertIsNotEnabled()
            .assertTextEquals("Ticket 2 · bonus ready for next win")
        compose.runOnIdle { powers = powers.won(hand[1].id, Prize.EARLY_FIVE) }
        compose.onNodeWithTag("use-power-ticket-2").assertTextEquals("Ticket 2 · already used this round")
        compose.onNodeWithTag("use-power-ticket-3").performScrollTo().assertIsNotEnabled()
            .assertTextEquals("Ticket 3 · already used this round")
        compose.onNodeWithTag("use-power-ticket-6").performScrollTo().assertIsEnabled().performClick()
        assertEquals(hand[5].id, activated)
    }

    @Test fun powerActivationAndDiscardedTicketsKeepOtherTicketPlayable() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        val round = Round.create(listOf(Player("me", "Mira"), Player("peer", "Noor")),
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 2, manualClaims = true), Random(27)).start()
        val hand = round.tickets.filter { it.playerId == "me" }
        val called = hand.flatMap { it.numbers }.take(10)
        var powers by mutableStateOf(MatchPowers(inventory = listOf(MatchPower.SHIELD, MatchPower.AUTO_DAB)))
        compose.setContent { TambolaTheme {
            PlayArena(round.toTable().copy(called = called, powers = powers, marks = powers.marks, serverTime = 100_000,
                coins = CoinTableView(4, 400, CoinPool(4, 2).prizes, 2, null)),
                "me", Preferences(reducedMotion = true), "Live", {}, {}, {}, null, {},
                markNumber = { _, _ -> }, claim = { powers = powers.falseClaim(it.ticketId) },
                usePower = { ticketId, power -> powers = powers.activate(hand.first { it.id == ticketId }, power, called, 100_000) },
                footer = { Text("Next call") })
        } }
        captureTestScreen("power-header-layout")
        compose.onNodeWithTag("top-power-control").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(135_000L, powers.autoUntil[hand[0].id])
        assertEquals(0, powers.correctMarks)
        compose.onNodeWithTag("dab-${called.first()}").assertIsNotEnabled()
        repeat(2) {
            compose.onNodeWithTag("claim-ticket-2").performClick()
            compose.onNodeWithTag("claim-prize-FULL_HOUSE").performClick()
            if (it == 0) {
                assertTrue(powers.discarded.isEmpty())
            }
        }
        compose.onNodeWithTag("ticket-prize-picker").assertDoesNotExist()
        compose.onNodeWithTag("discarded-${hand[1].id}").assertIsDisplayed()
        compose.onNodeWithTag("claim-ticket-2").assertIsNotEnabled()
        compose.onNodeWithTag("claim-ticket-1").assertIsEnabled()
        captureTestScreen("power-ticket-discard")
    }
}
