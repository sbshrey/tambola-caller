package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.game.R

internal object GameNightPalette {
    val background = Color(0xFF19162F)
    val panel = Color(0xFF28223F)
    val raised = Color(0xFF37304F)
    val cream = Color(0xFFFFF5DF)
    val muted = Color(0xFFC1B8D5)
    val coral = Color(0xFFFF8966)
    val gold = Color(0xFFFFD575)
    val mint = Color(0xFFA8E3CB)
    val ticketCell = Color(0xFFFFF9EF)
    val ticketBlank = Color(0xFFE6D9BB)
    val ticketInk = Color(0xFF463B35)
    val ticketDab = Color(0xFFF2B878)
    val ticketEdge = Color(0xFFB85A35)
    val colors = NightColors.copy(primary = coral, onPrimary = background, primaryContainer = raised, onPrimaryContainer = cream,
        secondary = mint, onSecondary = background, background = background, onBackground = cream,
        surface = panel, onSurface = cream, surfaceContainer = panel, surfaceContainerHigh = raised,
        surfaceContainerLowest = background, surfaceContainerLow = background, surfaceContainerHighest = raised,
        surfaceVariant = raised, surfaceBright = raised, surfaceDim = background, surfaceTint = coral,
        onSurfaceVariant = muted, outline = muted, outlineVariant = raised)
}

@Composable
internal fun LobbyBackdrop(modifier: Modifier) {
    Canvas(modifier) {
        drawCircle(GameNightPalette.panel.copy(alpha = .7f), size.width * .44f, Offset(size.width * .88f, size.height * .4f))
        drawCircle(GameNightPalette.raised.copy(alpha = .25f), size.width * .42f, Offset(size.width * .48f, size.height * 1.7f))
    }
}

@Composable
internal fun LobbyGreeting(name: String?, compact: Boolean) {
    val words = gameText()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (name == null) words(R.string.lobby_welcome) else words(R.string.lobby_greeting, name),
            color = GameNightPalette.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(words(if (name == null) R.string.lobby_first_heading else R.string.lobby_heading),
            color = GameNightPalette.cream, fontSize = if (compact) 28.sp else 36.sp,
            lineHeight = if (compact) 32.sp else 40.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.testTag(if (name == null) "lobby-welcome-heading" else "lobby-heading"))
        Text(words(R.string.lobby_pace), color = GameNightPalette.mint, fontSize = 12.sp)
    }
}

@Composable
internal fun LobbySettingsButton(settings: () -> Unit) {
    val words = gameText()
    IconButton(onClick = settings, modifier = Modifier.size(48.dp).testTag("lobby-settings")
        .semantics { contentDescription = words(R.string.ui_settings) }) {
        Canvas(Modifier.size(22.dp)) {
            val ink = GameNightPalette.cream
            drawCircle(ink, size.minDimension * .29f, style = Stroke(2.dp.toPx()))
            drawCircle(ink, size.minDimension * .09f)
            repeat(8) { tooth -> rotate(tooth * 45f) {
                drawLine(ink, Offset(center.x, size.height * .02f), Offset(center.x, size.height * .18f), 3.dp.toPx())
            } }
        }
    }
}
