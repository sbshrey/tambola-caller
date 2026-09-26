package io.github.sbshrey.tambola.game

import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.Random

/** Render real lobby screens with local fixtures; no guest, purchase or persisted profile. */
class CoinLobbyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun finished(): RoomView {
        val plan = CoinPool(4)
        var game = Round.create(listOf(Player("a", "You"), Player("b", "Asha"), Player("c", "Ben")),
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true,
                playAllNumbers = true, prizes = plan.prizes.map { it.prize }), Random(92),
            ticketCounts = mapOf("a" to 2, "b" to 1, "c" to 1)).start()
        repeat(90) { game = game.draw() }
        for (ticket in game.tickets.filter { it.playerId != "c" })
            game = game.claim(ticket.playerId, ticket.numbers.toSet(), ClaimSelection(ticket.id, Prize.EARLY_FIVE.name))
        val other = game.tickets.last()
        game = game.claim(other.playerId, other.numbers.toSet(), ClaimSelection(other.id, Prize.TOP_LINE.name)).draw()
        val own = game.tickets.filter { it.playerId == "a" }
        val paid = plan.allocations(game).filter { it.playerId == "a" }
        return RoomView(code = "LOCAL1", roomId = "local-result", revision = 100, phase = RoomPhase.FINISHED,
            hostId = "a", locked = true, options = RoomOptions(game = game.settings, intervalSeconds = 5, coinGame = true),
            members = game.players.map { MemberView(it.id, it.name, it.avatar, true, true) },
            round = PublicRound(game.id, game.status, game.called, own, game.awards, emptyList(), game.players.associate { it.id to game.score(it.id) }, "fixture", players = game.players),
            nextDrawAt = null, expiresAt = 100_000, serverTime = 10_000,
            coins = CoinTableView(4, 400, plan.prizes, 2, null,
                settledWinnings = paid.filter { it.prize != null }.sumOf { it.coins },
                returnedCoins = paid.filter { it.prize == null }.sumOf { it.coins }))
    }

    @Test fun sharedResultsShowOnlyOwnShareAndPlayAgainRemainsOneTap() = results("en", 1f)
    @Test fun sharedResultsStayReadableInHindiAtLargerTextSize() = results("hi", 1.3f)

    private fun results(language: String, scale: Float) {
        val room = finished()
        val config = Configuration(compose.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language)); fontScale = scale
        }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        var selected = 0
        var state by mutableStateOf(OnlineUiState(loading = false, name = "You", playerId = "a",
            room = room, wallet = WalletView(1487, 1, 0), connection = Connection.LIVE))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { CoinLobby(state, model, { selected = it }, {}, {}) }
            }
        }
        compose.onNodeWithTag("coin-winnings").assertTextEquals(words(R.string.coin_won, 27L))
        compose.onNodeWithTag("coin-prize-value-EARLY_FIVE").assertIsDisplayed().assertTextEquals("27")
        compose.onNodeWithTag("coin-prize-share-EARLY_FIVE").assertTextEquals(words(R.string.coin_shared))
        compose.onNodeWithTag("coin-prize-TOP_LINE").assertDoesNotExist()
        compose.onNodeWithTag("coin-prize-FULL_HOUSE").assertDoesNotExist()
        compose.onNodeWithTag("coin-play").performScrollTo().assertIsEnabled()
        captureTestScreen("coin-shared-results-$language")
        compose.onNodeWithTag("coin-play").performClick()
        assertEquals(3, selected)

        // Changing to a finished table where only another player won must remove the old win.
        compose.runOnIdle {
            val finishedRound = room.round!!
            state = state.copy(room = room.copy(round = finishedRound.copy(awards = finishedRound.awards.filter { it.prize != Prize.EARLY_FIVE }),
                coins = room.coins!!.copy(settledWinnings = 0)))
        }
        compose.onNodeWithTag("coin-winnings").performScrollTo().assertTextEquals(words(R.string.coin_won, 0L))
        compose.onNodeWithTag("coin-prize-EARLY_FIVE").assertDoesNotExist()
        compose.onNodeWithTag("coin-play").performScrollTo().assertIsEnabled()
    }

    @Test fun queueCountdownEndsAndNewServerSnapshotStartsTheNextCountdown() {
        var room by mutableStateOf(finished().copy(phase = RoomPhase.LOBBY, round = null,
            coins = finished().coins!!.copy(startsAt = 12_000)))
        val words = GameText(compose.activity.resources)
        compose.setContent { TambolaTheme { MatchCountdown(room) } }
        compose.onNodeWithTag("match-countdown").assertTextEquals(words(R.string.coin_starts, 2L))
        SystemClock.sleep(2100)
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("match-countdown").assertTextEquals(words(R.string.coin_starting))
        compose.runOnIdle { room = room.copy(serverTime = 20_000, coins = room.coins!!.copy(startsAt = 23_000)) }
        compose.onNodeWithTag("match-countdown").assertTextEquals(words(R.string.coin_starts, 3L))
    }
}
