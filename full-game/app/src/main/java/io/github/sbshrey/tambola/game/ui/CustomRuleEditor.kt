package io.github.sbshrey.tambola.game.ui

import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.presentation.uiMessage

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.*
import io.github.sbshrey.tambola.game.setup.*
import java.util.Random

@Composable
fun CustomRuleEditor(state: GameUiState, model: GameViewModel) {
    val words = gameText()
    val draft = state.ruleDraft ?: return
    val tickets = state.setupDraft.tickets
    CustomRuleEditor(draft, state.originalRuleDraft, tickets, model::updateRule, model::saveRule, model::cancelRule)
}

@Composable
fun CustomRuleEditor(draft: CustomRuleDraft, original: CustomRuleDraft?, tickets: Int,
    update: (CustomRuleDraft) -> Unit, save: () -> Unit, cancel: () -> Unit) {
    val words = gameText()
    val result = remember(draft, tickets) { runCatching { draft.prize(tickets) } }
    var discard by remember { mutableStateOf(false) }
    val back = { if (draft != original) discard = true else cancel() }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogSystemBars()
        val focus = LocalFocusManager.current
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = back) { Text(words(R.string.ui_setup)) }
                    Text(words(R.string.ui_custom_prize), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Eyebrow(words(R.string.ui_your_table_your_traditions))
                    Text(words(R.string.ui_make_the_rule_clear_nthen_make_it_yours), style = MaterialTheme.typography.headlineMedium)
                    GameCard {
                        OutlinedTextField(draft.title, { if (it.length <= 40) update(draft.copy(title = it)) },
                            label = { Text(words(R.string.ui_prize_name)) }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().testTag("prize-title"))
                        DigitField(words(R.string.ui_points), draft.points, { update(draft.copy(points = it)) }, 4, "prize-points")
                        Text(words(R.string.ui_every_tied_player_receives_these_points_once_even), color = Muted)
                    }
                    Text(words(R.string.ui_a_ticket_must_satisfy_one_group), style = MaterialTheme.typography.titleMedium)
                    draft.groups.forEachIndexed { groupIndex, group ->
                        if (groupIndex > 0) Eyebrow(words(R.string.ui_or_this_group), Saffron)
                        GameCard {
                            Text(words(R.string.ui_group_match_every_condition, groupIndex + 1), style = MaterialTheme.typography.titleMedium)
                            group.forEachIndexed { index, condition ->
                                if (index > 0) { HorizontalDivider(); Eyebrow(words(R.string.ui_and), Saffron) }
                                key(groupIndex, index) {
                                    ConditionEditor(condition, "$groupIndex-$index") { update(draft.changeCondition(groupIndex, index, it)) }
                                }
                                if (group.size > 1) TextButton(onClick = {
                                    update(draft.copy(groups = draft.groups.mapIndexed { i, list -> if (i == groupIndex) list.filterIndexed { j, _ -> j != index } else list }))
                                }) { Text(words(R.string.ui_remove_condition, index + 1)) }
                            }
                            OutlinedButton(onClick = { update(draft.copy(groups = draft.groups.mapIndexed { i, list -> if (i == groupIndex) list + ConditionDraft(all = true) else list })) },
                                enabled = group.size < 8 && draft.groups.sumOf { it.size } < 16, modifier = Modifier.testTag("add-and-$groupIndex")) { Text(words(R.string.ui_add_and_condition)) }
                            if (draft.groups.size > 1) TextButton(onClick = { update(draft.copy(groups = draft.groups.filterIndexed { i, _ -> i != groupIndex })) }) { Text(words(R.string.ui_remove_group, groupIndex + 1)) }
                        }
                    }
                    OutlinedButton(onClick = { update(draft.copy(groups = draft.groups + listOf(listOf(ConditionDraft(all = true))))) },
                        enabled = draft.groups.size < 4 && draft.groups.sumOf { it.size } < 16, modifier = Modifier.fillMaxWidth()) { Text(words(R.string.ui_add_or_group)) }
                    GameCard {
                        Text(words(R.string.ui_which_owned_tickets_count), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = draft.ticketOrdinals.isEmpty(), onClick = { update(draft.copy(ticketOrdinals = emptyList())) }, label = { Text(words(R.string.ui_all_owned_tickets)) })
                            (1..tickets).forEach { n -> FilterChip(selected = n in draft.ticketOrdinals,
                                onClick = { update(draft.copy(ticketOrdinals = if (n in draft.ticketOrdinals) draft.ticketOrdinals - n else draft.ticketOrdinals + n)) }, label = { Text(words(R.string.ui_ticket, n)) }) }
                        }
                        Text(words(R.string.ui_minimum_matching_tickets), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..tickets).forEach { n -> FilterChip(selected = draft.minimumTickets == n,
                                onClick = { update(draft.copy(minimumTickets = n)) }, label = { Text("$n") }, modifier = Modifier.testTag("minimum-tickets-$n")) }
                        }
                        Text(words(R.string.ui_each_selected_ticket_must_satisfy_the_pattern_a), color = Muted)
                    }
                    result.getOrNull()?.let { prize ->
                        GameCard { Eyebrow(words(R.string.ui_the_agreed_rule)); Text(words.customPrize(prize)); Text(words(R.string.ui_points_per_winning_player, prize.points), color = Saffron) }
                        RulePlayground(prize, tickets)
                    }
                }
                Column(Modifier.fillMaxWidth().background(Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.exceptionOrNull()?.let { Text(words.message(it.uiMessage(R.string.error_prize_settings)), color = Coral, style = MaterialTheme.typography.bodySmall) }
                    PrimaryAction(words(R.string.ui_save_prize), enabled = result.isSuccess, onClick = save)
                }
            }
        }
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(words(R.string.ui_discard_these_edits)) },
            text = { Text(words(R.string.ui_the_prize_in_your_setup_will_stay_as)) },
            confirmButton = { TextButton(onClick = cancel) { Text(words(R.string.ui_discard_edits)) } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text(words(R.string.ui_keep_editing)) } })
    }
}

@Composable
private fun ConditionEditor(draft: ConditionDraft, tag: String, change: (ConditionDraft) -> Unit) {
    val words = gameText()
    val focus = LocalFocusManager.current
    fun choose(next: ConditionDraft) { focus.clearFocus(); change(next) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionKind.entries.forEach { kind -> FilterChip(selected = draft.kind == kind, onClick = { choose(draft.copy(kind = kind, index = 0, all = true)) }, label = { Text(words.selectionKind(kind)) }, modifier = Modifier.testTag("select-$tag-${kind.name}")) }
    }
    when (draft.kind) {
        SelectionKind.ROW -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(words(R.string.ui_top_row), words(R.string.ui_middle_row), words(R.string.ui_bottom_row)).forEachIndexed { i, title -> FilterChip(selected = draft.index == i, onClick = { choose(draft.copy(index = i)) }, label = { Text(title) }) }
        }
        SelectionKind.COLUMN -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..8).forEach { i -> FilterChip(selected = draft.index == i, onClick = { choose(draft.copy(index = i)) }, label = { Text("${columnRange(i).first}–${columnRange(i).last}") }) }
        }
        SelectionKind.RANGE -> {
            DigitField(words(R.string.ui_first_number), draft.first, { change(draft.copy(first = it)) }, 2, "range-first-$tag")
            DigitField(words(R.string.ui_last_number), draft.last, { change(draft.copy(last = it)) }, 2, "range-last-$tag")
        }
        SelectionKind.POSITIONS -> {
            Text(words(R.string.ui_select_populated_positions_each_row_has_five_numbers), color = Muted)
            (0..2).forEach { row ->
                Text(listOf(words(R.string.ui_top_row), words(R.string.ui_middle_row), words(R.string.ui_bottom_row))[row], style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..4).forEach { column ->
                        val position = row * 5 + column
                        FilterChip(selected = position in draft.positions, onClick = { choose(draft.copy(positions = if (position in draft.positions) draft.positions - position else draft.positions + position)) },
                            label = { Text("${column + 1}") }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = words(R.string.ui_row_populated_number, row + 1, column + 1) })
                    }
                }
            }
            TextButton(onClick = { choose(draft.copy(positions = listOf(0, 4, 10, 14))) }) { Text(words(R.string.ui_select_four_corners)) }
        }
        SelectionKind.ALL -> Unit
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = draft.all, onClick = { choose(draft.copy(all = true)) }, label = { Text(words(R.string.ui_all_selected_numbers)) }, modifier = Modifier.testTag("all-$tag"))
        FilterChip(selected = !draft.all, onClick = { choose(draft.copy(all = false, minimum = "1")) }, label = { Text(words(R.string.ui_at_least_a_count)) }, modifier = Modifier.testTag("count-$tag"))
    }
    if (!draft.all) DigitField(words(R.string.ui_called_count), draft.minimum, { change(draft.copy(minimum = it)) }, 2, "count-input-$tag")
    val result = runCatching { draft.condition() }
    Text(result.getOrNull()?.let(words::condition) ?: result.exceptionOrNull()?.let { words.message(it.uiMessage(R.string.error_prize_settings)) }.orEmpty(), color = if (result.isSuccess) Muted else Coral, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DigitField(label: String, value: String, change: (String) -> Unit, maxLength: Int, tag: String) {
    val words = gameText()
    val focus = LocalFocusManager.current
    OutlinedTextField(value, { if (it.length <= maxLength && it.all(Char::isDigit)) change(it) }, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun RulePlayground(prize: CustomPrize, count: Int) {
    val words = gameText()
    val focus = LocalFocusManager.current
    val tickets = remember(count) { TicketGenerator(Random(44)).deal(listOf(Player("sample", "Sample")), count) }
    val positive = remember(prize, tickets) { prize.exampleCalls(tickets) }
    var called by remember(prize, count) { mutableStateOf(emptySet<Int>()) }
    var index by remember(count) { mutableIntStateOf(0) }
    val ticket = tickets[index]
    val satisfied = prize.eligibleTickets(tickets, called).isNotEmpty()
    GameCard {
        Eyebrow(words(R.string.ui_try_it_before_you_play))
        Text(words(R.string.ui_sample_tickets), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.ui_these_are_illustrations_not_your_next_game_s), color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { focus.clearFocus(); called = positive.orEmpty() }, enabled = positive != null) { Text(words(R.string.ui_winning_example)) }
            OutlinedButton(onClick = { focus.clearFocus(); called = emptySet() }) { Text(words(R.string.ui_clear_sample_calls)) }
        }
        if (positive == null) Text(words(R.string.ui_these_sample_tickets_cannot_satisfy_this_pattern_ranges), color = Coral)
        Text(if (satisfied) words(R.string.ui_sample_rule_satisfied) else words(R.string.ui_sample_rule_not_yet_satisfied), color = if (satisfied) Jade else Saffron,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (count > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tickets.indices.forEach { i -> FilterChip(selected = i == index, onClick = { index = i }, label = { Text(words(R.string.ui_sample, i + 1)) }) }
        }
        (0..2).forEach { row ->
            Text(listOf(words(R.string.ui_top_row), words(R.string.ui_middle_row), words(R.string.ui_bottom_row))[row], style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ticket.row(row).forEach { number ->
                    val active = number in called
                    OutlinedButton(onClick = { called = if (active) called - number else called + number },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) Jade else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (active) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface),
                        contentPadding = PaddingValues(10.dp), shape = RoundedCornerShape(12.dp), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = words(R.string.ui_sample_number, number); stateDescription = if (active) words(R.string.ui_called) else words(R.string.ui_not_called) }) { Text("$number") }
                }
            }
        }
        Text(words(R.string.ui_sample_numbers_called_clear_calls_shows_an_incomplete, called.size), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
