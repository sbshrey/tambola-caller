package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.game.R

/** Stable playfield: calls/recovery never replace tickets or change their bounds. */
@Composable
internal fun CoinRoundLayout(
    table: TableRound, remaining: Int, status: String, options: @Composable () -> Unit,
    players: () -> Unit, prizes: () -> Unit, board: () -> Unit, repeatCall: () -> Unit,
    power: @Composable () -> Unit, clock: @Composable () -> Unit, showCountdown: Boolean, reducedMotion: Boolean,
    tickets: @Composable () -> Unit, feedback: @Composable () -> Unit,
    claimPanel: (@Composable () -> Unit)? = null,
) {
    val words = gameText()
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxSize().testTag("coin-round-layout"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(if (largeText) 76.dp else 56.dp)
            .background(GameNightPalette.panel, RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options()
            TextButton(onClick = players, modifier = Modifier.testTag("table-players"), contentPadding = PaddingValues(4.dp)) {
                Text(words(R.string.play_players_short, table.players.size), fontSize = 12.sp, maxLines = 2)
            }
            TextButton(onClick = prizes, contentPadding = PaddingValues(4.dp), modifier = Modifier.testTag("round-prizes")) {
                Text(words(R.string.play_prizes_left, remaining), fontSize = 12.sp, maxLines = 2)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.widthIn(max = 200.dp).fillMaxHeight().testTag("round-power-slot"), contentAlignment = Alignment.CenterEnd) { power() }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.weight(.30f).fillMaxHeight().testTag("persistent-call-board"),
                color = GameNightPalette.panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().height(if (largeText) 88.dp else 70.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(64.dp).height(64.dp), contentAlignment = Alignment.Center) {
                        if (showCountdown && table.nextDrawAt != null && !table.finished)
                            DeadlineRing(table.nextDrawAt, table.serverTime, table.id, table.callIntervalSeconds * 1_000L,
                                reducedMotion, Modifier.fillMaxSize())
                        TextButton(onClick = repeatCall, enabled = table.latest != null,
                            contentPadding = PaddingValues(2.dp), modifier = Modifier.width(64.dp).fillMaxHeight()
                                .testTag("current-call").semantics {
                                    contentDescription = table.latest?.let { words(R.string.ui_current_number, it) } ?: words(R.string.play_waiting)
                                    liveRegion = LiveRegionMode.Polite
                                }) {
                            Text(table.latest?.toString() ?: "?", fontSize = 34.sp, fontWeight = FontWeight.Black,
                                color = GameNightPalette.gold, modifier = Modifier.clearAndSetSemantics {})
                        }
                        }
                        // Recovery and normal timing share one fixed slot, away from the hand.
                        Box(Modifier.weight(1f).fillMaxHeight().testTag("round-clock-slot"), contentAlignment = Alignment.Center) { clock() }
                    }
                    Row(Modifier.fillMaxWidth().height(if (largeText) 44.dp else 28.dp).testTag("recent-calls"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        table.called.takeLast(5).forEach { number ->
                            Surface(Modifier.weight(1f).fillMaxHeight(), color = GameNightPalette.raised, shape = RoundedCornerShape(6.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("$number", fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1,
                                    modifier = Modifier.testTag("recent-call-$number")) }
                            }
                        }
                    }
                    Surface(onClick = board, modifier = Modifier.weight(1f).fillMaxWidth().testTag("open-call-history"),
                        color = GameNightPalette.panel) { CalledNumberGrid(table.called, words, fixedColumns = 10) }
                    Text(status, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = GameNightPalette.muted, modifier = Modifier.testTag("play-status"))
                }
            }
            Box(Modifier.weight(.70f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().then(if (claimPanel != null) Modifier
                .graphicsLayer { alpha = 0f }.clearAndSetSemantics {}
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                } else Modifier)) {
                Box(Modifier.weight(1f).fillMaxWidth()) { tickets() }
                feedback()
            }
            claimPanel?.invoke()
            }
        }
    }
}
