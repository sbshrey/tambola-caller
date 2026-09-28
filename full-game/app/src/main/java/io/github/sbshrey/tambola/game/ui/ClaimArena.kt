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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    back: () -> Unit, win: WinMoment?, dismissWin: () -> Unit, markEnabled: Boolean, claimEnabled: Boolean, expandedFooter: Boolean,
    extraMenu: @Composable ColumnScope.(() -> Unit) -> Unit, usePower: ((String, MatchPower) -> Unit)? = null, footer: @Composable () -> Unit,
) {
    val words = gameText()
    val hand = table.copy(tickets = table.tickets.filter { it.playerId == ownerId }.take(6))
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val gameNight = table.coins != null
    val ground = if (gameNight) GameNightPalette.background else if (dark) Color(0xFF082E29) else Color(0xFFECF2EA)
    val ink = if (gameNight) GameNightPalette.cream else if (dark) Ivory else Ink
    val muted = if (gameNight) GameNightPalette.muted else if (dark) Color(0xFFB7D0C0) else Color(0xFF486257)
    val gold = Color(0xFFF4C879)
    val remaining = table.settings.prizes.size + table.settings.customPrizes.size -
        table.awards.count { table.finished || it.isClosed(table.settings, table.called.size) } - table.customAwards.size
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var board by remember(table.id) { mutableStateOf(false) }
    var history by remember(table.id) { mutableStateOf(false) }
    var players by remember(table.id) { mutableStateOf(false) }
    var claimTicketId by remember(table.id, ownerId) { mutableStateOf<String?>(null) }
    var powerFeedback by remember(table.id) { mutableStateOf<String?>(null) }
    var powerSequence by remember(table.id) { mutableIntStateOf(table.powers?.noticeSequence ?: 0) }
    LaunchedEffect(table.powers?.noticeSequence) {
        val powers = table.powers
        if (powers != null && powers.noticeSequence > powerSequence) {
            powerSequence = powers.noticeSequence
            powerFeedback = powerNoticeText(powers, words)
            delay(3400)
            powerFeedback = null
        }
    }
    LaunchedEffect(table.finished, claimEnabled) {
        if (table.finished || !claimEnabled) claimTicketId = null
    }
    LaunchedEffect(table.powers?.discarded) {
        if (claimTicketId in table.powers?.discarded.orEmpty()) claimTicketId = null
    }
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
                Row(Modifier.fillMaxWidth().height(62.dp).then(if (gameNight) Modifier.background(GameNightPalette.panel, RoundedCornerShape(16.dp)).padding(horizontal = 4.dp) else Modifier), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("game-options")) {
                            Canvas(Modifier.size(20.dp).semantics { contentDescription = words(R.string.play_more) }) {
                                repeat(3) { line -> drawLine(ink, Offset(0f, size.height * (.2f + line * .3f)), Offset(size.width, size.height * (.2f + line * .3f)), 2.dp.toPx()) }
                            }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(words(R.string.ui_home)) }, onClick = { menu = false; back() }, modifier = Modifier.testTag("home"))
                            DropdownMenuItem(text = { Text(words(R.string.ui_number_board)) }, onClick = { menu = false; history = false; board = true })
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
                    Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                    if (table.coins != null && table.nextDrawAt != null) DeadlineRing(table.nextDrawAt, table.serverTime, table.id, table.callIntervalSeconds * 1_000L,
                        preferences.reducedMotion, Modifier.matchParentSize())
                    Box(Modifier.size(52.dp).graphicsLayer {
                        val p = reveal.value
                        translationY = (1 - p) * -12.dp.toPx()
                        scaleX = .82f + .18f * p + sin(p * Math.PI).toFloat() * .16f; scaleY = scaleX
                    }.background(Brush.linearGradient(if (gameNight) listOf(GameNightPalette.cream, GameNightPalette.coral) else listOf(Color(0xFFFFE3AB), gold)), CircleShape)
                        .border(3.dp, Color.White.copy(alpha = .25f), CircleShape).clip(CircleShape)
                        .clickable(enabled = table.latest != null,
                            onClickLabel = words(R.string.ui_hear_again), onClick = repeatCall)
                        .testTag("current-call").semantics {
                            contentDescription = table.latest?.let { words(R.string.ui_current_number, it) } ?: words(R.string.play_waiting)
                            liveRegion = LiveRegionMode.Polite
                        }, contentAlignment = Alignment.Center) {
                        Text(table.latest?.toString() ?: "?", color = Ink, fontWeight = FontWeight.Black,
                            fontSize = (30 / LocalDensity.current.fontScale).sp, lineHeight = (34 / LocalDensity.current.fontScale).sp,
                            modifier = Modifier.clearAndSetSemantics {})
                    }
                    }
                    if (landscape && expandedFooter && !table.finished) {
                        // Reuse the header, so recovery never shifts the ticket touch targets.
                        Box(Modifier.weight(1f)) { footer() }
                    } else {
                    BoxWithConstraints(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { history = false; board = true }
                        .testTag("open-call-history").semantics(mergeDescendants = true) { contentDescription = words(R.string.ui_number_board) }, contentAlignment = Alignment.CenterStart) {
                        val ballSize = if (landscape) 34.dp else 28.dp
                        val count = ((maxWidth - 15.dp) / (ballSize + 5.dp)).toInt().coerceIn(0, if (landscape) 4 else 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        table.called.dropLast(1).takeLast(count).reversed().forEach { number ->
                            Box(Modifier.size(ballSize).graphicsLayer { translationX = (1 - reveal.value) * -14.dp.toPx() }
                                .background(ink.copy(alpha = .09f), CircleShape), contentAlignment = Alignment.Center) {
                                Text("$number", fontSize = (14 / LocalDensity.current.fontScale).sp, lineHeight = (18 / LocalDensity.current.fontScale).sp,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.testTag("recent-call-$number"))
                            }
                        }
                        Canvas(Modifier.size(10.dp).clearAndSetSemantics {}) {
                            drawLine(ink, Offset(size.width * .3f, 0f), Offset(size.width * .8f, size.height / 2), 1.5.dp.toPx())
                            drawLine(ink, Offset(size.width * .8f, size.height / 2), Offset(size.width * .3f, size.height), 1.5.dp.toPx())
                        }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = if (landscape) 164.dp else 104.dp)) {
                        Text(if (largeText) words(R.string.play_calls_short, table.called.size) else "$status · ${words(R.string.play_calls_short, table.called.size)}",
                            color = muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("play-status").semantics { contentDescription = "$status · ${words(R.string.play_calls_short, table.called.size)}" })
                        Text(pluralStringResource(R.plurals.ticket_count, hand.tickets.size, hand.tickets.size),
                            color = muted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("owned-ticket-count"))
                        if (!largeText) Text(table.coins?.let { words(R.string.coin_pool, it.pool) } ?: words(R.string.play_prizes_left, remaining), color = muted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1)
                    }
                    if (landscape && !table.finished) Box(Modifier.width(110.dp)) { footer() }
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (landscape) TableSidebar(table, ownerId, ink, muted,
                        Modifier.width(if (largeText) 94.dp else if (table.coins != null) 142.dp else 114.dp).fillMaxHeight(),
                        details = { details = true }, openPlayers = { players = true })
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (!landscape) Text("$status · ${words(R.string.play_prizes_left, remaining)}", fontSize = 11.sp, color = muted, maxLines = 1,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).testTag("portrait-prizes-status"))
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            TicketPages(hand, ownerId, preferences.reducedMotion, markNumber, markEnabled,
                                claimEnabled && table.called.isNotEmpty() && !table.finished) { claimTicketId = it }
                            win?.let { WinConfetti(it.id, preferences.reducedMotion, Modifier.matchParentSize(), intensity = .85f) }
                        }
                        // Reserve two scaled lines even when quiet, so feedback never moves a ticket.
                        Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("win-slot"), contentAlignment = Alignment.Center) {
                            Text(winText ?: claimMessage ?: powerFeedback ?: if (table.called.size == 90 && !table.finished) words(R.string.play_final_claims) else "",
                                fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                                color = if (win != null && dark) gold else muted, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().semantics { if (winText != null || claimMessage != null || powerFeedback != null) liveRegion = LiveRegionMode.Polite }.testTag("claim-feedback"))
                        }
                        if (table.powers != null && usePower != null && !table.finished) PowerDock(hand, claimEnabled, preferences.haptics, usePower)
                        if (!landscape || table.finished) Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) { footer() }
                    }
                }
            }
        }
    }
    hand.tickets.firstOrNull { it.id == claimTicketId }?.let { ticket ->
        TicketPrizePicker(table, ticket, hand.tickets.indexOf(ticket) + 1, claimEnabled,
            dismiss = { claimTicketId = null }, claim = claim)
    }
    if (details) ArenaDialog(words(R.string.play_prizes), { details = false }) {
        if (table.coins != null) {
            Text(words(R.string.coin_pool, table.coins.pool))
            if (table.settings.winnersPerPrize > 1) Text(words(R.string.multi_winner_rules, table.settings.winnersPerPrize))
            CoinPrizeGrid(table.coins.prizes, awarded = table.awards.filter { table.finished || it.isClosed(table.settings, table.called.size) }.map { it.prize }.toSet(), onDark = MaterialTheme.colorScheme.background.luminance() < .5f)
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
    if (board) CalledNumberDialog(table.called, history) { board = false }
}

@Composable
private fun TicketPages(table: TableRound, ownerId: String, reducedMotion: Boolean, mark: (String, Int) -> Unit,
    markEnabled: Boolean, claimEnabled: Boolean, choose: (String) -> Unit) {
    val words = gameText()
    Box(Modifier.fillMaxSize().testTag("owned-hand")) {
        val pageSize = 2
        var firstTicket by rememberSaveable(table.id, ownerId) { mutableIntStateOf(0) }
        val page = (firstTicket / pageSize).coerceIn(0, ((table.tickets.size - 1) / pageSize).coerceAtLeast(0))
        val start = page * pageSize
        val visible = table.tickets.drop(start).take(pageSize)
        val pages = (table.tickets.size + pageSize - 1) / pageSize
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { ticket -> key(ticket.id) {
                    val discarded = ticket.id in table.powers?.discarded.orEmpty()
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        CompactTicket(ticket, table, Modifier.fillMaxSize(), reducedMotion, mark, markEnabled && !discarded,
                            claimTicket = { choose(ticket.id) }, claimEnabled = claimEnabled && !discarded)
                        if (discarded) Surface(Modifier.align(Alignment.Center).testTag("discarded-${ticket.id}"),
                            color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(words(R.string.power_ticket_out), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                } }
                if (visible.size < pageSize) Spacer(Modifier.weight(1f))
            }
            if (pages > 1) Column(Modifier.width(48.dp).fillMaxHeight().testTag("ticket-pages"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                IconButton(onClick = { firstTicket = (page - 1) * pageSize }, enabled = page > 0,
                    modifier = Modifier.size(48.dp).background(if (page > 0) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).testTag("tickets-up").semantics { contentDescription = words(R.string.play_previous_tickets) }) {
                    Text("↑", fontSize = (28 / LocalDensity.current.fontScale).sp, lineHeight = (32 / LocalDensity.current.fontScale).sp, modifier = Modifier.clearAndSetSemantics {})
                }
                Text("${page + 1}/$pages", fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, modifier = Modifier.testTag("ticket-page")
                    .semantics { contentDescription = words(R.string.play_ticket_page, page + 1, pages) })
                IconButton(onClick = { firstTicket = (page + 1) * pageSize }, enabled = page + 1 < pages,
                    modifier = Modifier.size(48.dp).background(if (page + 1 < pages) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).testTag("tickets-down").semantics { contentDescription = words(R.string.play_next_tickets) }) {
                    Text("↓", fontSize = (28 / LocalDensity.current.fontScale).sp, lineHeight = (32 / LocalDensity.current.fontScale).sp, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
    }
}

@Composable
private fun TableSidebar(table: TableRound, ownerId: String, ink: Color, muted: Color, modifier: Modifier, details: () -> Unit, openPlayers: () -> Unit) {
    val words = gameText()
    val large = LocalDensity.current.fontScale > 1.3f
    val prizeScroll = rememberScrollState()
    val panel = MaterialTheme.colorScheme.surface
    LaunchedEffect(table.id) { prizeScroll.scrollTo(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = details).semantics(mergeDescendants = true) { contentDescription = words(R.string.play_prizes) }.testTag("prize-rail")) {
            val viewportHeight = (maxHeight - 8.dp).coerceAtLeast(0.dp)
            Column(Modifier.fillMaxWidth().height(viewportHeight).testTag("prize-list").verticalScroll(prizeScroll).heightIn(min = viewportHeight)
                .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceEvenly) {
                table.settings.prizes.take(if (table.coins != null) 8 else 6).forEach { prize ->
                    val won = table.awards.any { it.prize == prize && (table.finished || it.isClosed(table.settings, table.called.size)) }
                    val rank = when (prize) {
                        Prize.EARLY_FIVE -> "5"; Prize.EARLY_TEN -> "10"
                        Prize.FULL_HOUSE, Prize.HOUSE_ONE -> "1"; Prize.HOUSE_TWO -> "2"; Prize.HOUSE_THREE -> "3"
                        else -> null
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = if (large) 28.dp else 24.dp).testTag("sidebar-prize-${prize.name}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (large && rank != null) Text(rank, modifier = Modifier.width(26.dp).semantics { contentDescription = words.prizeTitle(prize) },
                            fontSize = 11.sp, lineHeight = 12.sp, textAlign = TextAlign.Center, color = if (won) muted else ink,
                            textDecoration = if (won) TextDecoration.LineThrough else null)
                        else Canvas(Modifier.size(if (large) 26.dp else 25.dp, 16.dp).semantics { contentDescription = words.prizeTitle(prize) }) {
                            repeat(15) { cell ->
                                val on = when (prize) {
                                    Prize.TOP_LINE, Prize.EARLY_FIVE -> cell < 5; Prize.MIDDLE_LINE -> cell in 5..9
                                    Prize.BOTTOM_LINE -> cell >= 10; Prize.CORNERS -> cell in setOf(0, 4, 10, 14)
                                    Prize.EARLY_TEN -> cell < 10; else -> true
                                }
                                drawRect((if (won) muted else ink).copy(alpha = if (on) 1f else .18f), Offset(cell % 5 * size.width / 5, cell / 5 * size.height / 3),
                                    Size(size.width / 5 - 2f, size.height / 3 - 2f))
                            }
                        }
                        if (!large) {
                            val short = when (prize) {
                                Prize.EARLY_FIVE -> R.string.play_early; Prize.CORNERS -> R.string.play_corners
                                Prize.TOP_LINE -> R.string.play_top; Prize.MIDDLE_LINE -> R.string.play_middle
                                Prize.BOTTOM_LINE -> R.string.play_bottom; else -> R.string.play_house
                            }
                            Text(if (prize == Prize.EARLY_TEN || prize.isRankedHouse) words.prizeTitle(prize) else words(short),
                                fontSize = 11.sp, lineHeight = 12.sp, color = if (won) muted else ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textDecoration = if (won) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
                        }
                        table.coins?.prizes?.firstOrNull { it.prize == prize }?.let { slot ->
                            Text("${slot.coins}", modifier = if (large) Modifier.weight(1f) else Modifier,
                                color = if (won) muted else GameNightPalette.gold, fontSize = 10.sp, lineHeight = 14.sp,
                                textAlign = if (large) TextAlign.End else TextAlign.Start,
                                fontWeight = FontWeight.Bold, textDecoration = if (won) TextDecoration.LineThrough else null)
                        }
                    }
                }
                val extra = (table.settings.prizes.size - if (table.coins != null) 8 else 6).coerceAtLeast(0) + table.settings.customPrizes.size
                if (extra > 0) Text("+$extra", fontSize = 11.sp, color = muted)
            }
            if (prizeScroll.canScrollForward) Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp).clearAndSetSemantics {}) {
                drawRect(panel)
                drawLine(muted, Offset(size.width / 2 - 4.dp.toPx(), 2.dp.toPx()), Offset(size.width / 2, 6.dp.toPx()), 1.5.dp.toPx())
                drawLine(muted, Offset(size.width / 2, 6.dp.toPx()), Offset(size.width / 2 + 4.dp.toPx(), 2.dp.toPx()), 1.5.dp.toPx())
            }
        }
        val playerSummary = if (table.coins != null) words(R.string.coin_players, table.players.size, table.players.count { it.computer })
            else words(R.string.play_players_short, table.players.size)
        val playerControl = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = openPlayers).testTag("table-players")
            .semantics(mergeDescendants = true) { contentDescription = playerSummary }
        if (large) Row(playerControl.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                table.players.sortedBy { it.id != ownerId }.take(2).forEach { AvatarBadge(it.avatar, size = 20.dp) }
            }
            Text("${table.players.size}", modifier = Modifier.weight(1f).testTag("player-count"), fontSize = 11.sp, lineHeight = 13.sp, color = muted, textAlign = TextAlign.Center)
            PlayerChevron(muted)
        } else Column(playerControl.padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(playerSummary, modifier = Modifier.weight(1f).clearAndSetSemantics {}, color = muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                PlayerChevron(muted)
            }
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val preview = table.players.sortedBy { it.id != ownerId }.take(3)
                preview.forEach { player -> AvatarBadge(player.avatar, size = 24.dp) }
                if (table.players.size > preview.size) Text("+${table.players.size - preview.size}", fontSize = 10.sp, color = muted)
            }
        }
    }
}

@Composable
private fun PlayerChevron(color: Color) {
    Canvas(Modifier.size(8.dp, 12.dp).clearAndSetSemantics {}) {
        drawLine(color, Offset(size.width * .25f, size.height * .2f), Offset(size.width * .75f, size.height * .5f), 1.5.dp.toPx())
        drawLine(color, Offset(size.width * .75f, size.height * .5f), Offset(size.width * .25f, size.height * .8f), 1.5.dp.toPx())
    }
}
