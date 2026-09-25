package io.github.sbshrey.tambola.game.ui


import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.*
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.data.SavedRound
import io.github.sbshrey.tambola.game.data.Appearance
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.presentation.WinMoment

@Composable
fun TambolaApp(state: GameUiState, model: GameViewModel, onlineState: OnlineUiState, online: OnlineViewModel) {
    val words = gameText()
    BackHandler(state.screen != Screen.HOME && state.ruleDraft == null) { model.navigate(Screen.HOME) }
    val pageScroll = key(state.screen, state.round?.id,
        onlineState.room?.roomId.takeIf { state.screen == Screen.ONLINE },
        onlineState.room?.round?.id.takeIf { state.screen == Screen.ONLINE }) { rememberScrollState() }
    Surface(Modifier.fillMaxSize().testTag("app-background"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.screen != Screen.HOME) TextButton(onClick = { model.navigate(Screen.HOME) }) { Text(words(R.string.ui_home)) }
                else Box(Modifier.size(36.dp).background(Saffron, CircleShape), contentAlignment = Alignment.Center) { Text("T", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary, fontSize = 22.sp) }
                Text(if (state.screen == Screen.HOME) words(R.string.ui_tambola_together) else words.screen(state.screen), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.screen != Screen.SETTINGS) TextButton(onClick = { model.navigate(Screen.SETTINGS) }) { Text(words(R.string.ui_settings), fontSize = 12.sp) }
            }
            if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else Column(Modifier.weight(1f).verticalScroll(pageScroll).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(if (state.screen == Screen.GAME) 12.dp else 20.dp)) {
                SoundNotice()
                when (state.screen) {
                    Screen.HOME -> Home(state, model)
                    Screen.SETUP -> Setup(state, model)
                    Screen.GAME -> state.round?.let { GameTable(it, state, model) }
                    Screen.RESULTS -> (state.viewedResult ?: state.round)?.let { Results(it, model) }
                    Screen.HISTORY -> History(state.history, model)
                    Screen.SETTINGS -> Settings(state, model)
                    Screen.ONLINE -> OnlineScreen(onlineState, online, state.preferences)
                    Screen.TUTORIAL -> TutorialScreen(state, model) { pageScroll.scrollTo(0) }
                    Screen.BADGES -> BadgesScreen(state, onlineState)
                }
            }
            if (!state.loading && state.screen == Screen.GAME) state.round?.let { GameControls(it, state, model) }
            if (!state.loading && state.screen == Screen.ONLINE) OnlineControls(onlineState, online)
        }
    }
    if (state.screen == Screen.SETUP && state.ruleDraft != null) CustomRuleEditor(state, model)
    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text(words(R.string.ui_a_quick_heads_up)) }, text = { Text(words.message(error)) }, confirmButton = { TextButton(onClick = model::clearError) { Text(words(R.string.ui_got_it)) } }) }
}

@Composable
private fun Home(state: GameUiState, model: GameViewModel) {
    val words = gameText()
    Eyebrow(words(R.string.ui_good_company_great_numbers))
    Text(words(R.string.ui_make_room_nfor_a_little_joy), style = MaterialTheme.typography.headlineLarge)
    Text(words(R.string.ui_your_tickets_your_people_one_happy_game_night), color = Muted)
    if (!state.preferences.tutorialDismissed && !state.preferences.tutorialCompleted) GameCard {
        Eyebrow(words(R.string.ui_new_to_tambola), Saffron)
        Text(words(R.string.ui_try_a_ticket_make_a_call_find_your), style = MaterialTheme.typography.titleMedium)
        PrimaryAction(words(R.string.ui_learn_with_a_sample_ticket)) { model.navigate(Screen.TUTORIAL) }
        TextButton(onClick = { model.finishTutorial(false) }) { Text(words(R.string.ui_maybe_later)) }
    }
    GameNightArtwork()
    state.round?.takeIf { !it.finished }?.let { round ->
        GameCard {
            Eyebrow(words(R.string.ui_your_table_is_waiting), Saffron)
            Text(pluralStringResource(R.plurals.numbers_called, round.called.size, round.called.size) + " · " +
                pluralStringResource(R.plurals.player_count, round.players.size, round.players.size), style = MaterialTheme.typography.titleMedium)
            PrimaryAction(words(R.string.ui_resume_round)) { model.navigate(Screen.GAME) }
        }
    }
    GameCard {
        Eyebrow(words(R.string.ui_play_your_way))
        Text(words(R.string.ui_a_little_me_time), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.ui_practice_at_your_pace_or_invite_some_friendly), color = Muted)
        PrimaryAction(words(R.string.ui_play_solo)) { model.setup(GameMode.PRACTICE) }
        HorizontalDivider(color = Muted.copy(alpha = .2f))
        Text(words(R.string.ui_bring_everyone_together), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.ui_pass_one_phone_around_every_player_gets_their), color = Muted)
        OutlinedButton(onClick = { model.setup(GameMode.FAMILY) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) { Text(words(R.string.ui_play_on_one_device)) }
        HorizontalDivider(color = Muted.copy(alpha = .2f))
        Text(words(R.string.ui_meet_at_a_private_table), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.ui_play_together_on_your_own_phones_with_a), color = Muted)
        OutlinedButton(onClick = { model.navigate(Screen.ONLINE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) { Text(words(R.string.ui_play_online)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { model.navigate(Screen.HISTORY) }, modifier = Modifier.weight(1f)) { Text(words(R.string.ui_your_rounds)) }
        OutlinedButton(onClick = { model.navigate(Screen.TUTORIAL) }, modifier = Modifier.weight(1f)) { Text(words(R.string.ui_how_to_play)) }
    }
    OutlinedButton(onClick = { model.navigate(Screen.BADGES) }, modifier = Modifier.fillMaxWidth()) { Text(words(R.string.ui_your_badges)) }
    Text(words(R.string.ui_development_alpha_private_rooms_require_the_configured_room), color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun GameTable(round: Round, state: GameUiState, model: GameViewModel) {
    val words = gameText()
    var cancel by remember { mutableStateOf(false) }
    TablePlay(round.toTable(), state.preferences, model::toggleMark, model::repeatCall, state.winMoment, model::dismissWin)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (round.settings.mode == GameMode.PRACTICE && !round.finished) TextButton(onClick = model::undo, enabled = round.called.isNotEmpty()) { Text(words(R.string.ui_undo_last_call)) }
        if (!round.finished) TextButton(onClick = { cancel = true }) { Text(words(R.string.ui_end_round)) }
    }
    if (cancel) AlertDialog(onDismissRequest = { cancel = false }, title = { Text(words(R.string.ui_end_this_round_early)) }, text = { Text(words(R.string.ui_this_round_will_be_saved_as_cancelled_with)) }, confirmButton = { TextButton(onClick = { cancel = false; model.finish() }) { Text(words(R.string.ui_end_round)) } }, dismissButton = { TextButton(onClick = { cancel = false }) { Text(words(R.string.ui_keep_playing)) } })
}

@Composable
private fun GameControls(round: Round, state: GameUiState, model: GameViewModel) {
    val words = gameText()
    Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(round.latest?.let { words(R.string.ui_latest_number_called, it, round.called.size) } ?: words(R.string.ui_your_tickets_are_ready), color = Muted, style = MaterialTheme.typography.bodySmall)
        when {
            round.finished -> PrimaryAction(words(R.string.ui_see_round_results)) { model.navigate(Screen.RESULTS) }
            round.status == RoundStatus.PAUSED -> PrimaryAction(words(R.string.ui_resume_calling)) { model.resume() }
            else -> {
                PrimaryAction(if (state.auto) words(R.string.ui_call_next_now) else words(R.string.ui_call_next_number)) { model.draw() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = model::auto, modifier = Modifier.weight(1f)) { Text(if (state.auto) words(R.string.ui_stop_auto) else words(R.string.ui_auto_s, state.preferences.interval)) }
                    TextButton(onClick = model::pause, modifier = Modifier.weight(1f)) { Text(words(R.string.ui_pause)) }
                }
            }
        }
    }
}

@Composable
private fun Results(round: Round, model: GameViewModel) {
    val words = gameText()
    val context = LocalContext.current
    var sharing by remember { mutableStateOf(false) }
    Eyebrow(if (round.status == RoundStatus.CANCELLED) words(R.string.ui_results_so_far) else words(R.string.ui_that_was_a_lovely_round))
    Text(if (round.status == RoundStatus.CANCELLED) words(R.string.ui_until_next_time) else words(R.string.ui_a_round_of_applause_2), style = MaterialTheme.typography.headlineLarge)
    val prizeCount = round.awards.size + round.customAwards.size
    Text(pluralStringResource(R.plurals.call_count, round.called.size, round.called.size) + " · " +
        pluralStringResource(R.plurals.prize_count, prizeCount, prizeCount) + " · " +
        pluralStringResource(R.plurals.player_count, round.players.size, round.players.size), color = Muted)
    GameCard {
        val topScore = round.players.maxOf { round.score(it.id) }
        round.players.sortedByDescending { round.score(it.id) }.forEach { player ->
            val leading = topScore > 0 && round.score(player.id) == topScore
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AvatarBadge(player.avatar, if (leading) Modifier.border(2.dp, Saffron, CircleShape) else Modifier, size = 42.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(player.name, style = MaterialTheme.typography.titleMedium); if (player.computer) Text(words(R.string.ui_computer_player), color = Muted, fontSize = 12.sp) }
                Text(words(R.string.ui_pts, round.score(player.id)), color = Jade, fontWeight = FontWeight.Bold)
            }
        }
    }
    GameCard { Text(words(R.string.ui_the_winning_moments), style = MaterialTheme.typography.titleLarge); RuleList(round.toTable()) }
    if (round.status == RoundStatus.COMPLETED) GameCard {
        Text(words(R.string.ui_another_good_memory), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.ui_this_completed_round_counts_toward_badges_reopening_it, words.badgeModeTitle(round.badgeMode()).lowercase(words.locale)), color = Muted)
        TextButton(onClick = { model.navigate(Screen.BADGES) }) { Text(words(R.string.ui_see_your_badges)) }
    }
    PrimaryAction(words(R.string.ui_play_another_round)) { model.rematch(round) }
    OutlinedButton(onClick = { sharing = true }, modifier = Modifier.fillMaxWidth()) { Text(words(R.string.ui_share_these_results)) }
    if (sharing) ShareResults(round.toTable(), onDismiss = { sharing = false }) { message ->
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }, words(R.string.ui_share_results)))
    }
}

@Composable
private fun History(history: List<SavedRound>, model: GameViewModel) {
    val words = gameText()
    Eyebrow(words(R.string.ui_a_little_book_of_game_nights))
    Text(words(R.string.ui_your_rounds), style = MaterialTheme.typography.headlineLarge)
    if (history.isEmpty()) GameCard { Text(words(R.string.ui_your_first_game_night_is_waiting), style = MaterialTheme.typography.titleMedium); Text(words(R.string.ui_completed_rounds_and_scores_will_appear_here), color = Muted) }
    history.take(50).forEach { saved ->
        val round = remember(saved) { runCatching { RoundCodec.decode(saved.payload) }.getOrNull() }
        GameCard {
            Text(words.date(saved.createdAt), style = MaterialTheme.typography.titleMedium)
            Text(round?.let { words(R.string.ui_calls, words.mode(it.settings.mode), it.called.size, words.status(it.status)) } ?: words(R.string.ui_saved_record_needs_attention), color = Muted)
            if (saved.completed) TextButton(onClick = { model.openHistory(saved) }, enabled = round != null) { Text(words(R.string.ui_view_results)) }
        }
    }
}

@Composable
private fun Settings(state: GameUiState, model: GameViewModel) {
    val words = gameText()
    val prefs = state.preferences
    var delete by remember { mutableStateOf(false) }
    Eyebrow(words(R.string.ui_make_yourself_comfortable_2))
    Text(words(R.string.ui_just_your_style), style = MaterialTheme.typography.headlineLarge)
    GameCard {
        Text(words(R.string.ui_your_table_day_or_night), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Appearance.entries.forEach { choice ->
                FilterChip(selected = prefs.appearance == choice, onClick = { model.updatePreferences(prefs.copy(appearance = choice)) }, label = { Text(words.appearance(choice)) })
            }
        }
        Text(words(R.string.ui_system_follows_your_device_s_light_or_dark), color = Muted)
    }
    InterfaceLanguagePicker()
    GameCard {
        Text(words(R.string.ui_the_voice_of_your_game), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("en" to "English", "hi" to "हिन्दी", "hinglish" to "Hinglish").forEach { (id, label) -> FilterChip(selected = prefs.language == id, onClick = { model.updatePreferences(prefs.copy(language = id)) }, label = { Text(label) }) } }
        SettingSwitch(words(R.string.ui_number_voice), words(R.string.ui_automatically_speak_each_new_call_ai_generated_recordings), prefs.voice) { model.updatePreferences(prefs.copy(voice = it)) }
        SoundVolume(words(R.string.ui_voice_volume), prefs.voiceVolume) { model.updatePreferences(prefs.copy(voiceVolume = it)) }
        Text(words(R.string.ui_hear_again_plays_a_number_on_request_even), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    SoundSettings(prefs, model::updatePreferences)
    GameCard {
        Text(words(R.string.ui_your_pace_your_comfort), style = MaterialTheme.typography.titleLarge)
        SettingSwitch(words(R.string.ui_gentle_haptics), words(R.string.ui_a_little_feedback_when_you_mark_a_number), prefs.haptics) { model.updatePreferences(prefs.copy(haptics = it)) }
        SettingSwitch(words(R.string.ui_reduced_motion), words(R.string.ui_keep_number_and_badge_reveals_still_device_animation), prefs.reducedMotion) { model.updatePreferences(prefs.copy(reducedMotion = it)) }
        Text(words(R.string.ui_automatic_calling_pace), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 15, 20, 30).forEach { seconds -> FilterChip(selected = prefs.interval == seconds, onClick = { model.updatePreferences(prefs.copy(interval = seconds)) }, label = { Text(words(R.string.seconds_short, seconds)) }) } }
        Text(words(R.string.ui_calls_wait_for_the_recording_to_finish_leaving), color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
    GameCard {
        Text(words(R.string.ui_your_first_game_made_easy), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { model.navigate(Screen.TUTORIAL) }) { Text(words(R.string.ui_try_the_interactive_tutorial)) }
        listOf(words(R.string.ui_1_choose_players_tickets_and_prizes_everyone_can), words(R.string.ui_2_call_numbers_yourself_or_turn_on_automatic), words(R.string.ui_3_tap_mark_ticket_and_dab_the_called), words(R.string.ui_4_check_claims_to_inspect_the_required_numbers), words(R.string.ui_5_finish_at_the_chosen_house_or_play)).forEach { Text(it, color = Muted) }
    }
    GameCard {
        Text(words(R.string.ui_your_games_and_privacy), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.ui_solo_and_family_rounds_stay_on_this_device), color = Muted)
        OutlinedButton(onClick = { delete = true }, modifier = Modifier.fillMaxWidth()) { Text(words(R.string.ui_delete_all_saved_rounds)) }
        Text(words(R.string.ui_build_status, BuildConfig.VERSION_NAME), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(words(R.string.ui_delete_saved_rounds)) }, text = { Text(words(R.string.ui_this_removes_your_offline_current_game_player_names)) }, confirmButton = { TextButton(onClick = { delete = false; model.deleteHistory() }) { Text(words(R.string.ui_delete_rounds)) } }, dismissButton = { TextButton(onClick = { delete = false }) { Text(words(R.string.ui_keep_rounds)) } })
}

@Composable
fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val words = gameText()
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium) }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun TablePlay(round: TableRound, preferences: io.github.sbshrey.tambola.game.data.Preferences,
    mark: (String, Int) -> Unit, repeatCall: () -> Unit, winMoment: WinMoment? = null, dismissWin: () -> Unit = {}) {
    val words = gameText()
    var selected by rememberSaveable(round.id) { mutableIntStateOf(0) }
    var board by remember { mutableStateOf(false) }
    var claims by remember { mutableStateOf(false) }
    val index = selected.coerceIn(round.tickets.indices)
    val numberChipSize = 34.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Eyebrow(if (round.finished) words(if (round.status == RoundStatus.COMPLETED) R.string.round_complete else R.string.round_cancelled) else if (round.status == RoundStatus.PAUSED) words(R.string.ui_take_a_breather_paused) else words(R.string.ui_let_the_good_times_roll))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(120.dp)) { NumberBall(round.latest, preferences.reducedMotion, compact = true) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (round.latest == null) words(R.string.ui_ready_when_you_are) else words(R.string.ui_call_of_90, round.called.size), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { board = true }) { Text(words(R.string.ui_number_board)) }
            TextButton(onClick = repeatCall, enabled = round.latest != null) { Text(words(R.string.ui_hear_again)) }
        }
    }
    LinearProgressIndicator(progress = { round.called.size / 90f }, modifier = Modifier.fillMaxWidth(), color = Jade, trackColor = Panel)
    winMoment?.let { WinCelebration(it, preferences.reducedMotion, dismissWin) { claims = true } }
    if (round.called.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(words(R.string.ui_recent_calls), fontSize = 10.sp, color = Muted, modifier = Modifier.fillMaxWidth())
            round.called.takeLast(5).reversed().forEachIndexed { i, n -> Box(Modifier.size(numberChipSize).background(if (i == 0) Saffron else Panel, CircleShape), contentAlignment = Alignment.Center) { Text("$n", fontSize = 14.sp, lineHeight = 18.sp, color = if (i == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) } }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(words(R.string.ui_your_table), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(pluralStringResource(R.plurals.ticket_count, round.tickets.size, round.tickets.size), color = Muted, fontSize = 12.sp)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        round.tickets.forEachIndexed { i, ticket -> FilterChip(selected = index == i, onClick = { selected = i }, label = { Text(words.ticketLabel(round, ticket.id)) }) }
    }
    TicketCard(round.tickets[index], round, preferences.haptics, mark)
    Text(words(R.string.ui_amber_outline_called_green_marked_tap_mark_ticket), color = Muted, style = MaterialTheme.typography.bodySmall)
    PrimaryAction(words(R.string.ui_check_claims_verified, round.awards.size + round.customAwards.size)) { claims = true }
    if (board) AlertDialog(onDismissRequest = { board = false }, title = { Text(words(R.string.ui_the_number_board)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(words(R.string.ui_called_to_go, round.called.size, 90 - round.called.size), color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                (1..90).forEach { n -> Box(Modifier.size(numberChipSize + 2.dp).background(if (n == round.latest) Saffron else if (n in round.called) Jade else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("$n", color = if (n == round.latest) MaterialTheme.colorScheme.onPrimary else if (n in round.called) MaterialTheme.colorScheme.onSecondary else Muted, fontWeight = FontWeight.Bold) } }
            }
            Text(words(R.string.ui_call_history), style = MaterialTheme.typography.titleMedium)
            Text(round.called.joinToString(" → ").ifEmpty { words(R.string.ui_no_numbers_yet) }, color = Muted)
        }
    }, confirmButton = { TextButton(onClick = { board = false }) { Text(words(R.string.ui_back_to_table)) } })
    if (claims) AlertDialog(onDismissRequest = { claims = false }, title = { Text(words(R.string.ui_fair_wins_happy_faces)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(words(R.string.ui_winners_are_verified_from_called_numbers_even_if), color = Muted)
            Text(words.endExplanation(round.settings), color = Saffron)
            Text(if (round.settings.assistedMarking) words(R.string.ui_assisted_marking_is_on_for_everyone) else if (round.settings.mode == GameMode.ONLINE) words(R.string.ui_manual_marking_mark_your_own_called_numbers) else words(R.string.ui_manual_marking_computer_tickets_mark_automatically), color = Muted)
            RuleList(round, round.tickets[index])
        }
    }, confirmButton = { TextButton(onClick = { claims = false }) { Text(words(R.string.ui_back_to_game)) } })
}
