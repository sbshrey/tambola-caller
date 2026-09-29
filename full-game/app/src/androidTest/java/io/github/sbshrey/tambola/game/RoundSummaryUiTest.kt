package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.online.OnlineUiState
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class RoundSummaryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun englishFiftyPlayersScrollToOwnRankAndConfirmSixTicketReplay() = checkSummary("en", 1f)
    @Test fun hindiLargeTextKeepsRankAndActionsReadable() = checkSummary("hi", 2f)

    private fun checkSummary(language: String, scale: Float) {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = compose.activity.createConfigurationContext(config)
        val players = List(50) { Player("p$it", "Player ${it.toString().padStart(2, '0')}") }
        val winnings = players.mapIndexed { index, player -> player.id to RoundWinnings((50 - index) * 10L, 0, 0) }.toMap()
        val pool = CoinPool(300, 2)
        val awards = listOf(Award(Prize.TOP_LINE, 30, listOf("ticket-p45"), listOf("p45")))
        val game = PublicRound("summary-fixture", RoundStatus.COMPLETED, (1..90).toList(), emptyList(), awards, emptyList(), emptyMap(), "fixture",
            players = players, winnings = winnings)
        val room = RoomView(code = "ABCD2345", roomId = "summary-room", revision = 100, phase = RoomPhase.FINISHED, hostId = "p0", locked = true,
            options = RoomOptions(game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true, prizes = pool.prizes.map { it.prize }),
                coinGame = true, capacity = 50, coinRulesVersion = 2, roundSummary = true), members = emptyList(), round = game,
            nextDrawAt = null, serverTime = 10000, expiresAt = 90000, coins = CoinTableView(300, 30000, pool.prizes, 6, null, settledWinnings = 50))
        val state = OnlineUiState(loading = false, available = true, playerId = "p45", room = room, wallet = WalletView(1000, 1, 0))
        var replayed: Int? = null
        var lobbies = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { CoinRoundSummary(state, { lobbies++ }, { replayed = it }, {}, {}, reducedMotion = scale > 1f) }
            }
        }
        compose.onNodeWithTag("summary-player-p45").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("summary-player-p0").assertDoesNotExist()
        val replayBounds = compose.onNodeWithTag("summary-replay").assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.onNodeWithTag("summary-rankings").performScrollToIndex(0)
        compose.onNodeWithTag("summary-player-p0").assertIsDisplayed()
        compose.onNodeWithTag("summary-my-rank").performClick()
        compose.onNodeWithTag("summary-player-p45").assertIsDisplayed()
        assertEquals(replayBounds, compose.onNodeWithTag("summary-replay").getUnclippedBoundsInRoot())
        val words = GameText(context.resources)
        compose.onNodeWithText(words.prizeTitle(Prize.TOP_LINE)).assertIsDisplayed()
        captureTestScreen("round-summary-$language-${scale.toInt()}")
        compose.onNodeWithTag("summary-replay").performClick()
        assertNull(replayed)
        compose.onNodeWithText(words(R.string.round_summary_confirm, 6, 600L)).assertIsDisplayed()
        compose.onNodeWithTag("summary-confirm-replay").performClick()
        assertEquals(6, replayed)
        compose.onNodeWithTag("summary-lobby").performClick()
        assertEquals(1, lobbies)
    }
}
