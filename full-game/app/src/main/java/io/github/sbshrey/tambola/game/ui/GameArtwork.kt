package io.github.sbshrey.tambola.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/** Original decoration, excluded from accessibility and never treated as a playable ticket. */
@Composable
fun GameNightArtwork(modifier: Modifier = Modifier, reducedMotion: Boolean = false) {
    // These numbers are illustration pixels, not UI labels or readable game data.
    val density = LocalDensity.current.density
    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1f)) {
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) arrival.snapTo(1f)
        else arrival.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    // Finite arrival only: no idle animation loop or per-frame composition.
    BoxWithConstraints(modifier.clearAndSetSemantics {}.graphicsLayer {
        alpha = arrival.value
        translationY = (1f - arrival.value) * 12.dp.toPx()
    }) {
        val cardWidth = maxWidth * .64f
        val cardHeight = maxHeight * .68f
        val heroSize = minOf(maxHeight * .8f, maxWidth * .31f)
        val rows = listOf(
            listOf(3, null, 22, 31, null, 52, null, 72, null),
            listOf(null, 14, null, 34, 45, null, 64, null, 83),
            listOf(8, null, 27, null, 49, 58, null, null, 90),
        )
        Column(Modifier.align(Alignment.Center).offset(x = -maxWidth * .08f, y = maxHeight * .08f)
            .rotate(-9f).width(cardWidth).height(cardHeight)
            .shadow(8.dp, RoundedCornerShape(12.dp)).background(GameNightPalette.cream, RoundedCornerShape(12.dp))
            .padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(GameNightPalette.gold, RoundedCornerShape(3.dp)))
            rows.forEach { row -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { value -> Box(Modifier.weight(1f).fillMaxHeight()
                    .background(if (value == null) Color(0xFFE7DDC9) else Color(0xFFFFFAEC), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center) {
                    if (value != null) Text("$value", color = GameNightPalette.background, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                } }
            } }
        }
        ArtworkBall(66, heroSize, GameNightPalette.coral, Color(0xFFFFD79B),
            Modifier.align(Alignment.TopEnd).offset(x = -maxWidth * .12f))
        ArtworkBall(5, heroSize * .52f, Color(0xFF72B99F), GameNightPalette.mint,
            Modifier.align(Alignment.BottomStart).offset(x = maxWidth * .06f, y = -maxHeight * .07f))
    }
    }
}

@Composable
private fun ArtworkBall(number: Int, size: Dp, dark: Color, light: Color, modifier: Modifier) {
    Box(modifier.size(size).shadow(6.dp, CircleShape)
        .background(Brush.linearGradient(listOf(light, dark)), CircleShape).padding(size * .17f)
        .background(GameNightPalette.cream, CircleShape), contentAlignment = Alignment.Center) {
        Text("$number", color = GameNightPalette.background, fontSize = (size.value * .34f).sp, fontWeight = FontWeight.Black)
    }
}
