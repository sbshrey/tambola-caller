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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.presentation.verifiedClaimFeedback
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.CoinTableView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.Random

/** Real text geometry and unchanged ticket targets; local fixture, no purchase or profile. */
class ClaimFeedbackUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun englishFeedbackInLandscape() = feedback("en", 1f, true)
    @Test fun largeEnglishFeedbackInPortrait() = feedback("en", 1.5f, false)
    @Test fun largeHindiFeedbackInLandscape() = feedback("hi", 1.5f, true)
    @Test fun largeHindiFeedbackInPortrait() = feedback("hi", 1.5f, false)

    private fun feedback(language: String, scale: Float, landscape: Boolean) {
        compose.runOnUiThread { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        val orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == orientation }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val pool = CoinPool(12)
        val round = Round.create(listOf(Player("me", "Mira"), Player("peer", "Noor")),
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true, prizes = pool.prizes.map { it.prize }), Random(81)).start().draw()
        var message by mutableStateOf<String?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme {
                    PlayArena(round.toTable().copy(coins = CoinTableView(12, 1200, pool.prizes, 6, null)),
                        "me", Preferences(reducedMotion = true), "Live", {}, {}, {}, null, {},
                        markNumber = { _, _ -> }, claim = {}, claimMessage = message, footer = { Text("Live") })
                }
            }
        }
        compose.onNodeWithTag("tickets-down").performClick()
        if (!landscape) {
            val statusLayout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("portrait-prizes-status").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(statusLayout) }
            val status = statusLayout.single()
            assertFalse("Portrait prize status must fit: ${status.size}, ${status.layoutInput.constraints}", status.hasVisualOverflow)
        }
        val shown = (1..6).first { compose.onAllNodesWithTag("hand-ticket-$it").fetchSemanticsNodes().isNotEmpty() }
        val bounds = compose.onNodeWithTag("hand-ticket-$shown").getUnclippedBoundsInRoot()
        val ownTickets = round.tickets.filter { it.playerId == "me" }
        val prize = round.settings.prizes.last()
        val confirmed = verifiedClaimFeedback(ClaimSelection(ownTickets[shown - 1].id, prize.name), ownTickets, round.settings)
        val messages = listOf(R.string.play_claim_late, R.string.play_claim_none, R.string.play_claim_marks, R.string.play_claim_confirmed)
            .map { it to words(it) } + (R.string.play_claim_confirmed_for to words.message(confirmed))
        assertTrue(words.message(confirmed).contains(words.prizeTitle(prize)))
        messages.forEach { (resource, expected) ->
            compose.runOnIdle { message = expected }
            val node = compose.onNodeWithTag("claim-feedback").assertIsDisplayed().assertTextEquals(expected)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            val layout = layouts.single()
            captureTestScreen("claim-feedback-$language-${if (landscape) "landscape" else "portrait"}-$resource")
            assertFalse("Claim feedback overflows ${layout.size}, paragraph ${layout.multiParagraph.width} x ${layout.multiParagraph.height}, ${layout.lineCount} lines, constraints ${layout.layoutInput.constraints}, slot ${compose.onNodeWithTag("win-slot").getUnclippedBoundsInRoot()}, density ${layout.layoutInput.density}: $expected", layout.hasVisualOverflow)
            assertTrue("Claim feedback is ellipsized", (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
            assertEquals(expected.length, layout.getLineEnd(layout.lineCount - 1))
            assertEquals("A claim response moved the ticket", bounds, compose.onNodeWithTag("hand-ticket-$shown").getUnclippedBoundsInRoot())
            compose.onNodeWithTag("claim-ticket-$shown").assertIsDisplayed().assertIsEnabled()
        }
    }
}
