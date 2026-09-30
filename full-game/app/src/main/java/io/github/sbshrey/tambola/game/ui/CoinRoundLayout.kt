package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.selectableGroup
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
    var showBoard by rememberSaveable(table.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("coin-round-layout"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(if (largeText) 84.dp else 64.dp)
            .background(GameNightPalette.panel, RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options()
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (showCountdown && table.nextDrawAt != null && !table.finished)
                    DeadlineRing(table.nextDrawAt, table.serverTime, table.id, table.callIntervalSeconds * 1_000L,
                        reducedMotion, Modifier.fillMaxSize())
                TextButton(onClick = repeatCall, enabled = table.latest != null,
                    contentPadding = PaddingValues(2.dp), modifier = Modifier.fillMaxSize().testTag("current-call")
                        .semantics {
                            contentDescription = table.latest?.let { words(R.string.ui_current_number, it) } ?: words(R.string.play_waiting)
                            liveRegion = LiveRegionMode.Polite
                        }) {
                    Text(table.latest?.toString() ?: "?", fontSize = 28.sp, fontWeight = FontWeight.Black,
                        color = GameNightPalette.gold, modifier = Modifier.clearAndSetSemantics {})
                }
            }
            Row(Modifier.weight(1f).testTag("recent-calls"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                table.called.dropLast(1).takeLast(if (largeText) 2 else 4).forEach { number ->
                    Surface(Modifier.weight(1f).height(36.dp), color = GameNightPalette.raised, shape = RoundedCornerShape(8.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("$number", fontSize = 14.sp,
                            modifier = Modifier.testTag("recent-call-$number")) }
                    }
                }
            }
            Box(Modifier.width(if (largeText) 100.dp else 72.dp).fillMaxHeight().testTag("round-clock-slot"), contentAlignment = Alignment.Center) { clock() }
            Box(Modifier.width(if (largeText) 180.dp else 150.dp).fillMaxHeight().testTag("round-power-slot")) { power() }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).selectableGroup()) {
                listOf(false to R.string.play_tickets_tab, true to R.string.play_board_tab).forEach { (value, label) ->
                    Tab(selected = showBoard == value, onClick = { showBoard = value }, enabled = claimPanel == null,
                        modifier = Modifier.weight(1f).background(if (showBoard == value) GameNightPalette.raised else GameNightPalette.background, RoundedCornerShape(12.dp)).testTag(if (value) "play-board-tab" else "play-tickets-tab"),
                        text = { Text(words(label), maxLines = 1) })
                }
            }
            TextButton(onClick = players, modifier = Modifier.testTag("table-players")) {
                Text(words(R.string.play_players_short, table.players.size), fontSize = 12.sp)
            }
            TextButton(onClick = prizes, modifier = Modifier.testTag("round-prizes")) {
                Text(words(R.string.play_prizes_left, remaining), fontSize = 12.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Keep the ticket composition alive so switching tabs preserves the selected page.
            Column(Modifier.fillMaxSize().then(if (showBoard || claimPanel != null) Modifier
                .graphicsLayer { alpha = 0f }.clearAndSetSemantics {}
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                } else Modifier)) {
                Box(Modifier.weight(1f).fillMaxWidth()) { tickets() }
                feedback()
            }
            if (showBoard) Surface(Modifier.fillMaxSize().testTag("persistent-call-board"),
                color = GameNightPalette.panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(words(R.string.ui_called_to_go, table.called.size, 90 - table.called.size), Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = board, modifier = Modifier.testTag("open-call-history")) { Text(words(R.string.board_history)) }
                    }
                    Box(Modifier.weight(1f)) { CalledNumberGrid(table.called, words) }
                }
            }
            claimPanel?.invoke()
        }
    }
}
