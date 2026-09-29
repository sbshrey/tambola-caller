package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.CoinTableView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.Random

/** Real system font-scale matrix; the runner restores device settings after each invocation. */
class PrizePickerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun englishPortraitChoicesRemainReadable() = readable("en", false)
    @Test fun hindiPortraitChoicesRemainReadable() = readable("hi", false)
    @Test fun englishLandscapeChoicesRemainReadable() = readable("en", true)
    @Test fun hindiLandscapeChoicesRemainReadable() = readable("hi", true)

    @Test fun fixedSixEnglishPortraitWithoutScrolling() = readable("en", false, 2)
    @Test fun fixedSixHindiPortraitWithoutScrolling() = readable("hi", false, 2)
    @Test fun fixedSixEnglishLandscapeWithoutScrolling() = readable("en", true, 2)
    @Test fun fixedSixHindiLandscapeWithoutScrolling() = readable("hi", true, 2)

    private fun readable(language: String, landscape: Boolean, version: Int = 1) {
        check(isAndroidEmulator())
        val scale = InstrumentationRegistry.getArguments().getString("tambolaPickerScale", "1.0").toFloat()
        assertTrue(scale == 1f || scale == 2f)
        compose.runOnUiThread { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        assertEquals("Configure the real system text size before running this matrix", scale, compose.activity.resources.configuration.fontScale, .001f)
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val pool = CoinPool(36, version)
        val round = Round.create(List(6) { Player("player-$it", "Player $it") },
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true, prizes = pool.prizes.map { it.prize }), Random(71)).start()
        val table = round.toTable().copy(called = (1..20).toList(), coins = CoinTableView(36, 3600, pool.prizes, 6, null))
        val ticket = table.tickets.filter { it.playerId == "player-0" }[5]
        val submitted = mutableListOf<ClaimSelection>()
        var visible by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { MaterialTheme(colorScheme = GameNightPalette.colors) {
                    if (visible) TicketPrizePicker(table, ticket, 6, true, { visible = false }) { submitted += it }
                    else Text("Table resumed")
                } }
            }
        }
        val runLabel = InstrumentationRegistry.getArguments().getString("tambolaPickerLabel", "manual")
        require(runLabel.matches(Regex("[a-z0-9-]+")))
        val label = "picker-v$version-$runLabel-$language-${if (landscape) "landscape" else "portrait"}-${(scale * 100).toInt()}"
        captureTestScreen("$label-top")
        val problems = mutableListOf<String>()
        val geometry = mutableListOf<String>()
        fun inspect(node: SemanticsNodeInteraction) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Expected rendered text", layouts.isNotEmpty())
            layouts.forEach { text ->
                // A paragraph can retain its wider measurement constraint after Text
                // chooses its intrinsic width. Inspect rendered lines, not that metadata.
                val clipped = (0 until text.lineCount).any { line ->
                    text.isLineEllipsized(line) || text.getLineLeft(line) < -1f ||
                        text.getLineRight(line) > text.size.width + 1f || text.getLineBottom(line) > text.size.height + 1f
                }
                geometry += "${text.layoutInput.text} | scale=${text.layoutInput.density.fontScale} | size=${text.size} | paragraph=${text.multiParagraph.width}x${text.multiParagraph.height} | overflowFlag=${text.hasVisualOverflow} | clipped=$clipped"
                if (text.layoutInput.density.fontScale != scale || clipped) problems += geometry.last()
            }
        }
        inspect(compose.onNodeWithText(words(R.string.play_choose_prize, 6), useUnmergedTree = true))
        if (version == 2) compose.onNodeWithTag("claim-prize-grid").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.VerticalScrollAxisRange))
        pool.prizes.forEach { prize ->
            val choice = compose.onNodeWithTag("claim-prize-${prize.prize.name}")
            if (version == 1) choice.performScrollTo()
            choice.assertIsDisplayed()
            choice.assertContentDescriptionEquals(words(R.string.coin_prize_amount, words.prizeTitle(prize.prize), prize.coins) + ". " + words.prizeExplanation(prize.prize))
            val text = compose.onNode(hasText(words.prizeTitle(prize.prize)) and hasAnyAncestor(hasTestTag("claim-prize-${prize.prize.name}")), useUnmergedTree = true)
            inspect(text)
            inspect(compose.onNode(hasText(prize.coins.toString()) and hasAnyAncestor(hasTestTag("claim-prize-${prize.prize.name}")), useUnmergedTree = true))
            val box = choice.getUnclippedBoundsInRoot()
            val dialog = compose.onNodeWithTag("ticket-prize-picker").getUnclippedBoundsInRoot()
            if (box.top < dialog.top || box.bottom > dialog.bottom || box.left < dialog.left || box.right > dialog.right) problems += "${prize.prize}: clipped choice $box inside $dialog"
            val tolerance = 1f / compose.activity.resources.displayMetrics.density
            assertTrue((box.bottom - box.top).value >= 48f - tolerance && (box.right - box.left).value >= 48f - tolerance)
            compose.onNodeWithTag("dismiss-claim").assertIsDisplayed()
        }
        captureTestScreen("$label-bottom")
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "$label-geometry.txt").writeText(geometry.joinToString("\n"))
        assertTrue("Prize text must remain complete at the requested scale: ${problems.joinToString("; ")}", problems.isEmpty())
        if (version == 1) {
            compose.onNodeWithTag("claim-prize-HOUSE_TWO").assertIsNotEnabled()
            compose.onNodeWithTag("claim-prize-HOUSE_THREE").assertIsNotEnabled()
            compose.onNodeWithTag("claim-prize-BOTTOM_LINE").performScrollTo()
        }
        compose.onNodeWithTag("claim-prize-BOTTOM_LINE").assertIsEnabled().performClick()
        compose.onNodeWithTag("ticket-prize-picker").assertDoesNotExist()
        assertEquals(listOf(ClaimSelection(ticket.id, Prize.BOTTOM_LINE.name)), submitted)
    }
}
