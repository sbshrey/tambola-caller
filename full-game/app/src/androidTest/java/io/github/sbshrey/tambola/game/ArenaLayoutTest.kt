package io.github.sbshrey.tambola.game

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
                    captureTestScreen("arena-$language-${appearance.name.lowercase()}-$count")
                }
            }
        }
    }
}
