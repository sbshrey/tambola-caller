package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Ticket-specific choices; availability follows the same call-boundary and house rules as the table. */
@Composable
internal fun TicketPrizePicker(table: TableRound, ticket: Ticket, ordinal: Int, enabled: Boolean,
    dismiss: () -> Unit, claim: (ClaimSelection) -> Unit) {
    val words = gameText()
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val choices = table.settings.prizes.map { it.name to words.prizeTitle(it) } + table.settings.customPrizes.map { it.id to it.title }
    val closedHouses = table.awards.filter { it.prize.isRankedHouse && it.drawIndex < table.called.size }
    val nextHouse = table.settings.prizes.filter { it.isRankedHouse && closedHouses.none { award -> award.prize == it } }.minByOrNull { it.ordinal }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth().heightIn(max = maxHeight).testTag("ticket-prize-picker")
                .semantics { testTagsAsResourceId = true }, shape = RoundedCornerShape(24.dp), color = colors.surface) {
                BoxWithConstraints(Modifier.padding(16.dp)) {
                    val columns = if (maxWidth > 420.dp) 3 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(words(R.string.play_choose_prize, ordinal), modifier = Modifier.weight(1f).semantics { heading() },
                                fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = dismiss, modifier = Modifier.size(48.dp).testTag("dismiss-claim")
                                .semantics { contentDescription = words(R.string.ui_back_to_game) }) { Text("×", fontSize = 28.sp) }
                        }
                        // A wrapping title can be taller than one row. Allocate the
                        // remaining height to prizes while keeping the close action fixed.
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (id, label) ->
                                        val award = table.awards.firstOrNull { it.prize.name == id }
                                        val custom = table.customAwards.firstOrNull { it.prizeId == id }
                                        val draw = award?.drawIndex ?: custom?.drawIndex
                                        val wonByTicket = ticket.id in (award?.ticketIds ?: custom?.ticketIds).orEmpty()
                                        val rankedHouse = table.settings.prizes.firstOrNull { it.name == id && it.isRankedHouse }
                                        val houseLocked = rankedHouse != null && (rankedHouse != nextHouse || closedHouses.any { ticket.id in it.ticketIds })
                                        val open = (draw == null || draw == table.called.size) && !wonByTicket && !houseLocked
                                        val active = enabled && open && !table.finished
                                        val taken = wonByTicket || (draw != null && draw < table.called.size)
                                        val coins = table.coins?.prizes?.firstOrNull { it.prize.name == id }?.coins
                                        Surface(onClick = { dismiss(); claim(ClaimSelection(ticket.id, id)) }, enabled = active,
                                            modifier = Modifier.weight(1f).heightIn(min = 60.dp).fillMaxHeight().testTag("claim-prize-$id")
                                                .semantics(mergeDescendants = true) {
                                                    contentDescription = coins?.let { words(R.string.coin_prize_amount, label, it) } ?: label
                                                    if (taken && draw != null) stateDescription = words(R.string.ui_verified_call_n, draw)
                                                },
                                            shape = RoundedCornerShape(14.dp), color = if (active) colors.surfaceContainerHigh else colors.background,
                                            border = BorderStroke(1.dp, if (active) colors.secondary.copy(alpha = .65f) else colors.outlineVariant)) {
                                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(if (taken) "✓ $label" else label, modifier = Modifier.weight(1f), fontSize = 13.sp, lineHeight = 16.sp,
                                                    fontWeight = FontWeight.SemiBold, color = if (active) colors.onSurface else colors.onSurfaceVariant)
                                                coins?.let { Text("$it", fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold,
                                                    color = if (active) GameNightPalette.gold else colors.onSurfaceVariant) }
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
}
