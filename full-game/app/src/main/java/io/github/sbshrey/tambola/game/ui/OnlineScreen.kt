package io.github.sbshrey.tambola.game.ui

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
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var configure by rememberSaveable { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var selectedHistory by rememberSaveable { mutableStateOf<String?>(null) }
    val room = state.room
    val host = room?.hostId == state.playerId
    val enabled = !state.busy && !state.pending && !state.sessionExpired && !state.storageFailure
    if (state.loading) { CircularProgressIndicator(); return }
    if (room?.round == null) {
        Eyebrow("A GAME NIGHT, ANYWHERE")
        Text("Your private table.", style = MaterialTheme.typography.headlineLarge)
    }
    if (!state.available) {
        GameCard { Text("Online rooms aren't available in this build yet."); Text("You can play solo or with everyone on one device from Home.", color = Muted) }
        return
    }
    if (state.storageFailure || state.sessionExpired) {
        GameCard {
            Text(if (state.sessionExpired) "Your online session has expired." else "Your saved online data needs attention.")
            Text("Resetting removes the online profile, cached tickets, and online history from this device. Offline rounds stay available. A new profile cannot reclaim the old profile's tickets.", color = Muted)
            OutlinedButton(onClick = { reset = true }) { Text("Reset online data") }
        }
    } else if (state.name == null) {
        GameCard {
            Text("What should your friends call you?", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { if (it.length <= 40) name = it }, label = { Text("Online display name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().testTag("online-name"))
            Text("Your name, tickets, calls and results are saved by the room service. Room members see your name and wins; your ticket numbers stay private. Free social play with points only.", color = Muted)
            PrimaryAction(if (state.busy) "Opening your profile…" else "Continue online", enabled = enabled && name.isNotBlank()) { focus.clearFocus(); model.register(name) }
        }
    } else {
        Text("Playing as ${state.name}", color = Jade)
        if (state.pending) GameCard {
            Text("An action is waiting for confirmation", style = MaterialTheme.typography.titleMedium)
            Text("Retry safely to recover the original result. Other changes wait until this action is resolved.", color = Muted)
            PrimaryAction(if (state.busy) "Checking…" else "Retry pending action", enabled = !state.busy) { model.retry() }
        }
        if (room == null) {
            GameCard {
                Text("Bring your people together", style = MaterialTheme.typography.titleLarge)
                PrimaryAction("Create private room", enabled = enabled) { model.create() }
                HorizontalDivider()
                OutlinedTextField(code, { if (it.length <= 8) code = it.uppercase() }, label = { Text("8-character room code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().testTag("room-code-input"))
                OutlinedButton(onClick = { focus.clearFocus(); model.join(code) }, enabled = enabled && code.trim().length == 8, modifier = Modifier.fillMaxWidth()) { Text("Join room") }
            }
        } else {
            GameCard {
                Eyebrow(if (host) "YOU'RE HOSTING" else "YOU'RE INVITED", Saffron)
                SelectionContainer { Text(room.code, style = if (room.phase == RoomPhase.LOBBY) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("room-code")) }
                Text(when (state.connection) {
                    Connection.LIVE -> "Connected · all calls confirmed by the server"
                    Connection.CONNECTING -> "Connecting to your room…"
                    Connection.RECONNECTING -> "Reconnecting · showing your last confirmed table"
                    else -> "Disconnected · showing your last confirmed table"
                }, color = if (state.connection == Connection.LIVE) Jade else Saffron)
                if (state.connection != Connection.LIVE) TextButton(onClick = model::reconnect) { Text("Reconnect now") }
                if (room.phase == RoomPhase.LOBBY) TextButton(onClick = {
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Join my private Tambola Together room: ${room.code}")
                    }, "Invite to room")) }
                }) { Text("Share room code") }
                Text("${room.members.size}/${room.options.capacity} players · ${room.options.game.ticketsPerPlayer} ticket(s) each", color = Muted)
            }
            if (room.phase == RoomPhase.LOBBY) {
                GameCard {
                    Text("Who's at the table?", style = MaterialTheme.typography.titleLarge)
                    room.members.forEach { member ->
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(member.displayName + if (member.playerId == room.hostId) " · host" else "", fontWeight = FontWeight.SemiBold)
                                Text((if (member.ready) "Ready" else "Choosing a seat") + if (member.connected) " · connected" else " · away", color = if (member.ready) Jade else Muted)
                            }
                            if (host && member.playerId != state.playerId) TextButton(onClick = { model.command(RoomAction.Remove(member.playerId)) }, enabled = enabled && state.connection == Connection.LIVE) { Text("Remove") }
                        }
                    }
                }
                GameCard {
                    Text("The rules for this round", style = MaterialTheme.typography.titleLarge)
                    Text(room.options.game.endExplanation(), color = Saffron)
                    Text(if (room.options.game.assistedMarking) "Assisted marking for everyone." else "Mark your own called numbers. Wins are checked automatically.", color = Muted)
                    Text(if (room.options.automaticCalling) "The server calls a number every ${room.options.intervalSeconds} seconds." else "The host calls each next number.", color = Muted)
                    room.options.game.prizes.forEach { Text("${it.title} · ${it.points} pts\n${it.explanation}") }
                    room.options.game.customPrizes.forEach { Text("${it.title} · ${it.points} pts\n${it.describe()}") }
                    Text("Ties on the same call share full points. Ready means you're happy with these rules. Changing rules clears everyone's ready status.", color = Muted)
                    if (host) {
                        OutlinedButton(onClick = { configure = true }, enabled = enabled) { Text("Edit room rules") }
                        SettingSwitch("Lock room", "Prevent new players from joining.", room.locked) { if (enabled && state.connection == Connection.LIVE) model.command(RoomAction.Lock(it)) }
                    }
                }
            } else {
                val table = room.toTable(state.marks)
                if (table != null && table.tickets.isNotEmpty()) {
                    TablePlay(table, preferences, model::mark, model::repeatCall)
                    if (table.finished) OnlineResults(table)
                }
            }
            Text("The room continues when you leave the app. If the host stays away, another connected player becomes host. All players can reconnect to their own tickets.", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (room.phase != RoomPhase.ACTIVE) TextButton(onClick = { leave = true }, enabled = enabled && state.connection == Connection.LIVE) { Text("Leave room") }
        }
        OutlinedButton(onClick = { history = !history }, modifier = Modifier.fillMaxWidth()) { Text(if (history) "Hide online history" else "Online history · ${state.history.size}") }
        if (history) state.history.forEach { item ->
            val game = item.round ?: return@forEach
            TextButton(onClick = { selectedHistory = game.id }) { Text("${item.code} · ${game.called.size} calls · ${game.status.name.lowercase()}") }
        }
        if (room == null) TextButton(onClick = { logout = true }, enabled = enabled) { Text("Sign out of online play") }
    }
    if (configure && room != null && host && room.phase == RoomPhase.LOBBY) RoomSettingsEditor(room,
        enabled && state.connection == Connection.LIVE, save = { model.command(RoomAction.Configure(it)); configure = false }, dismiss = { configure = false })
    state.history.firstOrNull { it.round?.id == selectedHistory }?.toTable(emptyMap())?.let { table ->
        AlertDialog(onDismissRequest = { selectedHistory = null }, title = { Text("Saved online results") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { OnlineResults(table); RuleList(table) }
        }, confirmButton = { TextButton(onClick = { selectedHistory = null }) { Text("Back") } })
    }
    if (reset) ConfirmOnline("Reset online data?", "This removes your local online profile and cached history. It does not delete server records, which expire under the room service's retention policy.", "Reset online data", { reset = false; model.resetLocalData() }) { reset = false }
    if (logout) ConfirmOnline("Sign out?", "Your online session will be revoked. This device's online tickets and history will be removed. Offline rounds stay here.", "Sign out", { logout = false; model.logout() }) { logout = false }
    if (leave) ConfirmOnline("Leave this room?", "Your completed results stay in online history. You can join another room after leaving.", "Leave room", { leave = false; model.command(RoomAction.Leave) }) { leave = false }
    state.error?.let { message -> AlertDialog(onDismissRequest = model::clearError, title = { Text("Online play") }, text = { Text(message) }, confirmButton = { TextButton(onClick = model::clearError) { Text("Got it") } }) }
}

@Composable
fun OnlineControls(state: OnlineUiState, model: OnlineViewModel) {
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
                PrimaryAction(if (me.ready) "I'm not ready yet" else "I'm ready", enabled = enabled) { model.command(RoomAction.Ready(!me.ready)) }
                if (!enoughTickets) Text("These house prizes need more tickets. Add players or increase tickets per player.", color = Saffron)
                if (host) PrimaryAction("Start online round", enabled = enabled && enoughTickets && room.members.size >= 2 && room.members.all { it.ready && it.connected }) { model.command(RoomAction.Start) }
                else Text("The host starts when everyone is ready.", color = Muted)
            }
            RoomPhase.ACTIVE -> {
                Text(room.round?.called?.lastOrNull()?.let { "Latest number: $it · ${room.round!!.called.size} called" } ?: "Your tickets are ready", color = Muted)
                Text(when {
                    state.connection != Connection.LIVE -> "Waiting for connection · the room may continue"
                    room.round?.status == RoundStatus.PAUSED -> "Calling paused by the host"
                    !room.options.automaticCalling -> "The host controls the next call"
                    seconds > 0 -> "Next call in about ${seconds}s"
                    else -> "Waiting for the next confirmed call"
                }, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (host) {
                    if (room.round?.status == RoundStatus.PAUSED) PrimaryAction("Resume online calling", enabled = enabled) { model.command(RoomAction.Resume) }
                    else {
                        if (!room.options.automaticCalling) PrimaryAction("Call next online number", enabled = enabled) { model.command(RoomAction.Draw) }
                        TextButton(onClick = { model.command(RoomAction.Pause) }, enabled = enabled) { Text("Pause online calling") }
                    }
                    TextButton(onClick = { end = true }, enabled = enabled) { Text("End online round") }
                }
            }
            RoomPhase.FINISHED -> if (host) PrimaryAction("Set up rematch", enabled = enabled) { model.command(RoomAction.Rematch) } else Text("Round complete · waiting for the host's rematch", color = Jade)
            RoomPhase.CLOSED -> Text("This room has closed.", color = Muted)
        }
    }
    if (end) ConfirmOnline("End the online round?", "Everyone will see a cancelled round with the results so far.", "End online round", { end = false; model.command(RoomAction.End) }) { end = false }
}

@Composable
private fun OnlineResults(table: TableRound) {
    val context = LocalContext.current
    var sharing by remember { mutableStateOf(false) }
    GameCard {
        Eyebrow(if (table.status == RoundStatus.CANCELLED) "RESULTS SO FAR" else "A ROUND OF APPLAUSE")
        table.players.sortedByDescending { table.score(it.id) }.forEach { Text("${it.name} · ${table.score(it.id)} points", color = Jade) }
        Text("${table.called.size} calls · ${table.awards.size + table.customAwards.size} verified prizes", color = Muted)
        Text("Draw commitment checked against the revealed order.", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { sharing = true }) { Text("Share online results") }
    }
    if (sharing) ShareResults(table, { sharing = false }) { message -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }, "Share results")) }
}

@Composable
private fun ConfirmOnline(title: String, message: String, action: String, confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = confirm) { Text(action) } }, dismissButton = { TextButton(onClick = dismiss) { Text("Keep playing") } })
}

@Composable
private fun RoomSettingsEditor(room: RoomView, enabled: Boolean, save: (RoomOptions) -> Unit, dismiss: () -> Unit) {
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
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TextButton(onClick = dismiss) { Text("‹ Lobby") }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Your room, your rules", style = MaterialTheme.typography.headlineMedium)
                    GameCard {
                        Text("Tickets per player")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..6).forEach { n -> FilterChip(draft.tickets == n, { update(draft.copy(tickets = n)) }, label = { Text("$n") }, modifier = Modifier.testTag("online-tickets-$n")) } }
                        SettingSwitch("Help with marking", "Automatically dab called numbers.", draft.assisted) { update(draft.copy(assisted = it)) }
                        SettingSwitch("Automatic online calling", "The server keeps time for everyone.", automatic) { automatic = it }
                        if (automatic) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 15, 20, 30).forEach { n -> FilterChip(interval == n, { interval = n }, label = { Text("${n}s") }) } }
                        Text("Maximum players")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(2, 4, 8, 16, 32).forEach { n -> FilterChip(capacity == n, { capacity = n }, enabled = n >= room.members.size, label = { Text("$n") }) } }
                    }
                    RoundRules(draft, update, edit = { prize ->
                        rawRule = WireJson.encodeToString(prize?.let(CustomRuleDraft::from) ?: CustomRuleDraft()); originalRule = rawRule
                    }, remove = { id -> update(draft.copy(customPrizes = draft.customPrizes.filterNot { it.id == id })) })
                    draft.errors.forEach { Text(it, color = Coral) }
                }
                Column(Modifier.padding(16.dp)) { PrimaryAction("Save room rules", enabled = enabled && draft.errors.isEmpty() && capacity >= room.members.size) { save(RoomOptions(draft.settings(), capacity, interval, automatic)) } }
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
