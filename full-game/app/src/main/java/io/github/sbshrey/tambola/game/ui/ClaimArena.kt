package io.github.sbshrey.tambola.game.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.presentation.WinMoment
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Readable ticket pages with a local claim action on each ticket. */
@Composable
internal fun ClaimArena(
    table: TableRound, ownerId: String, preferences: Preferences, status: String,
    markNumber: (String, Int) -> Unit, claim: (ClaimSelection) -> Unit, claimMessage: String?, repeatCall: () -> Unit,
    back: () -> Unit, win: WinMoment?, dismissWin: () -> Unit, markEnabled: Boolean, claimEnabled: Boolean,
    extraMenu: @Composable ColumnScope.(() -> Unit) -> Unit, footer: @Composable () -> Unit,
) {
    val words = gameText()
    val hand = table.copy(tickets = table.tickets.filter { it.playerId == ownerId }.take(6))
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val ground = if (dark) Color(0xFF082E29) else Color(0xFFECF2EA)
    val ink = if (dark) Ivory else Ink
    val muted = if (dark) Color(0xFFB7D0C0) else Color(0xFF486257)
    val gold = Color(0xFFF4C879)
    val remaining = table.settings.prizes.size + table.settings.customPrizes.size - table.awards.size - table.customAwards.size
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var board by remember { mutableStateOf(false) }
    var players by remember(table.id) { mutableStateOf(false) }
    var claimTicketId by remember(table.id, ownerId) { mutableStateOf<String?>(null) }
    LaunchedEffect(table.called.size, table.finished) { claimTicketId = null }
    LaunchedEffect(win?.id) { if (win != null) { delay(3400); dismissWin() } }
    val winText = win?.lines?.joinToString(" · ") { line ->
        val title = line.prize?.let(words::prizeTitle) ?: line.title
        if (line.players.any { it.id == ownerId }) words(R.string.play_my_win, title)
        else words(R.string.play_won, line.players.joinToString { words.playerLabel(it) }, title)
    }
    Surface(Modifier.fillMaxSize().testTag("play-arena"), color = ground, contentColor = ink) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 10.dp, vertical = 4.dp)) {
            val landscape = maxWidth > maxHeight
            val largeText = LocalDensity.current.fontScale > 1.3f
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("game-options")) {
                            Canvas(Modifier.size(20.dp).semantics { contentDescription = words(R.string.play_more) }) {
                                repeat(3) { line -> drawLine(ink, Offset(0f, size.height * (.2f + line * .3f)), Offset(size.width, size.height * (.2f + line * .3f)), 2.dp.toPx()) }
                            }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(words(R.string.ui_home)) }, onClick = { menu = false; back() }, modifier = Modifier.testTag("home"))
                            DropdownMenuItem(text = { Text(words(R.string.ui_number_board)) }, onClick = { menu = false; board = true })
                            DropdownMenuItem(text = { Text(words(R.string.play_prizes)) }, onClick = { menu = false; details = true })
                            DropdownMenuItem(text = { Text(words(R.string.play_players_short, table.players.size)) }, onClick = { menu = false; players = true }, modifier = Modifier.testTag("table-players-menu"))
                            DropdownMenuItem(text = { Text(words(R.string.ui_hear_again)) }, enabled = table.latest != null, onClick = { menu = false; repeatCall() })
                            extraMenu { menu = false }
                        }
                    }
                    val reveal = remember { Animatable(1f) }
                    LaunchedEffect(table.latest, preferences.reducedMotion) {
                        if (preferences.reducedMotion || table.latest == null) reveal.snapTo(1f)
                        else { reveal.snapTo(0f); reveal.animateTo(1f, tween(430)) }
                    }
                    val target = hand.tickets.firstOrNull { table.latest in it.numbers && table.latest !in table.marks[it.id].orEmpty() }
                    Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                    if (table.coins != null) DeadlineRing(table.nextDrawAt, table.serverTime, table.id, 5_000,
                        preferences.reducedMotion, Modifier.matchParentSize())
                    Box(Modifier.size(52.dp).graphicsLayer {
                        val p = reveal.value
                        translationY = (1 - p) * -12.dp.toPx()
                        scaleX = .82f + .18f * p + sin(p * Math.PI).toFloat() * .16f; scaleY = scaleX
                    }.background(Brush.linearGradient(listOf(Color(0xFFFFE3AB), gold)), CircleShape)
                        .border(3.dp, Color.White.copy(alpha = .25f), CircleShape).clip(CircleShape)
                        .clickable(enabled = markEnabled && !table.finished && !table.settings.assistedMarking && target != null,
                            onClickLabel = table.latest?.let { words(R.string.play_mark_number, it) }) { markNumber(target!!.id, table.latest!!) }
                        .testTag("current-call").semantics {
                            contentDescription = table.latest?.let { words(R.string.ui_current_number, it) } ?: words(R.string.play_waiting)
                            liveRegion = LiveRegionMode.Polite
                        }, contentAlignment = Alignment.Center) {
                        Text(table.latest?.toString() ?: "?", color = Ink, fontWeight = FontWeight.Black,
                            fontSize = (30 / LocalDensity.current.fontScale).sp, modifier = Modifier.clearAndSetSemantics {})
                    }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        table.called.dropLast(1).takeLast(if (landscape) 5 else 2).reversed().forEach { number ->
                            Box(Modifier.size(if (landscape) 34.dp else 28.dp).graphicsLayer { translationX = (1 - reveal.value) * -14.dp.toPx() }
                                .background(ink.copy(alpha = .09f), CircleShape), contentAlignment = Alignment.Center) {
                                Text("$number", fontSize = (14 / LocalDensity.current.fontScale).sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = if (landscape) 164.dp else 104.dp)) {
                        Text("$status · ${words(R.string.play_calls_short, table.called.size)}", color = muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("play-status"))
                        Text(words(R.string.play_tickets_short, hand.tickets.size), color = muted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!largeText) Text(table.coins?.let { words(R.string.coin_pool, it.pool) } ?: words(R.string.play_prizes_left, remaining), color = muted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1)
                    }
                    if (landscape && !table.finished) Box(Modifier.width(110.dp)) { footer() }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (landscape) TableSidebar(table, ownerId, ink, muted,
                        Modifier.width(if (largeText) 94.dp else if (table.coins != null) 142.dp else 114.dp).fillMaxHeight(),
                        details = { details = true }, openPlayers = { players = true })
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (!landscape) Text("$status · ${words(R.string.play_prizes_left, remaining)}", fontSize = 11.sp, color = muted, maxLines = 1,
                            modifier = Modifier.height(22.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            TicketPages(hand, ownerId, preferences.reducedMotion, markNumber, markEnabled,
                                claimEnabled && table.called.isNotEmpty() && !table.finished) { claimTicketId = it }
                            win?.let { WinConfetti(it.id, preferences.reducedMotion, Modifier.matchParentSize(), intensity = .85f) }
                        }
                        Box(Modifier.fillMaxWidth().height(if (largeText) 30.dp else 24.dp).testTag("win-slot"), contentAlignment = Alignment.Center) {
                            Text(claimMessage ?: winText ?: if (table.called.size == 90 && !table.finished) words(R.string.play_final_claims) else "",
                                fontSize = 11.sp, lineHeight = 14.sp, color = if (win != null && dark) gold else muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { if (winText != null || claimMessage != null) liveRegion = LiveRegionMode.Polite }.testTag("claim-feedback"))
                        }
                        if (!landscape || table.finished) Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) { footer() }
                    }
                }
            }
        }
    }
    hand.tickets.firstOrNull { it.id == claimTicketId }?.let { ticket ->
        Dialog(onDismissRequest = { claimTicketId = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
          Surface(Modifier.fillMaxWidth(.9f).widthIn(max = 640.dp).semantics { testTagsAsResourceId = true }, shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
           BoxWithConstraints(Modifier.padding(16.dp)) {
            val columns = if (maxWidth > 420.dp) 3 else 2
            Column {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(words(R.string.play_choose_prize, hand.tickets.indexOf(ticket) + 1), modifier = Modifier.weight(1f),
                    fontSize = 19.sp, lineHeight = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                IconButton(onClick = { claimTicketId = null }, modifier = Modifier.size(48.dp).testTag("dismiss-claim")
                    .semantics { contentDescription = words(R.string.ui_back_to_game) }) { Text("×", fontSize = 28.sp) }
              }
            val choices = table.settings.prizes.map { prize -> prize.name to (table.coins?.prizes?.firstOrNull { it.prize == prize }?.let { words(R.string.coin_prize_amount, words.prizeTitle(prize), it.coins) } ?: words.prizeTitle(prize)) } + table.settings.customPrizes.map { it.id to it.title }
            val closedHouses = table.awards.filter { it.prize.isRankedHouse && it.drawIndex < table.called.size }
            val nextHouse = table.settings.prizes.filter { it.isRankedHouse && closedHouses.none { award -> award.prize == it } }.minByOrNull { it.ordinal }
            Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
             choices.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (id, label) ->
                        val award = table.awards.firstOrNull { it.prize.name == id }
                        val custom = table.customAwards.firstOrNull { it.prizeId == id }
                        val draw = award?.drawIndex ?: custom?.drawIndex
                        val wonByTicket = ticket.id in (award?.ticketIds ?: custom?.ticketIds).orEmpty()
                        val rankedHouse = table.settings.prizes.firstOrNull { it.name == id && it.isRankedHouse }
                        val houseLocked = rankedHouse != null && (rankedHouse != nextHouse || closedHouses.any { ticket.id in it.ticketIds })
                        val open = (draw == null || draw == table.called.size) && !wonByTicket && !houseLocked
                        OutlinedButton(onClick = { claimTicketId = null; claim(ClaimSelection(ticket.id, id)) },
                            enabled = claimEnabled && open && !table.finished, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("claim-prize-$id"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(if (wonByTicket || (draw != null && draw < table.called.size)) "✓ $label" else label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
             }
            }
           }
          }
         }
        }
    }
    if (details) ArenaDialog(words(R.string.play_prizes), { details = false }) {
        if (table.coins != null) {
            Text(words(R.string.coin_pool, table.coins.pool))
            CoinPrizeGrid(table.coins.prizes, awarded = table.awards.map { it.prize }.toSet(), onDark = MaterialTheme.colorScheme.background.luminance() < .5f)
            Text(words(R.string.coin_ties), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(words(R.string.play_claim_rules))
            RuleList(hand)
            table.players.forEach { player -> Text("${words.playerLabel(player)} · ${table.score(player.id)}") }
        }
    }
    if (players) ArenaDialog(words(R.string.play_players_short, table.players.size), { players = false }) {
        table.players.sortedBy { it.id != ownerId }.forEach { player ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("table-player-${player.id}").semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarBadge(player.avatar, size = 32.dp, modifier = Modifier.clearAndSetSemantics {})
                Column(Modifier.weight(1f)) {
                    Text(player.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (player.computer) Text(words(R.string.play_computer_short), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (board) ArenaDialog(words(R.string.ui_the_number_board), { board = false }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..90).forEach { number -> Box(Modifier.size(34.dp).background(if (number in table.called) Color(0xFF21634D) else Panel, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                Text("$number", color = if (number in table.called) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            } }
        }
    }
}

@Composable
private fun TicketPages(table: TableRound, ownerId: String, reducedMotion: Boolean, mark: (String, Int) -> Unit,
    markEnabled: Boolean, claimEnabled: Boolean, choose: (String) -> Unit) {
    val words = gameText()
    BoxWithConstraints(Modifier.fillMaxSize().testTag("owned-hand")) {
        // Never shrink three or six tickets into one screen. Short windows get one.
        val pageSize = if (maxHeight >= 248.dp) 2 else 1
        var firstTicket by rememberSaveable(table.id, ownerId) { mutableIntStateOf(0) }
        val page = (firstTicket / pageSize).coerceIn(0, ((table.tickets.size - 1) / pageSize).coerceAtLeast(0))
        val start = page * pageSize
        val visible = table.tickets.drop(start).take(pageSize)
        val pages = (table.tickets.size + pageSize - 1) / pageSize
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { ticket -> key(ticket.id) {
                    CompactTicket(ticket, table, Modifier.fillMaxWidth().weight(1f), reducedMotion, mark, markEnabled,
                        claimTicket = { choose(ticket.id) }, claimEnabled = claimEnabled)
                } }
                if (visible.size < pageSize) Spacer(Modifier.weight(1f))
            }
            if (pages > 1) Column(Modifier.width(48.dp).fillMaxHeight().testTag("ticket-pages"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                IconButton(onClick = { firstTicket = (page - 1) * pageSize }, enabled = page > 0,
                    modifier = Modifier.size(48.dp).testTag("tickets-up").semantics { contentDescription = words(R.string.play_previous_tickets) }) {
                    Text("↑", fontSize = 28.sp)
                }
                Text("${page + 1}/$pages", fontSize = 11.sp, maxLines = 1, modifier = Modifier.testTag("ticket-page")
                    .semantics { contentDescription = words(R.string.play_ticket_page, page + 1, pages) })
                IconButton(onClick = { firstTicket = (page + 1) * pageSize }, enabled = page + 1 < pages,
                    modifier = Modifier.size(48.dp).testTag("tickets-down").semantics { contentDescription = words(R.string.play_next_tickets) }) {
                    Text("↓", fontSize = 28.sp)
                }
            }
        }
    }
}

@Composable
private fun TableSidebar(table: TableRound, ownerId: String, ink: Color, muted: Color, modifier: Modifier, details: () -> Unit, openPlayers: () -> Unit) {
    val words = gameText()
    val large = LocalDensity.current.fontScale > 1.3f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = details)
            .semantics(mergeDescendants = true) { contentDescription = words(R.string.play_prizes) }.testTag("prize-rail"), verticalArrangement = Arrangement.SpaceEvenly) {
            table.settings.prizes.take(if (table.coins != null) 8 else 6).forEach { prize ->
                val won = table.awards.any { it.prize == prize }
                val short = when (prize) {
                    Prize.EARLY_FIVE -> R.string.play_early; Prize.CORNERS -> R.string.play_corners
                    Prize.TOP_LINE -> R.string.play_top; Prize.MIDDLE_LINE -> R.string.play_middle
                    Prize.BOTTOM_LINE -> R.string.play_bottom; else -> R.string.play_house
                }
                Row(Modifier.testTag("sidebar-prize-${prize.name}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Canvas(Modifier.size(25.dp, 16.dp)) {
                        if (won) {
                            drawLine(ink, Offset(2f, size.height / 2), Offset(size.width * .4f, size.height - 2f), 2.dp.toPx())
                            drawLine(ink, Offset(size.width * .4f, size.height - 2f), Offset(size.width - 2f, 2f), 2.dp.toPx())
                        } else repeat(15) { cell ->
                            val on = when (prize) {
                                Prize.TOP_LINE, Prize.EARLY_FIVE -> cell < 5; Prize.MIDDLE_LINE -> cell in 5..9
                                Prize.BOTTOM_LINE -> cell >= 10; Prize.CORNERS -> cell in setOf(0, 4, 10, 14)
                                Prize.EARLY_TEN -> cell < 10; else -> true
                            }
                            drawRect(ink.copy(alpha = if (on) 1f else .18f), Offset(cell % 5 * size.width / 5, cell / 5 * size.height / 3), Size(size.width / 5 - 2f, size.height / 3 - 2f))
                        }
                    }
                    Text(if (large) if (prize == Prize.EARLY_FIVE) "5" else if (prize == Prize.EARLY_TEN) "10" else "" else
                        if (prize == Prize.EARLY_TEN || prize.isRankedHouse) words.prizeTitle(prize) else words(short), fontSize = 11.sp, lineHeight = 12.sp, color = if (won) muted else ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { contentDescription = words.prizeTitle(prize) })
                    table.coins?.prizes?.firstOrNull { it.prize == prize }?.let { slot ->
                        Text("${slot.coins}", color = if (won) muted else ink, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val extra = (table.settings.prizes.size - if (table.coins != null) 8 else 6).coerceAtLeast(0) + table.settings.customPrizes.size
            if (extra > 0) Text("+$extra", fontSize = 11.sp, color = muted)
        }
        val playerSummary = if (table.coins != null) words(R.string.coin_players, table.players.size, table.players.count { it.computer })
            else words(R.string.play_players_short, table.players.size)
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = openPlayers).testTag("table-players")
            .semantics(mergeDescendants = true) { contentDescription = playerSummary }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(playerSummary, modifier = Modifier.weight(1f).clearAndSetSemantics {}, color = muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Canvas(Modifier.size(8.dp, 12.dp).clearAndSetSemantics {}) {
                    drawLine(muted, Offset(size.width * .25f, size.height * .2f), Offset(size.width * .75f, size.height * .5f), 1.5.dp.toPx())
                    drawLine(muted, Offset(size.width * .75f, size.height * .5f), Offset(size.width * .25f, size.height * .8f), 1.5.dp.toPx())
                }
            }
            val preview = table.players.sortedBy { it.id != ownerId }.take(if (table.settings.prizes.size > 6) 2 else 3)
            preview.forEach { player ->
                Row(Modifier.height(if (large) 28.dp else 34.dp).fillMaxWidth().clearAndSetSemantics {},
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    AvatarBadge(player.avatar, size = 26.dp)
                    Column(Modifier.weight(1f)) {
                        if (!large) Text(player.name, fontSize = 11.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (player.computer) Text(words(R.string.play_computer_short), fontSize = if (large) 8.sp else 9.sp, lineHeight = 10.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
