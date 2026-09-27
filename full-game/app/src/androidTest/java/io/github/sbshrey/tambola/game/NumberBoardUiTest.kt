package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
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
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.CoinTableView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.Random
import kotlin.random.asKotlinRandom

class NumberBoardUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun calledAndUncalledNumbersHaveExplicitStates() {
        val round = Round.create(listOf(Player("me", "Mira"), Player("peer", "Noor")),
            RoundSettings(mode = GameMode.ONLINE, manualClaims = true), Random(71)).start()
        compose.setContent {
            TambolaTheme {
                PlayArena(round.toTable().copy(called = listOf(7, 21, 90)), "me", Preferences(reducedMotion = true), "Live",
                    {}, {}, {}, null, {}, markNumber = { _, _ -> }, claim = {}, footer = { Text("Live") })
            }
        }
        compose.openArenaOption(compose.activity.getString(R.string.ui_number_board))
        captureTestScreen("number-board-states")
        compose.onNode(hasText("7") and hasAnyAncestor(isDialog())).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Called at call 1"))
        compose.onNode(hasText("8") and hasAnyAncestor(isDialog())).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not called"))
    }

    @Test fun englishLandscapeHistoryKeepsTicketPage() = readableHistory("en", 1f, true)
    @Test fun largeEnglishPortraitHistoryKeepsTicketPage() = readableHistory("en", 2f, false)
    @Test fun largeHindiLandscapeHistoryKeepsTicketPage() = readableHistory("hi", 2f, true)
    @Test fun largeHindiPortraitHistoryKeepsTicketPage() = readableHistory("hi", 2f, false)

    private fun readableHistory(language: String, scale: Float, landscape: Boolean) {
        compose.runOnUiThread { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val round = Round.create(listOf(Player("me", "Mira"), Player("peer", "Noor")),
            RoundSettings(mode = GameMode.ONLINE, manualClaims = true, ticketsPerPlayer = 6), Random(71)).start()
        val allCalls = (1..90).shuffled(Random(41).asKotlinRandom())
        var called by mutableStateOf(allCalls.take(89))
        var marks = 0
        var claims = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme {
                    PlayArena(round.toTable().copy(called = called, coins = CoinTableView(12, 1200, CoinPool(12).prizes, 6, null)), "me", Preferences(reducedMotion = true), "Live",
                        {}, {}, {}, null, {}, markNumber = { _, _ -> marks++ }, claim = { claims++ }, footer = { Text("Live") })
                }
            }
        }
        compose.onNodeWithTag("tickets-down").performClick()
        val pageBefore = compose.onNodeWithTag("ticket-page").fetchSemanticsNode().config[SemanticsProperties.Text]
        compose.onNodeWithTag("open-call-history").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f)).performClick()
        compose.onNodeWithTag("number-board-history-tab").assertIsSelected()
        compose.onNodeWithTag("call-history-89").assertIsDisplayed().assertContentDescriptionEquals(words(R.string.board_history_entry, 89, called.last()))
        compose.onNodeWithTag("number-board-count").assertTextEquals(words(R.string.ui_called_to_go, 89, 1))
        captureTestScreen("history-$language-${if (landscape) "landscape" else "portrait"}")
        assertFits(compose.onNodeWithTag("number-board-latest"), scale)
        assertFits(compose.onNodeWithTag("number-board-count"), scale)
        assertFits(compose.onNode(hasText(words(R.string.board_history)) and hasAnyAncestor(isDialog()), useUnmergedTree = true), scale)
        captureTestScreen("history-$language-${if (landscape) "landscape" else "portrait"}")
        // Revealed calls continue arriving while the sheet is open; no future call is listed.
        compose.onNodeWithTag("call-history-90").assertDoesNotExist()
        compose.runOnIdle { called = allCalls }
        compose.onNodeWithTag("call-history-90").assertIsDisplayed().assertContentDescriptionEquals(words(R.string.board_history_entry, 90, called.last()))
        compose.onNodeWithTag("number-board-latest").assertTextEquals(words(R.string.board_latest, called.last()))
        compose.onNodeWithTag("number-board-history").performScrollToNode(hasTestTag("call-history-1"))
        compose.onNodeWithTag("call-history-1").assertIsDisplayed().assertContentDescriptionEquals(words(R.string.board_history_entry, 1, called.first()))
        compose.onNodeWithTag("number-board-numbers-tab").performClick()
        compose.onNodeWithTag("board-number-1").assertIsDisplayed()
        captureTestScreen("board-$language-${if (landscape) "landscape" else "portrait"}")
        (1..90).forEach { number ->
            val position = called.indexOf(number) + 1
            compose.onNodeWithTag("board-number-$number").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                words(if (number == called.last()) R.string.board_latest_at else R.string.board_called_at, position)))
            assertFits(compose.onNode(hasText(number.toString()) and hasAnyAncestor(hasTestTag("board-number-$number")), useUnmergedTree = true), scale)
        }
        compose.onNodeWithTag("board-number-90").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("dismiss-number-board").assertIsDisplayed().assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f)).performClick()
        compose.onNodeWithTag("number-board-dialog").assertDoesNotExist()
        assertEquals(pageBefore, compose.onNodeWithTag("ticket-page").fetchSemanticsNode().config[SemanticsProperties.Text])
        assertEquals(0, marks); assertEquals(0, claims)
    }

    @Test fun olderHistoryStaysInPlaceWhenAnotherNumberArrives() {
        var called by mutableStateOf((1..60).toList())
        compose.setContent { TambolaTheme { CalledNumberDialog(called, initiallyHistory = true) {} } }
        compose.onNodeWithTag("number-board-history").performScrollToNode(hasTestTag("call-history-12"))
        val before = compose.onNodeWithTag("call-history-12").getUnclippedBoundsInRoot()
        compose.runOnIdle { called = (1..61).toList() }
        assertEquals(before, compose.onNodeWithTag("call-history-12").assertIsDisplayed().getUnclippedBoundsInRoot())
    }

    @Test fun emptyHistoryAndAssistedBoardRemainReadOnly() {
        val round = Round.create(listOf(Player("me", "Mira"), Player("peer", "Noor")), RoundSettings(mode = GameMode.ONLINE), Random(71)).start()
        compose.setContent { TambolaTheme { PlayArena(round.toTable(), "me", Preferences(reducedMotion = true), "Live", {}, {}, {}, null, {}, footer = {}) } }
        compose.openArenaOption(compose.activity.getString(R.string.ui_number_board))
        compose.onNodeWithTag("board-number-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not called"))
        compose.onNodeWithTag("number-board-history-tab").performClick()
        compose.onNodeWithTag("number-board-latest").assertTextEquals(compose.activity.getString(R.string.play_waiting))
        compose.onNodeWithTag("call-history-1").assertDoesNotExist()
        compose.onNodeWithTag("dismiss-number-board").performClick()
        compose.onNodeWithTag("play-arena").assertIsDisplayed()
    }

    private fun assertFits(node: SemanticsNodeInteraction, expectedScale: Float) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Expected rendered text geometry", layouts.isNotEmpty())
        layouts.forEach {
            assertEquals("Dialog text must use the requested font scale", expectedScale, it.layoutInput.density.fontScale, .001f)
            assertFalse("Text overflows: ${it.layoutInput.text}; size=${it.size}; paragraph=${it.multiParagraph.width}x${it.multiParagraph.height}; constraints=${it.layoutInput.constraints}; density=${it.layoutInput.density}; style=${it.layoutInput.style}", it.hasVisualOverflow)
        }
    }
}
