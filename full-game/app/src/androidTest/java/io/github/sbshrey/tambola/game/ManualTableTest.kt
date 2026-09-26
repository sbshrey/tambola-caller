package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Random

class ManualTableTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun readablePagesPreserveMarksAndClaimsSelectOnlyOneTicketAndPrize() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        var round by mutableStateOf(Round.create(listOf(Player("me", "You")),
            RoundSettings(ticketsPerPlayer = 6, manualClaims = true, playAllNumbers = true,
                prizes = listOf(Prize.EARLY_FIVE, Prize.CORNERS, Prize.TOP_LINE, Prize.MIDDLE_LINE, Prize.BOTTOM_LINE, Prize.FULL_HOUSE)), Random(42)).start())
        repeat(90) { round = round.draw() }
        var submitted: ClaimSelection? = null
        compose.setContent { TambolaTheme {
            PlayArena(round.toTable(), "me", Preferences(reducedMotion = true), "Live", {}, {}, {}, null, {},
                markNumber = { ticket, number -> round = round.toggleMark(ticket, number) },
                claim = { choice -> submitted = choice; round = round.claim("me", selection = choice) },
                footer = { Text("Live") })
        } }
        compose.waitForIdle()
        val visible = compose.onAllNodes(hasTestTag("hand-ticket-1") or hasTestTag("hand-ticket-2")).fetchSemanticsNodes().size
        assertTrue(visible in 1..2)
        compose.onNodeWithTag("hand-ticket-3").assertDoesNotExist()
        compose.onNodeWithTag("tickets-up").assertIsNotEnabled()
        compose.onNodeWithTag("claim").assertDoesNotExist()
        val first = round.tickets.first()
        first.row(0).forEach { compose.onNodeWithTag("dab-$it").performClick() }
        val marked = round.marks[first.id]
        val initialBounds = compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("tickets-down").performClick()
        compose.onNodeWithTag("hand-ticket-1").assertDoesNotExist()
        captureTestScreen("manual-tickets-page-2")
        compose.onNodeWithTag("tickets-up").performClick()
        assertEquals(marked, round.marks[first.id])
        assertEquals(initialBounds, compose.onNodeWithTag("hand-ticket-1").getUnclippedBoundsInRoot())
        // Let the returned page actually paint before opening a separate dialog
        // window; semantic clicks can otherwise outrun the underlying frame.
        captureTestScreen("manual-before-prize-picker")
        compose.onNodeWithTag("claim-ticket-1").performClick()
        compose.onNodeWithTag("claim-prize-TOP_LINE").assertIsDisplayed()
        compose.onNodeWithTag("claim-prize-CORNERS").assertIsDisplayed()
        compose.onNodeWithTag("claim-prize-FULL_HOUSE").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
        captureTestScreen("manual-prize-picker")
        compose.onNodeWithTag("claim-prize-TOP_LINE").performClick()
        assertEquals(ClaimSelection(first.id, Prize.TOP_LINE.name), submitted)
        assertEquals(listOf(Prize.TOP_LINE), round.awards.map { it.prize })
        assertEquals(listOf(first.id), round.awards.single().ticketIds)
        compose.onNodeWithTag("claim-ticket-1").performClick()
        compose.onNodeWithTag("claim-prize-TOP_LINE").assertIsNotEnabled()
        compose.onNodeWithTag("dismiss-claim").performClick()
        val root = compose.onNodeWithTag("play-arena").getUnclippedBoundsInRoot()
        val rounding = 1f / compose.activity.resources.displayMetrics.density
        listOf("claim-ticket-1", "tickets-up", "tickets-down").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            // Pixel-aligned layouts can differ by a fraction of one dp after conversion.
            assertTrue("$tag too small: $bounds", (bounds.right - bounds.left).value >= 48 - rounding && (bounds.bottom - bounds.top).value >= 48 - rounding)
            assertTrue("$tag clipped", bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom)
        }
        captureTestScreen("manual-tickets-selected-claim")
        round.validated()
    }
}
