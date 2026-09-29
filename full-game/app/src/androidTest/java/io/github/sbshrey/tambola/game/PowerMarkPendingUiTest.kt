package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Random

class PowerMarkPendingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun inFlightMarkKeepsOtherNumbersAndClockLiveThenOffersExactRetryOnFailure() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val pool = CoinPool(12, 2)
        val settings = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true, prizes = pool.prizes.map { it.prize })
        val game = Round.create(listOf(Player("me", "Mira"), Player("peer", "Noor")), settings, Random(28)).start()
        val ticket = game.tickets.first()
        val room = RoomView(code = "ABCD2345", roomId = "mark-fixture", revision = 30, phase = RoomPhase.ACTIVE,
            hostId = "me", locked = true, options = RoomOptions(game = settings, capacity = 50, coinGame = true, coinRulesVersion = 2, powersEnabled = true),
            members = game.players.map { MemberView(it.id, it.name, 0, true, true) },
            round = PublicRound(game.id, game.status, ticket.numbers.take(5), game.tickets.filter { it.playerId == "me" }, emptyList(), emptyList(), emptyMap(), "0".repeat(64),
                players = game.players, powers = MatchPowers()), nextDrawAt = 20000, serverTime = 10000, expiresAt = 900000,
            coins = CoinTableView(12, 1200, pool.prizes, 6, null))
        var state by mutableStateOf(OnlineUiState(loading = false, available = true, name = "Mira", playerId = "me", room = room, connection = Connection.LIVE))
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        val words = GameText(compose.activity.resources)
        var retries = 0
        compose.setContent { TambolaTheme {
            OnlineArena(state, model, Preferences(reducedMotion = true), {}, {}, retry = { retries++ })
        } }
        val bounds = compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot()
        val number = ticket.numbers.first()
        compose.runOnIdle { state = state.copy(busy = true, pending = true, markSending = true, pendingMarks = mapOf(ticket.id to setOf(number))) }
        compose.onNodeWithTag("round-recovery").assertDoesNotExist()
        compose.onNodeWithTag("deadline-ring-${game.id}").assertExists()
        compose.onNodeWithTag("dab-$number").assertIsNotEnabled().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, words(R.string.power_mark_sending)))
        compose.onNodeWithTag("dab-${ticket.numbers[1]}").assertIsEnabled()
        compose.onNodeWithTag("claim-ticket-1").assertIsNotEnabled()
        assertEquals(bounds, compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot())
        compose.runOnIdle { state = state.copy(busy = false, markSending = false) }
        compose.onNodeWithTag("round-retry").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, retries)
        compose.onNodeWithTag("dab-${ticket.numbers[1]}").assertIsNotEnabled()
        assertEquals(bounds, compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot())
        compose.runOnIdle { state = state.copy(pending = false, pendingMarks = emptyMap(), room = room.copy(round = room.round!!.copy(powers = MatchPowers(marks = mapOf(ticket.id to setOf(number)), correctMarks = 1)))) }
        compose.onNodeWithTag("round-recovery").assertDoesNotExist()
        compose.onNodeWithTag("dab-$number").assertContentDescriptionEquals(words(R.string.power_marked_number, number))
        compose.onNodeWithTag("dab-${ticket.numbers[1]}").assertIsEnabled()
        assertEquals(bounds, compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot())
    }
}
