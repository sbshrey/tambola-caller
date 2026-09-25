package io.github.sbshrey.tambola.game.ui

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
    val rules = remember(round.settings) {
        round.settings.prizes.map { VisibleRule(it.name, it.title, it.points, it.explanation, listOf(listOf(it.condition())), prize = it) } +
            round.settings.customPrizes.map { VisibleRule(it.id, it.title, it.points, it.describe(), it.pattern.alternatives, custom = it) }
    }
    var inspecting by rememberSaveable(round.id) { mutableStateOf<String?>(null) }
    rules.forEach { rule ->
        val award = rule.award(round)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rule.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${rule.points} pts", color = Saffron, style = MaterialTheme.typography.labelLarge)
            }
            Text(rule.explanation, color = Muted, style = MaterialTheme.typography.bodyMedium)
            if (award != null) Text("Verified · call ${award.call}\n" + award.tickets.joinToString { round.ticketLabel(it) }, color = Jade, style = MaterialTheme.typography.bodyMedium)
            else if (round.finished) Text("Not awarded this round", color = Muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { inspecting = rule.id }) { Text("Inspect ${rule.title}") }
        }
        HorizontalDivider(color = Muted.copy(alpha = .15f))
    }
    rules.firstOrNull { it.id == inspecting }?.let { rule -> RuleDetails(round, rule, initialTicket) { inspecting = null } }
}

@Composable
private fun RuleDetails(round: TableRound, rule: VisibleRule, initialTicket: Ticket, dismiss: () -> Unit) {
    var index by rememberSaveable(rule.id, round.id) { mutableIntStateOf(round.tickets.indexOf(initialTicket).coerceAtLeast(0)) }
    val ticket = round.tickets[index]
    val called = round.called.toSet()
    val award = rule.award(round)
    AlertDialog(onDismissRequest = dismiss, title = { Text(rule.title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${rule.points} points · ${rule.explanation}", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                round.tickets.forEachIndexed { i, t -> FilterChip(selected = index == i, onClick = { index = i }, label = { Text(round.ticketLabel(t.id)) }) }
            }
            Text(round.ticketLabel(ticket.id), style = MaterialTheme.typography.titleMedium)
            when {
                award != null && ticket.id in award.tickets -> Text("Verified winner on call ${award.call}", color = Jade)
                award != null -> Text("This prize was awarded on call ${award.call}. This ticket was not in that winning group.", color = Saffron)
                round.finished -> Text("This prize was not awarded before the round ended.", color = Saffron)
                else -> Text("Not awarded yet. Numbers below show this ticket's progress.", color = Saffron)
            }
            if (rule.prize?.isRankedHouse == true) round.awards.firstOrNull { it.prize.isRankedHouse && ticket.id in it.ticketIds && it.prize != rule.prize }?.let {
                Text("This ticket already won ${it.prize.title} and cannot win another house rank.", color = Muted)
            }
            rule.custom?.let { prize ->
                val owned = round.tickets.filter { it.playerId == ticket.playerId }
                val ordinal = owned.indexOf(ticket) + 1
                val qualifying = owned.filterIndexed { i, t -> (prize.ticketOrdinals.isEmpty() || i + 1 in prize.ticketOrdinals) && prize.pattern.matches(t, called) }.size
                Text("Matching owned tickets: $qualifying / ${prize.minimumTickets} needed", color = Jade)
                if (prize.ticketOrdinals.isNotEmpty() && ordinal !in prize.ticketOrdinals) Text("This ticket is excluded. This rule considers tickets ${prize.ticketOrdinals.sorted().joinToString()}.", color = Coral)
            }
            rule.groups.forEachIndexed { i, conditions ->
                if (i > 0) Eyebrow("OR", Saffron)
                conditions.forEachIndexed { j, condition ->
                    if (j > 0) Eyebrow("AND", Saffron)
                    val detail = condition.inspect(ticket, called)
                    Text(condition.describe(), style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        !detail.possible -> "This ticket cannot satisfy this condition: ${detail.selected.size} selected numbers, ${detail.required} required. Empty selections never win."
                        detail.matches -> "Condition satisfied · ${detail.called.size} called, ${detail.required} required"
                        else -> "Needs ${detail.remaining} more · ${detail.called.size} called, ${detail.required} required"
                    }, color = if (detail.matches) Jade else Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        detail.selected.forEach { n ->
                            val active = n in called
                            Text("$n", color = if (active) Ink else Ivory,
                                modifier = Modifier.background(if (active) Jade else Ink, RoundedCornerShape(8.dp)).padding(10.dp)
                                    .semantics { contentDescription = "Number $n, ${if (active) "called" else "not called"}" })
                        }
                    }
                }
            }
            Text("Verification uses the official calls, independent of marks. A matching pattern after another ticket won does not change that earlier result.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Back to prizes") } })
}

@Composable
fun ShareResults(round: TableRound, onDismiss: () -> Unit, share: (String) -> Unit) {
    var names by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(round.players.map { it.id }.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    val message = buildString {
        append("Tambola Together · ${if (round.status == RoundStatus.CANCELLED) "Cancelled round" else "Round results"}\n${round.called.size} calls\n")
        round.players.forEachIndexed { index, player -> if (player.id in selected) append("${if (names) player.name else "Player ${index + 1}"}: ${round.score(player.id)} points\n") }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose what to share") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingSwitch("Include player names", "Names stay out of the message unless you choose to include them.", names) { names = it }
            round.players.forEach { player ->
                Row {
                    Checkbox(checked = player.id in selected, onCheckedChange = { selected = if (it) selected + player.id else selected - player.id })
                    Text(player.name, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Text("Message preview", style = MaterialTheme.typography.titleMedium)
            Text(message, color = Muted, modifier = Modifier.testTag("share-preview"))
            error?.let { Text(it, color = Coral) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching { share(message) }.onSuccess { onDismiss() }.onFailure { error = "No sharing app is available right now." } }, enabled = selected.isNotEmpty()) { Text("Open share sheet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep private") } })
}
