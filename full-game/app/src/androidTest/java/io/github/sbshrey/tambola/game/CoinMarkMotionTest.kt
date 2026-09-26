package io.github.sbshrey.tambola.game

import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Random

/** Pixel and semantics checks ensure deferring colour reads preserves visible marking and motion controls. */
@OptIn(ExperimentalTestApi::class)
class CoinMarkMotionTest {
    private val durationScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = Settings.Global.getFloat(
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    @get:Rule val compose = createComposeRule(effectContext = durationScale)

    @Test fun markUnmarkColoursAndFiniteMotionRemainVisible() {
        var table by mutableStateOf(Round.create(listOf(Player("motion", "You")),
            RoundSettings(ticketsPerPlayer = 1, manualClaims = true), Random(53)).start().toTable()
            .copy(called = (1..90).toList()))
        val ticket = table.tickets.single()
        val number = ticket.numbers.first()
        var reduced by mutableStateOf(false)
        compose.setContent { TambolaTheme {
            CompactTicket(ticket, table, Modifier.fillMaxWidth().height(160.dp), reduced,
                markNumber = { id, value ->
                    val marks = table.marks[id].orEmpty()
                    table = table.copy(marks = table.marks + (id to if (value in marks) marks - value else marks + value))
                })
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        val cell = compose.onNodeWithTag("dab-$number")
        fun pixels() = cell.captureToImage().toPixelMap()
        fun hash(): Int {
            val image = pixels(); var result = 1
            for (y in 0 until image.height step 2) for (x in 0 until image.width step 2) result = result * 31 + image[x, y].toArgb()
            return result
        }
        fun assertFill(rgb: Int) {
            val image = pixels(); var matching = 0; var count = 0
            for (y in 0 until image.height step 2) for (x in 0 until image.width step 2) {
                count++; if (image[x, y].toArgb() and 0xffffff == rgb) matching++
            }
            assertTrue("Expected visible cell fill ${rgb.toString(16)}; matched $matching/$count", matching > count * .3)
        }
        assertFill(0xffffff)
        cell.performClick(); compose.mainClock.advanceTimeBy(80)
        cell.assertContentDescriptionEquals("Unmark $number")
        val moving = hash()
        compose.mainClock.advanceTimeBy(500); assertFill(0x21634d)
        val settled = hash()
        if (durationScale.scaleFactor > 0f) assertNotEquals(moving, settled) else assertEquals(moving, settled)
        compose.mainClock.advanceTimeBy(500); assertEquals(settled, hash())
        cell.performClick(); compose.mainClock.advanceTimeBy(500)
        cell.assertContentDescriptionEquals("Mark $number"); assertFill(0xffffff)
        compose.runOnIdle { reduced = true }
        cell.performClick(); compose.mainClock.advanceTimeBy(32); assertFill(0x21634d)
        val still = hash()
        compose.mainClock.advanceTimeBy(500); assertEquals(still, hash())
    }
}
