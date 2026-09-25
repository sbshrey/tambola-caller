package io.github.sbshrey.tambola.game

import androidx.compose.ui.semantics.SemanticsProperties
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

class LearnAndBadgesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun tap(text: String) = compose.tapText(text)
    private fun exists(text: String) = compose.hasTextNow(text)
    private fun badge(badge: Badge, earned: Boolean) = compose.onNodeWithTag("badge-${badge.name}")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (earned) "Earned" else "Not yet earned"))

    @Before fun reset() {
        runBlocking { PreferenceStore(context).update(Preferences(voice = false, reducedMotion = true)) }
        compose.waitUntil(10_000) { exists("Play solo") }
        tap("Settings"); tap("Delete all saved rounds"); tap("Delete rounds")
        compose.waitUntil(10_000) { exists("Play solo") }
    }

    @Test fun tutorialSurvivesRecreationAndDoesNotChangeTheRealRoundOrEarnBadges() = runBlocking<Unit> {
        tap("Play solo"); tap("Just me"); tap("Deal the tickets")
        compose.waitUntil(10_000) { exists("Call next number") }
        tap("Call next number"); compose.waitUntil(10_000) { exists("Call 1 of 90") }
        tap("‹ Home")
        val database = GameDatabase.open(context)
        try {
            val before = RoundCodec.decode(database.rounds().active()!!.payload)
            tap("Learn with a sample ticket"); tap("Show me the ticket"); tap("Next lesson")
            compose.onNodeWithText("Next lesson").performScrollTo().assertIsNotEnabled()
            tap("Try calling a number"); tap("Mark ticket")
            compose.onNodeWithContentDescription("Number 22").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Number 7").performScrollTo().performClick()
            tap("Done"); compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { exists("Next lesson") }
            compose.onNodeWithText("Next lesson").performScrollTo().assertIsEnabled()
            captureTestScreen("tutorial-mark")
            tap("Next lesson"); tap("Try a top-line win")
            tap("Mark ticket")
            compose.onNodeWithContentDescription("Number 22").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Number 22").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Marked"))
            tap("Done")
            compose.onNodeWithText("15 points · all five numbers called").performScrollTo().assertIsDisplayed()
            captureTestScreen("tutorial-win")
            tap("Next lesson"); tap("Finish tutorial")
            compose.waitUntil(10_000) { exists("Play solo") }
            assertTrue(PreferenceStore(context).values.first().tutorialCompleted)
            compose.onNodeWithText("Learn with a sample ticket").assertDoesNotExist()
            val after = RoundCodec.decode(database.rounds().active()!!.payload)
            assertEquals(before.id, after.id); assertEquals(before.called, after.called)
            assertEquals(before.tickets, after.tickets); assertEquals(before.marks, after.marks)
            assertEquals(1, database.rounds().observe().first().size)
            tap("Your badges"); Badge.entries.forEach { badge(it, false) }
            compose.onNodeWithText("0 of 5 completed rounds").assertExists()
            tap("‹ Home"); tap("How to play"); tap("Skip for now")
            compose.waitUntil(10_000) { exists("Play solo") }
            assertTrue(PreferenceStore(context).values.first().tutorialCompleted)
            tap("Resume round"); compose.onNodeWithText("Call 1 of 90").assertExists()
        } finally { database.close() }
    }

    @Test fun savedCompletedGamesEarnSeparateBadgesAndDeletionClearsOfflineMilestones() = runBlocking<Unit> {
        val database = GameDatabase.open(context)
        try {
            val repository = LocalGameRepository(database)
            repeat(5) {
                var round = Round.create(listOf(Player("me", "Sample player")), RoundSettings()).start()
                repeat(90) { round = round.draw() }
                repository.save(round)
            }
            val cancelled = Round.create(listOf(Player("family-a", "Asha"), Player("family-b", "Bina")), RoundSettings(mode = GameMode.FAMILY)).start().draw().cancel()
            repository.save(cancelled)
            // The fixture uses a separate Room connection. A real app write refreshes its observer.
            tap("Play solo"); tap("Just me"); tap("Deal the tickets")
            compose.waitUntil(10_000) { exists("Call next number") }
            tap("End round"); compose.onAllNodesWithText("End round").onLast().performClick()
            compose.waitUntil(10_000) { exists("See round results") }
            tap("‹ Home")
            tap("Your badges")
            compose.waitUntil(10_000) { runCatching { badge(Badge.FIVE_ROUNDS, true); true }.getOrDefault(false) }
            Badge.entries.forEach { badge(it, true) }
            compose.onNodeWithTag("badge-FIRST_HOUSE").performScrollTo().assertIsDisplayed()
            captureTestScreen("earned-badges")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { exists("Your badges") }
            Badge.entries.forEach { badge(it, true) }
            tap("Family table"); Badge.entries.forEach { badge(it, false) }
            tap("Computer games"); Badge.entries.forEach { badge(it, false) }
            tap("Settings"); tap("Delete all saved rounds"); tap("Delete rounds")
            compose.waitUntil(10_000) { exists("Play solo") }
            tap("Your badges")
            compose.waitUntil(10_000) { exists("0 of 5 completed rounds") }
            Badge.entries.forEach { badge(it, false) }
            assertTrue(database.rounds().observe().first().isEmpty())
        } finally { database.close() }
    }
}
