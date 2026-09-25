package io.github.sbshrey.tambola.game.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.*
import io.github.sbshrey.tambola.game.data.SavedRound
import java.text.DateFormat
import java.util.Date

@Composable
fun TambolaApp(state: GameUiState, model: GameViewModel) {
    BackHandler(state.screen != Screen.HOME) { model.navigate(Screen.HOME) }
    val pageScroll = key(state.screen, state.round?.id) { rememberScrollState() }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.screen != Screen.HOME) TextButton(onClick = { model.navigate(Screen.HOME) }) { Text("‹ Home") }
                else Box(Modifier.size(36.dp).background(Saffron, CircleShape), contentAlignment = Alignment.Center) { Text("T", fontWeight = FontWeight.Black, color = Ink, fontSize = 22.sp) }
                Text(if (state.screen == Screen.HOME) "  tambola together" else state.screen.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.screen != Screen.SETTINGS) TextButton(onClick = { model.navigate(Screen.SETTINGS) }) { Text("Settings", fontSize = 12.sp) }
            }
            if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else Column(Modifier.weight(1f).verticalScroll(pageScroll).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(if (state.screen == Screen.GAME) 12.dp else 20.dp)) {
                when (state.screen) {
                    Screen.HOME -> Home(state, model)
                    Screen.SETUP -> Setup(state, model)
                    Screen.GAME -> state.round?.let { GameTable(it, state, model) }
                    Screen.RESULTS -> (state.viewedResult ?: state.round)?.let { Results(it, model) }
                    Screen.HISTORY -> History(state.history, model)
                    Screen.SETTINGS -> Settings(state, model)
                }
            }
            if (!state.loading && state.screen == Screen.GAME) state.round?.let { GameControls(it, state, model) }
        }
    }
    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text("A quick heads-up") }, text = { Text(error) }, confirmButton = { TextButton(onClick = model::clearError) { Text("Got it") } }) }
}

@Composable
private fun Home(state: GameUiState, model: GameViewModel) {
    Eyebrow("GOOD COMPANY. GREAT NUMBERS.")
    Text("Make room\nfor a little joy.", style = MaterialTheme.typography.headlineLarge)
    Text("Your tickets, your people, one happy game night.", color = Muted)
    Box(Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF314B49), Panel))), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(7 to Jade, 22 to Saffron, 90 to Coral).forEachIndexed { index, (number, color) ->
                Box(Modifier.size(if (index == 1) 94.dp else 68.dp).background(color, CircleShape).border(7.dp, Color.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(number.toString(), color = Ink, fontSize = if (index == 1) 36.sp else 26.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Text("A FULL HOUSE OF POSSIBILITIES", color = Jade.copy(alpha = .8f), fontSize = 9.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
    state.round?.takeIf { !it.finished }?.let { round ->
        GameCard {
            Eyebrow("YOUR TABLE IS WAITING", Saffron)
            Text("${round.called.size} numbers called · ${round.players.size} players", style = MaterialTheme.typography.titleMedium)
            PrimaryAction("Resume round") { model.navigate(Screen.GAME) }
        }
    }
    GameCard {
        Eyebrow("PLAY YOUR WAY")
        Text("A little me time", style = MaterialTheme.typography.titleLarge)
        Text("Practice at your pace, or invite some friendly computer players.", color = Muted)
        PrimaryAction("Play solo") { model.setup(GameMode.PRACTICE) }
        HorizontalDivider(color = Muted.copy(alpha = .2f))
        Text("Bring everyone together", style = MaterialTheme.typography.titleLarge)
        Text("Pass one phone around. Every player gets their own digital tickets.", color = Muted)
        OutlinedButton(onClick = { model.setup(GameMode.FAMILY) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) { Text("Play on one device") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { model.navigate(Screen.HISTORY) }, modifier = Modifier.weight(1f)) { Text("Your rounds") }
        OutlinedButton(onClick = { model.navigate(Screen.SETTINGS) }, modifier = Modifier.weight(1f)) { Text("How to play") }
    }
    Text("Offline alpha · Private online rooms are in development.", color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Setup(state: GameUiState, model: GameViewModel) {
    val family = state.setupMode == GameMode.FAMILY
    var names by rememberSaveable(family) { mutableStateOf(if (family) "Asha\nBina" else "You") }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var bots by rememberSaveable { mutableIntStateOf(2) }
    var assisted by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(Prize.defaults) }
    var confirm by remember { mutableStateOf(false) }
    val submit = { model.create(names.lines().filter { it.isNotBlank() }, count, assisted, selected, if (family) 0 else bots) }
    Eyebrow(if (family) "A TABLE FOR EVERYONE" else "YOUR OWN LITTLE GAME NIGHT")
    Text(if (family) "Who's playing?" else "Let's make it yours.", style = MaterialTheme.typography.headlineMedium)
    GameCard {
        OutlinedTextField(value = names, onValueChange = { if (it.length <= 330) names = it }, label = { Text(if (family) "Players · one name per line" else "Your name") }, supportingText = { Text(if (family) "2–8 players, up to 40 characters per name" else "Up to 40 characters") }, minLines = if (family) 3 else 1, singleLine = !family, modifier = Modifier.fillMaxWidth())
        Text("Tickets per player", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..6).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") }) } }
        if (!family) {
            Text("Computer players", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (0..5).forEach { n -> FilterChip(selected = bots == n, onClick = { bots = n }, label = { Text(if (n == 0) "Just me" else "$n") }) } }
            Text("Computer players get the same calls and rules as you.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Help with marking", style = MaterialTheme.typography.titleMedium); Text("Automatically dab called numbers.", color = Muted, style = MaterialTheme.typography.bodyMedium) }
            Switch(checked = assisted, onCheckedChange = { assisted = it })
        }
    }
    GameCard {
        Text("Pick your prizes", style = MaterialTheme.typography.titleLarge)
        Text("Free play, happy wins. Ties receive equal points. The app verifies every ticket as numbers are called.", color = Muted)
        Prize.entries.filter { !it.isRankedHouse }.forEach { prize ->
            Row(Modifier.fillMaxWidth().clickable(enabled = prize != Prize.FULL_HOUSE) { selected = if (prize in selected) selected - prize else selected + prize }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = prize in selected, onCheckedChange = if (prize == Prize.FULL_HOUSE) null else { checked -> selected = if (checked) selected + prize else selected - prize })
                Column(Modifier.weight(1f)) { Text(prize.title, style = MaterialTheme.typography.titleMedium); Text(prize.explanation, color = Muted, style = MaterialTheme.typography.bodySmall) }
                Text("${prize.points}", color = Saffron, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text("This round ends at the first full house. Same-call winners share the celebration.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
    PrimaryAction("Deal the tickets") { if (state.round?.finished == false) confirm = true else submit() }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Start a fresh round?") }, text = { Text("Your current round will be saved as cancelled. Its calls and results stay in history.") }, confirmButton = { TextButton(onClick = { confirm = false; submit() }) { Text("Start new round") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep current round") } })
}

@Composable
private fun GameTable(round: Round, state: GameUiState, model: GameViewModel) {
    var selected by rememberSaveable(round.id) { mutableIntStateOf(0) }
    var board by remember { mutableStateOf(false) }
    var claims by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    val index = selected.coerceIn(round.tickets.indices)
    Eyebrow(if (round.finished) "ROUND ${if (round.status == RoundStatus.COMPLETED) "COMPLETE" else "CANCELLED"}" else if (round.status == RoundStatus.PAUSED) "TAKE A BREATHER · PAUSED" else "LET THE GOOD TIMES ROLL")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(120.dp)) { NumberBall(round.latest, state.preferences.reducedMotion, compact = true) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (round.latest == null) "Ready when you are" else "Call ${round.called.size} of 90", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { board = true }) { Text("Number board") }
            TextButton(onClick = model::repeatCall, enabled = round.latest != null) { Text("Hear again") }
        }
    }
    LinearProgressIndicator(progress = { round.called.size / 90f }, modifier = Modifier.fillMaxWidth(), color = Jade, trackColor = Panel)
    if (round.called.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("RECENT", fontSize = 10.sp, color = Muted, modifier = Modifier.weight(1f))
            round.called.takeLast(5).reversed().forEachIndexed { i, n -> Box(Modifier.size(34.dp).background(if (i == 0) Saffron else Panel, CircleShape), contentAlignment = Alignment.Center) { Text("$n", fontSize = 14.sp, color = if (i == 0) Ink else Ivory, fontWeight = FontWeight.Bold) } }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Your table", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text("${round.tickets.size} tickets", color = Muted, fontSize = 12.sp)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        round.tickets.forEachIndexed { i, ticket -> FilterChip(selected = index == i, onClick = { selected = i }, label = { Text(round.players.first { it.id == ticket.playerId }.name + " · ${ticket.id.substringAfterLast('-')}") }) }
    }
    TicketCard(round.tickets[index], round, state.preferences.haptics, model::toggleMark)
    Text("Amber outline: called · Green: marked. Tap Mark ticket for large, comfortable number buttons.", color = Muted, style = MaterialTheme.typography.bodySmall)
    PrimaryAction("Check claims · ${round.awards.size} verified") { claims = true }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (round.settings.mode == GameMode.PRACTICE && !round.finished) TextButton(onClick = model::undo, enabled = round.called.isNotEmpty()) { Text("Undo last call") }
        if (!round.finished) TextButton(onClick = { cancel = true }) { Text("End round") }
    }
    if (board) AlertDialog(onDismissRequest = { board = false }, title = { Text("The number board") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${round.called.size} called · ${90 - round.called.size} to go", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                (1..90).forEach { n -> Box(Modifier.size(36.dp).background(if (n == round.latest) Saffron else if (n in round.called) Jade else Ink, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("$n", color = if (n in round.called) Ink else Muted, fontWeight = FontWeight.Bold) } }
            }
            Text("Call history", style = MaterialTheme.typography.titleMedium)
            Text(round.called.joinToString(" → ").ifEmpty { "No numbers yet" }, color = Muted)
        }
    }, confirmButton = { TextButton(onClick = { board = false }) { Text("Back to table") } })
    if (claims) AlertDialog(onDismissRequest = { claims = false }, title = { Text("Fair wins, happy faces") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Winners are verified from called numbers, even if someone forgets to mark. Tickets completing a prize on the same call tie.", color = Muted)
            RuleList(round)
        }
    }, confirmButton = { TextButton(onClick = { claims = false }) { Text("Back to game") } })
    if (cancel) AlertDialog(onDismissRequest = { cancel = false }, title = { Text("End this round early?") }, text = { Text("This round will be saved as cancelled with the results so far.") }, confirmButton = { TextButton(onClick = { cancel = false; model.finish() }) { Text("End round") } }, dismissButton = { TextButton(onClick = { cancel = false }) { Text("Keep playing") } })
}

@Composable
private fun GameControls(round: Round, state: GameUiState, model: GameViewModel) {
    Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(round.latest?.let { "Latest number: $it · ${round.called.size} called" } ?: "Your tickets are ready", color = Muted, style = MaterialTheme.typography.bodySmall)
        when {
            round.finished -> PrimaryAction("See round results") { model.navigate(Screen.RESULTS) }
            round.status == RoundStatus.PAUSED -> PrimaryAction("Resume calling") { model.resume() }
            else -> {
                PrimaryAction(if (state.auto) "Call next now" else "Call next number") { model.draw() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = model::auto, modifier = Modifier.weight(1f)) { Text(if (state.auto) "Stop auto" else "Auto · ${state.preferences.interval}s") }
                    TextButton(onClick = model::pause, modifier = Modifier.weight(1f)) { Text("Pause") }
                }
            }
        }
    }
}

@Composable
private fun Results(round: Round, model: GameViewModel) {
    val context = LocalContext.current
    Eyebrow(if (round.status == RoundStatus.CANCELLED) "RESULTS SO FAR" else "THAT WAS A LOVELY ROUND")
    Text(if (round.status == RoundStatus.CANCELLED) "Until next time." else "A round of applause!", style = MaterialTheme.typography.headlineLarge)
    Text("${round.called.size} calls · ${round.awards.size} prizes · ${round.players.size} players", color = Muted)
    GameCard {
        round.players.sortedByDescending { round.score(it.id) }.forEachIndexed { i, player ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(if (i == 0) Saffron else Ink, CircleShape), contentAlignment = Alignment.Center) { Text(player.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = if (i == 0) Ink else Ivory) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(player.name, style = MaterialTheme.typography.titleMedium); if (player.computer) Text("Computer player", color = Muted, fontSize = 12.sp) }
                Text("${round.score(player.id)} pts", color = Jade, fontWeight = FontWeight.Bold)
            }
        }
    }
    GameCard { Text("The winning moments", style = MaterialTheme.typography.titleLarge); RuleList(round) }
    PrimaryAction("Play another round") { model.setup(round.settings.mode) }
    OutlinedButton(onClick = {
        val message = buildString {
            append("Tambola Together · ${if (round.status == RoundStatus.CANCELLED) "Cancelled round" else "Round results"}\n${round.called.size} calls\n")
            round.players.sortedByDescending { round.score(it.id) }.forEach { append("${it.name}: ${round.score(it.id)} points\n") }
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }, "Share results"))
    }, modifier = Modifier.fillMaxWidth()) { Text("Share these results") }
}

@Composable
private fun History(history: List<SavedRound>, model: GameViewModel) {
    Eyebrow("A LITTLE BOOK OF GAME NIGHTS")
    Text("Your rounds", style = MaterialTheme.typography.headlineLarge)
    if (history.isEmpty()) GameCard { Text("Your first game night is waiting.", style = MaterialTheme.typography.titleMedium); Text("Completed rounds and scores will appear here.", color = Muted) }
    history.take(50).forEach { saved ->
        val round = remember(saved) { runCatching { RoundCodec.decode(saved.payload) }.getOrNull() }
        GameCard {
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(saved.createdAt)), style = MaterialTheme.typography.titleMedium)
            Text(round?.let { "${it.settings.mode.name.lowercase().replaceFirstChar { c -> c.uppercase() }} · ${it.called.size} calls · ${it.status.name.lowercase()}" } ?: "Saved record needs attention", color = Muted)
            if (saved.completed) TextButton(onClick = { model.openHistory(saved) }, enabled = round != null) { Text("View results") }
        }
    }
}

@Composable
private fun Settings(state: GameUiState, model: GameViewModel) {
    val prefs = state.preferences
    var delete by remember { mutableStateOf(false) }
    Eyebrow("MAKE YOURSELF COMFORTABLE")
    Text("Just your style.", style = MaterialTheme.typography.headlineLarge)
    GameCard {
        Text("The voice of your game", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("en" to "English", "hi" to "हिन्दी", "hinglish" to "Hinglish").forEach { (id, label) -> FilterChip(selected = prefs.language == id, onClick = { model.updatePreferences(prefs.copy(language = id)) }, label = { Text(label) }) } }
        SettingSwitch("Number voice", "AI-generated recordings, available offline.", prefs.voice) { model.updatePreferences(prefs.copy(voice = it)) }
        SettingSwitch("Gentle haptics", "A little feedback when you mark a number.", prefs.haptics) { model.updatePreferences(prefs.copy(haptics = it)) }
        SettingSwitch("Reduced motion", "Keep number reveals still.", prefs.reducedMotion) { model.updatePreferences(prefs.copy(reducedMotion = it)) }
        Text("Automatic calling pace", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 15, 20, 30).forEach { seconds -> FilterChip(selected = prefs.interval == seconds, onClick = { model.updatePreferences(prefs.copy(interval = seconds)) }, label = { Text("${seconds}s") }) } }
        Text("Calls wait for the recording to finish. Leaving the app pauses offline calling.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
    GameCard {
        Text("Your first game, made easy", style = MaterialTheme.typography.titleLarge)
        listOf("1. Choose players, tickets, and prizes. Everyone can see the rules before play.", "2. Call numbers yourself or turn on automatic calling. Each number appears only once.", "3. Tap Mark ticket and dab the called numbers, or choose assisted marking before a round.", "4. Check claims to see verified winners. Same-call winners tie, and everyone gets the full points.", "5. The first full house finishes the round. Enjoy the results and play again.").forEach { Text(it, color = Muted) }
    }
    GameCard {
        Text("Your data stays here", style = MaterialTheme.typography.titleLarge)
        Text("This offline alpha stores names, tickets, calls, and results on this device. No sign-in, ads, analytics, or in-app purchases. Number recordings were generated with OpenAI. Artwork and motion in this build use native graphics.", color = Muted)
        OutlinedButton(onClick = { delete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all saved rounds") }
        Text("Tambola Together · 0.1.0 alpha\nOnline rooms, music, badges, and Hindi interface are still in development.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete saved rounds?") }, text = { Text("This removes your current game, player names, and all local round history. This cannot be undone. Sound and display settings will stay.") }, confirmButton = { TextButton(onClick = { delete = false; model.deleteHistory() }) { Text("Delete rounds") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Keep rounds") } })
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
