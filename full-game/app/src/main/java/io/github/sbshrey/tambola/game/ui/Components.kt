package io.github.sbshrey.tambola.game.ui


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import io.github.sbshrey.tambola.game.R
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.*

@Composable
fun Eyebrow(text: String, color: Color = Jade) {
    val words = gameText()
    Text(text.uppercase(words.locale), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = if (words.locale.language == "hi") 0.sp else 2.sp)
}

@Composable
fun GameCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun PrimaryAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val words = gameText()
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
        Text(text, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
fun NumberBall(number: Int?, reducedMotion: Boolean, compact: Boolean = false) {
    val words = gameText()
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(number, reducedMotion) {
        if (reducedMotion || number == null) reveal.snapTo(1f)
        else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    Box(Modifier.fillMaxWidth().height(if (compact) 116.dp else 200.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (compact) 114.dp else 194.dp).background(Brush.radialGradient(listOf(Saffron.copy(alpha = .18f), Color.Transparent)), CircleShape))
        Box(Modifier.size(if (compact) 98.dp else 156.dp).graphicsLayer {
            val progress = if (reducedMotion) 1f else reveal.value
            scaleX = .86f + .14f * progress; scaleY = scaleX
            translationY = (1f - progress) * -16.dp.toPx(); rotationZ = (1f - progress) * -10f
        }.background(Brush.linearGradient(listOf(Color(0xFFFFE1B3), Color(0xFFFFC078), Color(0xFFF3A456))), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(if (compact) 82.dp else 128.dp).border(1.dp, Ink.copy(alpha = .15f), CircleShape))
            Text(number?.toString() ?: "90", fontSize = if (compact) 46.sp else 70.sp, fontWeight = FontWeight.Black, color = Ink,
                modifier = Modifier.semantics { contentDescription = number?.let { words(R.string.ui_current_number, it) } ?: words(R.string.ui_ready_to_call_numbers); liveRegion = LiveRegionMode.Polite })
        }
    }
}
