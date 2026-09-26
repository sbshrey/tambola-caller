package io.github.sbshrey.tambola.game

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.setup.SetupDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private fun until(predicate: () -> Boolean) = compose.waitUntil(12_000, predicate)
    private fun capture(name: String) { compose.waitForIdle(); captureTestScreen(name) }

    @Before fun reset() {
        check(isAndroidEmulator())
        compose.useEnglish()
        runBlocking { PreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext).update(
            Preferences(voice = false, reducedMotion = true, appearance = Appearance.DARK)) }
        until { !model.state.value.loading }
        compose.runOnIdle { model.deleteHistory() }
        until { model.state.value.round == null && model.state.value.screen == Screen.HOME }
    }

    @Test fun oneTapQuickGameHasOnlyMyThreeTicketsAndStartsCalling() {
        capture("redesign-home")
        compose.onNodeWithTag("quick-play").assertIsDisplayed().performClick()
        until { model.state.value.round != null && model.state.value.auto }
        val round = model.state.value.round!!
        assertEquals(3, round.settings.ticketsPerPlayer)
        assertTrue(round.settings.assistedMarking)
        assertEquals(2, round.players.count { it.computer })
        assertEquals(5, model.state.value.preferences.interval)
        assertEquals(6, round.settings.prizes.size)
        assertVisibleHand(3)
        until { model.state.value.round!!.called.isNotEmpty() }
        compose.onNodeWithTag("local-auto").performClick()
        until { model.state.value.round!!.status == RoundStatus.PAUSED && !model.state.value.auto }
        capture("redesign-three-tickets")
        val count = model.state.value.round!!.called.size
        compose.onNodeWithText("Resume").performClick()
        until { model.state.value.auto && model.state.value.round!!.called.size > count }
        compose.runOnIdle { model.pause() }
    }

    @Test fun allSixCardsFitAndRapidDabbingNeverUnmarks() {
        compose.tapTag("custom-game")
        compose.tapTag("tickets-6")
        compose.tapText("Help with marking") // Fresh custom setup defaults to assisted.
        compose.tapText("Deal the tickets")
        until { model.state.value.round?.tickets?.size == 18 && !model.state.value.saving }
        assertVisibleHand(6)
        val before = model.state.value.round!!
        val mine = before.tickets.filter { it.playerId == "p0" }
        assertEquals((1..90).toList(), mine.flatMap { it.numbers }.sorted())
        compose.onNodeWithTag("local-next").performClick()
        until { model.state.value.round!!.called.size == 1 }
        compose.onNodeWithTag("dab-called").assertIsEnabled().performClick()
        until { model.state.value.round!!.marks.filterKeys { id -> mine.any { it.id == id } }.values.sumOf { it.size } == 1 }
        val marked = model.state.value.round!!.marks
        compose.runOnIdle { model.dabCalled("p0"); model.dabCalled("p0") }
        compose.waitForIdle()
        assertEquals(marked, model.state.value.round!!.marks)
        assertVisibleHand(6)
        capture("redesign-six-tickets")
        compose.activityRule.scenario.recreate()
        until { !model.state.value.loading && model.state.value.round?.status == RoundStatus.PAUSED }
        assertEquals(before.tickets, model.state.value.round!!.tickets)
        assertEquals(marked, model.state.value.round!!.marks)
        assertVisibleHand(6)
    }

    @Test fun familyHandoffHidesThePreviousHandAndKeepsCallingPaused() {
        compose.tapText("Pass & play")
        compose.tapTag("tickets-3")
        compose.tapText("Deal the tickets")
        until { model.state.value.round?.settings?.mode == GameMode.FAMILY }
        assertVisibleHand(3)
        compose.onNodeWithText("Asha").assertIsDisplayed()
        compose.onNodeWithText("Bina").assertDoesNotExist()
        compose.onNodeWithContentDescription("Game options").performClick()
        compose.tapText("Pass the phone")
        compose.onNodeWithTag("handoff-screen").assertIsDisplayed()
        compose.onNodeWithTag("owned-hand").assertDoesNotExist()
        until { model.state.value.round!!.status == RoundStatus.PAUSED }
        compose.tapText("Show Bina’s tickets")
        compose.onNodeWithText("Bina").assertIsDisplayed()
        compose.onNodeWithText("Asha").assertDoesNotExist()
        assertVisibleHand(3)
        assertEquals(RoundStatus.PAUSED, model.state.value.round!!.status)
        capture("redesign-family-hand")
    }

    @Test fun everySupportedHandSizeStaysOnOneScreen() {
        for (count in 1..6) {
            val previousId = model.state.value.round?.id
            if (count > 1) compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_home)).performClick()
            compose.tapTag("custom-game")
            compose.tapTag("tickets-$count")
            compose.tapText("Deal the tickets")
            if (compose.hasTextNow("Start new round")) compose.tapText("Start new round")
            until { model.state.value.round?.id != previousId && !model.state.value.saving }
            assertVisibleHand(count)
            compose.onNodeWithText("Mango").assertDoesNotExist()
            if (count == 1 || count == 4) capture("redesign-$count-tickets")
        }
    }

    private fun assertVisibleHand(count: Int) {
        val hand = compose.onNodeWithTag("owned-hand").fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("play-arena").fetchSemanticsNode().boundsInRoot
        assertTrue(hand.top >= root.top && hand.bottom <= root.bottom && hand.height > 0)
        for (ticket in 1..count) {
            val card = compose.onNodeWithTag("hand-ticket-$ticket").assertIsDisplayed().fetchSemanticsNode()
            assertTrue(card.boundsInRoot.top >= hand.top && card.boundsInRoot.bottom <= hand.bottom)
            assertTrue(card.boundsInRoot.left >= hand.left && card.boundsInRoot.right <= hand.right)
            var parent = card.parent
            while (parent != null) {
                assertFalse(parent.config.contains(SemanticsProperties.VerticalScrollAxisRange))
                assertFalse(parent.config.contains(SemanticsProperties.HorizontalScrollAxisRange))
                parent = parent.parent
            }
        }
        compose.onNodeWithTag("hand-ticket-${count + 1}").assertDoesNotExist()
        val whole = compose.onNodeWithTag("play-arena").getUnclippedBoundsInRoot()
        listOf("dab-called", "local-next", "local-auto").forEach { tag ->
            if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
                val control = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                assertTrue("$tag right edge $control vs $whole", control.right <= whole.right)
                assertTrue("$tag left edge $control vs $whole", control.left >= whole.left)
                assertTrue("$tag bottom edge $control vs $whole", control.bottom <= whole.bottom)
            }
        }
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("play-arena"))).fetchSemanticsNodes().forEach { action ->
            val bounds = action.boundsInRoot
            assertTrue("Control exceeds right edge: $bounds vs $root", bounds.right <= root.right)
            assertTrue("Control exceeds left edge: $bounds vs $root", bounds.left >= root.left)
            assertTrue("Control exceeds bottom edge: $bounds vs $root", bounds.bottom <= root.bottom)
        }
    }
}
