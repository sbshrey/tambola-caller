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
fun Eyebrow(text: String, color: Color = Jade) = Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

@Composable
fun GameCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun PrimaryAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
        Text(text, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
fun NumberBall(number: Int?, reducedMotion: Boolean, compact: Boolean = false) {
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
                modifier = Modifier.semantics { contentDescription = number?.let { "Current number $it" } ?: "Ready to call numbers"; liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
fun TicketCard(ticket: Ticket, round: TableRound, haptics: Boolean, onMark: (String, Int) -> Unit) {
    var edit by rememberSaveable(ticket.id) { mutableStateOf(false) }
    val player = round.players.first { it.id == ticket.playerId }
    val marked = round.marks[ticket.id].orEmpty()
    val called = round.called.toSet()
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ivory).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(player.name + if (player.computer) " · computer" else "", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${marked.size}/15", color = Color(0xFF426452), fontSize = 12.sp)
        }
        if (largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..2).forEach { row ->
                Text(listOf("Top row", "Middle row", "Bottom row")[row], color = Ink, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ticket.row(row).forEach { number ->
                        Text(number.toString(), color = if (number in marked) Color.White else Ink,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (number in marked) Color(0xFF276950) else Color.White).padding(10.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..2).forEach { row ->
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${listOf("Top", "Middle", "Bottom")[row]} row: ${ticket.row(row).joinToString()}. Marked: ${ticket.row(row).filter { it in marked }.joinToString().ifEmpty { "none" }}" }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0..8).forEach { col ->
                        val number = ticket.cells[row * 9 + col]
                        val isMarked = number in marked
                        val fill = when { number == 0 -> Color(0xFFE8E1D5); isMarked -> Color(0xFF276950); else -> Color.White }
                        Box(Modifier.weight(1f).aspectRatio(.86f).clip(RoundedCornerShape(5.dp)).background(fill)
                            .then(if (number in called && !isMarked) Modifier.border(2.dp, Color(0xFFBC741E), RoundedCornerShape(5.dp)) else Modifier), contentAlignment = Alignment.Center) {
                            if (number != 0) Text(number.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isMarked) Color.White else Ink, modifier = Modifier.clearAndSetSemantics {})
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${ticket.numbers.count { it in called }} called · ticket ${ticket.id.substringAfterLast('-')}", color = Color(0xFF59665E), fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { edit = true }, colors = ButtonDefaults.textButtonColors(contentColor = Ink)) { Text(if (round.settings.assistedMarking || player.computer || round.finished) "Enlarge" else "Mark ticket") }
        }
    }
    if (edit) {
        val feedback = LocalHapticFeedback.current
        AlertDialog(onDismissRequest = { edit = false }, title = { Text(if (player.name == "You") stringResource(R.string.your_ticket) else stringResource(R.string.named_ticket, player.name)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (round.settings.assistedMarking || player.computer) "Numbers are marked automatically." else "Tap a called number to mark or unmark it. Amber numbers have been called.", style = MaterialTheme.typography.bodyMedium)
                (0..2).forEach { row ->
                    Text(listOf("Top line", "Middle line", "Bottom line")[row], color = Muted, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ticket.row(row).forEach { number ->
                            val isMarked = number in marked
                            val active = number in called
                            val enabled = active && !round.settings.assistedMarking && !player.computer && !round.finished
                            Box(Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (isMarked) Jade else if (active) Saffron else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable(enabled = enabled, role = Role.Checkbox) { if (haptics) feedback.performHapticFeedback(HapticFeedbackType.ToggleOn); onMark(ticket.id, number) }
                                .semantics { contentDescription = "Number $number"; stateDescription = if (isMarked) "Marked" else if (active) "Called, unmarked" else "Not called" }, contentAlignment = Alignment.Center) {
                                Text(number.toString(), color = if (isMarked) MaterialTheme.colorScheme.onSecondary else if (active) MaterialTheme.colorScheme.onPrimary else Muted, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { edit = false }) { Text("Done") } })
    }
}
