package io.github.sbshrey.tambola.game

import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.Player
import io.github.sbshrey.tambola.domain.Round
import io.github.sbshrey.tambola.domain.RoundSettings
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.presentation.winMoment
import io.github.sbshrey.tambola.game.ui.PlayArena
import io.github.sbshrey.tambola.game.ui.TambolaTheme
import io.github.sbshrey.tambola.game.ui.toTable
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ArenaMotionTest {
    private val durationScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = Settings.Global.getFloat(
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    @get:Rule val compose = createComposeRule(effectContext = durationScale)
    private fun ballHash(): Int {
        val pixels = compose.onNodeWithTag("current-call").captureToImage().toPixelMap()
        var hash = 1
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) hash = hash * 31 + pixels[x, y].toArgb()
        return hash
    }
    private fun cards() = (1..6).map { compose.onNodeWithTag("hand-ticket-$it").getUnclippedBoundsInRoot() }

    @Test fun callsAndWinsNeverMoveTicketsAndMotionRespectsAccessibility() {
        var round by mutableStateOf(Round.create(listOf(Player("me", "You")), RoundSettings(ticketsPerPlayer = 6, assistedMarking = true)).start())
        var reduced by mutableStateOf(true)
        compose.setContent { TambolaTheme {
            PlayArena(round.toTable(), "me", Preferences(reducedMotion = reduced), "Offline", {}, {}, {},
                round.winMoment(), {}, footer = { Text("Pause") })
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        val original = cards()
        compose.runOnIdle { round = round.draw(); reduced = false }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithContentDescription("Current number ${round.latest}").assertIsDisplayed()
        val moving = ballHash()
        compose.mainClock.advanceTimeBy(400)
        val settled = ballHash()
        if (durationScale.scaleFactor == 0f) assertEquals(settled, moving) else assertNotEquals(settled, moving)
        assertEquals(original, cards())
        compose.mainClock.advanceTimeBy(400); assertEquals(settled, ballHash())
        compose.runOnIdle {
            reduced = true
            do { round = round.draw() } while (round.winMoment() == null)
        }
        compose.mainClock.advanceTimeBy(32)
        assertNotNull(round.winMoment())
        val still = ballHash()
        assertEquals(original, cards())
        compose.mainClock.advanceTimeBy(500)
        assertEquals(still, ballHash()); assertEquals(original, cards())
    }
}
