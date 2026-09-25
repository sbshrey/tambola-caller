package io.github.sbshrey.tambola.game.ui

import io.github.sbshrey.tambola.game.R

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.GameUiState
import io.github.sbshrey.tambola.game.GameViewModel
import io.github.sbshrey.tambola.game.online.OnlineUiState
import io.github.sbshrey.tambola.protocol.WinningTicket

private val lessonTicket = Ticket("lesson-ticket-1", "lesson-player", listOf(
    7, 0, 22, 0, 40, 0, 0, 70, 80,
    0, 12, 0, 32, 0, 50, 60, 0, 81,
    9, 18, 28, 0, 0, 55, 0, 78, 0))

/** A labelled, isolated example; never creates a Round, touches game saves, or earns a badge. */
@Composable
fun TutorialScreen(state: GameUiState, model: GameViewModel, scrollToTop: suspend () -> Unit) {
    val words = gameText()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var called by rememberSaveable { mutableStateOf(false) }
    var markedNumbers by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    val marked = 7 in markedNumbers
    var topLine by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(step) { scrollToTop() }
    val prefs = state.preferences
    val calls = if (step >= 3 && topLine) lessonTicket.row(0) else if (called) listOf(7) else emptyList()
    val won = Prize.TOP_LINE.matches(lessonTicket, calls.toSet())
    val award = if (won) listOf(Award(Prize.TOP_LINE, 5, listOf(lessonTicket.id), listOf(lessonTicket.playerId))) else emptyList()
    val table = TableRound("tutorial-example", RoundSettings(prizes = listOf(Prize.TOP_LINE, Prize.FULL_HOUSE)),
        listOf(Player(lessonTicket.playerId, words(R.string.ui_sample_player))), listOf(lessonTicket), calls,
        mapOf(lessonTicket.id to markedNumbers.toSet().intersect(calls.toSet())), award, emptyList(), RoundStatus.PLAYING,
        mapOf(lessonTicket.playerId to if (won) Prize.TOP_LINE.points else 0), listOf(WinningTicket(lessonTicket.id, lessonTicket.playerId, 1)))
    Eyebrow(words(R.string.ui_your_first_game_of_5, step + 1))
    LinearProgressIndicator(progress = { (step + 1) / 5f }, modifier = Modifier.fillMaxWidth(), color = Jade)
    Text(listOf(words(R.string.ui_a_happy_place_to_start), words(R.string.ui_meet_your_ticket), words(R.string.ui_hear_it_find_it_dab_it), words(R.string.ui_fair_wins_every_time), words(R.string.ui_you_re_ready_for_game_night))[step], style = MaterialTheme.typography.headlineLarge)
    Text(words(R.string.ui_tutorial_sample_your_real_games_and_badges_stay), color = Muted, style = MaterialTheme.typography.bodySmall)
    when (step) {
        0 -> {
            Text(words(R.string.ui_tambola_is_a_race_to_complete_the_patterns), color = Muted)
            GameCard {
                Text(words(R.string.ui_make_yourself_comfortable), style = MaterialTheme.typography.titleLarge)
                SettingSwitch(words(R.string.ui_tutorial_voice), words(R.string.ui_hear_a_sample_call_using_the_same_offline), prefs.voice) { model.updatePreferences(prefs.copy(voice = it)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "English", "hi" to "हिन्दी", "hinglish" to "Hinglish").forEach { (id, label) ->
                        FilterChip(prefs.language == id, { model.updatePreferences(prefs.copy(language = id)) }, label = { Text(label) })
                    }
                }
                Text(words(R.string.ui_number_voices_are_ai_generated_sound_is_optional), color = Muted)
            }
        }
        1 -> {
            Text(words(R.string.ui_a_ticket_has_15_numbers_five_in_each), color = Muted)
            TicketCard(lessonTicket, table, false) { _, _ -> }
            Text(words(R.string.ui_this_sample_ticket_is_only_for_learning_every), color = Saffron)
        }
        2 -> {
            NumberBall(if (called) 7 else null, prefs.reducedMotion, compact = true)
            if (!called) PrimaryAction(words(R.string.ui_try_calling_a_number)) { called = true; model.tutorialCall() }
            else TextButton(onClick = model::tutorialCall, enabled = prefs.voice) { Text(words(R.string.ui_hear_7_again)) }
            Text(if (!called) words(R.string.ui_make_your_first_sample_call) else if (!marked) words(R.string.ui_seven_is_on_your_top_row_open_mark) else words(R.string.ui_nice_dab_green_means_marked_only_called_numbers), color = if (marked) Jade else Muted)
            TicketCard(lessonTicket, table, prefs.haptics) { _, number -> if (number in calls) markedNumbers = if (number in markedNumbers) markedNumbers - number else markedNumbers + number }
            Text(words(R.string.ui_prefer_a_helping_hand_choose_assisted_marking_before), color = Muted)
        }
        3 -> {
            Text(words(R.string.ui_let_s_finish_the_sample_top_line_a), color = Muted)
            if (!topLine) PrimaryAction(words(R.string.ui_try_a_top_line_win)) { topLine = true }
            TicketCard(lessonTicket, table, false) { _, number -> if (number in calls) markedNumbers = if (number in markedNumbers) markedNumbers - number else markedNumbers + number }
            if (won) GameCard {
                Eyebrow(words(R.string.ui_top_line_verified), Jade)
                Text(words(R.string.ui_15_points_all_five_numbers_called), style = MaterialTheme.typography.titleLarge)
                Text(words(R.string.ui_the_whole_line_has_been_called_the_game), color = Muted)
            }
            Text(words(R.string.ui_read_the_prizes_before_you_ready_up_full), color = Muted)
        }
        4 -> {
            GameCard {
                Text(words(R.string.ui_choose_your_table), style = MaterialTheme.typography.titleLarge)
                Text(words(R.string.ui_solo_take_your_time_with_optional_computer_players), color = Muted)
            }
            GameCard {
                Text(words(R.string.ui_keep_the_good_moments), style = MaterialTheme.typography.titleLarge)
                Text(words(R.string.ui_completed_rounds_earn_badges_ending_early_saves_a), color = Muted)
            }
            Text(words(R.string.ui_use_how_to_play_any_time_to_try), color = Jade)
        }
    }
    if (step < 4) PrimaryAction(if (step == 0) words(R.string.ui_show_me_the_ticket) else words(R.string.ui_next_lesson), enabled = when (step) { 2 -> marked; 3 -> won; else -> true }) { step++ }
    else PrimaryAction(words(R.string.ui_finish_tutorial)) { model.finishTutorial(true) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (step > 0) TextButton(onClick = { step-- }) { Text(words(R.string.ui_previous_lesson)) }
        TextButton(onClick = { model.finishTutorial(false) }) { Text(words(R.string.ui_skip_for_now)) }
    }
}

@Composable
fun BadgesScreen(state: GameUiState, online: OnlineUiState) {
    val words = gameText()
    var mode by rememberSaveable { mutableStateOf(BadgeMode.SOLO) }
    Eyebrow(words(R.string.ui_little_milestones_lovely_memories))
    Text(words(R.string.ui_your_badges), style = MaterialTheme.typography.headlineLarge)
    Text(words(R.string.ui_free_social_play_with_something_to_smile_about), color = Muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BadgeMode.entries.forEach { item -> FilterChip(mode == item, { mode = item }, label = { Text(words.badgeModeTitle(item)) }) }
    }
    Text(words.badgeModeDescription(mode), color = Muted)
    if (mode == BadgeMode.ONLINE && online.name == null) Text(words(R.string.ui_open_online_play_to_set_up_a_profile), color = Saffron)
    else if (mode == BadgeMode.ONLINE) Text(words(R.string.ui_playing_as, online.name), color = Jade)
    BadgeCollection(if (mode == BadgeMode.ONLINE) online.badges else state.badges[mode] ?: BadgeProgress(), state.preferences.reducedMotion)
    Text(if (mode == BadgeMode.ONLINE) words(R.string.ui_reconnecting_or_revisiting_a_result_never_counts_it)
        else words(R.string.ui_badges_come_from_your_saved_completed_rounds_cancelled), color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun BadgeCollection(progress: BadgeProgress, reducedMotion: Boolean) {
    val words = gameText()
    Badge.entries.forEach { badge ->
        val earned = progress.earned(badge)
        var shown by remember(badge, earned) { mutableStateOf(false) }
        LaunchedEffect(badge, earned) { shown = true }
        val scale by animateFloatAsState(if (shown || !earned) 1f else .85f, tween(if (reducedMotion) 0 else 200), label = "badge reveal")
        GameCard(Modifier.testTag("badge-${badge.name}").semantics(mergeDescendants = true) { stateDescription = if (earned) words(R.string.ui_earned) else words(R.string.ui_not_yet_earned) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(58.dp).scale(if (reducedMotion) 1f else scale).background(if (earned) Saffron else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    Text(badge.symbol, color = if (earned) MaterialTheme.colorScheme.onPrimary else Muted, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text(words.badgeTitle(badge), style = MaterialTheme.typography.titleLarge)
                    Text(if (earned) words(R.string.ui_earned) else words(R.string.ui_still_to_come), color = if (earned) Jade else Muted)
                }
            }
            Text(words.badgeExplanation(badge), color = Muted)
            if (badge == Badge.FIVE_ROUNDS && !earned) {
                LinearProgressIndicator(progress = { progress.completedRoundIds.size / 5f }, modifier = Modifier.fillMaxWidth(), color = Jade)
                Text(words(R.string.ui_of_5_completed_rounds, progress.completedRoundIds.size), color = Jade)
            }
        }
    }
}
