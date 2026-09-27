package io.github.sbshrey.tambola.game.ui

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A server deadline; recomposition does not restart the countdown. */
@Composable
internal fun DeadlineRing(deadline: Long?, reference: Long?, identity: String, duration: Long,
    reducedMotion: Boolean, modifier: Modifier = Modifier, color: Color = GameNightPalette.mint) {
    val resolver = LocalContext.current.contentResolver
    val smooth = !reducedMotion && Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    val anchor = remember(deadline, reference, identity) { SystemClock.elapsedRealtime() }
    val initial = ((deadline ?: 0) - (reference ?: deadline ?: 0)).coerceAtLeast(0)
    val fraction = remember(anchor, initial, duration) { mutableFloatStateOf((initial.toFloat() / duration).coerceIn(0f, 1f)) }
    LaunchedEffect(anchor, initial, duration, smooth) {
        // A monotonic clock also remains accurate when the animation scale changes.
        // Only this Canvas reads the frequent state: the tickets do not recompose.
        withContext(Dispatchers.Default) {
            var remaining: Long
            do {
                remaining = (initial - (SystemClock.elapsedRealtime() - anchor)).coerceAtLeast(0)
                fraction.floatValue = (remaining.toFloat() / duration).coerceIn(0f, 1f)
                if (remaining > 0) delay(minOf(if (smooth) 32L else 1000L, remaining))
            } while (remaining > 0)
        }
    }
    Canvas(modifier.testTag("deadline-ring-$identity")) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2
        val bounds = Size(size.width - stroke, size.height - stroke)
        drawArc(color.copy(alpha = .18f), -90f, 360f, false, Offset(inset, inset), bounds, style = Stroke(stroke))
        if (fraction.floatValue > 0f) drawArc(color, -90f, fraction.floatValue * 360f, false,
            Offset(inset, inset), bounds, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
