package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CustomRulesGameTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun exists(text: String) = compose.hasTextNow(text)
    private fun tap(text: String) = compose.tapText(text)
    private fun type(tag: String, text: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text); compose.waitForIdle() }

    @Before fun reset() {
        runBlocking { PreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext).update(Preferences(voice = false, reducedMotion = true)) }
        compose.waitUntil(10_000) { exists("Play solo") }
        tap("Settings"); tap("Delete all saved rounds"); tap("Delete rounds")
        compose.waitUntil(10_000) { exists("Play solo") }
    }

    @Test fun customCompoundMultiTicketRuleSurvivesRecreationAndNinetyCallRematch() {
        tap("Play solo"); tap("Just me"); compose.tapTag("tickets-2")
        tap("Two houses"); tap("Call all 90 numbers"); tap("Create custom prize")
        type("prize-title", "Lucky pair")
        compose.tapTag("select-0-0-ROW")
        compose.tapTag("add-and-0")
        compose.tapTag("select-0-1-POSITIONS")
        tap("Add OR group")
        compose.tapTag("count-1-0"); type("count-input-1-0", "2")
        compose.tapTag("minimum-tickets-2")
        tap("Winning example")
        compose.onNodeWithText("Sample rule satisfied").performScrollTo().assertIsDisplayed()
        captureTestScreen("custom-winning-example")
        tap("Clear sample calls")
        compose.onNodeWithText("Sample rule not yet satisfied").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { exists("Save prize") }
        compose.onNodeWithTag("prize-title").assertTextContains("Lucky pair")
        compose.onNodeWithTag("minimum-tickets-2").assertIsSelected()
        tap("Save prize"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Call next number") }
        tap("Check claims · 0 verified"); tap("Inspect Lucky pair")
        compose.onNodeWithText("Matching owned tickets: 0 / 2 needed").assertExists()
        captureTestScreen("custom-claim-details")
        tap("Back to prizes"); tap("Back to game")
        var calls = 0
        while (!exists("See round results") && calls < 90) {
            tap("Call next number"); calls++
            compose.waitUntil(10_000) { exists("Call $calls of 90") || exists("See round results") }
            Thread.sleep(550)
        }
        assertEquals(90, calls)
        tap("See round results")
        compose.onNodeWithText("Lucky pair").assertExists()
        val database = GameDatabase.open(InstrumentationRegistry.getInstrumentation().targetContext)
        val saved = try { runBlocking { database.rounds().observe().first().first() } } finally { database.close() }
        val round = RoundCodec.decode(saved.payload)
        assertEquals(90, round.called.size); assertEquals(RoundStatus.COMPLETED, round.status)
        assertEquals(1, round.customAwards.size); assertEquals(2, round.customAwards.single().ticketIds.size)
        assertEquals(2, round.settings.customPrizes.single().pattern.alternatives.size)
        assertTrue(Prize.HOUSE_TWO in round.settings.prizes)
        tap("Play another round")
        compose.onNodeWithText("Two houses").assertIsSelected()
        compose.onNodeWithText("Call all 90 numbers").assertIsOn()
        compose.onNodeWithText("Lucky pair").assertExists()
        compose.onNodeWithTag("tickets-2").assertIsSelected()
        compose.onNodeWithText("Just me").assertIsSelected()
    }

    @Test fun incompatibleTicketReductionIsExplainedAndDraftCanBeCorrected() {
        tap("Play solo"); compose.tapTag("tickets-3"); tap("Create custom prize")
        type("prize-title", "Third ticket")
        tap("Ticket 3"); tap("Save prize")
        compose.tapTag("tickets-1")
        compose.onNodeWithText("Deal the tickets").assertIsNotEnabled()
        compose.onNodeWithText("Third ticket needs more tickets. Edit the prize or increase tickets per player.").performScrollTo().assertIsDisplayed()
        tap("Edit Third ticket")
        tap("All owned tickets"); tap("Save prize")
        compose.onNodeWithText("Deal the tickets").assertIsEnabled()
        tap("Create custom prize")
        type("prize-title", "Invalid row")
        compose.tapTag("select-0-0-ROW"); compose.tapTag("count-0-0"); type("count-input-0-0", "6")
        compose.onNodeWithText("Save prize").assertIsNotEnabled()
        type("count-input-0-0", "3")
        compose.onNodeWithText("Save prize").assertIsEnabled()
        tap("‹ Setup"); tap("Discard edits")
        compose.onNodeWithText("Third ticket").assertExists()
        compose.onNodeWithText("Invalid row").assertDoesNotExist()
    }

    @Test fun resultsPreviewExcludesNamesUntilExplicitlyIncluded() {
        tap("Play on one device"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Call next number") }
        tap("End round"); compose.onAllNodesWithText("End round").onLast().performClick()
        compose.waitUntil(10_000) { exists("See round results") }
        tap("See round results"); tap("Share these results")
        compose.onNodeWithTag("share-preview").assertTextContains("Player 1: 0 points", substring = true)
        assertFalse(compose.onNodeWithTag("share-preview").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString().contains("Asha"))
        tap("Include player names")
        compose.onNodeWithTag("share-preview").assertTextContains("Asha: 0 points", substring = true)
        captureTestScreen("share-preview")
        tap("Keep private")
        compose.onNodeWithText("Until next time.").assertExists()
    }

    @Test fun rulePreviewAndClaimDetailsRemainUsableAtLargeText() {
        val font = (InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale * 100).toInt()
        tap("Play on one device"); tap("Create custom prize")
        type("prize-title", "A column prize")
        compose.tapTag("select-0-0-COLUMN")
        tap("Winning example")
        compose.onNodeWithText("Sample rule satisfied").performScrollTo().assertIsDisplayed()
        captureTestScreen("rule-preview-font-$font")
        tap("Save prize"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Call next number") }
        tap("Check claims · 0 verified"); tap("Inspect A column prize")
        compose.onNodeWithText("All of column 1 (1–9)").performScrollTo().assertIsDisplayed()
        captureTestScreen("rule-details-font-$font")
        tap("Back to prizes"); tap("Back to game")
        tap("End round"); compose.onAllNodesWithText("End round").onLast().performClick()
        compose.waitUntil(10_000) { exists("See round results") }
        tap("See round results")
        compose.onNodeWithText("Until next time.").assertExists()
    }
}
