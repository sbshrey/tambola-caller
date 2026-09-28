package io.github.sbshrey.tambola.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.protocol.RoomView
import io.github.sbshrey.tambola.protocol.MemberView
import io.github.sbshrey.tambola.domain.practicePersona
import kotlinx.coroutines.delay

/** Announce actual roster arrivals. Empty seats never impersonate another player. */
@Composable
internal fun TableCountdown(room: RoomView, ownerId: String?, reducedMotion: Boolean) {
    val words = gameText()
    val personas = (1..room.options.computerPlayers).map { practicePersona(room.roomId, it) }
    val members = room.members.sortedBy { it.playerId != ownerId } + personas.map { MemberView(it.id, it.name, it.avatar, true, true) }
    val slots = maxOf(4, members.size).coerceAtMost(8)
    val remaining = remainingCoinTime(room.coins?.startsAt, room.serverTime, room.roomId)
    val seconds by countdownSeconds(remaining)
    Column(Modifier.fillMaxWidth().testTag("table-countdown"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(words(R.string.table_filling), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                DeadlineRing(room.coins?.startsAt, room.serverTime, room.roomId, 12_000, reducedMotion, Modifier.fillMaxSize(), GameNightPalette.coral)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (seconds > 0) "%02d".format(seconds) else "…", fontSize = 32.sp, fontWeight = FontWeight.Black,
                        color = GameNightPalette.gold, modifier = Modifier.testTag("table-start-seconds"))
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(slots) { index ->
                    val member = members.getOrNull(index)
                    key(member?.playerId ?: "empty-$index") {
                        val entrance = remember { Animatable(if (reducedMotion) 1f else 0f) }
                        LaunchedEffect(member?.playerId, reducedMotion) {
                            if (reducedMotion || member == null) entrance.snapTo(1f)
                            else { delay(index * 80L); entrance.animateTo(1f, tween(420)) }
                        }
                        Column(Modifier.weight(1f).graphicsLayer {
                            alpha = entrance.value
                            translationY = (1f - entrance.value) * 16.dp.toPx()
                            scaleX = .88f + .12f * entrance.value; scaleY = scaleX
                        }.testTag("joining-seat-$index"), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (member != null) AvatarBadge(member.avatar, size = 46.dp)
                            else Canvas(Modifier.size(46.dp)) {
                                drawCircle(GameNightPalette.muted.copy(alpha = .4f), style = Stroke(2.dp.toPx()))
                                drawCircle(GameNightPalette.muted.copy(alpha = .25f), radius = 5.dp.toPx())
                            }
                            Text(if (member == null) words(R.string.table_joining) else if (member.playerId == ownerId) words(R.string.table_you) else member.displayName,
                                color = if (member == null) GameNightPalette.muted else GameNightPalette.cream,
                                fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (personas.any { it.id == member?.playerId }) Text(words(R.string.play_computer_short), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Text(members.lastOrNull { it.playerId != ownerId }?.let { words(R.string.table_joined, it.displayName) }
            ?: words(R.string.table_tickets_ready), color = GameNightPalette.mint, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(words(R.string.coin_players, members.size, personas.size), fontSize = 12.sp, modifier = Modifier.testTag("joining-count"))
        Text(words(R.string.practice_disclosure), fontSize = 11.sp, color = GameNightPalette.muted)
    }
}
