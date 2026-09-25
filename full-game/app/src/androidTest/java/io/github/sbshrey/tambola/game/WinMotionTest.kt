package io.github.sbshrey.tambola.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.game.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class WinMotionTest {
    private var scale = 1f
    private val motion = object : MotionDurationScale { override val scaleFactor get() = scale }
    @get:Rule val compose = createComposeRule(effectContext = motion)
    private fun hash(): Int {
        val pixels = compose.onNodeWithTag("win-motion").captureToImage().toPixelMap()
        var hash = 1
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) hash = hash * 31 + pixels[x, y].toArgb()
        return hash
    }
    @Test fun confettiEndsAndHonorsDisabledReducedAndSlowMotionSettings() {
        var event by mutableIntStateOf(0)
        var reduced by mutableStateOf(true)
        compose.setContent { TambolaTheme { Surface { Box(Modifier.size(220.dp, 80.dp).testTag("win-motion")) { WinConfetti("$event", reduced, Modifier.matchParentSize()) } } } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        val still = hash()
        for (durationScale in listOf(0f, 1f, 10f)) {
            compose.runOnIdle { scale = durationScale; event++; reduced = false }
            compose.mainClock.advanceTimeBy(80)
            val first = hash()
            compose.mainClock.advanceTimeBy(1700)
            assertEquals("Confetti must finish even when system motion is slow", still, hash())
            if (durationScale == 0f) assertEquals(still, first) else assertNotEquals(still, first)
            compose.mainClock.advanceTimeBy(500); assertEquals(still, hash())
        }
        compose.runOnIdle { scale = 1f; reduced = true; event++ }
        compose.mainClock.advanceTimeBy(80); assertEquals(still, hash())
        compose.mainClock.advanceTimeBy(1700); assertEquals(still, hash())
    }
}
