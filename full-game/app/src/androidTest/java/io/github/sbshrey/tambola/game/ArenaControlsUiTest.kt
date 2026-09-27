package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.Random

/** Native saved-state fixtures; no registration, purchase or room command. */
class ArenaControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var originalConfiguration: Configuration? = null

    @Suppress("DEPRECATION")
    @After fun restoreActivityConfiguration() {
        originalConfiguration?.let { original -> compose.runOnUiThread {
            compose.activity.resources.updateConfiguration(original, compose.activity.resources.displayMetrics)
        } }
    }
    @Test fun englishLandscapeControls() = controls("en", true)
    @Test fun hindiLandscapeControls() = controls("hi", true)
    @Test fun englishPortraitControls() = controls("en", false)
    @Test fun hindiPortraitControls() = controls("hi", false)

    @Test fun englishPortraitOneTicket() = controls("en", false, ticketCount = 1)
    @Test fun hindiLandscapeThreeTickets() = controls("hi", true, ticketCount = 3)
    @Test fun hindiLandscapeEightFriends() = controls("hi", true, playerCount = 8)
    @Test fun hindiLandscapeFiftyFriends() = controls("hi", true, playerCount = 50)

    @Suppress("DEPRECATION")
    private fun controls(language: String, landscape: Boolean, ticketCount: Int = 6, playerCount: Int = 4) {
        check(isAndroidEmulator())
        val args = InstrumentationRegistry.getArguments()
        val scale = args.getString("tambolaArenaScale", "2.0").toFloat()
        val label = args.getString("tambolaArenaLabel", "manual")
        require(label.matches(Regex("[a-z0-9-]+")))
        compose.runOnUiThread { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        assertEquals(scale, compose.activity.resources.configuration.fontScale, .001f)
        originalConfiguration = Configuration(compose.activity.resources.configuration)
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        // Dialogs create a window from the Activity. Match its locale as well as
        // Compose's locals, just as the production AppCompat locale does.
        compose.runOnUiThread { compose.activity.resources.updateConfiguration(config, compose.activity.resources.displayMetrics) }
        assertEquals(language, compose.activity.resources.configuration.locales[0].language)
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        val totalTickets = ticketCount * playerCount
        val pool = CoinPool(totalTickets, if (playerCount > 8) 2 else 1)
        var round = Round.create(List(playerCount) { Player(if (it == 0) "me" else "peer-$it", "Player $it") },
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = ticketCount, manualClaims = true, winnersPerPrize = if (pool.version == 2) 5 else 1, prizes = pool.prizes.map { it.prize }), Random(81)).start()
        repeat(30) { round = round.draw() }
        val room = RoomView(code = "ABCD2345", roomId = "arena-controls", revision = 30, phase = RoomPhase.ACTIVE,
            hostId = "me", locked = true, options = RoomOptions(game = round.settings.copy(ticketsPerPlayer = 6), coinGame = true, intervalSeconds = if (pool.version == 2) 10 else 5,
                capacity = if (pool.version == 2) 50 else 32, coinRulesVersion = pool.version),
            members = round.players.map { MemberView(it.id, it.name, it.avatar, true, true) },
            round = PublicRound(round.id, round.status, round.called, round.tickets.filter { it.playerId == "me" },
                emptyList(), emptyList(), emptyMap(), "fixture", players = round.players),
            nextDrawAt = 15_000, serverTime = 10_000, expiresAt = 900_000,
            coins = CoinTableView(totalTickets, totalTickets * 100L, pool.prizes, ticketCount, null, friendTable = true))
        val savedMarks = room.round!!.ownTickets.associate { it.id to it.numbers.intersect(round.called.toSet()) }
        val state = OnlineUiState(loading = false, available = true, name = "Player 0", playerId = "me", room = room,
            connection = Connection.LIVE, marks = savedMarks)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { OnlineArena(state, model, Preferences(reducedMotion = true), {}, {}) }
            }
        }
        val run = "arena-$label-$language-${if (landscape) "landscape" else "portrait"}-${(scale * 100).toInt()}-$ticketCount-tickets-$playerCount-players"
        fun capture(suffix: String) { compose.waitForIdle(); captureTestScreen("$run-$suffix") }
        val issues = mutableListOf<String>()
        val geometry = mutableListOf<String>()
        fun textFits(node: SemanticsNodeInteraction) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { text ->
                assertEquals(scale, text.layoutInput.density.fontScale, .001f)
                val clipped = (0 until text.lineCount).any { line -> text.isLineEllipsized(line) || text.getLineLeft(line) < -1f ||
                    text.getLineRight(line) > text.size.width + 1f || text.getLineBottom(line) > text.size.height + 1f }
                val lines = (0 until text.lineCount).joinToString { "${text.getLineLeft(it)},${text.getLineRight(it)},${text.getLineBottom(it)}" }
                geometry += "${text.layoutInput.text} | scale=${text.layoutInput.density.fontScale} | size=${text.size} | lines(left,right,bottom)=$lines | clipped=$clipped"
                if (clipped) issues += "Clipped text: ${text.layoutInput.text}"
            }
        }
        fun childTextFits(tag: String) {
            val nodes = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            repeat(nodes.fetchSemanticsNodes().size) { textFits(nodes[it]) }
        }
        capture("live")
        val card = compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot()
        if (ticketCount >= 2) compose.onNodeWithTag("hand-ticket-2").assertIsDisplayed()
        fun pageText() = compose.onAllNodesWithTag("ticket-page").fetchSemanticsNodes().firstOrNull()?.config?.get(SemanticsProperties.Text)
        val page = pageText()
        textFits(compose.onNodeWithTag("play-status"))
        childTextFits("claim-ticket-1")
        textFits(compose.onNodeWithTag("owned-ticket-count"))
        textFits(compose.onNodeWithTag("next-call-copy"))
        childTextFits("open-call-history")
        if (page != null) textFits(compose.onNodeWithTag("ticket-page"))
        val claimBounds = compose.onNodeWithTag("claim-ticket-1").getUnclippedBoundsInRoot()
        assertTrue("Claim touch target must be at least 48dp: $claimBounds",
            (claimBounds.right - claimBounds.left).value >= 48 && (claimBounds.bottom - claimBounds.top).value >= 48)
        if (landscape) {
            childTextFits("table-players")
            val rail = compose.onNodeWithTag("prize-list", useUnmergedTree = true).getUnclippedBoundsInRoot()
            pool.prizes.forEach { slot ->
                val tag = "sidebar-prize-${slot.prize.name}"
                val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
                var parent = node.fetchSemanticsNode().parent
                while (parent != null && !parent.config.contains(SemanticsActions.ScrollBy)) parent = parent.parent
                if (parent != null) { node.performScrollTo(); compose.waitForIdle() }
                val bounds = node.getUnclippedBoundsInRoot()
                geometry += "$tag | bounds=$bounds | rail=$rail"
                if (bounds.top < rail.top || bounds.bottom > rail.bottom) issues += "Unreachable prize: ${slot.prize}"
                childTextFits(tag)
            }
            compose.onNodeWithTag("prize-list", useUnmergedTree = true).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
            compose.waitForIdle()
            capture("prizes-end")
            assertEquals(card, compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot())
            assertEquals(page, pageText())
            compose.onNodeWithTag("prize-rail").performTouchInput { click(center) }
            capture("prize-details-open")
            val finalPrize = compose.onNode(hasText(words.prizeTitle(pool.prizes.last().prize)) and hasAnyAncestor(isDialog()), useUnmergedTree = true)
            finalPrize.performScrollTo().assertIsDisplayed()
            textFits(finalPrize)
            capture("prize-details")
            val closePrizes = compose.onNodeWithText(words(R.string.ui_back_to_game))
            textFits(closePrizes)
            closePrizes.performTouchInput { click(center) }
            compose.onNodeWithTag("table-players").performTouchInput { click(center) }
            compose.onNodeWithTag("table-player-peer-${playerCount - 1}").performScrollTo().assertIsDisplayed()
            capture("players")
            compose.onNodeWithText(words(R.string.ui_back_to_game)).performTouchInput { click(center) }
            assertEquals(card, compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot())
            assertEquals(page, pageText())
        }
        compose.onNodeWithTag("claim-ticket-1").performTouchInput { click(center) }
        compose.onNodeWithTag("ticket-prize-picker").assertIsDisplayed()
        compose.onNodeWithText(words(R.string.play_choose_prize, 1), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("dismiss-claim").performTouchInput { click(center) }
        repeat(6) {
            if (compose.onAllNodesWithTag("hand-ticket-$ticketCount").fetchSemanticsNodes().isEmpty())
                compose.onNodeWithTag("tickets-down").performTouchInput { click(center) }
        }
        childTextFits("claim-ticket-$ticketCount")
        capture("last-ticket")
        compose.onNodeWithTag("claim-ticket-$ticketCount").performTouchInput { click(center) }
        compose.onNodeWithText(words(R.string.play_choose_prize, ticketCount), useUnmergedTree = true).assertExists()
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "$run-geometry.txt").writeText(geometry.joinToString("\n"))
        assertTrue(issues.joinToString("\n"), issues.isEmpty())
    }
}
