package io.github.sbshrey.tambola.game.ui

import io.github.sbshrey.tambola.game.R

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Stable wire IDs 0..7. Original vector-like Canvas art; no downloads or profile photographs. */
enum class GameAvatar(val label: String, val paper: Color) {
    SUN("Sun", Color(0xFFFFE5B3)), MANGO("Mango", Color(0xFFDDECCB)),
    CHAI("Chai", Color(0xFFF2DECB)), PEACOCK("Peacock", Color(0xFFD1EAE8)),
    LOTUS("Lotus", Color(0xFFFFDCE0)), LADOO("Ladoo", Color(0xFFFFE9C7)),
    KITE("Kite", Color(0xFFDCEAF9)), MOON("Moon", Color(0xFF253853));
    companion object { fun from(id: Int) = entries.getOrElse(id) { SUN } }
}

@Composable
fun AvatarBadge(id: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val words = gameText()
    val avatar = GameAvatar.from(id)
    Canvas(modifier.size(size).clip(CircleShape).background(avatar.paper)
        .semantics { contentDescription = words(R.string.ui_avatar, words.avatar(avatar.ordinal)) }) {
        withTransform({ scale(this@Canvas.size.width / 100f, this@Canvas.size.height / 100f, Offset.Zero) }) {
            val ink = Color(0xFF23453B)
            val amber = Color(0xFFE79A25)
            val leaf = Color(0xFF367557)
            val coral = Color(0xFFD86665)
            fun path(fill: Color, outline: Boolean = false, block: Path.() -> Unit) {
                val shape = Path().apply(block)
                drawPath(shape, fill)
                if (outline) drawPath(shape, ink.copy(alpha = .7f), style = Stroke(1.8f))
            }
            when (avatar) {
                GameAvatar.SUN -> {
                    repeat(12) { ray ->
                        val angle = ray * PI / 6
                        drawLine(amber, Offset(50 + cos(angle).toFloat() * 31, 50 + sin(angle).toFloat() * 31),
                            Offset(50 + cos(angle).toFloat() * 39, 50 + sin(angle).toFloat() * 39), 3f)
                    }
                    drawCircle(amber, 25f, Offset(50f, 50f))
                    drawCircle(ink, 2.2f, Offset(42f, 47f)); drawCircle(ink, 2.2f, Offset(58f, 47f))
                    drawArc(ink, 10f, 160f, false, Offset(41f, 49f), Size(18f, 13f), style = Stroke(2f))
                }
                GameAvatar.MANGO -> {
                    path(amber, true) { moveTo(55f, 25f); cubicTo(85f, 31f, 79f, 73f, 47f, 82f); cubicTo(22f, 89f, 18f, 56f, 36f, 45f); quadraticTo(50f, 35f, 55f, 25f); close() }
                    path(leaf) { moveTo(54f, 27f); quadraticTo(59f, 8f, 79f, 19f); quadraticTo(72f, 33f, 54f, 27f); close() }
                    drawLine(ink, Offset(52f, 32f), Offset(55f, 18f), 3f)
                    drawArc(Color(0xFFFFD576), 120f, 110f, false, Offset(31f, 42f), Size(26f, 33f), style = Stroke(3f))
                }
                GameAvatar.CHAI -> {
                    drawArc(ink, -90f, 180f, false, Offset(63f, 43f), Size(22f, 22f), style = Stroke(5f))
                    path(Color(0xFFF8F1E3), true) { moveTo(24f, 42f); lineTo(70f, 42f); lineTo(66f, 70f); quadraticTo(48f, 83f, 28f, 70f); close() }
                    drawOval(Color(0xFF965A35), Offset(26f, 37f), Size(42f, 12f))
                    drawArc(ink.copy(alpha = .5f), 0f, 180f, false, Offset(19f, 69f), Size(58f, 15f), style = Stroke(3f))
                    listOf(37f, 52f, 66f).forEach { x ->
                        drawPath(Path().apply { moveTo(x, 31f); cubicTo(x - 8, 25f, x + 6, 20f, x, 13f) }, ink.copy(alpha = .5f), style = Stroke(2.5f))
                    }
                }
                GameAvatar.PEACOCK -> {
                    listOf(-55f, -28f, 0f, 28f, 55f).forEach { angle -> rotate(angle, Offset(50f, 69f)) {
                        drawOval(leaf, Offset(39f, 14f), Size(22f, 53f))
                        drawOval(amber, Offset(43f, 22f), Size(14f, 19f))
                        drawCircle(Color(0xFF244967), 4f, Offset(50f, 30f))
                    } }
                    drawOval(Color(0xFF23777D), Offset(38f, 49f), Size(24f, 36f))
                    drawCircle(Color(0xFF23777D), 9f, Offset(54f, 46f))
                    path(amber) { moveTo(61f, 44f); lineTo(71f, 49f); lineTo(61f, 50f); close() }
                    drawCircle(Color.White, 2.5f, Offset(56f, 43f)); drawCircle(ink, 1.4f, Offset(57f, 43f))
                }
                GameAvatar.LOTUS -> {
                    path(leaf) { moveTo(16f, 72f); quadraticTo(32f, 89f, 49f, 77f); quadraticTo(70f, 89f, 84f, 72f); quadraticTo(53f, 69f, 16f, 72f); close() }
                    listOf(-40f, 40f, -20f, 20f, 0f).forEachIndexed { i, angle -> rotate(angle, Offset(50f, 74f)) {
                        path(if (i % 2 == 0) coral else Color(0xFFF09799), true) { moveTo(50f, 17f); quadraticTo(21f, 58f, 50f, 77f); quadraticTo(79f, 58f, 50f, 17f); close() }
                    } }
                }
                GameAvatar.LADOO -> {
                    drawOval(Color(0xFFFAF6EA), Offset(14f, 62f), Size(72f, 22f))
                    listOf(Offset(34f, 61f), Offset(66f, 61f), Offset(50f, 36f)).forEach { center ->
                        drawCircle(amber, 19f, center)
                        repeat(8) { i ->
                            val angle = i * 2.4f
                            val point = center + Offset(cos(angle) * (6 + i), sin(angle) * (6 + i))
                            drawCircle(Color(0xFFFFD273), 1.8f, point)
                        }
                        drawLine(leaf, center + Offset(-4f, -8f), center + Offset(2f, -11f), 2f)
                    }
                }
                GameAvatar.KITE -> {
                    path(coral, true) { moveTo(50f, 12f); lineTo(78f, 37f); lineTo(50f, 66f); lineTo(22f, 37f); close() }
                    path(amber) { moveTo(50f, 13f); lineTo(50f, 65f); lineTo(23f, 37f); close() }
                    drawLine(ink, Offset(50f, 12f), Offset(50f, 66f), 1.7f)
                    drawLine(ink, Offset(22f, 37f), Offset(78f, 37f), 1.7f)
                    drawPath(Path().apply { moveTo(50f, 66f); cubicTo(70f, 75f, 34f, 79f, 49f, 91f) }, ink, style = Stroke(2f))
                    path(coral) { moveTo(48f, 74f); lineTo(61f, 83f); lineTo(60f, 72f); lineTo(46f, 82f); close() }
                }
                GameAvatar.MOON -> {
                    path(Color(0xFFFFE5B3)) { moveTo(64f, 18f); cubicTo(26f, 3f, 8f, 62f, 42f, 79f); cubicTo(60f, 91f, 79f, 76f, 83f, 61f); cubicTo(45f, 79f, 27f, 35f, 64f, 18f); close() }
                    listOf(Offset(76f, 28f), Offset(58f, 43f), Offset(84f, 46f)).forEach { star ->
                        drawLine(Color.White, star - Offset(4f, 0f), star + Offset(4f, 0f), 2f)
                        drawLine(Color.White, star - Offset(0f, 4f), star + Offset(0f, 4f), 2f)
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarChoice(owner: String, selected: Int, enabled: Boolean = true, choose: (Int) -> Unit) {
    val words = gameText()
    var open by rememberSaveable(owner) { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        AvatarBadge(selected)
        Text(words(R.string.ui_nchoose_avatar, owner, words.avatar(selected)), modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(words(R.string.ui_avatar_for, owner)) }, text = {
        val tileSize = 82.dp * LocalDensity.current.fontScale.coerceAtLeast(1f).coerceAtMost(2f)
        FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GameAvatar.entries.forEach { avatar ->
                val active = avatar.ordinal == selected
                Column(Modifier.width(tileSize).clip(RoundedCornerShape(16.dp))
                    .background(if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(if (active) 2.dp else 1.dp, if (active) Jade else Muted, RoundedCornerShape(16.dp))
                    .selectable(active, role = Role.RadioButton) { choose(avatar.ordinal); open = false }
                    .semantics { contentDescription = words(R.string.ui_avatar, words.avatar(avatar.ordinal)); stateDescription = if (active) words(R.string.ui_selected) else words(R.string.ui_not_selected) }
                    .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AvatarBadge(avatar.ordinal, Modifier.clearAndSetSemantics {}, size = 48.dp)
                    Text(words.avatar(avatar.ordinal), color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface, modifier = Modifier.clearAndSetSemantics {})
                    Text(if (active) "✓" else " ", color = Jade, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text(words(R.string.ui_keep_current_avatar)) } })
}
