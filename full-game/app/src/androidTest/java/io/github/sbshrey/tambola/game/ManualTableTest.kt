package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

class ManualTableTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun completePlayerListOpensDirectlyAndPreservesTheSelectedTicketPage() = playerList("en", 1f)
    @Test fun playerListRemainsAccessibleInHindiWithLargerText() = playerList("hi", 1.5f)

    private fun playerList(language: String, scale: Float) {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val config = Configuration(compose.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language)); fontScale = scale
        }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val players = (0 until 8).map { index -> Player(if (index == 0) "me" else "qa-$index",
            if (index == 7) "Computer with a long display name" else "Roster QA $index", computer = index >= 6, avatar = index) }
        val pool = CoinPool(48)
        var round by mutableStateOf(Round.create(players, RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6,
            manualClaims = true, playAllNumbers = true, prizes = pool.prizes.map { it.prize }), Random(81)).start())
        repeat(90) { round = round.draw() }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme {
                    PlayArena(round.toTable().copy(coins = CoinTableView(48, 48 * COIN_TICKET_PRICE, pool.prizes, 6, null)),
                        "me", Preferences(reducedMotion = true), "Live", {}, {}, {}, null, {},
                        markNumber = { ticket, number -> round = round.toggleMark(ticket, number) }, claim = {}, footer = { Text("Live") })
                }
            }
        }
        compose.onNodeWithTag("tickets-down").performClick()
        val shown = (1..6).first { compose.onAllNodesWithTag("hand-ticket-$it").fetchSemanticsNodes().isNotEmpty() }
        val ticket = round.tickets.filter { it.playerId == "me" }[shown - 1]
        val number = ticket.numbers.first()
        compose.onNodeWithTag("dab-$number").performClick()
        val marks = round.marks
        val bounds = compose.onNodeWithTag("hand-ticket-$shown").getUnclippedBoundsInRoot()
        val rosterButton = compose.onNodeWithTag("table-players").assertIsDisplayed().assertHasClickAction()
        rosterButton.assertContentDescriptionEquals(words(R.string.coin_players, 8, 2))
        val target = rosterButton.getUnclippedBoundsInRoot()
        val rounding = 1f / compose.activity.resources.displayMetrics.density
        assertTrue((target.right - target.left).value >= 48 - rounding &&
            (target.bottom - target.top).value >= 48 - rounding)
        val prizeRail = compose.onNodeWithTag("prize-rail").getUnclippedBoundsInRoot()
        pool.prizes.forEach { slot ->
            val prize = compose.onNodeWithTag("sidebar-prize-${slot.prize.name}", useUnmergedTree = true)
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("${slot.prize} clipped at font scale $scale", prize.top >= prizeRail.top && prize.bottom <= prizeRail.bottom)
        }
        compose.onNodeWithTag("table-player-qa-7").assertDoesNotExist()
        rosterButton.performClick()
        players.forEach { player ->
            val row = compose.onNodeWithTag("table-player-${player.id}").performScrollTo().assertIsDisplayed().assert(hasText(player.name))
            if (player.computer) row.assert(hasText(words(R.string.play_computer_short)))
        }
        captureTestScreen("manual-complete-player-list-$language")
        compose.onNodeWithText(words(R.string.ui_back_to_game)).performClick()
        compose.onNodeWithTag("table-player-qa-7").assertDoesNotExist()
        assertEquals(marks, round.marks)
        assertEquals(bounds, compose.onNodeWithTag("hand-ticket-$shown").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("claim-ticket-$shown").performClick()
        compose.onNodeWithTag("claim-prize-HOUSE_ONE").assertIsDisplayed()
        compose.onNodeWithTag("dismiss-claim").performClick()
        compose.onNodeWithTag("game-options").performClick()
        compose.onNodeWithTag("table-players-menu").performClick()
        compose.onNodeWithTag("table-player-me").assertIsDisplayed()
        compose.onNodeWithText(words(R.string.ui_back_to_game)).performClick()
        compose.onNodeWithTag("table-player-me").assertDoesNotExist()
        compose.waitForIdle()
        assertEquals(marks, round.marks)
        captureTestScreen("manual-table-player-access-$language")
    }

    @Test fun readablePagesPreserveMarksAndClaimsSelectOnlyOneTicketAndPrize() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        var round by mutableStateOf(Round.create(listOf(Player("me", "You")),
            RoundSettings(ticketsPerPlayer = 6, manualClaims = true, playAllNumbers = true,
                prizes = listOf(Prize.EARLY_FIVE, Prize.CORNERS, Prize.TOP_LINE, Prize.MIDDLE_LINE, Prize.BOTTOM_LINE, Prize.FULL_HOUSE)), Random(42)).start())
        repeat(90) { round = round.draw() }
        var submitted: ClaimSelection? = null
        compose.setContent { TambolaTheme {
            PlayArena(round.toTable(), "me", Preferences(reducedMotion = true), "Live", {}, {}, {}, null, {},
                markNumber = { ticket, number -> round = round.toggleMark(ticket, number) },
                claim = { choice -> submitted = choice; round = round.claim("me", selection = choice) },
                footer = { Text("Live") })
        } }
        compose.waitForIdle()
        val visible = compose.onAllNodes(hasTestTag("hand-ticket-1") or hasTestTag("hand-ticket-2")).fetchSemanticsNodes().size
        assertTrue(visible in 1..2)
        compose.onNodeWithTag("hand-ticket-3").assertDoesNotExist()
        compose.onNodeWithTag("tickets-up").assertIsNotEnabled()
        compose.onNodeWithTag("claim").assertDoesNotExist()
        val first = round.tickets.first()
        first.row(0).forEach { compose.onNodeWithTag("dab-$it").performClick() }
        val marked = round.marks[first.id]
        val initialBounds = compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("tickets-down").performClick()
        compose.onNodeWithTag("hand-ticket-1").assertDoesNotExist()
        captureTestScreen("manual-tickets-page-2")
        compose.onNodeWithTag("tickets-up").performClick()
        assertEquals(marked, round.marks[first.id])
        assertEquals(initialBounds, compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot())
        // Let the returned page actually paint before opening a separate dialog
        // window; semantic clicks can otherwise outrun the underlying frame.
        captureTestScreen("manual-before-prize-picker")
        compose.onNodeWithTag("claim-ticket-1").performClick()
        compose.onNodeWithTag("claim-prize-TOP_LINE").assertIsDisplayed()
        compose.onNodeWithTag("claim-prize-CORNERS").assertIsDisplayed()
        compose.onNodeWithTag("claim-prize-FULL_HOUSE").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
        captureTestScreen("manual-prize-picker")
        compose.onNodeWithTag("claim-prize-TOP_LINE").performClick()
        assertEquals(ClaimSelection(first.id, Prize.TOP_LINE.name), submitted)
        assertEquals(listOf(Prize.TOP_LINE), round.awards.map { it.prize })
        assertEquals(listOf(first.id), round.awards.single().ticketIds)
        compose.onNodeWithTag("claim-ticket-1").performClick()
        compose.onNodeWithTag("claim-prize-TOP_LINE").assertIsNotEnabled()
        compose.onNodeWithTag("dismiss-claim").performClick()
        val root = compose.onNodeWithTag("play-arena").getUnclippedBoundsInRoot()
        val rounding = 1f / compose.activity.resources.displayMetrics.density
        listOf("claim-ticket-1", "tickets-up", "tickets-down").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            // Pixel-aligned layouts can differ by a fraction of one dp after conversion.
            assertTrue("$tag too small: $bounds", (bounds.right - bounds.left).value >= 48 - rounding && (bounds.bottom - bounds.top).value >= 48 - rounding)
            assertTrue("$tag clipped", bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom)
        }
        captureTestScreen("manual-tickets-selected-claim")
        round.validated()
    }
}
