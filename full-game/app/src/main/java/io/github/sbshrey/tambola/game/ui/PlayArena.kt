package io.github.sbshrey.tambola.game.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.*
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.presentation.WinMoment
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.delay

private val DabGreen = Color(0xFF21634D)
private val BallGold = Color(0xFFFFCC7A)

/** The live table has bounded geometry. Ticket cells display state, while all
 * interaction uses full-size controls; six tickets never become 162 tiny buttons.
 */
@Composable
fun PlayArena(
    table: TableRound, ownerId: String, preferences: Preferences, status: String,
    dabCalled: () -> Unit, repeatCall: () -> Unit, back: () -> Unit,
    win: WinMoment?, dismissWin: () -> Unit, enabled: Boolean = true,
    extraMenu: @Composable ColumnScope.(() -> Unit) -> Unit = {},
    markNumber: ((String, Int) -> Unit)? = null, claim: ((ClaimSelection) -> Unit)? = null,
    claimMessage: String? = null, claimEnabled: Boolean = enabled, expandedFooter: Boolean = false,
    usePower: ((String, MatchPower) -> Unit)? = null,
    reactionMessage: String? = null,
    footer: @Composable () -> Unit,
) {
    if (table.settings.manualClaims && markNumber != null && claim != null) {
        MaterialTheme(colorScheme = if (table.coins != null) GameNightPalette.colors else MaterialTheme.colorScheme) {
            ClaimArena(table, ownerId, preferences, status, markNumber, claim, claimMessage, repeatCall, back,
                win, dismissWin, enabled, claimEnabled, expandedFooter, extraMenu, usePower, reactionMessage, footer)
        }
        return
    }
    val words = gameText()
    val tickets = table.tickets.filter { it.playerId == ownerId }.take(6)
    val ownTable = table.copy(tickets = tickets)
    val owner = table.players.firstOrNull { it.id == ownerId }
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var board by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val ground = if (dark) Color(0xFF092F2B) else Color(0xFFECF1E7)
    val ink = if (dark) Ivory else Ink
    val muted = if (dark) Color(0xFFA8C6BA) else Color(0xFF486157)
    LaunchedEffect(win?.id) { if (win != null) { delay(3400); dismissWin() } }
    Surface(Modifier.fillMaxSize().testTag("play-arena"), color = ground, contentColor = ink) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val short = maxHeight < 500.dp
            val dense = tickets.size >= 4 || short
            val largeText = LocalDensity.current.fontScale > 1.3f
            val compact = dense && (maxHeight < 680.dp || largeText)
            val line = win?.lines?.firstOrNull()
            val title = line?.let { it.prize?.let(words::prizeTitle) ?: it.title }
            val winText = line?.let { if (it.players.any { player -> player.id == ownerId }) words(R.string.play_my_win, title!!)
                else words(R.string.play_won, it.players.joinToString { player -> words.playerLabel(player) }, title!!) }
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back, modifier = Modifier.size(48.dp).testTag("home").semantics { contentDescription = words(R.string.ui_home) }) {
                        Canvas(Modifier.size(20.dp)) {
                            drawLine(ink, Offset(size.width * .65f, size.height * .15f), Offset(size.width * .3f, size.height * .5f), 2.dp.toPx())
                            drawLine(ink, Offset(size.width * .3f, size.height * .5f), Offset(size.width * .65f, size.height * .85f), 2.dp.toPx())
                        }
                    }
                    Text(if (short) "${owner?.name.orEmpty()} · $status" else "$status · ${words.mode(table.settings.mode)}", color = ink, style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("play-status"))
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("game-options").semantics { contentDescription = words(R.string.play_more) }) {
                            Canvas(Modifier.size(22.dp)) { repeat(3) { drawCircle(ink, 2.dp.toPx(), Offset(size.width * (.2f + it * .3f), size.height / 2)) } }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(words(R.string.ui_number_board)) }, onClick = { menu = false; board = true })
                            DropdownMenuItem(text = { Text(words(R.string.play_prizes)) }, onClick = { menu = false; details = true })
                            DropdownMenuItem(text = { Text(words(R.string.ui_hear_again)) }, enabled = table.latest != null, onClick = { menu = false; repeatCall() })
                            extraMenu { menu = false }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(if (short || compact) 64.dp else if (dense) 72.dp else 116.dp), verticalAlignment = Alignment.CenterVertically) {
                    val pulse = remember { Animatable(1f) }
                    LaunchedEffect(table.latest, preferences.reducedMotion) {
                        if (preferences.reducedMotion) pulse.snapTo(1f)
                        else { pulse.snapTo(.88f); pulse.animateTo(1f, tween(220)) }
                    }
                    Box(Modifier.padding(end = 14.dp).aspectRatio(1f).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                        .background(Brush.linearGradient(listOf(Color(0xFFFFE0A3), BallGold)), CircleShape)
                        .border(4.dp, Color.White.copy(alpha = .25f), CircleShape)
                        .clip(CircleShape).clickable(enabled = table.latest != null, role = Role.Button, onClick = repeatCall)
                        .testTag("current-call").semantics {
                            contentDescription = table.latest?.let { words(R.string.ui_current_number, it) } ?: words(R.string.play_waiting)
                            liveRegion = LiveRegionMode.Polite
                        }, contentAlignment = Alignment.Center) {
                        win?.let { WinConfetti(it.id, preferences.reducedMotion, Modifier.matchParentSize()) }
                        // Numeric display scales within its fixed ball; text alternatives
                        // carry the full value when system font size exceeds its geometry.
                        val density = LocalDensity.current
                        Text(table.latest?.toString() ?: "?", color = Ink, fontWeight = FontWeight.Black,
                            fontSize = ((if (short) 36 else if (dense) 46 else 64) / density.fontScale).sp,
                            modifier = Modifier.clearAndSetSemantics {})
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp)) {
                        Text(if (compact && winText != null) winText else if (table.finished) words(if (table.status == RoundStatus.COMPLETED) R.string.round_complete else R.string.round_cancelled)
                            else words(R.string.ui_call_of_90, table.called.size), color = muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { if (compact && winText != null) liveRegion = LiveRegionMode.Polite })
                        if (!compact && (!largeText || !dense)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            table.called.dropLast(1).takeLast(4).reversed().forEach { number ->
                                Box(Modifier.size(if (short) 28.dp else 34.dp).background(ink.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("$number", fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, maxLines = 1)
                                }
                            }
                            if (table.called.isEmpty()) Text(words(R.string.play_ready), color = ink, style = MaterialTheme.typography.titleMedium)
                            else if (table.called.size == 1) Text(words(R.string.play_your_tickets), color = ink, style = MaterialTheme.typography.titleMedium)
                        }
                        LinearProgressIndicator(progress = { table.called.size / 90f }, color = if (dark) BallGold else DabGreen,
                            trackColor = ink.copy(alpha = .08f), modifier = Modifier.fillMaxWidth().height(3.dp))
                        if (compact && !short) PrizeRail(ownTable, ink, muted, compact = true) { details = true }
                    }
                }
                if (!short && !compact) PrizeRail(ownTable, ink, muted, compact = dense) { details = true }
                if (!short) Row(Modifier.fillMaxWidth().height(if (largeText) 36.dp else if (dense) 24.dp else 30.dp), verticalAlignment = Alignment.CenterVertically) {
                    owner?.let { AvatarBadge(it.avatar, size = 24.dp) }
                    Text(owner?.name ?: words(R.string.play_your_tickets), Modifier.weight(1f).padding(start = 8.dp),
                        fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.ticket_count, tickets.size, tickets.size), fontSize = 11.sp, lineHeight = 14.sp, color = muted, maxLines = 1)
                }
                if (tickets.isNotEmpty()) TicketHand(ownTable, Modifier.weight(1f), preferences.reducedMotion)
                else Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(words(R.string.ui_your_tickets_are_ready)) }
                // This slot is always present, so a win never shifts a ticket.
                if (!compact) Box(Modifier.fillMaxWidth().height(if (largeText) 34.dp else if (dense) 24.dp else 30.dp).testTag("win-slot"), contentAlignment = Alignment.Center) {
                    Text(winText ?: words(if (table.settings.assistedMarking) R.string.play_auto_dab else R.string.play_dabbed),
                        color = if (line == null) muted else if (dark) BallGold else DabGreen,
                        fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { if (line != null) liveRegion = LiveRegionMode.Polite })
                }
                val missed = tickets.flatMap { ticket -> ticket.numbers.filter { it in table.called && it !in table.marks[ticket.id].orEmpty() }.map { ticket.id to it } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!table.settings.assistedMarking && !table.finished) {
                        Button(onClick = dabCalled, enabled = enabled && missed.isNotEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dab-called")
                                .semantics { contentDescription = words(R.string.play_dab, missed.size) }, shape = RoundedCornerShape(16.dp)) {
                            Text(words(R.string.play_dab_short, missed.size), maxLines = 1)
                        }
                    }
                    Box(Modifier.weight(if (table.settings.assistedMarking || table.finished) 1f else 1.2f)) { footer() }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
    if (details && tickets.isNotEmpty()) ArenaDialog(words(R.string.play_prizes), { details = false }) {
        Text(words(R.string.play_prizes_hint), color = Muted)
        RuleList(ownTable)
    }
    if (board) CalledNumberDialog(table.called) { board = false }
}

@Composable
internal fun TicketHand(table: TableRound, modifier: Modifier, reducedMotion: Boolean, markNumber: ((String, Int) -> Unit)? = null, markEnabled: Boolean = true) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("owned-hand")) {
        val columns = when {
            markNumber != null -> if (maxWidth > 380.dp && table.tickets.size >= 3) 2 else 1
            maxWidth > 580.dp && maxHeight < 300.dp && table.tickets.size >= 3 -> 3
            maxWidth > 580.dp && table.tickets.size > 1 -> 2
            else -> 1
        }
        val rows = table.tickets.chunked(columns)
        val handHeight = minOf(maxHeight, 176.dp * rows.size + 5.dp * (rows.size - 1))
        Column(Modifier.fillMaxWidth().height(handHeight).align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            rows.forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { ticket -> key(ticket.id) { CompactTicket(ticket, table, Modifier.weight(1f).fillMaxHeight(), reducedMotion, markNumber, markEnabled) } }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun CompactTicket(ticket: Ticket, table: TableRound, modifier: Modifier, reducedMotion: Boolean, markNumber: ((String, Int) -> Unit)? = null, markEnabled: Boolean = true,
    claimTicket: (() -> Unit)? = null, claimEnabled: Boolean = true) {
    val words = gameText()
    val density = LocalDensity.current
    val gameNight = table.coins != null
    val ordinal = table.tickets.indexOf(ticket) + 1
    val label = words(R.string.play_claim)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp)
    val measured = androidx.compose.ui.text.rememberTextMeasurer().measure(label, style = labelStyle)
    val actionWidth = maxOf(82.dp, with(density) { measured.size.width.toDp() } + 28.dp)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(if (gameNight) 16.dp else 10.dp))
        .background(if (gameNight) GameNightPalette.cream else Ivory).testTag("hand-ticket-$ordinal")) {
        // Keep a useful number-grid width instead of squeezing it around larger text.
        val stacked = claimTicket != null && density.fontScale > 1.3f && maxWidth - actionWidth < 224.dp
        if (stacked) Column(Modifier.fillMaxSize()) {
            TicketBody(ticket, table, Modifier.fillMaxWidth().weight(1f), reducedMotion, markNumber, markEnabled)
            TicketClaimAction(label, ordinal, gameNight, claimEnabled, requireNotNull(claimTicket),
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp, vertical = 3.dp), horizontal = true, progress = ticketClaimProgress(table, ticket))
        } else Row(Modifier.fillMaxSize()) {
            TicketBody(ticket, table, Modifier.weight(1f).fillMaxHeight(), reducedMotion, markNumber, markEnabled)
            if (claimTicket != null) TicketClaimAction(label, ordinal, gameNight, claimEnabled, claimTicket,
                Modifier.width(actionWidth).fillMaxHeight().padding(horizontal = 6.dp), horizontal = false, progress = ticketClaimProgress(table, ticket))
        }
    }
}

@Composable
private fun TicketClaimAction(label: String, ordinal: Int, gameNight: Boolean, enabled: Boolean, claim: () -> Unit,
    modifier: Modifier, horizontal: Boolean, progress: Float) {
    val words = gameText()
    val scale = LocalDensity.current.fontScale
    val glyph: @Composable () -> Unit = {
        // Decorative sparkle has a fixed icon size; the action label respects text scale.
        Text("✦", fontSize = (18 / scale).sp, lineHeight = (18 / scale).sp, modifier = Modifier.clearAndSetSemantics {})
    }
    val caption: @Composable () -> Unit = { Text(label, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1) }
    Box(modifier, contentAlignment = Alignment.Center) {
        val diameter = if (horizontal) 48.dp else 68.dp
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                color = if (progress >= 1f) Jade else Coral, trackColor = Ink.copy(alpha = .15f), strokeWidth = 4.dp)
            Button(onClick = claim, enabled = enabled, contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (gameNight) GameNightPalette.coral else BallGold, contentColor = Ink),
                modifier = Modifier.fillMaxSize().padding(6.dp).testTag("claim-ticket-$ordinal")
                    .semantics { contentDescription = words(R.string.play_claim_ticket, ordinal) }, shape = CircleShape) {
                if (scale <= 1.3f && !horizontal) caption() else glyph()
            }
        }
        if (horizontal) Text(label, Modifier.align(Alignment.CenterStart), fontSize = 12.sp)
    }
}

@Composable
private fun TicketBody(ticket: Ticket, table: TableRound, modifier: Modifier, reducedMotion: Boolean,
    markNumber: ((String, Int) -> Unit)?, markEnabled: Boolean) {
    val words = gameText()
    val marked = table.marks[ticket.id].orEmpty()
    val ordinal = table.tickets.indexOf(ticket) + 1
    val gameNight = table.coins != null
    val ticketInk = if (gameNight) GameNightPalette.ticketInk else Ink
    Row(modifier) {
        Box(Modifier.width(22.dp).fillMaxHeight().background(if (gameNight) GameNightPalette.ticketBlank else BallGold), contentAlignment = Alignment.Center) {
            Text("%02d".format(ordinal), color = ticketInk, fontSize = (10 / LocalDensity.current.fontScale).sp, lineHeight = (12 / LocalDensity.current.fontScale).sp, maxLines = 1, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = words(R.string.play_ticket_label, ordinal, marked.size) })
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(if (gameNight) 5.dp else 2.dp)) {
            val cellHeight = ((maxHeight - 2.dp) / 3).coerceAtLeast(1.dp)
            val cellWidth = ((maxWidth - 8.dp) / 9).coerceAtLeast(1.dp)
            val density = LocalDensity.current
            val numberSize = minOf(cellHeight.value * .95f, cellWidth.value * .8f, 26f * density.fontScale) / density.fontScale
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                (0..2).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth().then(if (markNumber != null) Modifier else Modifier.semantics(mergeDescendants = true) {
                        contentDescription = words(R.string.ui_row_marked, words.row(row), ticket.row(row).joinToString(), ticket.row(row).filter { it in marked }.joinToString().ifEmpty { words(R.string.no_marked_numbers) })
                    }), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        (0..8).forEach { col ->
                            val number = ticket.cells[row * 9 + col]
                            val dabbed = number in marked
                            val sending = number in table.pendingMarks[ticket.id].orEmpty()
                            val canMark = markEnabled && !sending && markNumber != null && number != 0 && !table.finished && !table.settings.assistedMarking && !(table.powers != null && dabbed)
                            val stamp = remember(ticket.id, number) { Animatable(if (dabbed) 1f else 0f) }
                            LaunchedEffect(dabbed, reducedMotion) {
                                if (!dabbed || reducedMotion) stamp.snapTo(if (dabbed) 1f else 0f)
                                else if (stamp.value < 1f) stamp.animateTo(1f, tween(360))
                            }
                            val fill = animateColorAsState(if (gameNight) {
                                if (dabbed) GameNightPalette.ticketDab else if (number == 0) GameNightPalette.ticketBlank else GameNightPalette.ticketCell
                            } else if (dabbed) DabGreen else if (number == 0) Color(0xFFECE7D9) else Color.White,
                                animationSpec = tween(if (reducedMotion) 0 else 160), label = "dab")
                            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                                .drawBehind { drawRect(fill.value) }
                                .drawWithContent {
                                    drawContent()
                                    if (sending) drawCircle(GameNightPalette.gold, radius = size.minDimension * .4f, style = Stroke(2.dp.toPx()))
                                    if (dabbed && !reducedMotion && stamp.value < 1f) drawCircle((if (gameNight) GameNightPalette.ticketEdge else BallGold).copy(alpha = 1f - stamp.value),
                                        radius = size.minDimension * (.15f + stamp.value * .45f), style = Stroke(2.dp.toPx()))
                                }
                                .then(if (number != 0 && markNumber != null) Modifier.testTag("dab-$number")
                                    .pointerInput(ticket.id, number, canMark) { detectTapGestures { if (canMark) markNumber(ticket.id, number) } }
                                    .semantics(mergeDescendants = true) {
                                        role = Role.Button
                                        contentDescription = words(if (dabbed && table.powers != null) R.string.power_marked_number else if (dabbed) R.string.play_unmark_number else R.string.play_mark_number, number)
                                        if (sending) stateDescription = words(R.string.power_mark_sending)
                                        if (!canMark) disabled()
                                        onClick { if (canMark) { markNumber(ticket.id, number); true } else false }
                                    } else Modifier), contentAlignment = Alignment.Center) {
                                if (number != 0) Text("$number", fontSize = numberSize.sp, lineHeight = numberSize.sp, color = if (dabbed && !gameNight) Color.White else ticketInk,
                                    fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.testTag("ticket-number").clearAndSetSemantics {})
                                if (dabbed) Canvas(Modifier.align(Alignment.BottomEnd).size(5.dp).padding(1.dp)) {
                                    val check = if (gameNight) ticketInk else Color.White
                                    drawLine(check, Offset(0f, size.height / 2), Offset(size.width / 3, size.height), 1f)
                                    drawLine(check, Offset(size.width / 3, size.height), Offset(size.width, 0f), 1f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrizeRail(table: TableRound, ink: Color, muted: Color, compact: Boolean, open: () -> Unit) {
    val words = gameText()
    val overflow = table.settings.prizes.size + table.settings.customPrizes.size > 6
    val visible = table.settings.prizes.take(if (overflow) 5 else 6)
    Row(Modifier.fillMaxWidth().testTag("prize-rail"), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        visible.forEach { prize ->
            val won = table.awards.any { it.prize == prize && (table.finished || it.isClosed(table.settings, table.called.size)) }
            val short = when (prize) {
                Prize.EARLY_FIVE -> R.string.play_early
                Prize.CORNERS -> R.string.play_corners
                Prize.TOP_LINE -> R.string.play_top
                Prize.MIDDLE_LINE -> R.string.play_middle
                Prize.BOTTOM_LINE -> R.string.play_bottom
                Prize.FULL_HOUSE, Prize.HOUSE_ONE, Prize.HOUSE_TWO, Prize.HOUSE_THREE -> R.string.play_house
                else -> null
            }
            Column(Modifier.weight(1f).height(if (compact) 22.dp else 48.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = if (won) .14f else .05f))
                .then(if (compact) Modifier else Modifier.clickable(role = Role.Button, onClick = open))
                .semantics(mergeDescendants = true) { contentDescription = words.prizeTitle(prize); if (won) stateDescription = words(R.string.ui_verified_call_n, table.awards.first { it.prize == prize }.drawIndex) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (!won && prize in setOf(Prize.EARLY_FIVE, Prize.EARLY_TEN)) Text(if (prize == Prize.EARLY_FIVE) "5" else "10",
                    fontSize = (14 / LocalDensity.current.fontScale).sp, lineHeight = (16 / LocalDensity.current.fontScale).sp, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.clearAndSetSemantics {})
                else Canvas(Modifier.size(27.dp, if (compact) 12.dp else 16.dp).clearAndSetSemantics {}) {
                    if (won) {
                        drawLine(ink, Offset(5f, size.height * .5f), Offset(size.width * .4f, size.height - 3f), 2.dp.toPx())
                        drawLine(ink, Offset(size.width * .4f, size.height - 3f), Offset(size.width - 3f, 2f), 2.dp.toPx())
                    } else repeat(15) { cell ->
                        val active = when (prize) {
                            Prize.TOP_LINE, Prize.EARLY_FIVE -> cell < 5
                            Prize.MIDDLE_LINE -> cell in 5..9
                            Prize.BOTTOM_LINE -> cell >= 10
                            Prize.CORNERS -> cell in setOf(0, 4, 10, 14)
                            Prize.EARLY_TEN -> cell < 10
                            else -> true
                        }
                        drawRoundRect(ink.copy(alpha = if (active) .9f else .2f), Offset(cell % 5 * size.width / 5, cell / 5 * size.height / 3),
                            Size(size.width / 5 - 2f, size.height / 3 - 2f), CornerRadius(1f))
                    }
                }
                if (!compact) {
                    Spacer(Modifier.height(3.dp))
                    Text(short?.let { words(it) } ?: words.prizeTitle(prize), color = muted, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
        if (overflow) Box(Modifier.weight(1f).height(if (compact) 22.dp else 48.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = .05f))
            .then(if (compact) Modifier else Modifier.clickable(role = Role.Button, onClick = open)).semantics { contentDescription = words(R.string.play_prizes) }, contentAlignment = Alignment.Center) {
            Text("+${table.settings.prizes.size + table.settings.customPrizes.size - visible.size}", color = ink)
        }
    }
}

@Composable
internal fun ArenaDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val words = gameText()
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }, confirmButton = { TextButton(onClick = dismiss) { Text(words(R.string.ui_back_to_game)) } })
}

@Composable
fun OfflineArena(round: Round, state: GameUiState, model: GameViewModel) {
    val words = gameText()
    val humans = round.players.filterNot { it.computer }
    val iconControls = round.settings.manualClaims || LocalDensity.current.fontScale > 1.3f
    var ownerId by rememberSaveable(round.id) { mutableStateOf(humans.first().id) }
    var handoff by rememberSaveable(round.id) { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    if (!handoff) PlayArena(round.toTable(), ownerId, state.preferences,
        if (round.status == RoundStatus.PAUSED) words(R.string.play_paused) else words(R.string.play_offline),
        { model.dabCalled(ownerId) }, model::repeatCall, { model.navigate(Screen.HOME) }, state.winMoment, model::dismissWin,
        markNumber = model::toggleMark, claim = { model.claim(ownerId, it) }, claimMessage = state.claimMessage?.let(words::message),
        extraMenu = { close ->
            DropdownMenuItem(text = { Text(words(R.string.ui_settings)) }, onClick = { close(); model.navigate(Screen.SETTINGS) })
            if (humans.size > 1) DropdownMenuItem(text = { Text(words(R.string.play_hand_off)) }, onClick = { close(); model.pause(); handoff = true })
            if (!round.finished) {
                if (round.settings.mode == GameMode.PRACTICE) DropdownMenuItem(text = { Text(words(R.string.ui_undo_last_call)) }, enabled = round.called.isNotEmpty(), onClick = { close(); model.undo() })
                DropdownMenuItem(text = { Text(words(R.string.ui_end_round)) }, onClick = { close(); end = true })
            }
        }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                round.finished -> ArenaPrimaryAction(words(R.string.ui_see_round_results)) { model.navigate(Screen.RESULTS) }
                round.status == RoundStatus.PAUSED -> ArenaPrimaryAction(words(R.string.play_resume)) { model.resume() }
                else -> {
                    FilledTonalButton(onClick = model::draw, contentPadding = PaddingValues(8.dp), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("local-next")
                        .semantics { contentDescription = words(R.string.play_next) }) {
                        if (iconControls) PlaybackSymbol(next = true) else Text(words(R.string.play_next))
                    }
                    Button(onClick = if (state.auto) model::pause else model::auto, contentPadding = PaddingValues(8.dp), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1.4f).heightIn(min = 48.dp).testTag("local-auto")
                        .semantics { contentDescription = if (state.auto) words(R.string.play_pause) else words(R.string.ui_auto_s, state.preferences.interval) }) {
                        if (iconControls) PlaybackSymbol(paused = state.auto) else Text(if (state.auto) words(R.string.play_pause) else words(R.string.ui_auto_s, state.preferences.interval), maxLines = 1)
                    }
                }
            }
        }
    }
    if (handoff) Surface(Modifier.fillMaxSize().testTag("handoff-screen")) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(words(R.string.play_hand_off), style = MaterialTheme.typography.headlineMedium)
            Text(words(R.string.play_hand_off_note))
            humans.forEach { player -> PrimaryAction(words(R.string.play_show_hand, player.name)) { ownerId = player.id; handoff = false } }
        }
    }
    if (end) AlertDialog(onDismissRequest = { end = false }, title = { Text(words(R.string.ui_end_this_round_early)) }, text = { Text(words(R.string.ui_this_round_will_be_saved_as_cancelled_with)) },
        confirmButton = { TextButton(onClick = { end = false; model.finish() }) { Text(words(R.string.ui_end_round)) } }, dismissButton = { TextButton(onClick = { end = false }) { Text(words(R.string.ui_keep_playing)) } })
}

@Composable
internal fun PlaybackSymbol(next: Boolean = false, paused: Boolean = false) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        if (paused) {
            drawRect(color, Offset(size.width * .22f, size.height * .15f), Size(size.width * .2f, size.height * .7f))
            drawRect(color, Offset(size.width * .58f, size.height * .15f), Size(size.width * .2f, size.height * .7f))
        } else {
            val triangle = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * .2f, size.height * .15f); lineTo(size.width * .75f, size.height * .5f)
                lineTo(size.width * .2f, size.height * .85f); close()
            }
            drawPath(triangle, color)
            if (next) drawRect(color, Offset(size.width * .8f, size.height * .15f), Size(size.width * .1f, size.height * .7f))
        }
    }
}

@Composable
private fun ArenaPrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun OnlineArena(state: OnlineUiState, model: OnlineViewModel, preferences: Preferences, back: () -> Unit, roomDetails: () -> Unit,
    retry: () -> Unit = model::retry, reconnect: () -> Unit = model::reconnect) {
    val words = gameText()
    val room = state.room ?: return
    val table = room.toTable(state.marks)?.copy(pendingMarks = state.pendingMarks) ?: return
    val enabled = state.connection == Connection.LIVE && !state.pending && !state.busy && !state.sessionExpired && !state.storageFailure
    val sendingMarks = state.markSending && state.busy && state.connection == Connection.LIVE
    val marksEnabled = state.connection == Connection.LIVE && (!state.pending || sendingMarks) && (!state.busy || sendingMarks) &&
        !state.sessionExpired && !state.storageFailure && !state.deletingProfile
    val host = state.playerId == room.hostId
    val iconControls = table.settings.manualClaims || LocalDensity.current.fontScale > 1.3f
    val recovering = room.options.coinGame && !table.finished && !state.sessionExpired && !state.storageFailure &&
        !state.deletingProfile && ((state.pending && !sendingMarks) || state.connection != Connection.LIVE)
    val status = when {
        state.sessionExpired -> words(R.string.ui_your_online_session_has_expired)
        state.pending && !sendingMarks -> words(R.string.ui_an_action_is_waiting_for_confirmation)
        state.connection != Connection.LIVE -> words(R.string.play_reconnecting)
        table.status == RoundStatus.PAUSED -> words(R.string.play_paused)
        else -> words(R.string.play_live)
    }
    // A saved deadline is not a live countdown while the stream is disconnected.
    val displayed = if (state.connection == Connection.LIVE) table else table.copy(nextDrawAt = null)
    PlayArena(displayed, state.playerId.orEmpty(), preferences, status, model::dabCalled, model::repeatCall, back,
        state.winMoment, model::dismissWin, enabled = if (table.powers != null) marksEnabled else !state.deletingProfile && !state.storageFailure && !state.sessionExpired,
        markNumber = model::mark, claim = model::claim, claimMessage = state.claimMessage?.let(words::message), claimEnabled = enabled,
        usePower = model::usePower,
        reactionMessage = if (state.connection == Connection.LIVE) state.reactionNotice?.let(words::message) ?: friendReactionCaption(state.reactions, room, state.reactionClock) else null,
        expandedFooter = recovering, extraMenu = { close ->
            DropdownMenuItem(text = { Text(words(R.string.play_room)) }, onClick = { close(); roomDetails() })
            state.reactions?.takeIf { it.roomId == room.roomId && it.roundId == room.round?.id && room.coins?.friendTable == true && !table.finished }?.let {
                state.reactionClock?.let { clock -> FriendReactionMenu(it, enabled, state.reactionSending, clock, close, model::react) }
            }
        }) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (room.options.coinGame) 56.dp else 52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (table.finished) ArenaPrimaryAction(words(R.string.ui_see_round_results)) { roomDetails() }
            else if (recovering) RoundRecoveryControls(state, retry, reconnect)
            else if (state.pending && !sendingMarks) ArenaPrimaryAction(words(R.string.ui_retry_pending_action), enabled = !state.busy && !state.storageFailure) { model.retry() }
            else if (room.options.coinGame) CoinCallClock(room, state.connection, model::reconnect)
            else if (host && !table.finished) {
                if (!room.options.automaticCalling && table.status != RoundStatus.PAUSED) FilledTonalButton(
                    onClick = { model.command(RoomAction.Draw) }, enabled = enabled, contentPadding = PaddingValues(8.dp), modifier = Modifier.weight(1f).height(48.dp).testTag("online-next")
                        .semantics { contentDescription = words(R.string.play_next) }) { if (iconControls) PlaybackSymbol(next = true) else Text(words(R.string.play_next)) }
                Button(onClick = { model.command(if (table.status == RoundStatus.PAUSED) RoomAction.Resume else RoomAction.Pause) }, enabled = enabled,
                    contentPadding = PaddingValues(8.dp), modifier = Modifier.weight(1f).height(48.dp).testTag("online-pause").semantics {
                        contentDescription = words(if (table.status == RoundStatus.PAUSED) R.string.play_resume else R.string.play_pause)
                    }, shape = RoundedCornerShape(16.dp)) {
                    if (iconControls) PlaybackSymbol(paused = table.status != RoundStatus.PAUSED)
                    else Text(words(if (table.status == RoundStatus.PAUSED) R.string.play_resume else R.string.play_pause))
                }
            } else TextButton(onClick = { if (state.connection != Connection.LIVE && !state.sessionExpired) model.reconnect() else roomDetails() }, contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (state.connection != Connection.LIVE && !state.sessionExpired) words(R.string.ui_reconnect_now) else words(R.string.play_room), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    state.error?.let { message -> AlertDialog(onDismissRequest = model::clearError, title = { Text(words(R.string.ui_online_play)) },
        text = { Text(words.message(message)) }, confirmButton = { TextButton(onClick = model::clearError) { Text(words(R.string.ui_got_it)) } }) }
}
