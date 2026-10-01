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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val config = LocalConfiguration.current
    val widePlay = config.screenWidthDp > config.screenHeightDp && room?.phase == RoomPhase.ACTIVE
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
    val blocked = state.loading || state.busy || state.pending || state.storageFailure || state.sessionExpired || !state.available
    MaterialTheme(colorScheme = GameNightPalette.colors) {
    Surface(color = GameNightPalette.background, contentColor = GameNightPalette.cream) {
    Column(Modifier.fillMaxSize().background(GameNightPalette.background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!widePlay) Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = home, modifier = Modifier.testTag("bingo-online-home")) { Text(stringResource(R.string.ui_home)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bingo_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.bingo_live_table), color = GameNightPalette.mint, style = MaterialTheme.typography.labelSmall)
            }
            Text(state.wallet?.let { stringResource(R.string.hub_coins, it.balance) } ?: "…",
                color = GameNightPalette.gold, style = MaterialTheme.typography.labelLarge)
        }
        state.error?.let { message -> Text(stringResource(message.resource, *message.arguments.toTypedArray()), color = MaterialTheme.colorScheme.error) }
        if (state.pending || state.connection == Connection.RECONNECTING || state.error != null) {
            TextButton(onClick = model::retry, enabled = !state.busy && !state.storageFailure, modifier = Modifier.testTag("bingo-online-retry")) { Text(stringResource(R.string.bingo_retry)) }
        }
        when {
            state.loading -> CircularProgressIndicator()
            room == null -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = GameNightPalette.panel,
                    border = BorderStroke(1.dp, GameNightPalette.mint.copy(alpha = .5f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.hub_multiplayer), color = GameNightPalette.mint,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.bingo_pick_cards), style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black)
                        Text(stringResource(R.string.hub_bingo), color = GameNightPalette.muted)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth < 480.dp || compactPlay) 3 else 6
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..6).toList().chunked(columns).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { cards -> FilterChip(selected = count == cards, onClick = { count = cards },
                                            label = { Text("$cards") }, modifier = Modifier.weight(1f).testTag("bingo-online-cards-$cards")) }
                                    }
                                }
                            }
                        }
                        Button(onClick = { model.playBingo(count) }, enabled = !blocked,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("bingo-online-play")) {
                            Text(stringResource(R.string.bingo_buy, count * COIN_TICKET_PRICE))
                        }
                        OutlinedButton(onClick = { friends = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.bingo_friends), color = GameNightPalette.cream)
                        }
                    }
                }
                }
            }
            room.phase == RoomPhase.LOBBY -> {
                Spacer(Modifier.weight(1f))
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = GameNightPalette.panel) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(color = GameNightPalette.mint, modifier = Modifier.size(40.dp))
                        Text(if (room.members.size < 2) stringResource(R.string.bingo_waiting_for_people)
                            else stringResource(R.string.bingo_countdown, room.startsAt?.let { ((it - room.serverTime - elapsed).coerceAtLeast(0) + 999) / 1000 } ?: 0),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(stringResource(R.string.bingo_live_players, room.members.size), color = GameNightPalette.mint)
                        Text(stringResource(R.string.bingo_waiting, room.players.size, room.cardCounts.values.sum()), color = GameNightPalette.muted)
                        if (room.friendTable) Text(room.code, style = MaterialTheme.typography.headlineMedium, color = GameNightPalette.gold)
                        if (room.friendTable && room.hostId == state.playerId) Button(onClick = { model.bingoCommand(BingoAction.Start) },
                            enabled = !blocked && room.members.size >= 2) { Text(stringResource(R.string.bingo_start)) }
                        TextButton(onClick = { model.bingoCommand(BingoAction.Leave) }, enabled = !blocked,
                            modifier = Modifier.testTag("bingo-online-leave")) { Text(stringResource(R.string.bingo_leave)) }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            game != null && room.phase == RoomPhase.FINISHED -> {
                Text(stringResource(R.string.bingo_results), style = MaterialTheme.typography.headlineSmall)
                val ranked = game.players.sortedWith(compareByDescending<Player> { game.winnings[it.id]?.total ?: 0 }.thenBy { it.id })
                val list = rememberLazyListState()
                LaunchedEffect(game.id) { list.scrollToItem(ranked.indexOfFirst { it.id == state.playerId }.coerceAtLeast(0)) }
                LazyColumn(Modifier.weight(1f).testTag("bingo-online-ranking"), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(ranked, key = { _, player -> player.id }) { index, player ->
                        Card(colors = CardDefaults.cardColors(containerColor = if (player.id == state.playerId) MaterialTheme.colorScheme.secondaryContainer else Panel)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", Modifier.width(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(player.name, fontWeight = FontWeight.Bold)
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
                    if (widePlay) TextButton(onClick = home, modifier = Modifier.testTag("bingo-online-home")) { Text(stringResource(R.string.ui_home)) }
                    Surface(shape = RoundedCornerShape(22.dp), color = GameNightPalette.panel,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(if (compactPlay) 8.dp else 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!compactPlay) Text(stringResource(R.string.bingo_live_table), color = GameNightPalette.mint,
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(onClick = model::repeatBingoCall, enabled = game.called.isNotEmpty(),
                                    shape = androidx.compose.foundation.shape.CircleShape, color = GameNightPalette.gold,
                                    modifier = Modifier.size(if (compactPlay) 52.dp else 86.dp).testTag("bingo-current-call")) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(game.called.lastOrNull()?.let(::bingoCallLabel) ?: "—",
                                            color = GameNightPalette.background, style = if (compactPlay) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black)
                                    }
                                }
                                if (compactPlay) Text(stringResource(R.string.bingo_called, game.called.size, game.players.size),
                                    color = GameNightPalette.cream, style = MaterialTheme.typography.labelSmall)
                            }
                            if (!compactPlay) Text(stringResource(R.string.bingo_called, game.called.size, game.players.size),
                                color = GameNightPalette.cream, style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(progress = { game.called.size / 75f },
                                modifier = Modifier.fillMaxWidth(), color = GameNightPalette.mint)
                            if (!compactPlay) Text(stringResource(R.string.bingo_live_players, game.players.count { !it.computer }),
                                color = GameNightPalette.mint, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (game.called.isNotEmpty() && !compactPlay) {
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
                    if (!compactPlay) OutlinedButton(onClick = { history = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bingo_history)) }
                    Button(onClick = { prizes = true }, enabled = !blocked, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("bingo-online-prizes")) { Text(stringResource(R.string.bingo_claim)) }
                }
                val hand: @Composable ColumnScope.() -> Unit = {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        BingoCardView(card, game.called, game.ownMarks[card.id].orEmpty(), !blocked && state.connection == Connection.LIVE,
                            { cardId, number -> model.bingoCommand(BingoAction.Mark(game.id, cardId, number)) }, Modifier.fillMaxSize())
                        if (state.bingoWinSequence > 0) WinConfetti("${game.id}:${state.bingoWinSequence}", reducedMotion, Modifier.matchParentSize(), intensity = .85f)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { page-- }, enabled = page > 0, modifier = Modifier.testTag("bingo-online-previous").semantics { contentDescription = previousCard }) { Text("←") }
                        Text(stringResource(R.string.bingo_card_page, page + 1, game.ownCards.size), style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { page++ }, enabled = page < game.ownCards.lastIndex, modifier = Modifier.testTag("bingo-online-next").semantics { contentDescription = nextCard }) { Text("→") }
                    }
                }
                if (widePlay) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), content = hand)
                    Column(Modifier.weight(.55f).fillMaxHeight(), content = controls)
                } else Column(Modifier.weight(1f).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = model::repeatBingoCall, enabled = game.called.isNotEmpty(),
                            shape = androidx.compose.foundation.shape.CircleShape, color = GameNightPalette.gold,
                            modifier = Modifier.size(58.dp).testTag("bingo-current-call")) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(game.called.lastOrNull()?.let(::bingoCallLabel) ?: "—", color = GameNightPalette.background,
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.bingo_called, game.called.size, game.players.size), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.bingo_live_players, game.players.count { !it.computer }),
                                color = GameNightPalette.mint, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { history = true }) { Text(stringResource(R.string.bingo_history)) }
                        Button(onClick = { prizes = true }, enabled = !blocked, modifier = Modifier.testTag("bingo-online-prizes")) { Text(stringResource(R.string.bingo_claim)) }
                    }
                    hand()
                }
                if (prizes) AlertDialog(onDismissRequest = { prizes = false }, title = { Text(stringResource(R.string.bingo_claim)) }, text = {
                    Column {
                        BingoPattern.entries.forEach { pattern ->
                            val wins = game.claims.filter { it.pattern == pattern }
                            val won = wins.any { it.playerId == state.playerId }
                            val open = wins.size < game.winnersPerPattern || wins.last().drawCount == game.called.size
                            val complete = pattern.isComplete(card, game.called.toSet(), game.ownMarks[card.id].orEmpty())
                            TextButton(onClick = { model.bingoCommand(BingoAction.Claim(game.id, card.id, pattern)); prizes = false }, enabled = !blocked && !won && open && complete,
                                modifier = Modifier.testTag("bingo-online-claim-${pattern.name}")) {
                                Column {
                                    Text(patternTitle(pattern))
                                    Text(if (won) stringResource(R.string.prize_already_claimed) else if (wins.size >= game.winnersPerPattern && open) stringResource(R.string.prize_ties_open)
                                        else stringResource(R.string.prize_places_left, (game.winnersPerPattern - wins.size).coerceAtLeast(0), game.winnersPerPattern))
                                }
                            }
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
