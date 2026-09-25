package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.*

@Composable
fun Setup(state: GameUiState, model: GameViewModel) {
    val draft = state.setupDraft
    val family = draft.mode == GameMode.FAMILY
    var confirm by remember { mutableStateOf(false) }
    Eyebrow(if (family) "A TABLE FOR EVERYONE" else "YOUR OWN LITTLE GAME NIGHT")
    Text(if (family) "Who's playing?" else "Let's make it yours.", style = MaterialTheme.typography.headlineMedium)
    GameCard {
        OutlinedTextField(value = draft.names, onValueChange = { if (it.length <= 330) model.updateSetup(draft.copy(names = it)) },
            label = { Text(if (family) "Players · one name per line" else "Your name") },
            supportingText = { Text(if (family) "2–8 players, up to 40 characters per name" else "Up to 40 characters") },
            minLines = if (family) 3 else 1, singleLine = !family, modifier = Modifier.fillMaxWidth().testTag("setup-names"))
        Text("Tickets per player", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..6).forEach { n -> FilterChip(selected = draft.tickets == n, onClick = { model.updateSetup(draft.copy(tickets = n)) },
                label = { Text("$n") }, modifier = Modifier.testTag("tickets-$n")) }
        }
        if (!family) {
            Text("Computer players", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..5).forEach { n -> FilterChip(selected = draft.bots == n, onClick = { model.updateSetup(draft.copy(bots = n)) }, label = { Text(if (n == 0) "Just me" else "$n") }) }
            }
            Text("Computer players get the same calls and rules as you.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        SettingSwitch("Help with marking", "Automatically dab called numbers.", draft.assisted) { model.updateSetup(draft.copy(assisted = it)) }
    }
    RoundRules(draft, model::updateSetup, model::editRule, model::removeRule)
    if (draft.errors.isNotEmpty()) GameCard { draft.errors.forEach { Text(it, color = Coral) } }
    PrimaryAction(if (state.saving) "Dealing your tickets…" else "Deal the tickets", enabled = draft.errors.isEmpty() && !state.saving) {
        if (state.round?.finished == false) confirm = true else model.create()
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Start a fresh round?") },
        text = { Text("Your current round will be saved as cancelled. Its calls and results stay in history.") },
        confirmButton = { TextButton(onClick = { confirm = false; model.create() }) { Text("Start new round") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep current round") } })
}

@Composable
fun RoundRules(draft: io.github.sbshrey.tambola.game.setup.SetupDraft,
    update: (io.github.sbshrey.tambola.game.setup.SetupDraft) -> Unit, edit: (CustomPrize?) -> Unit, remove: (String) -> Unit) {
    GameCard {
        Text("Pick your prizes", style = MaterialTheme.typography.titleLarge)
        Text("Free play, happy wins. Same-call winners receive equal points. The app verifies every ticket, even if someone forgets to mark.", color = Muted)
        Prize.entries.filter { !it.isRankedHouse && it != Prize.FULL_HOUSE }.forEach { prize ->
            fun select(checked: Boolean) = update(draft.copy(prizes = if (checked) (draft.prizes + prize).distinct() else draft.prizes - prize))
            Row(Modifier.fillMaxWidth().clickable { select(prize !in draft.prizes) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = prize in draft.prizes, onCheckedChange = ::select)
                Column(Modifier.weight(1f)) { Text(prize.title, style = MaterialTheme.typography.titleMedium); Text(prize.explanation, color = Muted, style = MaterialTheme.typography.bodySmall) }
                Text("${prize.points}", color = Saffron, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    GameCard {
        Text("The grand finale", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("One full house", "Two houses", "Three houses").forEachIndexed { i, title ->
                FilterChip(selected = draft.houses == i + 1, onClick = { update(draft.copy(houses = i + 1)) }, label = { Text(title) })
            }
        }
        Text(if (draft.houses == 1) "Full house · 100 points" else "House one · 100 points\nHouse two · 75 points" + if (draft.houses == 3) "\nHouse three · 50 points" else "", color = Saffron)
        if (draft.houses > 1) Text("Tickets finishing together share a house rank. Those tickets cannot win a later rank; the next newly completed group wins it.", color = Muted)
        SettingSwitch("Call all 90 numbers", "Keep playing after the final house.", draft.playAllNumbers) { update(draft.copy(playAllNumbers = it)) }
        runCatching { draft.settings() }.getOrNull()?.let { Text(it.endExplanation(), color = Muted) }
    }
    GameCard {
        Text("Make a prize of your own", style = MaterialTheme.typography.titleLarge)
        Text("Choose positions, rows, columns or number ranges. Try the rule on sample tickets before adding it.", color = Muted)
        draft.customPrizes.forEach { prize ->
            HorizontalDivider(color = Muted.copy(alpha = .2f))
            Text(prize.title, style = MaterialTheme.typography.titleMedium)
            Text("${prize.points} points · ${prize.describe()}", color = Muted)
            Row {
                TextButton(onClick = { edit(prize) }, modifier = Modifier.testTag("edit-${prize.id}")) { Text("Edit ${prize.title}") }
                TextButton(onClick = { remove(prize.id) }) { Text("Remove") }
            }
        }
        OutlinedButton(onClick = { edit(null) }, enabled = draft.customPrizes.size < 12, modifier = Modifier.fillMaxWidth()) { Text("Create custom prize") }
        Text("${draft.customPrizes.size} of 12 custom prizes · Rules lock when tickets are dealt.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
