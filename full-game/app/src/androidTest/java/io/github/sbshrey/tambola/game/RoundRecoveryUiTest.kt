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
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.Random

/** Saved-state fixtures only: no guest registration or network command. */
class RoundRecoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun englishLandscapeRetryRemainsReadable() = readable("en", true)
    @Test fun hindiLandscapeRetryRemainsReadable() = readable("hi", true)
    @Test fun englishPortraitRetryRemainsReadable() = readable("en", false)
    @Test fun hindiPortraitRetryRemainsReadable() = readable("hi", false)

    private fun readable(language: String, landscape: Boolean) {
        check(isAndroidEmulator())
        val args = InstrumentationRegistry.getArguments()
        val scale = args.getString("tambolaRecoveryScale", "1.0").toFloat()
        val label = args.getString("tambolaRecoveryLabel", "manual")
        require(label.matches(Regex("[a-z0-9-]+")))
        compose.runOnUiThread { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        assertEquals(scale, compose.activity.resources.configuration.fontScale, .001f)
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        val pool = CoinPool(24)
        var round = Round.create(List(4) { Player(if (it == 0) "me" else "peer-$it", "Player $it") },
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true, prizes = pool.prizes.map { it.prize }), Random(81)).start()
        repeat(20) { round = round.draw() }
        val room = RoomView(code = "ABCD2345", roomId = "recovery-fixture", revision = 30, phase = RoomPhase.ACTIVE,
            hostId = "me", locked = true, options = RoomOptions(game = round.settings, coinGame = true, intervalSeconds = 5),
            members = round.players.map { MemberView(it.id, it.name, it.avatar, true, true) },
            round = PublicRound(round.id, round.status, round.called, round.tickets.filter { it.playerId == "me" },
                emptyList(), emptyList(), emptyMap(), "fixture", players = round.players),
            nextDrawAt = 15_000, serverTime = 10_000, expiresAt = 900_000,
            coins = CoinTableView(24, 2400, pool.prizes, 6, null, friendTable = true))
        val savedMarks = room.round!!.ownTickets.associate { it.id to it.numbers.intersect(round.called.toSet()) }
        var state by mutableStateOf(OnlineUiState(loading = false, available = true, name = "Player 0", playerId = "me", room = room,
            connection = Connection.LIVE, marks = savedMarks))
        var retries = 0
        var reconnects = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { OnlineArena(state, model, Preferences(reducedMotion = true), {}, {},
                    retry = { retries++ }, reconnect = { reconnects++ }) }
            }
        }
        val run = "recovery-$label-$language-${if (landscape) "landscape" else "portrait"}-${(scale * 100).toInt()}"
        fun capture(suffix: String) { compose.waitForIdle(); captureTestScreen("$run-$suffix") }
        val geometry = mutableListOf<String>()
        fun readableText(value: String) {
            val textNode = compose.onNodeWithText(value, useUnmergedTree = true).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { text ->
                val clipped = (0 until text.lineCount).any { line -> text.isLineEllipsized(line) || text.getLineLeft(line) < -1f ||
                    text.getLineRight(line) > text.size.width + 1f || text.getLineBottom(line) > text.size.height + 1f }
                geometry += "${text.layoutInput.text} | scale=${text.layoutInput.density.fontScale} | size=${text.size} | clipped=$clipped"
                File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "$run-geometry.txt").writeText(geometry.joinToString("\n"))
                assertEquals(scale, text.layoutInput.density.fontScale, .001f)
                assertFalse("Recovery text must be complete: ${geometry.last()}", clipped)
            }
        }
        compose.onNodeWithTag("tickets-down").performClick()
        val shown = (1..6).first { compose.onAllNodesWithTag("hand-ticket-$it").fetchSemanticsNodes().isNotEmpty() }
        val bounds = compose.onNodeWithTag("hand-ticket-$shown").getUnclippedBoundsInRoot()
        val page = compose.onNodeWithTag("ticket-page").fetchSemanticsNode().config[SemanticsProperties.Text]
        fun retained() {
            assertEquals(bounds, compose.onNodeWithTag("hand-ticket-$shown").getUnclippedBoundsInRoot())
            assertEquals(page, compose.onNodeWithTag("ticket-page").fetchSemanticsNode().config[SemanticsProperties.Text])
            assertEquals(savedMarks, state.marks)
            val ticket = room.round!!.ownTickets[shown - 1]
            savedMarks.getValue(ticket.id).forEach { number -> compose.onNodeWithTag("dab-$number").assertContentDescriptionEquals(words(R.string.play_unmark_number, number)) }
        }
        capture("live")
        compose.onNodeWithTag("claim-ticket-$shown").performClick()
        compose.onNodeWithTag("ticket-prize-picker").assertIsDisplayed()
        listOf(Connection.RECONNECTING, Connection.CONNECTING, Connection.SUSPENDED).forEachIndexed { index, connection ->
            compose.runOnIdle { state = state.copy(connection = connection) }
            capture(connection.name.lowercase())
            compose.onNodeWithTag("ticket-prize-picker").assertDoesNotExist()
            compose.onNodeWithTag("deadline-ring-${round.id}").assertDoesNotExist()
            retained()
            compose.onNodeWithTag("claim-ticket-$shown").assertIsNotEnabled()
            readableText(words(R.string.ui_reconnect_now))
            if (compose.onAllNodesWithTag("round-recovery-copy").fetchSemanticsNodes().isNotEmpty()) readableText(words(R.string.play_saved_calls))
            compose.onNodeWithTag("round-reconnect").assertIsDisplayed().assertIsEnabled().performClick()
            assertEquals(index + 1, reconnects)
            assertEquals(0, retries)
        }
        compose.runOnIdle { state = state.copy(pending = true) }
        capture("pending")
        retained()
        compose.onNodeWithTag("round-reconnect").assertDoesNotExist()
        val shortRetry = words(R.string.round_retry_short)
        readableText(if (compose.onAllNodesWithText(shortRetry).fetchSemanticsNodes().isNotEmpty()) shortRetry else words(R.string.ui_retry_pending_action))
        compose.onNodeWithTag("round-retry").assertContentDescriptionEquals(words(R.string.ui_retry_pending_action))
        if (compose.onAllNodesWithTag("round-recovery-copy").fetchSemanticsNodes().isNotEmpty()) readableText(words(R.string.play_waiting_result))
        compose.onNodeWithTag("round-retry").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, retries)
        assertEquals(3, reconnects)
        compose.runOnIdle { state = state.copy(busy = true) }
        compose.onNodeWithTag("round-retry").assertIsNotEnabled()
        readableText(words(R.string.ui_checking))
        retained()
        compose.runOnIdle { state = state.copy(busy = false, pending = false, connection = Connection.LIVE) }
        compose.onNodeWithTag("round-recovery").assertDoesNotExist()
        compose.onNodeWithTag("deadline-ring-${round.id}").assertExists()
        compose.onNodeWithTag("claim-ticket-$shown").assertIsEnabled()
        retained()
        capture("restored")
    }
}
