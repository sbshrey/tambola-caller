package io.github.sbshrey.tambola.game.ui

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.delay

private val CoinGold = Color(0xFFF4C879)

/** A server timestamp plus elapsed time avoids jumps when the device clock changes. */
@Composable
internal fun serverNow(room: RoomView?): Long {
    val anchor = remember(room?.roomId, room?.serverTime) { SystemClock.elapsedRealtime() }
    var elapsed by remember(room?.roomId, room?.serverTime) { mutableLongStateOf(0) }
    LaunchedEffect(room?.roomId, room?.serverTime) {
        while (true) { elapsed = SystemClock.elapsedRealtime() - anchor; delay(200) }
    }
    return (room?.serverTime ?: remember { System.currentTimeMillis() }) + elapsed
}

@Composable
fun CoinLobby(state: OnlineUiState, model: OnlineViewModel, play: (Int) -> Unit, resume: () -> Unit, settings: () -> Unit) {
    val words = gameText()
    val room = state.room
    val coins = room?.coins
    val now = serverNow(room)
    val waiting = room?.phase == RoomPhase.LOBBY
    val finished = room?.phase == RoomPhase.FINISHED
    val active = room?.phase == RoomPhase.ACTIVE
    val enabled = !state.loading && !state.busy && !state.pending && !state.storageFailure && !state.sessionExpired && state.available
    var tickets by rememberSaveable { mutableIntStateOf(3) }
    var profile by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var gameData by remember { mutableStateOf(false) }
    val balance = state.wallet?.balance ?: if (state.name == null) COIN_STARTER_BALANCE else null
    val cost = tickets * COIN_TICKET_PRICE
    Surface(Modifier.fillMaxSize().testTag("coin-lobby"), color = Color(0xFF0B352E), contentColor = Ivory) {
      BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth > 650.dp && maxWidth > maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("T", color = Ink, fontWeight = FontWeight.Black, fontSize = 23.sp,
                    modifier = Modifier.background(CoinGold, CircleShape).padding(horizontal = 13.dp, vertical = 7.dp))
                Text(words(R.string.ui_tambola_together), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { profile = true }, modifier = Modifier.testTag("coin-wallet")) {
                    Text(balance?.let { words(R.string.coin_balance, it) } ?: "…", color = CoinGold, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = settings) { Text(words(R.string.ui_settings), color = Ivory, fontSize = 12.sp) }
            }
            val hero: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (waiting) {
                        val seconds = (((coins?.startsAt ?: now) - now + 999) / 1000).coerceAtLeast(0)
                        Text(if (seconds > 0) words(R.string.coin_starts, seconds) else words(R.string.coin_starting),
                            fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.testTag("match-countdown"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            room.members.take(8).forEach { member -> AvatarBadge(member.avatar, size = 40.dp) }
                        }
                        if (room.options.computerPlayers > 0) Text("${words(R.string.ui_computer_players)} · ${room.options.computerPlayers}", color = Color(0xFFB7D0C0), fontSize = 12.sp)
                        Text(words(R.string.coin_choose_count, coins?.ownTickets ?: 0) + " · " + words(R.string.coin_pool_preview, coins?.pool ?: 0), color = CoinGold, fontWeight = FontWeight.Bold)
                        CoinPrizeGrid(coins?.prizes.orEmpty(), Modifier.fillMaxWidth())
                        TextButton(onClick = { model.command(RoomAction.Leave) }, enabled = enabled && state.connection == Connection.LIVE,
                            modifier = Modifier.testTag("cancel-match")) { Text(words(R.string.coin_cancel), color = Ivory) }
                    } else {
                        Text(words(if (finished) R.string.coin_results else R.string.play_tagline), fontSize = if (wide) 26.sp else 32.sp,
                            lineHeight = 38.sp, fontWeight = FontWeight.Black)
                        if (finished) {
                            Text(words(R.string.coin_won, coins?.settledWinnings ?: 0), color = CoinGold, fontSize = 28.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("coin-winnings"))
                            if ((coins?.returnedCoins ?: 0) > 0) Text(words(R.string.coin_returned, coins!!.returnedCoins), fontSize = 13.sp, color = Color(0xFFB7D0C0))
                            CoinPrizeGrid(coins?.prizes.orEmpty(), Modifier.fillMaxWidth(), room?.round?.awards.orEmpty())
                        } else Box(Modifier.fillMaxWidth().height(if (wide) 132.dp else 168.dp)) { GameNightArtwork() }
                    }
                }
            }
            val controls: @Composable () -> Unit = {
              if (!waiting || state.pending || state.connection != Connection.LIVE) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Color(0xFFF7F2E5), contentColor = Ink) {
                  Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (waiting) {
                        if (state.connection != Connection.LIVE) TextButton(onClick = model::reconnect) { Text(words(R.string.play_reconnecting)) }
                    } else if (active) {
                        Text(words(R.string.coin_pool, coins?.pool ?: 0), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Button(onClick = resume, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("resume-match")) { Text(words(R.string.coin_resume)) }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(words(R.string.coin_tickets), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(words(R.string.coin_price), fontSize = 12.sp, color = Color(0xFF486257))
                        }
                        BoxWithConstraints {
                            val columns = if (maxWidth < 330.dp) 3 else 6
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                              (1..6).toList().chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { count ->
                                    val selected = tickets == count
                                    Surface(onClick = { tickets = count }, enabled = enabled, shape = RoundedCornerShape(13.dp),
                                        color = if (selected) Color(0xFF21634D) else Color.White, contentColor = if (selected) Color.White else Ink,
                                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("buy-tickets-$count")
                                            .semantics { this.selected = selected; contentDescription = words(R.string.coin_choose_count, count) }) {
                                        Box(contentAlignment = Alignment.Center) { Text("$count", fontSize = 22.sp, fontWeight = FontWeight.Black) }
                                    }
                                }
                              } }
                            }
                        }
                        if (balance != null && balance < COIN_TICKET_PRICE) {
                            val seconds = (((state.wallet?.refillAfter ?: 0) - now + 999) / 1000).coerceAtLeast(0)
                            Button(onClick = model::refill, enabled = enabled && seconds == 0L,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("coin-refill")) {
                                Text(if (seconds > 0) words(R.string.coin_refill_timer, seconds / 60, seconds % 60) else words(R.string.coin_collect))
                            }
                        } else {
                            Button(onClick = { play(tickets) }, enabled = enabled && (balance == null || balance >= cost),
                                colors = ButtonDefaults.buttonColors(containerColor = CoinGold, contentColor = Ink),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("coin-play"), shape = RoundedCornerShape(16.dp)) {
                                if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Ink)
                                else Text(words(if (finished) R.string.coin_play_again else R.string.coin_play, cost), fontSize = 19.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    if (state.pending) {
                        Text(words(R.string.coin_pending), fontSize = 13.sp)
                        OutlinedButton(onClick = model::retry, enabled = !state.busy && !state.storageFailure, modifier = Modifier.fillMaxWidth().testTag("coin-retry")) { Text(words(R.string.coin_retry)) }
                    }
                    if (state.sessionExpired || state.storageFailure) TextButton(onClick = { reset = true }) { Text(words(R.string.ui_reset_online_data)) }
                    if (!state.available) Text(words(R.string.coin_unavailable), fontSize = 13.sp)
                    Text(words(R.string.coin_free), color = Color(0xFF486257), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                  }
                }
              }
            }
            if (wide) Row(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(if (waiting) 2f else 1f)) { hero() }
                if (!waiting || state.pending || state.connection != Connection.LIVE) Box(Modifier.weight(1f)) { controls() }
            } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { hero(); controls() }
        }
      }
    }
    if (profile) AlertDialog(onDismissRequest = { profile = false }, title = { Text(state.name ?: words(R.string.coin_profile)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(words(R.string.coin_free))
            Text(words(R.string.coin_ties))
            TextButton(onClick = { profile = false; gameData = true }) { Text(words(R.string.privacy_open)) }
            if (state.name != null) TextButton(onClick = { profile = false; delete = true }, enabled = !state.busy && !state.pending && !state.storageFailure) { Text(words(R.string.ui_delete_online_profile)) }
        }
    }, confirmButton = { TextButton(onClick = { profile = false }) { Text(words(R.string.ui_got_it)) } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(words(R.string.ui_delete_online_profile)) },
        text = { Text(words(R.string.ui_this_permanently_removes_your_service_profile_and_access)) },
        confirmButton = { TextButton(onClick = { delete = false; model.deleteProfile() }) { Text(words(R.string.ui_delete_online_profile)) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(words(R.string.ui_keep_playing)) } })
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text(words(R.string.ui_reset_online_data)) },
        text = { Text(words(R.string.coin_reset_warning)) },
        confirmButton = { TextButton(onClick = { reset = false; model.resetLocalData() }) { Text(words(R.string.ui_reset_online_data)) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(words(R.string.ui_keep_playing)) } })
    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text(words(R.string.ui_online_play)) },
        text = { Text(words.message(error)) }, confirmButton = { TextButton(onClick = model::clearError) { Text(words(R.string.ui_got_it)) } }) }
    if (gameData) GameDataDialog { gameData = false }
}

@Composable
internal fun CoinPrizeGrid(prizes: List<CoinPrize>, modifier: Modifier = Modifier, awards: List<Award> = emptyList(), onDark: Boolean = true) {
    val words = gameText()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        prizes.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { entry ->
                Column(Modifier.weight(1f).background(Color.White.copy(alpha = .07f), RoundedCornerShape(12.dp)).padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((if (awards.any { it.prize == entry.prize }) "✓ " else "") + words.prizeTitle(entry.prize), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.coins}", color = if (onDark) CoinGold else MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        } }
    }
}

@Composable
internal fun CoinCallClock(room: RoomView, connection: Connection, reconnect: () -> Unit) {
    val words = gameText()
    val now = serverNow(room)
    val remaining = ((room.nextDrawAt ?: now) - now).coerceAtLeast(0)
    if (connection != Connection.LIVE) TextButton(onClick = reconnect) { Text(words(R.string.play_reconnecting), fontSize = 11.sp) }
    else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (remaining > 0) words(R.string.coin_next, (remaining + 999) / 1000) else words(R.string.coin_next_wait),
            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LinearProgressIndicator(progress = { (remaining / 5000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = CoinGold)
    }
}
