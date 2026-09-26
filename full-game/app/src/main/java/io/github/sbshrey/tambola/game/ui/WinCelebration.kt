package io.github.sbshrey.tambola.game.ui

import io.github.sbshrey.tambola.game.R

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.game.presentation.WinMoment
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.roundToInt
import kotlin.math.sin

/** Decorative, finite motion. Its message and the game controls remain independently usable. */
@Composable
internal fun WinConfetti(eventId: String, reducedMotion: Boolean, modifier: Modifier = Modifier, intensity: Float = .22f) {
    val words = gameText()
    val progress = remember(eventId) { Animatable(1f) }
    val colors = listOf(Saffron, Jade, Coral)
    LaunchedEffect(eventId, reducedMotion) {
        if (reducedMotion) progress.snapTo(1f)
        else {
            val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale <= 0f) progress.snapTo(1f) else {
                progress.snapTo(0f)
                // Respect motion-off/faster settings; cap slow animation scales at about 1.5 seconds.
                progress.animateTo(1f, tween((1500 / scale.coerceAtLeast(1f)).roundToInt().coerceAtLeast(1), easing = LinearEasing))
            }
        }
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val p = progress.value
        if (reducedMotion || p >= 1f) return@Canvas
        repeat(18) { index ->
            val x = size.width * ((index * 37 % 101) / 100f) + sin(p * 5 + index) * 5.dp.toPx()
            val y = size.height * (((index * 17 % 31) / 100f) + p * .65f)
            rotate(index * 27f + p * 120f, Offset(x, y)) {
                drawRect(colors[index % colors.size].copy(alpha = (1 - p) * intensity), Offset(x, y), Size(4.dp.toPx(), 7.dp.toPx()))
            }
        }
    }
}
