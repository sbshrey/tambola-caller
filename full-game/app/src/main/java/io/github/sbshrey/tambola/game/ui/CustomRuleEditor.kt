package io.github.sbshrey.tambola.game.ui

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
    val draft = state.ruleDraft ?: return
    val tickets = state.setupDraft.tickets
    CustomRuleEditor(draft, state.originalRuleDraft, tickets, model::updateRule, model::saveRule, model::cancelRule)
}

@Composable
fun CustomRuleEditor(draft: CustomRuleDraft, original: CustomRuleDraft?, tickets: Int,
    update: (CustomRuleDraft) -> Unit, save: () -> Unit, cancel: () -> Unit) {
    val result = remember(draft, tickets) { runCatching { draft.prize(tickets) } }
    var discard by remember { mutableStateOf(false) }
    val back = { if (draft != original) discard = true else cancel() }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val focus = LocalFocusManager.current
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = back) { Text("‹ Setup") }
                    Text("Custom prize", style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Eyebrow("YOUR TABLE. YOUR TRADITIONS.")
                    Text("Make the rule clear,\nthen make it yours.", style = MaterialTheme.typography.headlineMedium)
                    GameCard {
                        OutlinedTextField(draft.title, { if (it.length <= 40) update(draft.copy(title = it)) },
                            label = { Text("Prize name") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().testTag("prize-title"))
                        DigitField("Points", draft.points, { update(draft.copy(points = it)) }, 4, "prize-points")
                        Text("Every tied player receives these points once, even if several of their tickets win.", color = Muted)
                    }
                    Text("A ticket must satisfy one group", style = MaterialTheme.typography.titleMedium)
                    draft.groups.forEachIndexed { groupIndex, group ->
                        if (groupIndex > 0) Eyebrow("OR THIS GROUP", Saffron)
                        GameCard {
                            Text("Group ${groupIndex + 1} · match every condition", style = MaterialTheme.typography.titleMedium)
                            group.forEachIndexed { index, condition ->
                                if (index > 0) { HorizontalDivider(); Eyebrow("AND", Saffron) }
                                key(groupIndex, index) {
                                    ConditionEditor(condition, "$groupIndex-$index") { update(draft.changeCondition(groupIndex, index, it)) }
                                }
                                if (group.size > 1) TextButton(onClick = {
                                    update(draft.copy(groups = draft.groups.mapIndexed { i, list -> if (i == groupIndex) list.filterIndexed { j, _ -> j != index } else list }))
                                }) { Text("Remove condition ${index + 1}") }
                            }
                            OutlinedButton(onClick = { update(draft.copy(groups = draft.groups.mapIndexed { i, list -> if (i == groupIndex) list + ConditionDraft(all = true) else list })) },
                                enabled = group.size < 8 && draft.groups.sumOf { it.size } < 16, modifier = Modifier.testTag("add-and-$groupIndex")) { Text("Add AND condition") }
                            if (draft.groups.size > 1) TextButton(onClick = { update(draft.copy(groups = draft.groups.filterIndexed { i, _ -> i != groupIndex })) }) { Text("Remove group ${groupIndex + 1}") }
                        }
                    }
                    OutlinedButton(onClick = { update(draft.copy(groups = draft.groups + listOf(listOf(ConditionDraft(all = true))))) },
                        enabled = draft.groups.size < 4 && draft.groups.sumOf { it.size } < 16, modifier = Modifier.fillMaxWidth()) { Text("Add OR group") }
                    GameCard {
                        Text("Which owned tickets count?", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = draft.ticketOrdinals.isEmpty(), onClick = { update(draft.copy(ticketOrdinals = emptyList())) }, label = { Text("All owned tickets") })
                            (1..tickets).forEach { n -> FilterChip(selected = n in draft.ticketOrdinals,
                                onClick = { update(draft.copy(ticketOrdinals = if (n in draft.ticketOrdinals) draft.ticketOrdinals - n else draft.ticketOrdinals + n)) }, label = { Text("Ticket $n") }) }
                        }
                        Text("Minimum matching tickets", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..tickets).forEach { n -> FilterChip(selected = draft.minimumTickets == n,
                                onClick = { update(draft.copy(minimumTickets = n)) }, label = { Text("$n") }, modifier = Modifier.testTag("minimum-tickets-$n")) }
                        }
                        Text("Each selected ticket must satisfy the pattern. A player wins when enough of their own tickets match.", color = Muted)
                    }
                    result.getOrNull()?.let { prize ->
                        GameCard { Eyebrow("THE AGREED RULE"); Text(prize.describe()); Text("${prize.points} points per winning player", color = Saffron) }
                        RulePlayground(prize, tickets)
                    }
                }
                Column(Modifier.fillMaxWidth().background(Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.exceptionOrNull()?.message?.let { Text(it, color = Coral, style = MaterialTheme.typography.bodySmall) }
                    PrimaryAction("Save prize", enabled = result.isSuccess, onClick = save)
                }
            }
        }
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard these edits?") },
            text = { Text("The prize in your setup will stay as it was.") },
            confirmButton = { TextButton(onClick = cancel) { Text("Discard edits") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    }
}

@Composable
private fun ConditionEditor(draft: ConditionDraft, tag: String, change: (ConditionDraft) -> Unit) {
    val focus = LocalFocusManager.current
    fun choose(next: ConditionDraft) { focus.clearFocus(); change(next) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionKind.entries.forEach { kind -> FilterChip(selected = draft.kind == kind, onClick = { choose(draft.copy(kind = kind, index = 0, all = true)) }, label = { Text(kind.title) }, modifier = Modifier.testTag("select-$tag-${kind.name}")) }
    }
    when (draft.kind) {
        SelectionKind.ROW -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Top row", "Middle row", "Bottom row").forEachIndexed { i, title -> FilterChip(selected = draft.index == i, onClick = { choose(draft.copy(index = i)) }, label = { Text(title) }) }
        }
        SelectionKind.COLUMN -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..8).forEach { i -> FilterChip(selected = draft.index == i, onClick = { choose(draft.copy(index = i)) }, label = { Text("${columnRange(i).first}–${columnRange(i).last}") }) }
        }
        SelectionKind.RANGE -> {
            DigitField("First number", draft.first, { change(draft.copy(first = it)) }, 2, "range-first-$tag")
            DigitField("Last number", draft.last, { change(draft.copy(last = it)) }, 2, "range-last-$tag")
        }
        SelectionKind.POSITIONS -> {
            Text("Select populated positions. Each row has five numbers; blank cells do not count.", color = Muted)
            (0..2).forEach { row ->
                Text(listOf("Top row", "Middle row", "Bottom row")[row], style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..4).forEach { column ->
                        val position = row * 5 + column
                        FilterChip(selected = position in draft.positions, onClick = { choose(draft.copy(positions = if (position in draft.positions) draft.positions - position else draft.positions + position)) },
                            label = { Text("${column + 1}") }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Row ${row + 1}, populated number ${column + 1}" })
                    }
                }
            }
            TextButton(onClick = { choose(draft.copy(positions = listOf(0, 4, 10, 14))) }) { Text("Select four corners") }
        }
        SelectionKind.ALL -> Unit
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = draft.all, onClick = { choose(draft.copy(all = true)) }, label = { Text("All selected numbers") }, modifier = Modifier.testTag("all-$tag"))
        FilterChip(selected = !draft.all, onClick = { choose(draft.copy(all = false, minimum = "1")) }, label = { Text("At least a count") }, modifier = Modifier.testTag("count-$tag"))
    }
    if (!draft.all) DigitField("Called count", draft.minimum, { change(draft.copy(minimum = it)) }, 2, "count-input-$tag")
    val result = runCatching { draft.condition() }
    Text(result.getOrNull()?.describe() ?: result.exceptionOrNull()?.message.orEmpty(), color = if (result.isSuccess) Muted else Coral, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DigitField(label: String, value: String, change: (String) -> Unit, maxLength: Int, tag: String) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, { if (it.length <= maxLength && it.all(Char::isDigit)) change(it) }, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun RulePlayground(prize: CustomPrize, count: Int) {
    val focus = LocalFocusManager.current
    val tickets = remember(count) { TicketGenerator(Random(44)).deal(listOf(Player("sample", "Sample")), count) }
    val positive = remember(prize, tickets) { prize.exampleCalls(tickets) }
    var called by remember(prize, count) { mutableStateOf(emptySet<Int>()) }
    var index by remember(count) { mutableIntStateOf(0) }
    val ticket = tickets[index]
    val satisfied = prize.eligibleTickets(tickets, called).isNotEmpty()
    GameCard {
        Eyebrow("TRY IT BEFORE YOU PLAY")
        Text("Sample tickets", style = MaterialTheme.typography.titleLarge)
        Text("These are illustrations, not your next game's tickets. Tap numbers to simulate calls.", color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { focus.clearFocus(); called = positive.orEmpty() }, enabled = positive != null) { Text("Winning example") }
            OutlinedButton(onClick = { focus.clearFocus(); called = emptySet() }) { Text("Clear sample calls") }
        }
        if (positive == null) Text("These sample tickets cannot satisfy this pattern. Ranges and columns vary between tickets; empty selections never win.", color = Coral)
        Text(if (satisfied) "Sample rule satisfied" else "Sample rule not yet satisfied", color = if (satisfied) Jade else Saffron,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (count > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tickets.indices.forEach { i -> FilterChip(selected = i == index, onClick = { index = i }, label = { Text("Sample ${i + 1}") }) }
        }
        (0..2).forEach { row ->
            Text(listOf("Top row", "Middle row", "Bottom row")[row], style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ticket.row(row).forEach { number ->
                    val active = number in called
                    OutlinedButton(onClick = { called = if (active) called - number else called + number },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) Jade else Ink, contentColor = if (active) Ink else Ivory),
                        contentPadding = PaddingValues(10.dp), shape = RoundedCornerShape(12.dp), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = "Sample number $number"; stateDescription = if (active) "Called" else "Not called" }) { Text("$number") }
                }
            }
        }
        Text("${called.size} sample numbers called · Clear calls shows an incomplete example.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
