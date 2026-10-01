package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.*
import io.github.sbshrey.tambola.game.R

@Composable
fun BingoScreen(state: BingoUiState, model: BingoViewModel, name: String?, reducedMotion: Boolean = false, home: () -> Unit) {
    val round = state.round
    val previousCard = stringResource(R.string.bingo_previous_card)
    val nextCard = stringResource(R.string.bingo_next_card)
    var page by rememberSaveable(round?.id) { mutableIntStateOf(0) }
    LaunchedEffect(round?.id, state.winSequence) {
        if (state.winSequence > 0) { kotlinx.coroutines.delay(1600); model.dismissWin(state.winSequence) }
    }
    var prizePicker by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var replay by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { model.pause(); home() }, modifier = Modifier.testTag("bingo-home")) { Text(stringResource(R.string.ui_home)) }
            Text(stringResource(R.string.bingo_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.bingo_practice), color = Muted)
        }
        when {
            state.loading -> CircularProgressIndicator()
            state.error -> {
                Text(stringResource(R.string.bingo_storage_error))
                OutlinedButton(onClick = { reset = true }, modifier = Modifier.testTag("bingo-reset")) { Text(stringResource(R.string.bingo_reset)) }
            }
            round == null -> {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.bingo_pick_cards), style = MaterialTheme.typography.headlineMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (1..6).forEach { count -> FilterChip(selected = state.cards == count, onClick = { model.chooseCards(count) },
                        label = { Text("$count") }, modifier = Modifier.testTag("bingo-cards-$count")) }
                }
                Button(onClick = { model.newRound(name.orEmpty()) }, modifier = Modifier.fillMaxWidth().testTag("bingo-deal")) {
                    Text(stringResource(R.string.bingo_deal))
                }
                Spacer(Modifier.weight(1f))
            }
            round.finished -> {
                Text(stringResource(R.string.bingo_results), style = MaterialTheme.typography.headlineMedium)
                val ranking = round.ranking()
                val list = rememberLazyListState()
                LaunchedEffect(round.id) { list.scrollToItem(ranking.indexOfFirst { it.id == "me" }.coerceAtLeast(0)) }
                LazyColumn(Modifier.weight(1f).testTag("bingo-ranking"), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(ranking, key = { _, player -> player.id }) { index, player ->
                        Card(modifier = Modifier.testTag("bingo-result-${player.id}"), colors = CardDefaults.cardColors(containerColor = if (player.id == "me") MaterialTheme.colorScheme.secondaryContainer else Panel)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", Modifier.width(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(player.name, fontWeight = FontWeight.Bold)
                                    if (player.computer) Text(stringResource(R.string.bingo_practice_player), style = MaterialTheme.typography.labelSmall, color = Muted)
                                    val wins = round.claims.filter { it.playerId == player.id }
                                    wins.forEach { Text(patternTitle(it.pattern), style = MaterialTheme.typography.labelSmall) }
                                }
                                Text(stringResource(R.string.bingo_points, round.points(player.id)))
                            }
                        }
                    }
                }
                Button(onClick = { replay = true }, modifier = Modifier.fillMaxWidth().testTag("bingo-replay")) { Text(stringResource(R.string.bingo_again)) }
            }
            else -> {
                val cards = round.cards.filter { it.playerId == "me" }
                val card = cards[page.coerceIn(cards.indices)]
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val landscape = maxWidth > maxHeight
                    val sidebarWidth = (maxWidth * .30f).coerceAtMost(240.dp)
                    val controls: @Composable () -> Unit = {
                        TextButton(onClick = model::repeatCall, enabled = round.draw.latest != null, modifier = Modifier.testTag("bingo-current-call")) { Text(round.draw.latest?.let(::bingoCallLabel) ?: "75", style = MaterialTheme.typography.headlineLarge,
                            color = Saffron, fontWeight = FontWeight.Black) }
                        Text(stringResource(R.string.bingo_called, round.draw.count, round.players.size), style = MaterialTheme.typography.labelMedium)
                        Row {
                            TextButton(onClick = { history = true }, modifier = Modifier.testTag("bingo-history")) { Text(stringResource(R.string.bingo_history)) }
                            TextButton(onClick = { if (round.status == RoundStatus.PLAYING) model.pause() else model.resume() }, modifier = Modifier.testTag("bingo-toggle")) {
                                Text(stringResource(if (round.status == RoundStatus.PLAYING) R.string.bingo_pause else R.string.bingo_start))
                            }
                        }
                        Button(onClick = { prizePicker = true }, modifier = Modifier.fillMaxWidth().testTag("bingo-prizes")) {
                            Text(stringResource(R.string.bingo_claim))
                        }
                    }
                    val hand: @Composable ColumnScope.() -> Unit = {
                        BingoCardView(card, round, model::mark, Modifier.weight(1f).fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(onClick = { page-- }, enabled = page > 0, modifier = Modifier.testTag("bingo-previous")
                                .semantics { contentDescription = previousCard }) { Text("←") }
                            Text(stringResource(R.string.bingo_card_page, page + 1, cards.size))
                            OutlinedButton(onClick = { page++ }, enabled = page < cards.lastIndex, modifier = Modifier.testTag("bingo-next")
                                .semantics { contentDescription = nextCard }) { Text("→") }
                        }
                    }
                    if (landscape) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), content = hand)
                        Column(Modifier.width(sidebarWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            controls()
                        }
                    } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        controls()
                        hand()
                    }
                    state.winSequence.takeIf { it > 0 }?.let {
                        WinConfetti("${round.id}:$it", reducedMotion, Modifier.matchParentSize(), intensity = .85f)
                    }
                }
                if (prizePicker) BingoPrizeDialog(round, card, { prizePicker = false }) { pattern ->
                    model.claim(card.id, pattern); prizePicker = false
                }
            }
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text(stringResource(R.string.bingo_reset)) },
        text = { Text(stringResource(R.string.bingo_reset_detail)) },
        confirmButton = { TextButton(onClick = { model.resetFailedSave(); reset = false }, modifier = Modifier.testTag("bingo-reset-confirm")) { Text(stringResource(R.string.bingo_reset)) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(stringResource(R.string.ui_back_to_game)) } })
    if (replay) AlertDialog(onDismissRequest = { replay = false }, title = { Text(stringResource(R.string.bingo_pick_cards)) },
        text = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..6).forEach { count -> FilterChip(selected = state.cards == count, onClick = { model.chooseCards(count) }, label = { Text("$count") }, modifier = Modifier.testTag("bingo-replay-cards-$count")) }
        } }, confirmButton = { TextButton(onClick = { model.newRound(name.orEmpty()); replay = false }, modifier = Modifier.testTag("bingo-replay-deal")) { Text(stringResource(R.string.bingo_deal)) } },
        dismissButton = { TextButton(onClick = { replay = false }) { Text(stringResource(R.string.ui_back_to_game)) } })
    if (history && round != null) AlertDialog(onDismissRequest = { history = false }, title = { Text(stringResource(R.string.bingo_history)) },
        text = { LazyColumn { itemsIndexed(round.draw.called.reversed()) { _, number -> Text(bingoCallLabel(number), Modifier.padding(4.dp)) } } },
        confirmButton = { TextButton(onClick = { history = false }) { Text(stringResource(R.string.ui_got_it)) } })
    if (state.invalidClaim) AlertDialog(onDismissRequest = model::clearClaimMessage, text = { Text(stringResource(R.string.bingo_incomplete)) },
        confirmButton = { TextButton(onClick = model::clearClaimMessage) { Text(stringResource(R.string.ui_got_it)) } })
}

@Composable
internal fun patternTitle(pattern: BingoPattern): String = stringResource(when (pattern) {
    BingoPattern.ANY_LINE -> R.string.bingo_line
    BingoPattern.FOUR_CORNERS -> R.string.bingo_corners
    BingoPattern.X -> R.string.bingo_x
    BingoPattern.BLACKOUT -> R.string.bingo_blackout
})

@Composable
private fun BingoCardView(card: BingoCard, round: BingoRound, mark: (String, Int) -> Unit, modifier: Modifier) {
    BingoCardView(card, round.draw.called, round.marks[card.id].orEmpty(), round.status == RoundStatus.PLAYING, mark, modifier)
}

@Composable
internal fun BingoCardView(card: BingoCard, called: List<Int>, marks: Set<Int>, enabled: Boolean, mark: (String, Int) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.testTag("bingo-card")) {
        val heading = 30.dp
        val side = minOf(maxWidth, (maxHeight - heading - 4.dp).coerceAtLeast(0.dp))
        Column(Modifier.width(side).height(side + heading + 4.dp).align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().height(heading), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                "BINGO".forEach { letter -> Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("$letter", style = MaterialTheme.typography.titleMedium, color = Jade, fontWeight = FontWeight.Black)
                } }
            }
            repeat(5) { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { column ->
                        val number = card.cells[row * 5 + column]
                        val marked = number == 0 || number in marks
                        val ready = number != 0 && number in called && !marked
                        val description = if (number == 0) stringResource(R.string.bingo_free) else bingoCallLabel(number)
                        val markState = stringResource(when {
                            marked -> R.string.bingo_marked
                            ready -> R.string.bingo_ready_to_mark
                            else -> R.string.bingo_unmarked
                        })
                        Surface(onClick = { mark(card.id, number) }, enabled = enabled && number != 0 && number in called,
                            modifier = Modifier.weight(1f).fillMaxHeight().testTag("bingo-cell-$number").semantics {
                                contentDescription = description; stateDescription = markState
                            }, shape = RoundedCornerShape(10.dp),
                            border = if (ready) BorderStroke(2.dp, GameNightPalette.gold) else null,
                            color = if (marked) Jade else if (ready) GameNightPalette.gold.copy(alpha = .18f) else Panel,
                            contentColor = if (marked) MaterialTheme.colorScheme.onSecondary else if (ready) GameNightPalette.gold else MaterialTheme.colorScheme.onSurface) {
                            BoxWithConstraints(contentAlignment = Alignment.Center) {
                                val scale = LocalDensity.current.fontScale
                                val font = minOf(24f * scale, maxHeight.value * .65f) / scale
                                Text(if (number == 0) "★" else "$number", fontSize = androidx.compose.ui.unit.TextUnit(font, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BingoPatternPreview(pattern: BingoPattern) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(40.dp)) {
        repeat(25) { index ->
            val row = index / 5; val col = index % 5
            val on = when (pattern) {
                BingoPattern.ANY_LINE -> row == 2
                BingoPattern.FOUR_CORNERS -> row in listOf(0, 4) && col in listOf(0, 4)
                BingoPattern.X -> row == col || row + col == 4
                BingoPattern.BLACKOUT -> true
            }
            drawRect(color.copy(alpha = if (on) 1f else .15f), Offset(col * size.width / 5, row * size.height / 5), Size(size.width / 5 - 2.dp.toPx(), size.height / 5 - 2.dp.toPx()))
        }
    }
}

@Composable
private fun BingoPrizeDialog(round: BingoRound, card: BingoCard, dismiss: () -> Unit, claim: (BingoPattern) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = dismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.bingo_claim), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = dismiss, modifier = Modifier.testTag("bingo-prizes-close")) { Text(stringResource(R.string.ui_got_it)) }
                    }
                    BingoPattern.entries.chunked(2).forEach { patterns ->
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            patterns.forEach { pattern ->
                                val complete = pattern.isComplete(card, round.draw.called.toSet(), round.marks[card.id].orEmpty())
                                val won = round.claims.any { it.playerId == "me" && it.pattern == pattern }
                                val available = round.remaining(pattern)
                                val open = !round.closed(pattern)
                                val caption = if (won) stringResource(R.string.prize_already_claimed)
                                    else if (available == 0 && open) stringResource(R.string.prize_ties_open)
                                    else stringResource(R.string.prize_places_left, available, round.winnersPerPattern)
                                Surface(onClick = { claim(pattern) }, enabled = round.status == RoundStatus.PLAYING && complete && !won && open,
                                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("bingo-claim-${pattern.name}"),
                                    shape = RoundedCornerShape(16.dp), color = if (complete) MaterialTheme.colorScheme.secondaryContainer else Panel) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            BingoPatternPreview(pattern)
                                            Text(patternTitle(pattern), style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text(caption, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
