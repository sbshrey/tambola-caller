package io.github.sbshrey.tambola.game

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
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
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.game.ui.NumberBall
import io.github.sbshrey.tambola.game.ui.TambolaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NumberMotionTest {
    // Compose's test recomposer does not inherit the device duration scale. Supply it explicitly.
    private val durationScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = Settings.Global.getFloat(
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    @get:Rule val compose = createComposeRule(effectContext = durationScale)
    private fun frameHash(): Int {
        val pixels = compose.onNodeWithTag("ball-sample").captureToImage().toPixelMap()
        var hash = 1
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) hash = hash * 31 + pixels[x, y].toArgb()
        return hash
    }

    @Test fun committedNumberIsImmediateAndMotionRespectsBothControls() {
        var number by mutableIntStateOf(7)
        var reduced by mutableStateOf(true)
        compose.setContent { TambolaTheme { Surface { Box(Modifier.width(220.dp).testTag("ball-sample")) { NumberBall(number, reduced) } } } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        compose.runOnIdle { number = 22; reduced = false }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithContentDescription("Current number 22").assertIsDisplayed()
        val moving = frameHash()
        compose.mainClock.advanceTimeBy(500)
        val settled = frameHash()
        val scale = durationScale.scaleFactor
        if (scale == 0f) assertEquals("System animation-off should settle immediately", settled, moving)
        else assertNotEquals("Normal number reveal should move before settling", settled, moving)
        compose.mainClock.advanceTimeBy(500); assertEquals("Animation must finish rather than loop", settled, frameHash())
        compose.runOnIdle { number = 90; reduced = true }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithContentDescription("Current number 90").assertIsDisplayed()
        val still = frameHash()
        compose.mainClock.advanceTimeBy(500); assertEquals("Reduced motion keeps the new number still", still, frameHash())
    }
}
