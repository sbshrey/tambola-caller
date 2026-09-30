package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.presentation.PrizePlaceState
import io.github.sbshrey.tambola.game.presentation.prizeAvailability

/** Ticket-specific choices; availability follows the same call-boundary and house rules as the table. */
@Composable
internal fun TicketPrizePicker(table: TableRound, ticket: Ticket, ordinal: Int, enabled: Boolean,
    dismiss: () -> Unit, embedded: Boolean = false, claim: (ClaimSelection) -> Unit) {
    val words = gameText()
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val choices = table.settings.prizes.map { it.name to words.prizeTitle(it) } + table.settings.customPrizes.map { it.id to it.title }
    val closedHouses = table.awards.filter { it.prize.isRankedHouse && it.drawIndex < table.called.size }
    val nextHouse = table.settings.prizes.filter { it.isRankedHouse && closedHouses.none { award -> award.prize == it } }.minByOrNull { it.ordinal }
    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalDensity provides density) {
        BoxWithConstraints(if (embedded) Modifier.fillMaxSize() else Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface((if (embedded) Modifier.fillMaxSize() else Modifier.widthIn(max = 680.dp).fillMaxWidth().heightIn(max = maxHeight)).testTag("ticket-prize-picker")
                .semantics { testTagsAsResourceId = true }, shape = RoundedCornerShape(24.dp), color = colors.surface) {
                BoxWithConstraints(Modifier.padding(if (embedded) 8.dp else 16.dp)) {
                    val columns = if (maxWidth > 420.dp) 3 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(words(R.string.play_choose_prize, ordinal), modifier = Modifier.weight(1f).semantics { heading() },
                                fontSize = if (embedded) 13.sp else 19.sp, lineHeight = if (embedded) 16.sp else 24.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = dismiss, modifier = Modifier.size(48.dp).testTag("dismiss-claim")
                                .semantics { contentDescription = words(R.string.ui_back_to_game) }) { Text("×", fontSize = 28.sp) }
                        }
                        // A wrapping title can be taller than one row. Allocate the
                        // remaining height to prizes while keeping the close action fixed.
                        // Current coin rooms have six prizes, all visible together. Older
                        // custom/ranked rooms retain their longer list without losing choices.
                        val prizeGrid = Modifier.weight(1f, fill = embedded).testTag("claim-prize-grid")
                        Column(if (choices.size <= 6) prizeGrid else prizeGrid.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.chunked(columns).forEach { row ->
                                Row((if (embedded) Modifier.weight(1f) else Modifier.height(IntrinsicSize.Min)).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (id, label) ->
                                        val award = table.awards.firstOrNull { it.prize.name == id }
                                        val custom = table.customAwards.firstOrNull { it.prizeId == id }
                                        val draw = award?.drawIndex ?: custom?.drawIndex
                                        val wonByTicket = ticket.id in (award?.ticketIds ?: custom?.ticketIds).orEmpty() ||
                                            (table.settings.winnersPerPrize > 1 && ticket.playerId in award?.playerIds.orEmpty())
                                        val rankedHouse = table.settings.prizes.firstOrNull { it.name == id && it.isRankedHouse }
                                        val houseLocked = rankedHouse != null && (rankedHouse != nextHouse || closedHouses.any { ticket.id in it.ticketIds })
                                        val closed = award?.isClosed(table.settings, table.called.size) ?: (draw != null && draw < table.called.size)
                                        val open = !closed && !wonByTicket && !houseLocked
                                        val active = enabled && open && !table.finished
                                        val taken = wonByTicket || closed
                                        val coins = table.coins?.prizes?.firstOrNull { it.prize.name == id }?.coins
                                        val standardPrize = table.settings.prizes.firstOrNull { it.name == id }
                                        val availability = standardPrize?.let { prizeAvailability(award, table.settings, table.called.size, wonByTicket) }
                                        val availabilityLabel = availability?.let {
                                            when (it.state) {
                                                PrizePlaceState.OPEN -> words(R.string.prize_places_left, it.remaining, it.total)
                                                PrizePlaceState.TIES_OPEN -> words(R.string.prize_ties_open)
                                                PrizePlaceState.FULL -> words(R.string.prize_places_full)
                                                PrizePlaceState.OWNED -> words(R.string.prize_already_claimed)
                                            }
                                        }
                                        Surface(onClick = { dismiss(); claim(ClaimSelection(ticket.id, id)) }, enabled = active,
                                            modifier = Modifier.weight(1f).heightIn(min = 60.dp).fillMaxHeight().testTag("claim-prize-$id")
                                                .semantics(mergeDescendants = true) {
                                                    val title = coins?.let { words(R.string.coin_prize_amount, label, it) } ?: label
                                                    contentDescription = standardPrize?.let { "$title. ${words.prizeExplanation(it)}" } ?: title
                                                    if (taken && draw != null) stateDescription = words(R.string.ui_verified_call_n, draw)
                                                    availabilityLabel?.let { stateDescription = it }
                                                },
                                            shape = RoundedCornerShape(14.dp), color = if (active) colors.surfaceContainerHigh else colors.background,
                                            border = BorderStroke(1.dp, if (active) colors.secondary.copy(alpha = .65f) else colors.outlineVariant)) {
                                            Column(Modifier.padding(horizontal = if (embedded) 6.dp else 10.dp, vertical = if (embedded) 4.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                if (standardPrize != null && density.fontScale <= 1.3f) {
                                                    val progress = ticketPrizeProgress(table, ticket, standardPrize)
                                                    Box(Modifier.size(if (embedded) 36.dp else 52.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize().testTag("prize-progress-$id"),
                                                            color = if (progress >= 1f) colors.primary else colors.secondary,
                                                            trackColor = colors.onSurface.copy(alpha = .22f), strokeWidth = 3.dp)
                                                        Text(if (wonByTicket) "✓" else "${(progress * 100).toInt()}%", fontSize = 10.sp)
                                                    }
                                                }
                                                Text(if (taken) "✓ $label" else label, modifier = Modifier.weight(1f), fontSize = if (embedded) 11.sp else 13.sp, lineHeight = if (embedded) 13.sp else 16.sp,
                                                    fontWeight = FontWeight.SemiBold, color = if (active) colors.onSurface else colors.onSurfaceVariant)
                                                if (embedded) {
                                                    val places = availability?.let {
                                                        when (it.state) {
                                                            PrizePlaceState.OPEN -> words(R.string.prize_places_compact, it.remaining, it.total)
                                                            PrizePlaceState.TIES_OPEN -> words(R.string.prize_ties_compact)
                                                            PrizePlaceState.FULL -> words(R.string.prize_places_full)
                                                            PrizePlaceState.OWNED -> words(R.string.prize_owned_compact)
                                                        }
                                                    }
                                                    Text(listOfNotNull(coins?.toString(), places).joinToString(" \u00b7 "), fontSize = 10.sp, lineHeight = 12.sp,
                                                        color = if (active) GameNightPalette.gold else colors.onSurfaceVariant)
                                                } else {
                                                coins?.let { Text(words(R.string.prize_coin_pool, it), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold,
                                                    color = if (active) GameNightPalette.gold else colors.onSurfaceVariant) }
                                                availabilityLabel?.let { Text(it, fontSize = 10.sp, lineHeight = 12.sp,
                                                    color = colors.onSurfaceVariant) }
                                                }
                                            }
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
    }
    if (embedded) content() else Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { content() }
}

/** Schematic numbered positions, never the player's ticket or current called/marked state. */
@Composable
private fun PrizePattern(prize: Prize, active: Boolean) {
    val ink = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(62.dp, 22.dp).clearAndSetSemantics {}) {
        val early = prize == Prize.EARLY_FIVE || prize == Prize.EARLY_TEN
        val rows = if (prize == Prize.EARLY_FIVE) 1 else if (prize == Prize.EARLY_TEN) 2 else 3
        val gapX = size.width / 5
        val gapY = size.height / 3
        repeat(rows) { row -> repeat(5) { column ->
            val selected = when (prize) {
                Prize.TOP_LINE -> row == 0
                Prize.MIDDLE_LINE -> row == 1
                Prize.BOTTOM_LINE -> row == 2
                Prize.CORNERS -> row != 1 && column in listOf(0, 4)
                else -> true
            }
            drawCircle(if (selected) ink else ink.copy(alpha = .18f), radius = 2.3.dp.toPx(),
                center = Offset((column + .5f) * gapX, (row + .5f + if (early) (3 - rows) / 2f else 0f) * gapY))
        } }
    }
}
