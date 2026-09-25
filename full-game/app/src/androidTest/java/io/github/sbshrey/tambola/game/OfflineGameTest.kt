package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.game.data.PreferenceStore
import io.github.sbshrey.tambola.game.data.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OfflineGameTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun exists(text: String) = compose.hasTextNow(text)
    private fun tap(text: String) = compose.tapText(text)

    @Before fun reset() {
        runBlocking { PreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext).update(Preferences(voice = false, reducedMotion = true)) }
        compose.waitUntil(10_000) { exists("Play solo") }
        tap("Settings")
        tap("Delete all saved rounds")
        tap("Delete rounds")
        compose.waitUntil(10_000) { exists("Play solo") }
    }

    @Test fun fullOfflineRoundCanBePlayedSavedAndSharedFromResults() {
        tap("Play solo")
        tap("Just me")
        tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Call next number") }
        var count = 0
        while (!exists("See round results") && count < 90) {
            tap("Call next number")
            Thread.sleep(550) // The production accidental-double-tap guard is intentionally active.
            compose.waitForIdle()
            count++
        }
        compose.onNodeWithText("See round results").assertExists()
        tap("See round results")
        compose.onNodeWithText("A round of applause!").assertExists()
        compose.onNodeWithText("Share these results").performScrollTo().assertIsEnabled()
        tap("See your badges")
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("badge-FIRST_ROUND").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Earned")); true }.getOrDefault(false) }
        compose.onNodeWithTag("badge-FIRST_ROUND").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Earned"))
        compose.onNodeWithTag("badge-FIRST_HOUSE").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Earned"))
        tap("‹ Home")
        tap("Your rounds")
        tap("View results")
        compose.onNodeWithText("A round of applause!").assertExists()
    }

    @Test fun recreationPreservesCallsAndLeavesRoundPaused() {
        tap("Play solo"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Call next number") }
        tap("Call next number")
        compose.waitUntil(10_000) { exists("Call 1 of 90") }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { exists("Resume calling") }
        compose.onNodeWithText("Call 1 of 90").assertExists()
        compose.onNodeWithText("Resume calling").assertExists()
        tap("Resume calling")
        compose.waitUntil(10_000) { exists("Call next number") }
        Thread.sleep(550)
        tap("Call next number")
        compose.waitUntil(10_000) { exists("Call 2 of 90") }
    }

    @Test fun familyTicketsAreSeparateAndRulesCanBeInspected() {
        tap("Play on one device"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Call next number") }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyDescendant(hasText("Bina · ticket 1"))).performScrollTo()
        tap("Bina · ticket 1")
        compose.onNodeWithText("Bina · ticket 1").assertIsSelected()
        tap("Mark ticket")
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = java.io.File(target.filesDir, "family-${target.resources.configuration.fontScale}.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            screenshot.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Bina's ticket").assertExists()
        tap("Done")
        tap("Check claims · 0 verified")
        compose.onNodeWithText("Fair wins, happy faces").assertExists()
        tap("Back to game")
        tap("End round")
        compose.onAllNodesWithText("End round").onLast().performClick()
        compose.waitUntil(10_000) { exists("See round results") }
        tap("See round results")
        compose.onNodeWithText("Until next time.").assertExists()
    }

    @Test fun viewingPastResultsKeepsCurrentRoundResumable() {
        tap("Play solo"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Call next number") }
        tap("End round")
        compose.onAllNodesWithText("End round").onLast().performClick()
        compose.waitUntil(10_000) { exists("See round results") }
        tap("‹ Home"); tap("Play solo"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Call next number") }
        tap("Call next number")
        compose.waitUntil(10_000) { exists("Call 1 of 90") }
        tap("‹ Home"); tap("Your rounds"); tap("View results")
        compose.onNodeWithText("Until next time.").assertExists()
        tap("‹ Home"); tap("Resume round")
        compose.onNodeWithText("Call 1 of 90").assertExists()
    }
}
