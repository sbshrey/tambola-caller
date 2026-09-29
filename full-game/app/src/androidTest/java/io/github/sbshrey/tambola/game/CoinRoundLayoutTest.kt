package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.CoinTableView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.Random

class CoinRoundLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun sixTicketsKeepTheirBoundsAcrossMarksCallsAndRecovery() = checkLayout("en", 1f)
    @Test fun hindiLargeTextKeepsBoardAndSixTicketNavigationVisible() = checkLayout("hi", 2f)

    private fun checkLayout(language: String, scale: Float) {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = compose.activity.createConfigurationContext(config)
        val pool = CoinPool(12, 2)
        val round = Round.create(listOf(Player("me", "You"), Player("peer", "Mira")),
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true, prizes = pool.prizes.map { it.prize }), Random(71)).start()
        val own = round.tickets.filter { it.playerId == "me" }
        val number = own.first().numbers.first()
        var table by mutableStateOf(round.toTable().copy(called = own.first().numbers.take(5),
            coins = CoinTableView(12, 1200, pool.prizes, 6, null), powers = MatchPowers()))
        var pending by mutableStateOf(false)
        var activation: Pair<String, MatchPower>? = null
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources,
                LocalConfiguration provides config, LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { MaterialTheme(colorScheme = GameNightPalette.colors) {
                    ClaimArena(table, "me", Preferences(reducedMotion = true), "Live",
                        markNumber = { ticket, value -> table = table.copy(marks = table.marks + (ticket to (table.marks[ticket].orEmpty() + value))) },
                        claim = {}, claimMessage = null, repeatCall = {}, back = {}, win = null, dismissWin = {},
                        markEnabled = !pending, claimEnabled = !pending, expandedFooter = pending, extraMenu = {},
                        usePower = { ticket, power -> activation = ticket to power }) {
                        Text(if (pending) "Reconnecting" else "8s")
                    }
                } }
            }
        }
        compose.onNodeWithTag("persistent-call-board").assertIsDisplayed()
        (1..90).forEach { compose.onNodeWithTag("board-number-$it", useUnmergedTree = true).assertIsDisplayed() }
        val first = compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("hand-ticket-2").getUnclippedBoundsInRoot()
        val board = compose.onNodeWithTag("persistent-call-board").getUnclippedBoundsInRoot()
        assertTrue(board.right <= first.left)
        val power = compose.onNodeWithTag("top-power-control").getUnclippedBoundsInRoot()
        assertTrue("Powers must stay above the hand", power.bottom <= first.top)
        compose.onNodeWithTag("dab-$number").performClick()
        compose.runOnIdle { assertTrue(number in table.marks[own.first().id].orEmpty()); pending = true }
        assertEquals(first, compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot())
        assertEquals(second, compose.onNodeWithTag("hand-ticket-2").getUnclippedBoundsInRoot())
        compose.runOnIdle { pending = false; table = table.copy(called = table.called + own.first().numbers[5]) }
        assertEquals(first, compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot())
        repeat(2) { compose.onNodeWithTag("tickets-down").performClick() }
        compose.onNodeWithTag("hand-ticket-5").assertIsDisplayed()
        compose.onNodeWithTag("hand-ticket-6").assertIsDisplayed()
        compose.onNodeWithTag("tickets-down").assertIsNotEnabled()
        compose.runOnIdle { table = table.copy(powers = MatchPowers(inventory = listOf(MatchPower.AUTO_DAB))) }
        compose.onNodeWithTag("top-power-control").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(own[4].id to MatchPower.AUTO_DAB, activation) }
        compose.onNodeWithTag("power-dock").assertDoesNotExist()
        captureTestScreen("coin-round-six-$language-${scale.toInt()}")
        repeat(2) { compose.onNodeWithTag("tickets-up").performClick() }
        compose.onNodeWithTag("hand-ticket-1").assertIsDisplayed()
        compose.runOnIdle { assertTrue(number in table.marks[own.first().id].orEmpty()) }
    }
}
