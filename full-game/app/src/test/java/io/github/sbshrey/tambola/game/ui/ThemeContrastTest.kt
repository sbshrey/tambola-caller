package io.github.sbshrey.tambola.game.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ThemeContrastTest {
    @Test fun smallTextAndControlsRemainLegibleAcrossBothPalettes() {
        listOf("day" to DayColors, "night" to NightColors, "game night" to GameNightPalette.colors).forEach { (name, c) ->
            val pairs = listOf(
                "background" to (c.onBackground to c.background), "primary" to (c.onPrimary to c.primary),
                "secondary" to (c.onSecondary to c.secondary), "tertiary" to (c.onTertiary to c.tertiary),
                "error" to (c.onError to c.error), "error container" to (c.onErrorContainer to c.errorContainer),
                "primary container" to (c.onPrimaryContainer to c.primaryContainer),
                "secondary container" to (c.onSecondaryContainer to c.secondaryContainer),
                "tertiary container" to (c.onTertiaryContainer to c.tertiaryContainer),
            ) + listOf(c.background, c.surface, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest).flatMapIndexed { i, surface ->
                listOf("body $i" to (c.onSurface to surface), "secondary text $i" to (c.onSurfaceVariant to surface),
                    "accent $i" to (c.primary to surface), "success $i" to (c.secondary to surface), "error text $i" to (c.error to surface))
            }
            pairs.forEach { (label, pair) -> assertTrue("$name $label needs 4.5:1 text contrast: ${ratio(pair.first, pair.second)}", ratio(pair.first, pair.second) >= 4.5f) }
            listOf(c.background, c.surface, c.surfaceContainer).forEach { assertTrue("$name outline needs 3:1 contrast", ratio(c.outline, it) >= 3f) }
        }
        listOf(Color.White, Ivory).forEach { assertTrue(ratio(Ink, it) >= 4.5f) }
        assertTrue(ratio(Color.White, Color(0xFF276950)) >= 4.5f)
        listOf(GameNightPalette.cream, GameNightPalette.ticketCell, GameNightPalette.ticketDab, GameNightPalette.ticketBlank).forEach {
            assertTrue("Printed ticket contrast", ratio(GameNightPalette.ticketInk, it) >= 4.5f)
        }
    }

    private fun ratio(a: Color, b: Color): Float = (max(a.luminance(), b.luminance()) + .05f) / (min(a.luminance(), b.luminance()) + .05f)
}
