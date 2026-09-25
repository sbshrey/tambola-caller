package io.github.sbshrey.tambola.game.ui

import io.github.sbshrey.tambola.game.R

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.setup.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString

@Composable
fun OnlineScreen(state: OnlineUiState, model: OnlineViewModel, preferences: Preferences) {
    val words = gameText()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableIntStateOf(0) }
    var code by rememberSaveable { mutableStateOf("") }
    var configure by rememberSaveable { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var selectedHistory by rememberSaveable { mutableStateOf<String?>(null) }
    val room = state.room
    val host = room?.hostId == state.playerId
    val enabled = !state.busy && !state.pending && !state.sessionExpired && !state.storageFailure
    if (state.loading) { CircularProgressIndicator(); return }
    if (room?.round == null) {
        Eyebrow(words(R.string.ui_a_game_night_anywhere))
        Text(words(R.string.ui_your_private_table), style = MaterialTheme.typography.headlineLarge)
    }
    if (!state.available) {
        GameCard { Text(words(R.string.ui_online_rooms_aren_t_available_in_this_build)); Text(words(R.string.ui_you_can_play_solo_or_with_everyone_on), color = Muted) }
        return
    }
    state.notice?.let { message -> GameCard { Text(words.message(message), color = Jade); TextButton(onClick = model::clearNotice) { Text(words(R.string.ui_dismiss_notice)) } } }
    if (state.pending) GameCard {
        Text(if (state.deletingProfile) words(R.string.ui_profile_deletion_is_waiting_for_confirmation) else words(R.string.ui_an_action_is_waiting_for_confirmation), style = MaterialTheme.typography.titleMedium)
        Text(if (state.deletingProfile) words(R.string.ui_retry_the_same_request_to_confirm_its_outcome)
            else words(R.string.ui_retry_safely_to_recover_the_original_result_other), color = Muted)
        PrimaryAction(if (state.busy) words(R.string.ui_checking) else words(R.string.ui_retry_pending_action), enabled = !state.busy && !state.storageFailure) { model.retry() }
    }
    if (state.storageFailure || state.sessionExpired) {
        GameCard {
            Text(if (state.sessionExpired) words(R.string.ui_your_online_session_has_expired) else words(R.string.ui_your_saved_online_data_needs_attention))
            Text(words(R.string.ui_resetting_removes_the_online_profile_cached_tickets_online), color = Muted)
            OutlinedButton(onClick = { reset = true }) { Text(words(R.string.ui_reset_online_data)) }
        }
    } else if (state.name == null) {
        GameCard {
            Text(words(R.string.ui_what_should_your_friends_call_you), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { if (it.length <= 40) name = it }, label = { Text(words(R.string.ui_online_display_name)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().testTag("online-name"))
            Text(words(R.string.ui_your_name_tickets_calls_and_results_are_saved), color = Muted)
            AvatarChoice(words(R.string.ui_your_profile), avatar, enabled) { focus.clearFocus(); avatar = it }
            PrimaryAction(if (state.busy) words(R.string.ui_opening_your_profile) else words(R.string.ui_continue_online), enabled = enabled && name.isNotBlank()) { focus.clearFocus(); model.register(name, avatar) }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarBadge(state.avatar); Text(words(R.string.ui_playing_as, state.name), color = Jade, modifier = Modifier.weight(1f))
        }
        if (room == null) {
            GameCard {
                Text(words(R.string.ui_bring_your_people_together), style = MaterialTheme.typography.titleLarge)
                PrimaryAction(words(R.string.ui_create_private_room), enabled = enabled) { model.create() }
                HorizontalDivider()
                OutlinedTextField(code, { if (it.length <= 8) code = it.uppercase() }, label = { Text(words(R.string.ui_8_character_room_code)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().testTag("room-code-input"))
                OutlinedButton(onClick = { focus.clearFocus(); model.join(code) }, enabled = enabled && code.trim().length == 8, modifier = Modifier.fillMaxWidth()) { Text(words(R.string.ui_join_room)) }
            }
        } else {
            GameCard {
                Eyebrow(if (host) words(R.string.ui_you_re_hosting) else words(R.string.ui_you_re_invited), Saffron)
                SelectionContainer { Text(room.code, style = if (room.phase == RoomPhase.LOBBY) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("room-code")) }
                Text(when (state.connection) {
                    Connection.LIVE -> words(R.string.ui_connected_all_calls_confirmed_by_the_server)
                    Connection.CONNECTING -> words(R.string.ui_connecting_to_your_room)
                    Connection.RECONNECTING -> words(R.string.ui_reconnecting_showing_your_last_confirmed_table)
                    else -> words(R.string.ui_disconnected_showing_your_last_confirmed_table)
                }, color = if (state.connection == Connection.LIVE) Jade else Saffron)
                if (state.connection != Connection.LIVE) TextButton(onClick = model::reconnect) { Text(words(R.string.ui_reconnect_now)) }
                if (room.phase == RoomPhase.LOBBY) TextButton(onClick = {
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, words(R.string.ui_join_my_private_tambola_together_room, room.code))
                    }, words(R.string.ui_invite_to_room))) }
                }) { Text(words(R.string.ui_share_room_code)) }
                Text(words(R.string.ui_players_ticket_s_each, room.members.size, room.options.capacity, room.options.game.ticketsPerPlayer), color = Muted)
            }
            if (room.phase == RoomPhase.LOBBY) {
                GameCard {
                    Text(words(R.string.ui_who_s_at_the_table), style = MaterialTheme.typography.titleLarge)
                    val me = room.members.firstOrNull { it.playerId == state.playerId }
                    if (me != null) AvatarChoice(words(R.string.ui_your_profile), me.avatar, enabled && state.connection == Connection.LIVE && room.protocolVersion >= 2) {
                        if (it != me.avatar) model.command(RoomAction.ChooseAvatar(it))
                    }
                    Text(words(R.string.ui_avatar_changes_save_for_your_next_room_too), color = Muted)
                    room.members.forEach { member ->
                        Row(Modifier.fillMaxWidth()) {
                            AvatarBadge(member.avatar, Modifier.padding(end = 8.dp), size = 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(member.displayName + if (member.playerId == room.hostId) words(R.string.ui_host) else "", fontWeight = FontWeight.SemiBold)
                                Text((if (member.ready) words(R.string.ui_ready) else words(R.string.ui_choosing_a_seat)) + if (member.connected) words(R.string.ui_connected) else words(R.string.ui_away), color = if (member.ready) Jade else Muted)
                            }
                            if (host && member.playerId != state.playerId) TextButton(onClick = { model.command(RoomAction.Remove(member.playerId)) }, enabled = enabled && state.connection == Connection.LIVE) { Text(words(R.string.ui_remove)) }
                        }
                    }
                }
                GameCard {
                    Text(words(R.string.ui_the_rules_for_this_round), style = MaterialTheme.typography.titleLarge)
                    Text(words.endExplanation(room.options.game), color = Saffron)
                    Text(if (room.options.game.assistedMarking) words(R.string.ui_assisted_marking_for_everyone) else words(R.string.ui_mark_your_own_called_numbers_wins_are_checked), color = Muted)
                    Text(if (room.options.automaticCalling) words(R.string.ui_the_server_calls_a_number_every_seconds, room.options.intervalSeconds) else words(R.string.ui_the_host_calls_each_next_number), color = Muted)
                    room.options.game.prizes.forEach { Text(words(R.string.ui_pts_n, words.prizeTitle(it), it.points, words.prizeExplanation(it))) }
                    room.options.game.customPrizes.forEach { Text(words(R.string.ui_pts_n, it.title, it.points, words.customPrize(it))) }
                    Text(words(R.string.ui_ties_on_the_same_call_share_full_points), color = Muted)
                    if (host) {
                        OutlinedButton(onClick = { configure = true }, enabled = enabled) { Text(words(R.string.ui_edit_room_rules)) }
                        SettingSwitch(words(R.string.ui_lock_room), words(R.string.ui_prevent_new_players_from_joining), room.locked) { if (enabled && state.connection == Connection.LIVE) model.command(RoomAction.Lock(it)) }
                    }
                }
            } else {
                val table = room.toTable(state.marks)
                if (table != null && table.tickets.isNotEmpty()) {
                    TablePlay(table, preferences, model::mark, model::repeatCall, state.winMoment, model::dismissWin)
                    if (table.finished) OnlineResults(table)
                }
            }
            Text(words(R.string.ui_the_room_continues_when_you_leave_the_app), color = Muted, style = MaterialTheme.typography.bodySmall)
            if (room.phase != RoomPhase.ACTIVE) TextButton(onClick = { leave = true }, enabled = enabled && state.connection == Connection.LIVE) { Text(words(R.string.ui_leave_room)) }
        }
        OutlinedButton(onClick = { history = !history }, modifier = Modifier.fillMaxWidth()) { Text(if (history) words(R.string.ui_hide_online_history) else words(R.string.ui_online_history, state.history.size)) }
        if (history) state.history.forEach { item ->
            val game = item.round ?: return@forEach
            TextButton(onClick = { selectedHistory = game.id }) { Text(words(R.string.ui_calls, item.code, game.called.size, words.status(game.status))) }
        }
        if (room == null) TextButton(onClick = { logout = true }, enabled = enabled) { Text(words(R.string.ui_sign_out_of_online_play)) }
    }
    if (state.name != null && !state.storageFailure) TextButton(onClick = { deleteProfile = true }, enabled = !state.busy && !state.pending) { Text(words(R.string.ui_delete_online_profile)) }
    if (configure && room != null && host && room.phase == RoomPhase.LOBBY) RoomSettingsEditor(room,
        enabled && state.connection == Connection.LIVE, save = { model.command(RoomAction.Configure(it)); configure = false }, dismiss = { configure = false })
    state.history.firstOrNull { it.round?.id == selectedHistory }?.toTable(emptyMap())?.let { table ->
        AlertDialog(onDismissRequest = { selectedHistory = null }, title = { Text(words(R.string.ui_saved_online_results)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { OnlineResults(table); RuleList(table) }
        }, confirmButton = { TextButton(onClick = { selectedHistory = null }) { Text(words(R.string.ui_back)) } })
    }
    if (reset) ConfirmOnline(words(R.string.ui_reset_online_data_2), words(R.string.ui_this_removes_your_local_online_profile_cached_history), words(R.string.ui_reset_online_data), { reset = false; model.resetLocalData() }) { reset = false }
    if (deleteProfile) ConfirmOnline(words(R.string.ui_delete_your_online_profile), words(R.string.ui_this_permanently_removes_your_service_profile_and_access), words(R.string.ui_delete_profile_permanently), { deleteProfile = false; model.deleteProfile() }, dismissLabel = words(R.string.ui_keep_profile)) { deleteProfile = false }
    if (logout) ConfirmOnline(words(R.string.ui_sign_out), words(R.string.ui_your_online_session_will_be_revoked_this_device), words(R.string.ui_sign_out_2), { logout = false; model.logout() }) { logout = false }
    if (leave) ConfirmOnline(words(R.string.ui_leave_this_room), words(R.string.ui_your_completed_results_stay_in_online_history_you), words(R.string.ui_leave_room), { leave = false; model.command(RoomAction.Leave) }) { leave = false }
    state.error?.let { message -> AlertDialog(onDismissRequest = model::clearError, title = { Text(words(R.string.ui_online_play)) }, text = { Text(words.message(message)) }, confirmButton = { TextButton(onClick = model::clearError) { Text(words(R.string.ui_got_it)) } }) }
}

@Composable
fun OnlineControls(state: OnlineUiState, model: OnlineViewModel) {
    val words = gameText()
    if (state.deletingProfile) return
    val room = state.room ?: return
    val me = room.members.firstOrNull { it.playerId == state.playerId } ?: return
    val enabled = state.connection == Connection.LIVE && !state.pending && !state.busy && !state.sessionExpired && !state.storageFailure
    val host = room.hostId == state.playerId
    val enoughTickets = room.members.size * room.options.game.ticketsPerPlayer >= room.options.game.prizes.count { it.isRankedHouse }.coerceAtLeast(1)
    var end by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(room.nextDrawAt, room.serverTime) {
        seconds = ((room.nextDrawAt?.minus(room.serverTime) ?: 0) / 1000).toInt().coerceAtLeast(0)
        while (seconds > 0) { delay(1000); seconds-- }
    }
    Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (room.phase) {
            RoomPhase.LOBBY -> {
                PrimaryAction(if (me.ready) words(R.string.ui_i_m_not_ready_yet) else words(R.string.ui_i_m_ready), enabled = enabled) { model.command(RoomAction.Ready(!me.ready)) }
                if (!enoughTickets) Text(words(R.string.ui_these_house_prizes_need_more_tickets_add_players), color = Saffron)
                if (host) PrimaryAction(words(R.string.ui_start_online_round), enabled = enabled && enoughTickets && room.members.size >= 2 && room.members.all { it.ready && it.connected }) { model.command(RoomAction.Start) }
                else Text(words(R.string.ui_the_host_starts_when_everyone_is_ready), color = Muted)
            }
            RoomPhase.ACTIVE -> {
                Text(room.round?.called?.lastOrNull()?.let { words(R.string.ui_latest_number_called, it, room.round!!.called.size) } ?: words(R.string.ui_your_tickets_are_ready), color = Muted)
                Text(when {
                    state.connection != Connection.LIVE -> words(R.string.ui_waiting_for_connection_the_room_may_continue)
                    room.round?.status == RoundStatus.PAUSED -> words(R.string.ui_calling_paused_by_the_host)
                    !room.options.automaticCalling -> words(R.string.ui_the_host_controls_the_next_call)
                    seconds > 0 -> words(R.string.ui_next_call_in_about_s, seconds)
                    else -> words(R.string.ui_waiting_for_the_next_confirmed_call)
                }, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (host) {
                    if (room.round?.status == RoundStatus.PAUSED) PrimaryAction(words(R.string.ui_resume_online_calling), enabled = enabled) { model.command(RoomAction.Resume) }
                    else {
                        if (!room.options.automaticCalling) PrimaryAction(words(R.string.ui_call_next_online_number), enabled = enabled) { model.command(RoomAction.Draw) }
                        TextButton(onClick = { model.command(RoomAction.Pause) }, enabled = enabled) { Text(words(R.string.ui_pause_online_calling)) }
                    }
                    TextButton(onClick = { end = true }, enabled = enabled) { Text(words(R.string.ui_end_online_round)) }
                }
            }
            RoomPhase.FINISHED -> if (host) PrimaryAction(words(R.string.ui_set_up_rematch), enabled = enabled) { model.command(RoomAction.Rematch) } else Text(words(R.string.ui_round_complete_waiting_for_the_host_s_rematch), color = Jade)
            RoomPhase.CLOSED -> Text(words(R.string.ui_this_room_has_closed), color = Muted)
        }
    }
    if (end) ConfirmOnline(words(R.string.ui_end_the_online_round), words(R.string.ui_everyone_will_see_a_cancelled_round_with_the), words(R.string.ui_end_online_round), { end = false; model.command(RoomAction.End) }) { end = false }
}

@Composable
private fun OnlineResults(table: TableRound) {
    val words = gameText()
    val context = LocalContext.current
    var sharing by remember { mutableStateOf(false) }
    GameCard {
        Eyebrow(if (table.status == RoundStatus.CANCELLED) words(R.string.ui_results_so_far) else words(R.string.ui_a_round_of_applause))
        table.players.sortedByDescending { table.score(it.id) }.forEach { player ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AvatarBadge(player.avatar)
                Text(words(R.string.ui_points_2, player.name, table.score(player.id)), color = Jade, modifier = Modifier.weight(1f))
            }
        }
        Text(words(R.string.ui_calls_verified_prizes, table.called.size, table.awards.size + table.customAwards.size), color = Muted)
        Text(words(R.string.ui_draw_commitment_checked_against_the_revealed_order), color = Muted, style = MaterialTheme.typography.bodySmall)
        if (table.status == RoundStatus.COMPLETED) Text(words(R.string.ui_this_round_counts_toward_your_online_badges_find), color = Jade)
        OutlinedButton(onClick = { sharing = true }) { Text(words(R.string.ui_share_online_results)) }
    }
    if (sharing) ShareResults(table, { sharing = false }) { message -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }, words(R.string.ui_share_results))) }
}

@Composable
private fun ConfirmOnline(title: String, message: String, action: String, confirm: () -> Unit, dismissLabel: String? = null, dismiss: () -> Unit) {
    val words = gameText()
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = confirm) { Text(action) } }, dismissButton = { TextButton(onClick = dismiss) { Text(dismissLabel ?: words(R.string.ui_keep_playing)) } })
}

@Composable
private fun RoomSettingsEditor(room: RoomView, enabled: Boolean, save: (RoomOptions) -> Unit, dismiss: () -> Unit) {
    val words = gameText()
    val game = room.options.game
    var rawDraft by rememberSaveable(room.roomId) { mutableStateOf(WireJson.encodeToString(SetupDraft(mode = GameMode.ONLINE, names = "", bots = 0,
        tickets = game.ticketsPerPlayer, assisted = game.assistedMarking, prizes = game.prizes.filterNot { it.isRankedHouse || it == Prize.FULL_HOUSE },
        houses = game.prizes.count { it.isRankedHouse }.coerceAtLeast(1), playAllNumbers = game.playAllNumbers, customPrizes = game.customPrizes))) }
    val draft = remember(rawDraft) { WireJson.decodeFromString<SetupDraft>(rawDraft) }
    val update: (SetupDraft) -> Unit = { rawDraft = WireJson.encodeToString(it) }
    var automatic by rememberSaveable { mutableStateOf(room.options.automaticCalling) }
    var interval by rememberSaveable { mutableIntStateOf(room.options.intervalSeconds) }
    var capacity by rememberSaveable { mutableIntStateOf(room.options.capacity) }
    var rawRule by rememberSaveable { mutableStateOf<String?>(null) }
    var originalRule by rememberSaveable { mutableStateOf<String?>(null) }
    val rule = remember(rawRule) { rawRule?.let { WireJson.decodeFromString<CustomRuleDraft>(it) } }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogSystemBars()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TextButton(onClick = dismiss) { Text(words(R.string.ui_lobby)) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(words(R.string.ui_your_room_your_rules), style = MaterialTheme.typography.headlineMedium)
                    GameCard {
                        Text(words(R.string.ui_tickets_per_player))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..6).forEach { n -> FilterChip(draft.tickets == n, { update(draft.copy(tickets = n)) }, label = { Text("$n") }, modifier = Modifier.testTag("online-tickets-$n")) } }
                        SettingSwitch(words(R.string.ui_help_with_marking), words(R.string.ui_automatically_dab_called_numbers), draft.assisted) { update(draft.copy(assisted = it)) }
                        SettingSwitch(words(R.string.ui_automatic_online_calling), words(R.string.ui_the_server_keeps_time_for_everyone), automatic) { automatic = it }
                        if (automatic) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 15, 20, 30).forEach { n -> FilterChip(interval == n, { interval = n }, label = { Text(words(R.string.seconds_short, n)) }) } }
                        Text(words(R.string.ui_maximum_players))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(2, 4, 8, 16, 32).forEach { n -> FilterChip(capacity == n, { capacity = n }, enabled = n >= room.members.size, label = { Text("$n") }) } }
                    }
                    RoundRules(draft, update, edit = { prize ->
                        rawRule = WireJson.encodeToString(prize?.let(CustomRuleDraft::from) ?: CustomRuleDraft()); originalRule = rawRule
                    }, remove = { id -> update(draft.copy(customPrizes = draft.customPrizes.filterNot { it.id == id })) })
                    draft.errors.forEach { Text(words.message(it), color = Coral) }
                }
                Column(Modifier.padding(16.dp)) { PrimaryAction(words(R.string.ui_save_room_rules), enabled = enabled && draft.errors.isEmpty() && capacity >= room.members.size) { save(RoomOptions(draft.settings(), capacity, interval, automatic)) } }
            }
        }
    }
    if (rule != null) CustomRuleEditor(rule, originalRule?.let { WireJson.decodeFromString(it) }, draft.tickets,
        update = { rawRule = WireJson.encodeToString(it) }, save = {
            val prize = rule.prize(draft.tickets)
            update(draft.copy(customPrizes = if (draft.customPrizes.any { it.id == prize.id }) draft.customPrizes.map { if (it.id == prize.id) prize else it } else draft.customPrizes + prize))
            rawRule = null; originalRule = null
        }, cancel = { rawRule = null; originalRule = null })
}
