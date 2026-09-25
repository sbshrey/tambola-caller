package io.github.sbshrey.tambola.game.ui

import io.github.sbshrey.tambola.game.R

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
    val words = gameText()
    val draft = state.setupDraft
    val family = draft.mode == GameMode.FAMILY
    var confirm by remember { mutableStateOf(false) }
    Eyebrow(if (family) words(R.string.ui_a_table_for_everyone) else words(R.string.ui_your_own_little_game_night))
    Text(if (family) words(R.string.ui_who_s_playing) else words(R.string.ui_let_s_make_it_yours), style = MaterialTheme.typography.headlineMedium)
    GameCard {
        OutlinedTextField(value = draft.names, onValueChange = { if (it.length <= 330) model.updateSetup(draft.copy(names = it)) },
            label = { Text(if (family) words(R.string.ui_players_one_name_per_line) else words(R.string.ui_your_name)) },
            supportingText = { Text(if (family) words(R.string.ui_2_8_players_up_to_40_characters_per) else words(R.string.ui_up_to_40_characters)) },
            minLines = if (family) 3 else 1, singleLine = !family, modifier = Modifier.fillMaxWidth().testTag("setup-names"))
        Text(words(R.string.ui_tickets_per_player), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..6).forEach { n -> FilterChip(selected = draft.tickets == n, onClick = { model.updateSetup(draft.copy(tickets = n)) },
                label = { Text("$n") }, modifier = Modifier.testTag("tickets-$n")) }
        }
        if (!family) {
            Text(words(R.string.ui_computer_players), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..5).forEach { n -> FilterChip(selected = draft.bots == n, onClick = { model.updateSetup(draft.copy(bots = n)) }, label = { Text(if (n == 0) words(R.string.ui_just_me) else "$n") }) }
            }
            Text(words(R.string.ui_computer_players_get_the_same_calls_and_rules), color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        SettingSwitch(words(R.string.ui_help_with_marking), words(R.string.ui_automatically_dab_called_numbers), draft.assisted) { model.updateSetup(draft.copy(assisted = it)) }
    }
    if (draft.playerNames.isNotEmpty()) GameCard {
        Text(words(R.string.ui_a_face_for_every_place), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.ui_choose_each_player_s_avatar_before_dealing_computer), color = Muted)
        draft.playerNames.take(if (family) 8 else 1).forEachIndexed { index, name ->
            AvatarChoice(name, draft.avatar(index)) { model.updateSetup(draft.withAvatar(index, it)) }
        }
    }
    RoundRules(draft, model::updateSetup, model::editRule, model::removeRule)
    if (draft.errors.isNotEmpty()) GameCard { draft.errors.forEach { Text(words.message(it), color = Coral) } }
    PrimaryAction(if (state.saving) words(R.string.ui_dealing_your_tickets) else words(R.string.ui_deal_the_tickets), enabled = draft.errors.isEmpty() && !state.saving) {
        if (state.round?.finished == false) confirm = true else model.create()
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(words(R.string.ui_start_a_fresh_round)) },
        text = { Text(words(R.string.ui_your_current_round_will_be_saved_as_cancelled)) },
        confirmButton = { TextButton(onClick = { confirm = false; model.create() }) { Text(words(R.string.ui_start_new_round)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(words(R.string.ui_keep_current_round)) } })
}

@Composable
fun RoundRules(draft: io.github.sbshrey.tambola.game.setup.SetupDraft,
    update: (io.github.sbshrey.tambola.game.setup.SetupDraft) -> Unit, edit: (CustomPrize?) -> Unit, remove: (String) -> Unit) {
    val words = gameText()
    GameCard {
        Text(words(R.string.ui_pick_your_prizes), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.ui_free_play_happy_wins_same_call_winners_receive), color = Muted)
        Prize.entries.filter { !it.isRankedHouse && it != Prize.FULL_HOUSE }.forEach { prize ->
            fun select(checked: Boolean) = update(draft.copy(prizes = if (checked) (draft.prizes + prize).distinct() else draft.prizes - prize))
            Row(Modifier.fillMaxWidth().clickable { select(prize !in draft.prizes) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = prize in draft.prizes, onCheckedChange = ::select)
                Column(Modifier.weight(1f)) { Text(words.prizeTitle(prize), style = MaterialTheme.typography.titleMedium); Text(words.prizeExplanation(prize), color = Muted, style = MaterialTheme.typography.bodySmall) }
                Text("${prize.points}", color = Saffron, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    GameCard {
        Text(words(R.string.ui_the_grand_finale), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(words(R.string.ui_one_full_house), words(R.string.ui_two_houses), words(R.string.ui_three_houses)).forEachIndexed { i, title ->
                FilterChip(selected = draft.houses == i + 1, onClick = { update(draft.copy(houses = i + 1)) }, label = { Text(title) })
            }
        }
        Text(if (draft.houses == 1) words(R.string.ui_full_house_100_points) else words(R.string.ui_house_one_100_points_nhouse_two_75_points) + if (draft.houses == 3) words(R.string.ui_nhouse_three_50_points) else "", color = Saffron)
        if (draft.houses > 1) Text(words(R.string.ui_tickets_finishing_together_share_a_house_rank_those), color = Muted)
        SettingSwitch(words(R.string.ui_call_all_90_numbers), words(R.string.ui_keep_playing_after_the_final_house), draft.playAllNumbers) { update(draft.copy(playAllNumbers = it)) }
        runCatching { draft.settings() }.getOrNull()?.let { Text(words.endExplanation(it), color = Muted) }
    }
    GameCard {
        Text(words(R.string.ui_make_a_prize_of_your_own), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.ui_choose_positions_rows_columns_or_number_ranges_try), color = Muted)
        draft.customPrizes.forEach { prize ->
            HorizontalDivider(color = Muted.copy(alpha = .2f))
            Text(prize.title, style = MaterialTheme.typography.titleMedium)
            Text(words(R.string.ui_points_3, prize.points, words.customPrize(prize)), color = Muted)
            Row {
                TextButton(onClick = { edit(prize) }, modifier = Modifier.testTag("edit-${prize.id}")) { Text(words(R.string.ui_edit, prize.title)) }
                TextButton(onClick = { remove(prize.id) }) { Text(words(R.string.ui_remove)) }
            }
        }
        OutlinedButton(onClick = { edit(null) }, enabled = draft.customPrizes.size < 12, modifier = Modifier.fillMaxWidth()) { Text(words(R.string.ui_create_custom_prize)) }
        Text(words(R.string.ui_of_12_custom_prizes_rules_lock_when_tickets, draft.customPrizes.size), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
