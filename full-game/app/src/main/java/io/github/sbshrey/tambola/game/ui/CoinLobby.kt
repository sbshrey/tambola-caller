package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.presentation.ownCoinWins
import io.github.sbshrey.tambola.game.presentation.affordableTickets
import io.github.sbshrey.tambola.client.ServerTime
import io.github.sbshrey.tambola.protocol.*

private val CoinGold = Color(0xFFF4C879)

@Composable
fun CoinLobby(state: OnlineUiState, model: OnlineViewModel, play: (Int) -> Unit, resume: () -> Unit, settings: () -> Unit,
    reducedMotion: Boolean = false, friends: (Int, String?) -> Unit = { tickets, code -> model.play(tickets, true, code) },
    replayFriends: (Int) -> Unit = model::replayFriends, reconnect: () -> Unit = model::reconnect) {
    val words = gameText()
    val room = state.room
    val coins = room?.coins
    val waiting = room?.phase == RoomPhase.LOBBY
    val showControls = !waiting || state.pending || state.sessionExpired || state.storageFailure
    val finished = room?.phase == RoomPhase.FINISHED
    val friendsFinished = finished && coins?.friendTable == true
    val active = room?.phase == RoomPhase.ACTIVE
    val enabled = !state.loading && !state.busy && !state.pending && !state.storageFailure && !state.sessionExpired && state.available
    var chosenTickets by rememberSaveable(state.preferredTickets) { mutableIntStateOf(state.preferredTickets) }
    var profile by remember { mutableStateOf(false) }
    var profileName by rememberSaveable { mutableStateOf("") }
    var profileAvatar by rememberSaveable { mutableIntStateOf(0) }
    var delete by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var gameData by remember { mutableStateOf(false) }
    var friendDialog by rememberSaveable { mutableStateOf(false) }
    val balance = state.wallet?.balance ?: if (state.name == null) COIN_STARTER_BALANCE else null
    val tickets = affordableTickets(chosenTickets, balance)
    val cost = tickets * COIN_TICKET_PRICE
    MaterialTheme(colorScheme = GameNightPalette.colors) {
    Surface(Modifier.fillMaxSize().testTag("coin-lobby"), color = GameNightPalette.background, contentColor = GameNightPalette.cream) {
      BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth > 650.dp && maxWidth > maxHeight
        val compact = maxHeight < 430.dp || LocalDensity.current.fontScale >= 1.3f
        LobbyBackdrop(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("T", color = GameNightPalette.background, fontWeight = FontWeight.Black, fontSize = 23.sp,
                    modifier = Modifier.background(GameNightPalette.coral, CircleShape).padding(horizontal = 13.dp, vertical = 7.dp).clearAndSetSemantics {})
                Text(words(R.string.ui_tambola_together), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(onClick = { profile = true }, color = GameNightPalette.raised, shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("coin-wallet").semantics { contentDescription = words(R.string.coin_profile) }) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarBadge(state.avatar, Modifier.clearAndSetSemantics {}, size = 32.dp)
                        Text(balance?.let { words(R.string.coin_balance, it) } ?: "…", color = GameNightPalette.gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                LobbySettingsButton(settings)
            }
            val hero: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (waiting) {
                        if (coins?.friendTable == true) FriendWaitingRoom(room, state.playerId, enabled,
                            start = { model.command(RoomAction.Start) }, invitationLink = model::friendInvitation,
                            connection = state.connection, reconnect = reconnect)
                        else {
                            TableCountdown(room, state.playerId, reducedMotion)
                            if (state.connection != Connection.LIVE) WaitingConnectionNotice(state.connection, enabled, reconnect)
                        }
                        Text(words(R.string.coin_choose_count, coins?.ownTickets ?: 0) + " · " + words(R.string.coin_pool_preview, coins?.pool ?: 0) +
                            " · " + pluralStringResource(R.plurals.table_prize_count, coins?.prizes?.size ?: 0, coins?.prizes?.size ?: 0), color = CoinGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { model.command(RoomAction.Leave) }, enabled = enabled && state.connection == Connection.LIVE,
                            modifier = Modifier.testTag("cancel-match")) { Text(words(R.string.coin_cancel), color = Ivory) }
                    } else {
                        if (finished) {
                            Text(words(R.string.coin_results), fontSize = 24.sp, fontWeight = FontWeight.Black)
                            Text(words(R.string.coin_won, coins?.settledWinnings ?: 0), color = CoinGold, fontSize = 28.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("coin-winnings"))
                            if ((coins?.returnedCoins ?: 0) > 0) Text(words(R.string.coin_returned, coins!!.returnedCoins), fontSize = 13.sp, color = Color(0xFFB7D0C0))
                            val ownWins = remember(coins?.prizes, room.round?.awards, room.round?.ownTickets) {
                                ownCoinWins(coins?.prizes.orEmpty(), room.round?.awards.orEmpty(), room.round?.ownTickets.orEmpty().map { it.id }.toSet())
                            }
                            CoinPrizeGrid(ownWins.map { it.prize }, Modifier.fillMaxWidth(), awarded = ownWins.map { it.prize.prize }.toSet(),
                                shared = ownWins.filter { it.shared }.map { it.prize.prize }.toSet())
                        } else {
                            LobbyGreeting(state.name, compact)
                            GameNightArtwork(Modifier.fillMaxWidth().height(if (compact) 124.dp else 190.dp), reducedMotion)
                        }
                    }
                }
            }
            val controls: @Composable () -> Unit = {
              if (showControls) {
                Surface(modifier = Modifier.fillMaxWidth().testTag("lobby-ticket-panel"), shape = RoundedCornerShape(26.dp), color = GameNightPalette.panel, contentColor = GameNightPalette.cream,
                    border = BorderStroke(1.dp, GameNightPalette.raised)) {
                  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.pending) {
                        Text(words(R.string.coin_pending), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (state.busy) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                        OutlinedButton(onClick = model::retry, enabled = !state.busy && !state.storageFailure,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("coin-retry")) { Text(words(R.string.coin_retry)) }
                    } else if (waiting) {
                        // Connection recovery stays with the table; fatal recovery controls follow below.
                    } else if (active) {
                        Text(words(R.string.coin_pool, coins?.pool ?: 0), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Button(onClick = resume, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("resume-match")) { Text(words(R.string.coin_resume)) }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(words(R.string.coin_tickets), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(words(R.string.coin_price), fontSize = 12.sp, color = GameNightPalette.muted)
                        }
                        if (!compact) Text(words(R.string.lobby_choose), fontSize = 12.sp, color = GameNightPalette.muted)
                        BoxWithConstraints {
                            val columns = if (maxWidth < 308.dp) 3 else 6
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                              (1..6).toList().chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { count ->
                                    val selected = tickets == count
                                    val affordable = balance == null || balance >= count * COIN_TICKET_PRICE
                                    val description = pluralStringResource(R.plurals.lobby_ticket_count, count, count)
                                    Surface(onClick = { chosenTickets = count }, enabled = enabled && affordable, shape = RoundedCornerShape(13.dp),
                                        color = if (selected && affordable) GameNightPalette.coral else GameNightPalette.raised,
                                        contentColor = if (!affordable) GameNightPalette.muted.copy(alpha = .45f) else if (selected) GameNightPalette.background else GameNightPalette.cream,
                                        modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("buy-tickets-$count")
                                            .semantics { this.selected = selected && affordable; contentDescription = description }) {
                                        Box(contentAlignment = Alignment.Center) { Text("$count", fontSize = 24.sp, fontWeight = FontWeight.Black) }
                                    }
                                }
                              } }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(pluralStringResource(R.plurals.lobby_ticket_count, tickets, tickets), fontSize = 13.sp)
                            Text(words(R.string.coin_balance, cost), color = GameNightPalette.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("lobby-ticket-cost"))
                        }
                        if (balance != null && balance < COIN_TICKET_PRICE) {
                            CoinRefill(state.serverTime, state.wallet?.refillAfter, enabled, model::refill)
                        } else {
                          if (friendsFinished) {
                            Text(words(R.string.friend_replay_hint), fontSize = 12.sp, color = GameNightPalette.muted)
                            Button(onClick = { replayFriends(tickets) }, enabled = enabled && balance != null && balance >= cost,
                                colors = ButtonDefaults.buttonColors(containerColor = GameNightPalette.coral, contentColor = GameNightPalette.background),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("friend-replay"), shape = RoundedCornerShape(16.dp)) {
                                Text(words(R.string.friend_replay, cost), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                          }
                          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { play(tickets) }, enabled = enabled && (balance == null || balance >= cost),
                                colors = ButtonDefaults.buttonColors(containerColor = if (friendsFinished) GameNightPalette.raised else GameNightPalette.coral,
                                    contentColor = if (friendsFinished) GameNightPalette.cream else GameNightPalette.background,
                                    disabledContainerColor = GameNightPalette.raised, disabledContentColor = GameNightPalette.muted),
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("coin-play"), shape = RoundedCornerShape(16.dp)) {
                                if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = GameNightPalette.cream)
                                else Text(words(if (friendsFinished) R.string.friend_quick_play else if (finished) R.string.coin_play_again else R.string.coin_play, cost),
                                    fontSize = if (friendsFinished) 14.sp else 19.sp, fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(onClick = { friendDialog = true }, enabled = enabled && (balance == null || balance >= cost),
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("play-friends"), shape = RoundedCornerShape(16.dp)) {
                                Text(words(if (friendsFinished) R.string.friend_other_table else R.string.friend_play), fontWeight = FontWeight.Bold)
                            }
                          }
                        }
                    }
                    if (state.sessionExpired || state.storageFailure) TextButton(onClick = { reset = true }) { Text(words(R.string.ui_reset_online_data)) }
                    if (!state.available) Text(words(R.string.coin_unavailable), fontSize = 13.sp)
                    Text(words(R.string.coin_free), color = GameNightPalette.muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                  }
                }
              }
            }
            if (wide) Row(Modifier.weight(1f).testTag("lobby-scroll").verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(if (waiting) 2f else .85f)) { hero() }
                if (showControls) Box(Modifier.weight(1.15f)) { controls() }
            } else Column(Modifier.weight(1f).testTag("lobby-scroll").verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { hero(); controls() }
        }
      }
    }
    if (profile && state.name == null) LobbyPlayerDialog(profileName, profileAvatar, enabled,
        changeName = { if (it.length <= 40) profileName = it }, chooseAvatar = { profileAvatar = it },
        save = { profile = false; model.register(profileName, profileAvatar) }, close = { profile = false })
    else if (profile) AlertDialog(onDismissRequest = { profile = false }, title = { Text(state.name ?: words(R.string.coin_profile)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    if (friendDialog) FriendEntryDialog(tickets, cost, enabled,
        enter = { code -> friendDialog = false; friends(tickets, code) }, close = { friendDialog = false })
    }
}

@Composable
internal fun CoinPrizeGrid(prizes: List<CoinPrize>, modifier: Modifier = Modifier, awarded: Set<Prize> = emptySet(), shared: Set<Prize> = emptySet(), onDark: Boolean = true) {
    val words = gameText()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
      // Preserve the user's text size: use fewer cards per row before wrapping labels.
      val columns = ((maxWidth.value + 6f) / (84f * fontScale + 6f)).toInt().coerceIn(1, 3)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        prizes.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { entry ->
                Column(Modifier.weight(1f).background(Color.White.copy(alpha = .07f), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("coin-prize-${entry.prize.name}"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((if (entry.prize in awarded) "✓ " else "") + words.prizeTitle(entry.prize), fontSize = 12.sp, lineHeight = 16.sp,
                        minLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("coin-prize-title-${entry.prize.name}"))
                    Text("${entry.coins}", color = if (onDark) CoinGold else MaterialTheme.colorScheme.primary, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("coin-prize-value-${entry.prize.name}"))
                    if (shared.isNotEmpty()) Text(if (entry.prize in shared) words(R.string.coin_shared) else "", fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag("coin-prize-share-${entry.prize.name}"))
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        } }
      }
    }
}

@Composable
internal fun CoinCallClock(room: RoomView, connection: Connection, reconnect: () -> Unit) {
    val words = gameText()
    if (connection != Connection.LIVE) TextButton(onClick = reconnect) { Text(words(R.string.play_reconnecting), fontSize = 11.sp) }
    else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val remaining = remainingCoinTime(room.nextDrawAt, room.serverTime, room.roomId)
        val seconds by countdownSeconds(remaining)
        val largeText = LocalDensity.current.fontScale > 1.3f
        Text(if (seconds > 0) words(if (largeText) R.string.play_next_call_short else R.string.coin_next, seconds)
            else words(if (largeText) R.string.play_next_call_wait else R.string.coin_next_wait),
            fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("next-call-copy"))
        LinearProgressIndicator(progress = { (remaining.value / 5000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = CoinGold)
    }
}

@Composable
internal fun MatchCountdown(room: RoomView) {
    val words = gameText()
    val seconds by countdownSeconds(remainingCoinTime(room.coins?.startsAt, room.serverTime, room.roomId))
    Text(if (seconds > 0) words(R.string.coin_starts, seconds) else words(R.string.coin_starting),
        fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.testTag("match-countdown"))
}

@Composable
internal fun CoinRefill(serverTime: ServerTime?, refillAfter: Long?, enabled: Boolean, refill: () -> Unit) {
    val words = gameText()
    // A finished room can be hours old. Anchor to the fresh HTTP clock on each mount/update.
    val reference = remember(serverTime, refillAfter) { serverTime?.currentTimeMillis() ?: System.currentTimeMillis() }
    val seconds by countdownSeconds(remainingCoinTime(refillAfter, reference, null))
    Button(onClick = refill, enabled = enabled && seconds == 0L,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("coin-refill")) {
        Text(if (seconds > 0) words(R.string.coin_refill_timer, seconds / 60, seconds % 60) else words(R.string.coin_collect))
    }
}
