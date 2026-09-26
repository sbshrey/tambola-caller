package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
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
    private fun awaitHome(phase: String) {
        try { compose.waitUntil(10_000) { exists("Custom game") } }
        catch (error: Exception) {
            runCatching { captureTestScreen("offline-reset-failure") }
            val state = ViewModelProvider(compose.activity)[GameViewModel::class.java].state.value
            throw AssertionError("Offline reset $phase: screen=${state.screen}, loading=${state.loading}, saving=${state.saving}, " +
                "error=${state.error?.resource}, locale=${compose.activity.resources.configuration.locales.toLanguageTags()}", error)
        }
    }

    @Before fun reset() {
        compose.useEnglish()
        runBlocking { PreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext).update(Preferences(voice = false, reducedMotion = true)) }
        awaitHome("before deletion")
        tap("Settings")
        tap("Delete all saved rounds")
        tap("Delete rounds")
        awaitHome("after deletion")
    }

    @Test fun fullOfflineRoundCanBePlayedSavedAndSharedFromResults() {
        tap("Custom game")
        tap("Just me")
        tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Next") }
        var count = 0
        while (!exists("See round results") && count < 90) {
            tap("Next")
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
        compose.goHome()
        tap("Your rounds")
        tap("View results")
        compose.onNodeWithText("A round of applause!").assertExists()
    }

    @Test fun recreationPreservesCallsAndLeavesRoundPaused() {
        tap("Custom game"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Next") }
        tap("Next")
        compose.waitUntil(10_000) { exists("Call 1 of 90") }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { exists("Resume") }
        compose.onNodeWithText("Call 1 of 90").assertExists()
        compose.onNodeWithText("Resume").assertExists()
        tap("Resume")
        compose.waitUntil(10_000) { exists("Next") }
        Thread.sleep(550)
        tap("Next")
        compose.waitUntil(10_000) { exists("Call 2 of 90") }
    }

    @Test fun familyTicketsAreSeparateAndRulesCanBeInspected() {
        tap("Pass & play"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Next") }
        compose.openArenaOption("Pass the phone")
        compose.onNodeWithTag("owned-hand").assertDoesNotExist()
        tap("Show Bina’s tickets")
        compose.onNodeWithText("Bina").assertIsDisplayed()
        compose.onNodeWithText("Asha").assertDoesNotExist()
        compose.onNodeWithTag("hand-ticket-1").assertIsDisplayed()
        captureTestScreen("family-current-hand")
        compose.openArenaOption("Prizes")
        tap("Inspect Full house")
        compose.onAllNodesWithText("Bina · ticket 1").onFirst().assertExists()
        tap("Back to prizes"); tap("Back to game")
        compose.openArenaOption("End round")
        compose.onAllNodesWithText("End round").onLast().performClick()
        compose.waitUntil(10_000) { exists("See round results") }
        tap("See round results")
        compose.onNodeWithText("Until next time.").assertExists()
    }

    @Test fun viewingPastResultsKeepsCurrentRoundResumable() {
        tap("Custom game"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Next") }
        compose.openArenaOption("End round")
        compose.onAllNodesWithText("End round").onLast().performClick()
        compose.waitUntil(10_000) { exists("See round results") }
        compose.goHome(); tap("Custom game"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Next") }
        tap("Next")
        compose.waitUntil(10_000) { exists("Call 1 of 90") }
        compose.goHome(); tap("Your rounds"); tap("View results")
        compose.onNodeWithText("Until next time.").assertExists()
        compose.goHome(); tap("Resume round")
        compose.onNodeWithText("Call 1 of 90").assertExists()
    }
}
