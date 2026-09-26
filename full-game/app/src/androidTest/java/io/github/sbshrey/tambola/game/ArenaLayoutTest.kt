package io.github.sbshrey.tambola.game

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.geometry.Rect
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.GameMode
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.setup.SetupDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Run at several real emulator sizes/font scales via android-layout-matrix.mjs. */
class ArenaLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private fun until(predicate: () -> Boolean) = compose.waitUntil(15_000, predicate)

    @After fun restoreLocale() {
        // Apply the fixture baseline while a live Activity can persist it.
        compose.useEnglish()
        until { compose.activity.resources.configuration.locales[0].language == "en" }
        compose.activityRule.scenario.close()
    }

    @Test fun completeHandsStayReadableInBothLanguagesAndThemes() = runBlocking<Unit> {
        check(isAndroidEmulator())
        val store = PreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        until { !model.state.value.loading }
        for (language in listOf("en", "hi")) {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
            until { compose.activity.resources.configuration.locales[0].language == language }
            for (appearance in listOf(Appearance.DARK, Appearance.LIGHT)) {
                store.update(Preferences(voice = false, effects = false, reducedMotion = true, appearance = appearance))
                until { model.state.value.preferences.appearance == appearance }
                for (count in listOf(1, 3, 6)) {
                    val previous = model.state.value.round?.id
                    compose.runOnIdle {
                        model.updateSetup(SetupDraft.fresh(GameMode.PRACTICE).copy(tickets = count, bots = 0, assisted = false))
                        model.create()
                    }
                    until { model.state.value.round?.id != previous && !model.state.value.saving }
                    compose.waitForIdle()
                    val root = compose.onNodeWithTag("play-arena").getUnclippedBoundsInRoot()
                    for (ordinal in 1..count) {
                        val card = compose.onNodeWithTag("hand-ticket-$ordinal").assertIsDisplayed().getUnclippedBoundsInRoot()
                        assertTrue(card.top >= root.top && card.bottom <= root.bottom && card.left >= root.left && card.right <= root.right)
                    }
                    val numbers = compose.onAllNodesWithTag("ticket-number", useUnmergedTree = true)
                    assertEquals(15 * count, numbers.fetchSemanticsNodes().size)
                    val density = compose.activity.resources.displayMetrics.density
                    numbers.fetchSemanticsNodes().forEach { number ->
                        assertTrue("Unreadable number box ${number.boundsInRoot}", number.boundsInRoot.height / density >= 12f)
                    }
                    listOf("dab-called", "local-next", "local-auto").forEach { tag ->
                        val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
                        assertTrue("$tag outside screen: $bounds", bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom)
                        assertTrue("$tag too small: $bounds", (bounds.right - bounds.left).value >= 48 && (bounds.bottom - bounds.top).value >= 48)
                    }
                    // Settle Compose's test clock too: a wall-clock screenshot delay alone
                    // does not flush theme/graphics transitions controlled by that clock.
                    compose.mainClock.advanceTimeBy(100)
                    compose.waitForIdle()
                    val screenshot = "arena-$language-${appearance.name.lowercase()}-$count"
                    val values = model.state.value.round!!.tickets.flatMap { it.cells.filter { value -> value != 0 } }
                    val glyphBounds = numbers.fetchSemanticsNodes().map { it.boundsInWindow }
                    captureTestScreen(screenshot)
                    if (count == 6) assertCompleteDigits(screenshot, values.zip(glyphBounds))
                }
            }
        }
    }
    /** A complete strip supplies repeated samples of every digit. Bounds and scaled previews
     * alone cannot establish complete glyphs; compare ink in the original saved screenshot.
     * This is a loss-of-strokes check, not OCR or a substitute for visual review.
     */
    private fun assertCompleteDigits(screenshot: String, labels: List<Pair<Int, Rect>>) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val bitmap = checkNotNull(BitmapFactory.decodeFile(File(directory, "$screenshot.png").path))
        data class Glyph(val number: Int, val digit: Int, val position: Int, val pixels: Int)
        try {
            assertEquals((1..90).toList(), labels.map { it.first }.sorted())
            val samples = labels.flatMap { (number, bounds) ->
                val text = number.toString()
                text.mapIndexed { index, digit ->
                    val left = floor(bounds.left + bounds.width * index / text.length).toInt().coerceIn(0, bitmap.width)
                    val right = ceil(bounds.left + bounds.width * (index + 1) / text.length).toInt().coerceIn(0, bitmap.width)
                    var ink = 0
                    for (y in floor(bounds.top).toInt().coerceAtLeast(0) until ceil(bounds.bottom).toInt().coerceAtMost(bitmap.height)) {
                        for (x in left until right) {
                            val color = bitmap.getPixel(x, y)
                            if (Color.red(color) < 100 && Color.green(color) < 110 && Color.blue(color) < 120) ink++
                        }
                    }
                    Glyph(number, digit.digitToInt(), index, ink)
                }
            }
            // Trailing digits are independent reference samples; every digit occurs at least nine times.
            val reference = samples.filter { it.number < 10 || it.position == 1 }
                .groupBy { it.digit }.mapValues { (_, values) -> values.maxOf { it.pixels } }
            val failures = samples.filter { it.pixels < (reference.getValue(it.digit) * .70f).toInt() || it.pixels < 8 }
            File(directory, "$screenshot-glyphs.txt").writeText(samples.joinToString("\n") {
                "number=${it.number} position=${it.position} digit=${it.digit} ink=${it.pixels} reference=${reference.getValue(it.digit)}"
            })
            assertTrue("Incomplete ticket digits in $screenshot: ${failures.take(12)}", failures.isEmpty())
        } finally { bitmap.recycle() }
    }

}
