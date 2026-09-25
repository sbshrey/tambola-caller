package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original, resolution-independent decoration. It is never presented as a playable ticket. */
@Composable
fun GameNightArtwork() {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(colors.secondaryContainer, colors.surfaceContainer)))
        .clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .5f, size.height * .52f)
            repeat(3) { ring -> drawCircle(colors.onSecondaryContainer.copy(alpha = .08f), size.width * (.32f + ring * .13f), center, style = Stroke(1.dp.toPx())) }
            val card = Size(size.width * .48f, size.height * .56f)
            val origin = Offset(size.width * .18f, size.height * .22f)
            rotate(-12f, origin + Offset(card.width / 2, card.height / 2)) {
                drawRoundRect(Ink.copy(alpha = .12f), origin + Offset(4.dp.toPx(), 7.dp.toPx()), card, CornerRadius(10.dp.toPx()))
                drawRoundRect(Ivory, origin, card, CornerRadius(10.dp.toPx()))
                val gap = 3.dp.toPx()
                val cell = Size((card.width - 24.dp.toPx()) / 9, (card.height - 20.dp.toPx()) / 3)
                repeat(27) { i ->
                    val location = origin + Offset(12.dp.toPx() + i % 9 * cell.width, 10.dp.toPx() + i / 9 * cell.height)
                    val fill = if ((i * 7) % 5 < 2) Color(0xFFE8E1D5) else Color(0xFFD4E8DC)
                    drawRoundRect(fill, location, Size(cell.width - gap, cell.height - gap), CornerRadius(2.dp.toPx()))
                    if (i in listOf(2, 11, 16, 22)) drawCircle(Color(0xFF276950), cell.width * .24f, location + Offset(cell.width / 2, cell.height / 2))
                }
            }
            listOf(.12f to .3f, .84f to .24f, .82f to .75f).forEach { (x, y) ->
                val point = Offset(size.width * x, size.height * y)
                drawLine(colors.primary.copy(alpha = .5f), point - Offset(4.dp.toPx(), 0f), point + Offset(4.dp.toPx(), 0f), 2.dp.toPx())
                drawLine(colors.primary.copy(alpha = .5f), point - Offset(0f, 4.dp.toPx()), point + Offset(0f, 4.dp.toPx()), 2.dp.toPx())
            }
        }
        Box(Modifier.align(Alignment.Center).offset(x = 45.dp, y = 2.dp).size(90.dp)
            .background(Brush.linearGradient(listOf(Color(0xFFFFE1B3), Color(0xFFF3A456))), CircleShape), contentAlignment = Alignment.Center) {
            Text("22", color = Ink, fontSize = 38.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.align(Alignment.Center).offset(x = 91.dp, y = 37.dp).rotate(12f).size(52.dp)
            .background(Color(0xFF8CDBBB), CircleShape), contentAlignment = Alignment.Center) {
            Text("7", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}
