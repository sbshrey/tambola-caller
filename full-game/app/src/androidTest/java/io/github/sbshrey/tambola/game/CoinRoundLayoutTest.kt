package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
    @Test fun quickTambolaClaimsReadyGoalInOneTapWithoutPowerSpace() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val pool = CoinPool(4, 2, quickTambola = true)
        val round = Round.create(listOf(Player("me", "QA"), Player("peer", "Mira", computer = true)),
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 2, assistedMarking = true, manualClaims = true,
                prizes = pool.prizes.map { it.prize }, winnersPerPrize = 5, maxCalls = 60), Random(91)).start()
        val ticket = round.tickets.first { it.playerId == "me" }
        val called = ticket.row(0).take(2) + ticket.row(1).take(2) + ticket.row(2).take(1)
        assertFalse(Prize.ANY_LINE.matches(ticket, called.toSet()))
        var table by mutableStateOf(round.toTable().copy(called = called.take(4), marks = mapOf(ticket.id to called.take(4).toSet()),
            coins = CoinTableView(4, 400, pool.prizes, 2, null), callIntervalSeconds = 5))
        var submitted: ClaimSelection? = null
        compose.setContent { TambolaTheme { MaterialTheme(colorScheme = GameNightPalette.colors) {
            ClaimArena(table, "me", Preferences(reducedMotion = true), "Live", { _, _ -> }, { submitted = it },
                null, {}, {}, null, {}, markEnabled = true, claimEnabled = true, expandedFooter = false,
                extraMenu = {}, footer = { Text("5s") })
        } } }
        compose.onNodeWithTag("hand-ticket-1").assertIsDisplayed()
        compose.onNodeWithTag("round-power-slot").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.quick_tambola_goal_places, 5, 5)).assertIsDisplayed()
        compose.onNodeWithTag("claim-ticket-1").assertIsNotEnabled()
        compose.runOnIdle { table = table.copy(called = called, marks = mapOf(ticket.id to called.toSet())) }
        compose.onNodeWithText(compose.activity.getString(R.string.quick_tambola_claim_now, 1,
            compose.activity.getString(R.string.prize_early_five))).assertIsDisplayed()
        compose.onNodeWithTag("claim-ticket-1").performClick()
        compose.runOnIdle { assertEquals(ClaimSelection(ticket.id, Prize.EARLY_FIVE.name), submitted) }
        compose.onNodeWithTag("ticket-prize-picker").assertDoesNotExist()
        captureTestScreen("quick-tambola-arena")
    }
    @Test fun sixTicketsKeepTheirBoundsAcrossMarksCallsAndRecovery() = checkLayout("en", 1f)
    @Test fun hindiLargeTextKeepsBoardAndSixTicketNavigationVisible() = checkLayout("hi", 2f)

    @Test fun singleTicketUsesTheStageAndKeepsClaimVisible() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val pool = CoinPool(12, 2)
        val round = Round.create(listOf(Player("me", "QA"), Player("peer", "Mira", computer = true)),
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 1, manualClaims = true,
                prizes = pool.prizes.map { it.prize }), Random(72)).start()
        val ticket = round.tickets.first { it.playerId == "me" }
        val table = round.toTable().copy(called = ticket.numbers.take(3),
            coins = CoinTableView(12, 1200, pool.prizes, 1, null))
        compose.setContent { TambolaTheme { MaterialTheme(colorScheme = GameNightPalette.colors) {
            ClaimArena(table, "me", Preferences(reducedMotion = true), "Live", { _, _ -> }, {}, null, {}, {}, null, {},
                markEnabled = true, claimEnabled = true, expandedFooter = false, extraMenu = {}, footer = { Text("8s") })
        } } }
        compose.onNodeWithTag("hand-ticket-1").assertIsDisplayed()
        compose.onNodeWithTag("ticket-prize-pace-1").assertIsDisplayed()
        compose.onNodeWithTag("claim-ticket-1").assertIsDisplayed()
        compose.onNodeWithTag("table-players").performClick()
        compose.onNodeWithText("Mira · ${compose.activity.getString(R.string.play_computer_short)}").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_back_to_game)).performClick()
        val ticketBounds = compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot()
        val stage = compose.onAllNodesWithTag("ticket-call-stage").fetchSemanticsNodes()
        if (stage.isNotEmpty()) {
            val stageBounds = compose.onNodeWithTag("ticket-call-stage").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Call stage follows the ticket", ticketBounds.bottom <= stageBounds.top)
            assertTrue("Call stage uses the ticket width", stageBounds.right - stageBounds.left >= ticketBounds.right - ticketBounds.left - 1.dp)
        } else assertTrue("A short stage lets one ticket use the full playfield", ticketBounds.bottom - ticketBounds.top > 180.dp)
        captureTestScreen("coin-single-ticket-stage")
    }

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
        var claimed: ClaimSelection? = null
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources,
                LocalConfiguration provides config, LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { MaterialTheme(colorScheme = GameNightPalette.colors) {
                    ClaimArena(table, "me", Preferences(reducedMotion = true), "Live",
                        markNumber = { ticket, value -> table = table.copy(marks = table.marks + (ticket to (table.marks[ticket].orEmpty() + value))) },
                        claim = { claimed = it }, claimMessage = null, repeatCall = {}, back = {}, win = null, dismissWin = {},
                        markEnabled = !pending, claimEnabled = !pending, expandedFooter = pending, extraMenu = {},
                        usePower = { ticket, power -> activation = ticket to power }) {
                        Text(if (pending) "Reconnecting" else "8s")
                    }
                } }
            }
        }
        compose.onNodeWithTag("play-board-tab").performClick()
        compose.onNodeWithTag("persistent-call-board").assertIsDisplayed()
        (1..90).forEach { compose.onNodeWithTag("board-number-$it", useUnmergedTree = true).assertIsDisplayed() }
        val board = compose.onNodeWithTag("persistent-call-board").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("play-tickets-tab").performClick()
        val first = compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("hand-ticket-2").getUnclippedBoundsInRoot()
        assertTrue("Board uses the full playing width", board.right - board.left >= first.right - first.left)
        val call = compose.onNodeWithTag("current-call").getUnclippedBoundsInRoot()
        assertTrue("Calls stay above the tickets", call.bottom <= first.top)
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
        compose.onNodeWithTag("play-board-tab").performClick()
        compose.onNodeWithTag("play-tickets-tab").performClick()
        compose.onNodeWithTag("hand-ticket-5").assertIsDisplayed()
        compose.runOnIdle {
            val ticket = own.first()
            val progressTable = table.copy(called = ticket.numbers.take(2), marks = mapOf(ticket.id to ticket.numbers.toSet()))
            assertEquals(.4f, ticketPrizeProgress(progressTable, ticket, Prize.EARLY_FIVE), .001f)
            assertEquals(0f, ticketPrizeProgress(progressTable.copy(marks = emptyMap()), ticket, Prize.EARLY_FIVE), .001f)
        }
        compose.runOnIdle { table = table.copy(powers = MatchPowers(inventory = listOf(MatchPower.AUTO_DAB))) }
        compose.onNodeWithTag("top-power-control").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(own[4].id to MatchPower.AUTO_DAB, activation) }
        compose.onNodeWithTag("power-dock").assertDoesNotExist()
        captureTestScreen("coin-round-six-$language-${scale.toInt()}")
        val fifth = compose.onNodeWithTag("hand-ticket-5").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("claim-ticket-5").performClick()
        val panel = compose.onNodeWithTag("ticket-prize-picker").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Claim panel uses the playing width", panel.right - panel.left >= first.right - first.left)
        compose.onNodeWithTag("current-call").assertIsDisplayed()
        pool.prizes.forEach { prize ->
            val choice = compose.onNodeWithTag("claim-prize-${prize.prize.name}").assertIsDisplayed().assertIsEnabled().getUnclippedBoundsInRoot()
            val title = compose.onNode(hasText(GameText(context.resources).prizeTitle(prize.prize)) and
                hasAnyAncestor(hasTestTag("claim-prize-${prize.prize.name}")), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("${prize.prize} title is below its choice", title.bottom <= choice.bottom + 1.dp)
            assertTrue("${prize.prize} title is above its choice", title.top >= choice.top - 1.dp)
        }
        fun claimTextFits() {
        compose.onAllNodes(hasAnyAncestor(hasTestTag("ticket-prize-picker")) and
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            layouts.forEach { text ->
                assertFalse("Clipped claim text: ${text.layoutInput.text}", (0 until text.lineCount).any { line ->
                    text.isLineEllipsized(line) || text.getLineLeft(line) < -1f || text.getLineRight(line) > text.size.width + 1f || text.getLineBottom(line) > text.size.height + 1f
                })
            }
        }
        }
        captureTestScreen("coin-round-claim-$language-${scale.toInt()}")
        claimTextFits()
        compose.runOnIdle { table = table.copy(awards = listOf(Award(Prize.TOP_LINE, table.called.size, listOf("peer-ticket"), listOf("peer")))) }
        compose.onNodeWithTag("claim-prize-TOP_LINE").assertIsEnabled()
        claimTextFits()
        compose.runOnIdle { table = table.copy(called = table.called + own.first().numbers[6]) }
        compose.onNodeWithTag("ticket-prize-picker").assertIsDisplayed()
        compose.onNodeWithTag("claim-prize-TOP_LINE").assertIsNotEnabled()
        claimTextFits()
        compose.runOnIdle { table = table.copy(awards = table.awards + Award(Prize.CORNERS, table.called.size, listOf(own[4].id), listOf("me"))) }
        compose.onNodeWithTag("claim-prize-CORNERS").assertIsNotEnabled()
        claimTextFits()
        compose.onNodeWithTag("claim-prize-BOTTOM_LINE").performClick()
        compose.runOnIdle { assertEquals(ClaimSelection(own[4].id, Prize.BOTTOM_LINE.name), claimed) }
        compose.onNodeWithTag("ticket-prize-picker").assertDoesNotExist()
        assertEquals(fifth, compose.onNodeWithTag("hand-ticket-5").getUnclippedBoundsInRoot())
        repeat(2) { compose.onNodeWithTag("tickets-up").performClick() }
        compose.onNodeWithTag("hand-ticket-1").assertIsDisplayed()
        compose.runOnIdle { assertTrue(number in table.marks[own.first().id].orEmpty()) }
    }
}

