package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.delay

@Composable
fun BingoOnlineScreen(state: OnlineUiState, model: OnlineViewModel, reducedMotion: Boolean, home: () -> Unit) {
    DisposableEffect(model) { model.setBingoVisible(true); onDispose { model.setBingoVisible(false) } }
    val previousCard = stringResource(R.string.bingo_previous_card)
    val nextCard = stringResource(R.string.bingo_next_card)
    val room = state.bingoRoom
    val game = room?.round
    val quick = game?.prizes?.map { it.pattern } == listOf(BingoPattern.ANY_LINE, BingoPattern.FOUR_CORNERS)
    val config = LocalConfiguration.current
    val widePlay = config.screenWidthDp > config.screenHeightDp && room?.phase == RoomPhase.ACTIVE
    val shortLandscape = config.screenWidthDp > config.screenHeightDp && config.screenHeightDp < 430
    val compactPlay = LocalDensity.current.fontScale >= 1.3f
    var count by rememberSaveable(state.preferredBingoCards) { mutableIntStateOf(state.preferredBingoCards) }
    var page by rememberSaveable(game?.id) { mutableIntStateOf(0) }
    var friends by remember { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var prizes by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var elapsed by remember(room?.revision) { mutableLongStateOf(0) }
    LaunchedEffect(room?.revision) { while (true) { delay(1000); elapsed += 1000 } }
    LaunchedEffect(state.bingoWinSequence) { if (state.bingoWinSequence > 0) { delay(1600); model.dismissBingoWin() } }
    val blocked = state.loading || (state.busy && !state.markSending) ||
        (state.pending && !state.markSending) || state.storageFailure || state.sessionExpired || !state.available
    val claimBlocked = blocked || state.busy || state.pending
    MaterialTheme(colorScheme = GameNightPalette.colors) {
    Surface(color = GameNightPalette.background, contentColor = GameNightPalette.cream) {
    Column(Modifier.fillMaxSize().background(GameNightPalette.background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!widePlay) Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = home, modifier = Modifier.testTag("bingo-online-home")) { Text(stringResource(R.string.ui_home)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bingo_title),
                    style = if (compactPlay) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compactPlay) Text(stringResource(R.string.bingo_live_table), color = GameNightPalette.mint, style = MaterialTheme.typography.labelSmall)
            }
            Text(state.wallet?.let { stringResource(R.string.hub_coins, it.balance) } ?: "…",
                color = GameNightPalette.gold, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        state.error?.let { message -> Text(stringResource(message.resource, *message.arguments.toTypedArray()), color = MaterialTheme.colorScheme.error) }
        if ((state.pending && !state.markSending) || state.connection == Connection.RECONNECTING || state.error != null) {
            TextButton(onClick = model::retry, enabled = !state.busy && !state.storageFailure, modifier = Modifier.testTag("bingo-online-retry")) { Text(stringResource(R.string.bingo_retry)) }
        }
        when {
            state.loading -> CircularProgressIndicator()
            room == null -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                if (shortLandscape) BingoLobbyArt()
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = GameNightPalette.panel,
                    border = BorderStroke(1.dp, GameNightPalette.mint.copy(alpha = .5f))) {
                    Column(Modifier.padding(if (shortLandscape) 12.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (shortLandscape) 4.dp else 12.dp)) {
                        Text(stringResource(R.string.hub_multiplayer), color = GameNightPalette.mint,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.bingo_pick_cards), style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black)
                        if (!shortLandscape) Text(stringResource(R.string.bingo_quick_intro), color = GameNightPalette.muted)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val columns = if (shortLandscape || maxWidth >= 480.dp && !compactPlay) 6 else 3
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..6).toList().chunked(columns).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { cards -> FilterChip(selected = count == cards, onClick = { count = cards },
                                            label = { Text("$cards") }, modifier = Modifier.weight(1f).testTag("bingo-online-cards-$cards")) }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { model.playBingo(count) }, enabled = !blocked,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("bingo-online-play")) {
                                Text(stringResource(R.string.bingo_buy, count * COIN_TICKET_PRICE), maxLines = 1)
                            }
                            OutlinedButton(onClick = { friends = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("bingo-online-friends")) {
                                Text(stringResource(R.string.bingo_friends), color = GameNightPalette.cream, maxLines = 1)
                            }
                        }
                    }
                }
                }
            }
            room.phase == RoomPhase.LOBBY -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = GameNightPalette.panel) {
                    Column(Modifier.padding(if (compactPlay || shortLandscape) 12.dp else 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (compactPlay || shortLandscape) 6.dp else 14.dp)) {
                        if (!shortLandscape) CircularProgressIndicator(color = GameNightPalette.mint, modifier = Modifier.size(if (compactPlay) 28.dp else 40.dp))
                        val heading = if (room.friendTable) {
                            if (room.members.size < 2) stringResource(R.string.bingo_waiting_for_people)
                            else stringResource(R.string.bingo_friend_ready)
                        } else stringResource(R.string.bingo_countdown,
                            room.startsAt?.let { ((it - room.serverTime - elapsed).coerceAtLeast(0) + 999) / 1000 } ?: 0)
                        Text(heading, style = if (shortLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val cardCount = room.cardCounts.values.sum()
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.bingo_live_players, room.members.count { it.connected }), color = GameNightPalette.mint,
                                style = if (shortLandscape) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.bingo_waiting,
                                pluralStringResource(R.plurals.player_count, room.players.size, room.players.size),
                                pluralStringResource(R.plurals.bingo_card_count, cardCount, cardCount)), color = GameNightPalette.muted,
                                style = if (shortLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium)
                        }
                        if (!room.friendTable) Text(stringResource(R.string.bingo_computer_fill), color = GameNightPalette.muted,
                            style = MaterialTheme.typography.labelSmall)
                        if (room.friendTable) Text(room.code,
                            style = if (shortLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                            color = GameNightPalette.gold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (room.friendTable && room.hostId == state.playerId) Button(onClick = { model.bingoCommand(BingoAction.Start) },
                                enabled = !blocked && room.members.size >= 2) { Text(stringResource(R.string.bingo_start)) }
                            TextButton(onClick = { model.bingoCommand(BingoAction.Leave) }, enabled = !blocked,
                                modifier = Modifier.testTag("bingo-online-leave")) { Text(stringResource(R.string.bingo_leave)) }
                        }
                    }
                }
                }
            }
            game != null && room.phase == RoomPhase.FINISHED -> {
                val ranked = game.players.sortedWith(compareByDescending<Player> { game.winnings[it.id]?.total ?: 0 }.thenBy { it.id })
                val ownRank = ranked.indexOfFirst { it.id == state.playerId } + 1
                val ownCoins = game.winnings[state.playerId]?.total ?: 0
                val ownGoals = game.claims.count { it.playerId == state.playerId }
                val ownMarks = game.ownMarks.values.sumOf { it.size }
                Surface(Modifier.fillMaxWidth().testTag("bingo-results-summary"), shape = RoundedCornerShape(22.dp),
                    color = GameNightPalette.panel, border = BorderStroke(1.dp, GameNightPalette.mint)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (ownGoals > 0) stringResource(R.string.bingo_summary_win, ownCoins)
                            else stringResource(R.string.bingo_summary_finish, ownCoins), color = GameNightPalette.cream,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(stringResource(R.string.bingo_summary_stats, ownRank, ranked.size, ownMarks, ownGoals),
                            color = GameNightPalette.mint, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.bingo_results), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                val list = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).testTag("bingo-online-ranking"), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(ranked, key = { _, player -> player.id }) { index, player ->
                        Card(colors = CardDefaults.cardColors(containerColor = if (player.id == state.playerId) MaterialTheme.colorScheme.secondaryContainer else Panel)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", Modifier.width(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(player.name, fontWeight = FontWeight.Bold)
                                    if (player.computer) Text(stringResource(R.string.play_computer_short),
                                        color = GameNightPalette.mint, style = MaterialTheme.typography.labelSmall)
                                    game.claims.filter { it.playerId == player.id }.forEach { Text(patternTitle(it.pattern), style = MaterialTheme.typography.labelSmall) }
                                }
                                Text(stringResource(R.string.hub_coins, game.winnings[player.id]?.total ?: 0))
                            }
                        }
                    }
                }
                Button(onClick = model::bingoLobby, enabled = !blocked, modifier = Modifier.fillMaxWidth().testTag("bingo-online-again")) { Text(stringResource(R.string.bingo_again)) }
            }
            game != null && game.ownCards.isNotEmpty() -> {
                val card = game.ownCards[page.coerceIn(game.ownCards.indices)]
                val controls: @Composable ColumnScope.() -> Unit = {
                    if (widePlay) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = home, modifier = Modifier.testTag("bingo-online-home")) { Text(stringResource(R.string.ui_home)) }
                        TextButton(onClick = { history = true }) { Text(stringResource(R.string.bingo_history)) }
                    }
                    Surface(shape = RoundedCornerShape(22.dp), color = GameNightPalette.panel,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(if (compactPlay || shortLandscape) 8.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(onClick = model::repeatBingoCall, enabled = game.called.isNotEmpty(),
                                shape = androidx.compose.foundation.shape.CircleShape, color = GameNightPalette.gold,
                                modifier = Modifier.size(if (compactPlay || shortLandscape) 52.dp else 68.dp).testTag("bingo-current-call")) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(game.called.lastOrNull()?.let(::bingoCallLabel) ?: "—",
                                        color = GameNightPalette.background, fontSize = if (compactPlay) 10.sp else 18.sp,
                                        lineHeight = if (compactPlay) 12.sp else 20.sp,
                                        fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.bingo_live_table), color = GameNightPalette.mint,
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                                 Text(if (quick) stringResource(R.string.bingo_quick_called, game.called.size, game.players.size)
                                     else stringResource(R.string.bingo_called, game.called.size, game.players.size),
                                     color = GameNightPalette.cream, style = MaterialTheme.typography.labelMedium)
                                 LinearProgressIndicator(progress = { game.called.size / if (quick) 45f else 75f },
                                     modifier = Modifier.fillMaxWidth(), color = GameNightPalette.mint)
                                 if (quick) room.nextDrawAt?.let { next -> Text(stringResource(R.string.bingo_next_in,
                                     ((next - room.serverTime - elapsed).coerceAtLeast(0) + 999) / 1000),
                                     color = GameNightPalette.gold, style = MaterialTheme.typography.labelSmall) }
                                Text(bingoPresenceSummary(room, game, state.connection),
                                    color = GameNightPalette.mint, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.testTag("bingo-live-seats"))
                            }
                        }
                    }
                    if (quick) BingoQuickGoals(game, card, state.playerId, claimBlocked, false) { pattern ->
                        model.bingoCommand(BingoAction.Claim(game.id, card.id, pattern))
                    } else Button(onClick = { prizes = true }, enabled = !blocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("bingo-online-prizes")) {
                        Text(stringResource(R.string.bingo_claim))
                    }
                    if (!compactPlay) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (game.called.isNotEmpty() && !shortLandscape) {
                            Text(stringResource(R.string.bingo_recent_calls), color = GameNightPalette.muted,
                                style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                game.called.takeLast(5).forEach { call ->
                                    Surface(shape = RoundedCornerShape(10.dp), color = GameNightPalette.raised) {
                                        Text(bingoCallLabel(call), Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            color = GameNightPalette.cream, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                        if (quick) BingoLiveWins(game) else BingoChasePanel(card, game.ownMarks[card.id].orEmpty(), shortLandscape)
                    }
                }
                val hand: @Composable ColumnScope.() -> Unit = {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        BingoCardView(card, game.called, game.ownMarks[card.id].orEmpty(), !blocked && state.connection == Connection.LIVE,
                            { cardId, number -> model.bingoCommand(BingoAction.Mark(game.id, cardId, number)) }, Modifier.fillMaxSize(), quick,
                            state.pendingBingoMarks[card.id].orEmpty())
                        if (state.bingoWinSequence > 0) WinConfetti("${game.id}:${state.bingoWinSequence}", reducedMotion, Modifier.matchParentSize(), intensity = .85f)
                    }
                    if (quick && game.ownCards.size > 1) Row(Modifier.fillMaxWidth().testTag("bingo-card-tabs"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        game.ownCards.forEachIndexed { index, own ->
                            val due = own.numbers.count { it in game.called && it !in game.ownMarks[own.id].orEmpty() &&
                                it !in state.pendingBingoMarks[own.id].orEmpty() }
                            Surface(onClick = { page = index }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .testTag("bingo-card-tab-${index + 1}"), shape = RoundedCornerShape(10.dp),
                                color = if (page == index) GameNightPalette.mint else GameNightPalette.panel,
                                contentColor = if (page == index) GameNightPalette.background else GameNightPalette.cream) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(if (due > 0) "${index + 1} · $due" else "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } else if (game.ownCards.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { page-- }, enabled = page > 0, modifier = Modifier.testTag("bingo-online-previous").semantics { contentDescription = previousCard }) { Text("←") }
                        Text(stringResource(R.string.bingo_card_page, page + 1, game.ownCards.size), style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { page++ }, enabled = page < game.ownCards.lastIndex, modifier = Modifier.testTag("bingo-online-next").semantics { contentDescription = nextCard }) { Text("→") }
                    }
                }
                if (widePlay) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), content = hand)
                    Column(Modifier.weight(.75f).fillMaxHeight(), content = controls)
                } else Column(Modifier.weight(1f).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = model::repeatBingoCall, enabled = game.called.isNotEmpty(),
                            shape = androidx.compose.foundation.shape.CircleShape, color = GameNightPalette.gold,
                            modifier = Modifier.size(58.dp).testTag("bingo-current-call")) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(game.called.lastOrNull()?.let(::bingoCallLabel) ?: "—", color = GameNightPalette.background,
                                    fontSize = if (compactPlay) 10.sp else 16.sp,
                                    lineHeight = if (compactPlay) 12.sp else 18.sp,
                                    fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (quick) stringResource(R.string.bingo_quick_called, game.called.size, game.players.size)
                                else stringResource(R.string.bingo_called, game.called.size, game.players.size),
                                style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(bingoPresenceSummary(room, game, state.connection),
                                color = GameNightPalette.mint, style = MaterialTheme.typography.labelSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("bingo-live-seats"))
                            val ready = card.numbers.count { it in game.called && it !in game.ownMarks[card.id].orEmpty() }
                            if (ready > 0) Text(stringResource(R.string.bingo_ready_count, ready),
                                color = GameNightPalette.gold, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (quick) BingoQuickGoals(game, card, state.playerId, claimBlocked, true) { pattern ->
                        model.bingoCommand(BingoAction.Claim(game.id, card.id, pattern))
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { history = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.bingo_history), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(onClick = { prizes = true }, enabled = !blocked,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("bingo-online-prizes")) {
                            Text(stringResource(if (compactPlay) R.string.play_claim else R.string.bingo_claim),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    hand()
                }
                if (prizes) AlertDialog(onDismissRequest = { prizes = false }, title = { Text(stringResource(R.string.bingo_claim)) }, text = {
                    if (shortLandscape && !compactPlay) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BingoPattern.entries.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { pattern ->
                                    BingoClaimChoice(pattern, game, card, state.playerId, blocked,
                                        { model.bingoCommand(BingoAction.Claim(game.id, card.id, pattern)); prizes = false }, Modifier.weight(1f))
                                }
                            }
                        }
                    } else Column(Modifier.verticalScroll(rememberScrollState())) {
                        BingoPattern.entries.forEach { pattern ->
                            BingoClaimChoice(pattern, game, card, state.playerId, blocked,
                                { model.bingoCommand(BingoAction.Claim(game.id, card.id, pattern)); prizes = false })
                            }
                    }
                }, confirmButton = { TextButton(onClick = { prizes = false }) { Text(stringResource(R.string.ui_back_to_game)) } })
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    }
    if (friends) AlertDialog(onDismissRequest = { friends = false }, title = { Text(stringResource(R.string.bingo_friends)) },
        text = { OutlinedTextField(value = code, onValueChange = { code = it.take(10) }, label = { Text(stringResource(R.string.bingo_code)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { model.playBingo(count, true, code); friends = false }, enabled = code.length == 10 && !blocked) { Text(stringResource(R.string.bingo_join)) } },
        dismissButton = { TextButton(onClick = { model.playBingo(count, true); friends = false }, enabled = !blocked) { Text(stringResource(R.string.bingo_create)) } })
    if (history && game != null) AlertDialog(onDismissRequest = { history = false }, title = { Text(stringResource(R.string.bingo_history)) },
        text = { LazyColumn { itemsIndexed(game.called.reversed()) { _, number -> Text(bingoCallLabel(number), Modifier.padding(4.dp)) } } },
        confirmButton = { TextButton(onClick = { history = false }) { Text(stringResource(R.string.ui_got_it)) } })
    }
}

@Composable
private fun bingoPresenceSummary(room: BingoRoomView, game: PublicBingoRound, connection: Connection): String {
    val people = if (connection == Connection.LIVE) room.members.count { member ->
        member.connected && game.players.any { !it.computer && it.id == member.playerId }
    } else game.players.count { !it.computer }
    val computers = game.players.count { it.computer }
    val humanLabel = stringResource(if (connection == Connection.LIVE) R.string.arena_live_people else R.string.arena_people_seated, people)
    return listOfNotNull(humanLabel, stringResource(R.string.arena_computer_seats, computers).takeIf { computers > 0 })
        .joinToString(" · ")
}

@Composable
private fun BingoLiveWins(game: PublicBingoRound) {
    Surface(Modifier.fillMaxWidth().testTag("bingo-live-wins"), shape = RoundedCornerShape(18.dp),
        color = GameNightPalette.panel) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.bingo_live_wins), color = GameNightPalette.mint,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            val latest = game.claims.takeLast(3).reversed()
            if (latest.isEmpty()) Text(stringResource(R.string.bingo_live_open),
                color = GameNightPalette.cream, style = MaterialTheme.typography.bodySmall)
            latest.forEach { win ->
                val player = game.players.firstOrNull { it.id == win.playerId }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(listOfNotNull(player?.name, if (player?.computer == true)
                        stringResource(R.string.play_computer_short) else null).joinToString(" · "),
                        Modifier.weight(1f), color = GameNightPalette.cream,
                        style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(patternTitle(win.pattern), color = GameNightPalette.gold,
                        style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BingoLobbyArt() {
    Row(Modifier.fillMaxWidth().height(108.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.bingo_quick_title), color = GameNightPalette.gold,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.bingo_quick_intro), color = GameNightPalette.mint,
                style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(5) { column ->
                        val special = (row == 2 && column == 2) || (row in listOf(0, 4) && column in listOf(0, 4))
                        Surface(Modifier.size(18.dp), shape = RoundedCornerShape(4.dp),
                            color = if (special) GameNightPalette.mint else GameNightPalette.raised) {
                            if (row == 2 && column == 2) Box(contentAlignment = Alignment.Center) {
                                Text("★", fontSize = 10.sp, color = GameNightPalette.background)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BingoQuickGoals(game: PublicBingoRound, card: BingoCard, playerId: String?, blocked: Boolean,
    horizontal: Boolean, claim: (BingoPattern) -> Unit) {
    val marks = game.ownMarks[card.id].orEmpty()
    val called = game.called.toSet()
    val goals: @Composable RowScope.(BingoCoinPrize) -> Unit = { prize ->
        val pattern = prize.pattern
        val wins = game.claims.filter { it.pattern == pattern }
        val won = wins.any { it.playerId == playerId }
        val open = wins.size < game.winnersPerPattern || wins.last().drawCount == game.called.size
        val mask = pattern.masks().maxBy { candidate -> candidate.count { it == 12 || card.cells[it] in marks } }
        val done = mask.count { it == 12 || card.cells[it] in marks }
        val ready = pattern.isComplete(card, called, marks) && open && !won
        Surface(onClick = { claim(pattern) }, enabled = ready && !blocked,
            modifier = Modifier.weight(1f).heightIn(min = if (horizontal) 88.dp else 120.dp)
                .testTag("bingo-quick-claim-${pattern.name}"), shape = RoundedCornerShape(18.dp),
            color = if (ready) GameNightPalette.mint else GameNightPalette.panel,
            contentColor = if (ready) GameNightPalette.background else GameNightPalette.cream,
            border = BorderStroke(1.dp, if (ready) GameNightPalette.mint else GameNightPalette.raised)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(patternTitle(pattern), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when {
                    won -> stringResource(R.string.bingo_goal_won)
                    !open -> stringResource(R.string.bingo_goal_closed)
                    ready -> stringResource(R.string.bingo_goal_claim_now)
                    else -> stringResource(R.string.bingo_goal_progress, done, mask.size)
                }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.bingo_goal_reward, prize.coins,
                    (game.winnersPerPattern - wins.size).coerceAtLeast(0)), style = MaterialTheme.typography.labelSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("bingo-quick-goals"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.bingo_quick_goals), color = GameNightPalette.mint,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            game.prizes.forEach { goals(it) }
        }
    }
}

@Composable
private fun BingoClaimChoice(pattern: BingoPattern, game: PublicBingoRound, card: BingoCard,
    playerId: String?, blocked: Boolean, onClaim: () -> Unit, modifier: Modifier = Modifier) {
    val wins = game.claims.filter { it.pattern == pattern }
    val won = wins.any { it.playerId == playerId }
    val open = wins.size < game.winnersPerPattern || wins.last().drawCount == game.called.size
    val complete = pattern.isComplete(card, game.called.toSet(), game.ownMarks[card.id].orEmpty())
    TextButton(onClick = onClaim, enabled = !blocked && !won && open && complete,
        modifier = modifier.testTag("bingo-online-claim-${pattern.name}")) {
        Column {
            Text(patternTitle(pattern), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (won) stringResource(R.string.prize_already_claimed) else if (wins.size >= game.winnersPerPattern && open) stringResource(R.string.prize_ties_open)
                else stringResource(R.string.prize_places_left, (game.winnersPerPattern - wins.size).coerceAtLeast(0), game.winnersPerPattern),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BingoChasePanel(card: BingoCard, marks: Set<Int>, compact: Boolean = false) {
    Surface(Modifier.fillMaxWidth().testTag("bingo-chase"), shape = RoundedCornerShape(18.dp), color = GameNightPalette.panel) {
        Column(Modifier.padding(if (compact) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp)) {
            Text(stringResource(R.string.bingo_your_chase), color = GameNightPalette.mint,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            BingoPattern.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
                    row.forEach { pattern ->
                        val mask = pattern.masks().maxBy { candidate -> candidate.count { it == 12 || card.cells[it] in marks } }
                        val done = mask.count { it == 12 || card.cells[it] in marks }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(patternTitle(pattern), color = GameNightPalette.cream,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                Text("$done/${mask.size}", color = GameNightPalette.gold,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            LinearProgressIndicator(progress = { done.toFloat() / mask.size },
                                modifier = Modifier.fillMaxWidth(), color = GameNightPalette.mint,
                                trackColor = GameNightPalette.raised)
                        }
                    }
                }
            }
        }
    }
}
