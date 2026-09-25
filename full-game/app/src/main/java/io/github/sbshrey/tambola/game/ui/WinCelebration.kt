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

@Composable
fun WinCelebration(moment: WinMoment, reducedMotion: Boolean, dismiss: () -> Unit, inspect: () -> Unit) {
    val words = gameText()
    GameCard(Modifier.testTag("verified-win")) {
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            WinConfetti(moment.id, reducedMotion, Modifier.matchParentSize())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                moment.players.firstOrNull()?.let { AvatarBadge(it.avatar, size = 44.dp) }
                Column(Modifier.weight(1f)) {
                    Eyebrow(words(R.string.ui_a_verified_win))
                    Text(if (moment.players.size > 1) words(R.string.ui_good_times_shared) else words(R.string.ui_a_little_round_of_applause), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(words(R.string.ui_call_number, moment.drawIndex, moment.number), color = Muted, style = MaterialTheme.typography.bodySmall)
            moment.lines.take(2).forEach { line ->
                val names = line.players.take(2).joinToString { words.playerLabel(it) } +
                    if (line.players.size > 2) words(R.string.ui_and_more, line.players.size - 2) else ""
                Text(line.prize?.let(words::prizeTitle) ?: line.title, fontWeight = FontWeight.SemiBold)
                Text(words(R.string.ui_points_2, names, line.points) + if (line.players.size > 1) words(R.string.ui_each_tied) else "", color = Jade)
            }
            if (moment.lines.size > 2) Text(words(R.string.ui_more_verified_prizes_on_this_call, moment.lines.size - 2), color = Muted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = inspect) { Text(words(R.string.ui_see_winning_tickets)) }
            TextButton(onClick = dismiss) { Text(words(R.string.ui_dismiss_celebration)) }
        }
    }
}

/** Decorative, finite motion. Its message and the game controls remain independently usable. */
@Composable
internal fun WinConfetti(eventId: String, reducedMotion: Boolean, modifier: Modifier = Modifier) {
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
                drawRect(colors[index % colors.size].copy(alpha = (1 - p) * .22f), Offset(x, y), Size(4.dp.toPx(), 7.dp.toPx()))
            }
        }
    }
}
