package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.COIN_TICKET_PRICE
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.online.OnlineUiState
import io.github.sbshrey.tambola.game.presentation.roundStandings
import kotlinx.coroutines.launch

@Composable
internal fun CoinRoundSummary(state: OnlineUiState, lobby: () -> Unit, replay: (Int) -> Unit,
    retry: () -> Unit, clearError: () -> Unit, reducedMotion: Boolean = false) {
    val words = gameText()
    val room = requireNotNull(state.room)
    val game = requireNotNull(room.round)
    val rows = remember(game.players, game.winnings) { roundStandings(game.players, game.winnings) }
    val ownerIndex = rows.indexOfFirst { it.player.id == state.playerId }.coerceAtLeast(0)
    val own = rows.getOrNull(ownerIndex)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = ownerIndex)
    val scope = rememberCoroutineScope()
    var confirm by rememberSaveable(game.id) { mutableStateOf(false) }
    val tickets = (room.coins?.ownTickets ?: state.preferredTickets).coerceIn(1, 6)
    val cost = tickets * COIN_TICKET_PRICE
    val enabled = !state.busy && !state.pending && !state.storageFailure && !state.sessionExpired && state.available
    val affordable = (state.wallet?.balance ?: 0) >= cost
    MaterialTheme(colorScheme = GameNightPalette.colors) {
    Surface(Modifier.fillMaxSize().testTag("round-summary"), color = GameNightPalette.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(words(R.string.round_summary_title), Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { scope.launch {
                    if (reducedMotion) list.scrollToItem(ownerIndex) else list.animateScrollToItem(ownerIndex)
                } }, modifier = Modifier.testTag("summary-my-rank")) {
                    Text(words(R.string.round_summary_find_me), fontSize = 12.sp)
                }
            }
            own?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(words(R.string.round_summary_you, it.rank), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(words(R.string.round_summary_total, it.winnings.total), fontSize = 15.sp, color = GameNightPalette.gold)
                }
                if (it.winnings.returned > 0) Text(words(R.string.round_summary_returned, it.winnings.returned), fontSize = 11.sp)
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("summary-rankings"),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows, key = { it.player.id }) { row ->
                    val mine = row.player.id == state.playerId
                    val prizes = game.awards.filter { row.player.id in it.playerIds }.map { words.prizeTitle(it.prize) }
                    Surface(Modifier.fillMaxWidth().testTag("summary-player-${row.player.id}")
                        .semantics { selected = mine }, shape = RoundedCornerShape(14.dp),
                        color = if (mine) GameNightPalette.raised else GameNightPalette.panel,
                        border = if (mine) BorderStroke(2.dp, GameNightPalette.mint) else null) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${row.rank}", fontSize = 13.sp, color = GameNightPalette.muted)
                                Text(row.player.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("${row.winnings.total}", fontSize = 14.sp, color = GameNightPalette.gold)
                            }
                            Text(prizes.joinToString(" · ").ifEmpty { words(R.string.round_summary_no_prizes) }, fontSize = 11.sp, lineHeight = 15.sp)
                            if (row.winnings.bonus > 0) Text(words(R.string.round_summary_bonus, row.winnings.bonus), fontSize = 11.sp, color = GameNightPalette.mint)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = lobby, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("summary-lobby")) {
                    Text(words(R.string.round_summary_lobby), fontSize = 13.sp)
                }
                Button(onClick = { if (state.pending) retry() else confirm = true }, enabled = if (state.pending) !state.busy && !state.storageFailure else enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("summary-replay")) {
                    Text(words(if (state.pending) R.string.coin_retry else R.string.round_summary_again), fontSize = 13.sp)
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(words(R.string.round_summary_again)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(words(R.string.round_summary_confirm,
                pluralStringResource(R.plurals.ticket_count, tickets, tickets), cost))
            if (!affordable) Text(words(R.string.round_summary_low_balance, state.wallet?.balance ?: 0L))
        } },
        confirmButton = { TextButton(onClick = { confirm = false; replay(tickets) }, enabled = enabled && affordable,
            modifier = Modifier.testTag("summary-confirm-replay")) { Text(words(R.string.coin_play, cost)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(words(R.string.round_summary_cancel)) } })
    state.error?.let { message -> AlertDialog(onDismissRequest = clearError, text = { Text(words.message(message)) },
        confirmButton = { TextButton(onClick = clearError) { Text(words(R.string.ui_got_it)) } }) }
    }
}
