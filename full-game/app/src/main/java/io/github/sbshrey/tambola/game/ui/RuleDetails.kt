package io.github.sbshrey.tambola.game.ui

import io.github.sbshrey.tambola.game.R

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.domain.*

private data class VisibleRule(val id: String, val title: String, val points: Int, val explanation: String,
    val groups: List<List<RuleCondition>>, val prize: Prize? = null, val custom: CustomPrize? = null) {
    fun award(round: TableRound): VisibleAward? = if (prize != null) round.awards.firstOrNull { it.prize == prize }?.let { VisibleAward(it.drawIndex, it.ticketIds, it.playerIds) }
        else round.customAwards.firstOrNull { it.prizeId == id }?.let { VisibleAward(it.drawIndex, it.ticketIds, it.playerIds) }
}
private data class VisibleAward(val call: Int, val tickets: List<String>, val players: List<String>)

@Composable
fun RuleList(round: TableRound, initialTicket: Ticket = round.tickets.first()) {
    val words = gameText()
    val rules = remember(round.settings, words) {
        round.settings.prizes.map { prize ->
            val groups = prize.conditions().map { listOf(it) }
            VisibleRule(prize.name, words.prizeTitle(prize), prize.points, words.prizeExplanation(prize), groups, prize = prize)
        } +
            round.settings.customPrizes.map { VisibleRule(it.id, it.title, it.points, words.customPrize(it), it.pattern.alternatives, custom = it) }
    }
    var inspecting by rememberSaveable(round.id) { mutableStateOf<String?>(null) }
    rules.forEach { rule ->
        val award = rule.award(round)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rule.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(words(R.string.ui_pts, rule.points), color = Saffron, style = MaterialTheme.typography.labelLarge)
            }
            Text(rule.explanation, color = Muted, style = MaterialTheme.typography.bodyMedium)
            if (award != null) Text(words(R.string.ui_verified_call_n, award.call) + award.tickets.joinToString { words.ticketLabel(round, it) }, color = Jade, style = MaterialTheme.typography.bodyMedium)
            else if (round.finished) Text(words(R.string.ui_not_awarded_this_round), color = Muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { inspecting = rule.id }) { Text(words(R.string.ui_inspect, rule.title)) }
        }
        HorizontalDivider(color = Muted.copy(alpha = .15f))
    }
    rules.firstOrNull { it.id == inspecting }?.let { rule -> RuleDetails(round, rule, initialTicket) { inspecting = null } }
}

@Composable
private fun RuleDetails(round: TableRound, rule: VisibleRule, initialTicket: Ticket, dismiss: () -> Unit) {
    val words = gameText()
    var index by rememberSaveable(rule.id, round.id) { mutableIntStateOf(round.tickets.indexOf(initialTicket).coerceAtLeast(0)) }
    val ticket = round.tickets[index]
    val called = round.called.toSet()
    val award = rule.award(round)
    AlertDialog(onDismissRequest = dismiss, title = { Text(rule.title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(words(R.string.ui_points_3, rule.points, rule.explanation), color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                round.tickets.forEachIndexed { i, t -> FilterChip(selected = index == i, onClick = { index = i }, label = { Text(words.ticketLabel(round, t.id)) }) }
            }
            Text(words.ticketLabel(round, ticket.id), style = MaterialTheme.typography.titleMedium)
            when {
                award != null && ticket.id in award.tickets -> Text(words(R.string.ui_verified_winner_on_call, award.call), color = Jade)
                award != null -> Text(words(R.string.ui_this_prize_was_awarded_on_call_this_ticket, award.call), color = Saffron)
                round.finished -> Text(words(R.string.ui_this_prize_was_not_awarded_before_the_round), color = Saffron)
                else -> Text(words(R.string.ui_not_awarded_yet_numbers_below_show_this_ticket), color = Saffron)
            }
            if (rule.prize?.isRankedHouse == true) round.awards.firstOrNull { it.prize.isRankedHouse && ticket.id in it.ticketIds && it.prize != rule.prize }?.let {
                Text(words(R.string.ui_this_ticket_already_won_and_cannot_win_another, words.prizeTitle(it.prize)), color = Muted)
            }
            rule.custom?.let { prize ->
                val owned = round.tickets.filter { it.playerId == ticket.playerId }
                val ordinal = owned.indexOf(ticket) + 1
                val qualifying = owned.filterIndexed { i, t -> (prize.ticketOrdinals.isEmpty() || i + 1 in prize.ticketOrdinals) && prize.pattern.matches(t, called) }.size
                Text(words(R.string.ui_matching_owned_tickets_needed, qualifying, prize.minimumTickets), color = Jade)
                if (prize.ticketOrdinals.isNotEmpty() && ordinal !in prize.ticketOrdinals) Text(words(R.string.ui_this_ticket_is_excluded_this_rule_considers_tickets, prize.ticketOrdinals.sorted().joinToString()), color = Coral)
            }
            rule.groups.forEachIndexed { i, conditions ->
                if (i > 0) Eyebrow(words(R.string.ui_or), Saffron)
                conditions.forEachIndexed { j, condition ->
                    if (j > 0) Eyebrow(words(R.string.ui_and), Saffron)
                    val detail = condition.inspect(ticket, called)
                    Text(words.condition(condition), style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        !detail.possible -> words(R.string.ui_this_ticket_cannot_satisfy_this_condition_selected_numbers, detail.selected.size, detail.required)
                        detail.matches -> words(R.string.ui_condition_satisfied_called_required, detail.called.size, detail.required)
                        else -> words(R.string.ui_needs_more_called_required, detail.remaining, detail.called.size, detail.required)
                    }, color = if (detail.matches) Jade else Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        detail.selected.forEach { n ->
                            val active = n in called
                            Text("$n", color = if (active) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.background(if (active) Jade else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)).padding(10.dp)
                                    .semantics { contentDescription = words(R.string.ui_number_2, n, words(if (active) R.string.ui_called else R.string.ui_not_called)) })
                        }
                    }
                }
            }
            Text(words(R.string.ui_verification_uses_the_official_calls_independent_of_marks), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(words(R.string.ui_back_to_prizes)) } })
}

@Composable
fun ShareResults(round: TableRound, onDismiss: () -> Unit, share: (String) -> Unit) {
    val words = gameText()
    var names by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(round.players.map { it.id }.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    val message = buildString {
        append(words(R.string.ui_tambola_together_n_calls_n, words(if (round.status == RoundStatus.CANCELLED) R.string.cancelled_results else R.string.round_results), round.called.size))
        round.players.forEachIndexed { index, player -> if (player.id in selected) append(words(R.string.ui_points_n, if (names) words.playerLabel(player) else words(R.string.anonymous_player, index + 1), round.score(player.id))) }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(words(R.string.ui_choose_what_to_share)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingSwitch(words(R.string.ui_include_player_names), words(R.string.ui_names_stay_out_of_the_message_unless_you), names) { names = it }
            round.players.forEach { player ->
                Row {
                    Checkbox(checked = player.id in selected, onCheckedChange = { selected = if (it) selected + player.id else selected - player.id })
                    Text(words.playerLabel(player), modifier = Modifier.padding(top = 12.dp))
                }
            }
            Text(words(R.string.ui_message_preview), style = MaterialTheme.typography.titleMedium)
            Text(message, color = Muted, modifier = Modifier.testTag("share-preview"))
            error?.let { Text(it, color = Coral) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching { share(message) }.onSuccess { onDismiss() }.onFailure { error = words(R.string.ui_no_sharing_app_is_available_right_now) } }, enabled = selected.isNotEmpty()) { Text(words(R.string.ui_open_share_sheet)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(words(R.string.ui_keep_private)) } })
}
