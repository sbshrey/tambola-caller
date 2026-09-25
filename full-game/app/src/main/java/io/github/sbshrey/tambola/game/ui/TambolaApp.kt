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
import java.text.DateFormat
import java.util.Date

@Composable
fun TambolaApp(state: GameUiState, model: GameViewModel, onlineState: OnlineUiState, online: OnlineViewModel) {
    BackHandler(state.screen != Screen.HOME && state.ruleDraft == null) { model.navigate(Screen.HOME) }
    val pageScroll = key(state.screen, state.round?.id,
        onlineState.room?.roomId.takeIf { state.screen == Screen.ONLINE },
        onlineState.room?.round?.id.takeIf { state.screen == Screen.ONLINE }) { rememberScrollState() }
    Surface(Modifier.fillMaxSize().testTag("app-background"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.screen != Screen.HOME) TextButton(onClick = { model.navigate(Screen.HOME) }) { Text("‹ Home") }
                else Box(Modifier.size(36.dp).background(Saffron, CircleShape), contentAlignment = Alignment.Center) { Text("T", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary, fontSize = 22.sp) }
                Text(if (state.screen == Screen.HOME) "  tambola together" else state.screen.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.screen != Screen.SETTINGS) TextButton(onClick = { model.navigate(Screen.SETTINGS) }) { Text("Settings", fontSize = 12.sp) }
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
    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text("A quick heads-up") }, text = { Text(error) }, confirmButton = { TextButton(onClick = model::clearError) { Text("Got it") } }) }
}

@Composable
private fun Home(state: GameUiState, model: GameViewModel) {
    Eyebrow("GOOD COMPANY. GREAT NUMBERS.")
    Text("Make room\nfor a little joy.", style = MaterialTheme.typography.headlineLarge)
    Text("Your tickets, your people, one happy game night.", color = Muted)
    if (!state.preferences.tutorialDismissed && !state.preferences.tutorialCompleted) GameCard {
        Eyebrow("NEW TO TAMBOLA?", Saffron)
        Text("Try a ticket. Make a call. Find your first win.", style = MaterialTheme.typography.titleMedium)
        PrimaryAction("Learn with a sample ticket") { model.navigate(Screen.TUTORIAL) }
        TextButton(onClick = { model.finishTutorial(false) }) { Text("Maybe later") }
    }
    GameNightArtwork()
    state.round?.takeIf { !it.finished }?.let { round ->
        GameCard {
            Eyebrow("YOUR TABLE IS WAITING", Saffron)
            Text(pluralStringResource(R.plurals.numbers_called, round.called.size, round.called.size) + " · " +
                pluralStringResource(R.plurals.player_count, round.players.size, round.players.size), style = MaterialTheme.typography.titleMedium)
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
        HorizontalDivider(color = Muted.copy(alpha = .2f))
        Text("Meet at a private table", style = MaterialTheme.typography.titleLarge)
        Text("Play together on your own phones with a room code.", color = Muted)
        OutlinedButton(onClick = { model.navigate(Screen.ONLINE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) { Text("Play online") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { model.navigate(Screen.HISTORY) }, modifier = Modifier.weight(1f)) { Text("Your rounds") }
        OutlinedButton(onClick = { model.navigate(Screen.TUTORIAL) }, modifier = Modifier.weight(1f)) { Text("How to play") }
    }
    OutlinedButton(onClick = { model.navigate(Screen.BADGES) }, modifier = Modifier.fillMaxWidth()) { Text("Your badges") }
    Text("Development alpha · Private rooms require the configured room service.", color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun GameTable(round: Round, state: GameUiState, model: GameViewModel) {
    var cancel by remember { mutableStateOf(false) }
    TablePlay(round.toTable(), state.preferences, model::toggleMark, model::repeatCall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (round.settings.mode == GameMode.PRACTICE && !round.finished) TextButton(onClick = model::undo, enabled = round.called.isNotEmpty()) { Text("Undo last call") }
        if (!round.finished) TextButton(onClick = { cancel = true }) { Text("End round") }
    }
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
    var sharing by remember { mutableStateOf(false) }
    Eyebrow(if (round.status == RoundStatus.CANCELLED) "RESULTS SO FAR" else "THAT WAS A LOVELY ROUND")
    Text(if (round.status == RoundStatus.CANCELLED) "Until next time." else "A round of applause!", style = MaterialTheme.typography.headlineLarge)
    val prizeCount = round.awards.size + round.customAwards.size
    Text(pluralStringResource(R.plurals.call_count, round.called.size, round.called.size) + " · " +
        pluralStringResource(R.plurals.prize_count, prizeCount, prizeCount) + " · " +
        pluralStringResource(R.plurals.player_count, round.players.size, round.players.size), color = Muted)
    GameCard {
        val topScore = round.players.maxOf { round.score(it.id) }
        round.players.sortedByDescending { round.score(it.id) }.forEach { player ->
            val leading = topScore > 0 && round.score(player.id) == topScore
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(if (leading) Saffron else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Text(player.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = if (leading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(player.name, style = MaterialTheme.typography.titleMedium); if (player.computer) Text("Computer player", color = Muted, fontSize = 12.sp) }
                Text("${round.score(player.id)} pts", color = Jade, fontWeight = FontWeight.Bold)
            }
        }
    }
    GameCard { Text("The winning moments", style = MaterialTheme.typography.titleLarge); RuleList(round.toTable()) }
    if (round.status == RoundStatus.COMPLETED) GameCard {
        Text("Another good memory", style = MaterialTheme.typography.titleLarge)
        Text("This completed round counts toward ${round.badgeMode().title.lowercase()} badges. Reopening it won't count it twice.", color = Muted)
        TextButton(onClick = { model.navigate(Screen.BADGES) }) { Text("See your badges") }
    }
    PrimaryAction("Play another round") { model.rematch(round) }
    OutlinedButton(onClick = { sharing = true }, modifier = Modifier.fillMaxWidth()) { Text("Share these results") }
    if (sharing) ShareResults(round.toTable(), onDismiss = { sharing = false }) { message ->
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }, "Share results"))
    }
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
        Text("Your table, day or night", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Appearance.entries.forEach { choice ->
                FilterChip(selected = prefs.appearance == choice, onClick = { model.updatePreferences(prefs.copy(appearance = choice)) }, label = { Text(choice.label) })
            }
        }
        Text("System follows your device's light or dark setting. Your choice is saved for every game.", color = Muted)
    }
    GameCard {
        Text("The voice of your game", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("en" to "English", "hi" to "हिन्दी", "hinglish" to "Hinglish").forEach { (id, label) -> FilterChip(selected = prefs.language == id, onClick = { model.updatePreferences(prefs.copy(language = id)) }, label = { Text(label) }) } }
        SettingSwitch("Number voice", "Automatically speak each new call. AI-generated recordings, available offline.", prefs.voice) { model.updatePreferences(prefs.copy(voice = it)) }
        SoundVolume("Voice volume", prefs.voiceVolume) { model.updatePreferences(prefs.copy(voiceVolume = it)) }
        Text("Hear again plays a number on request, even with automatic voice off. Set voice volume to zero for silence.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    SoundSettings(prefs, model::updatePreferences)
    GameCard {
        Text("Your pace, your comfort", style = MaterialTheme.typography.titleLarge)
        SettingSwitch("Gentle haptics", "A little feedback when you mark a number.", prefs.haptics) { model.updatePreferences(prefs.copy(haptics = it)) }
        SettingSwitch("Reduced motion", "Keep number and badge reveals still. Device animation settings also apply.", prefs.reducedMotion) { model.updatePreferences(prefs.copy(reducedMotion = it)) }
        Text("Automatic calling pace", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 15, 20, 30).forEach { seconds -> FilterChip(selected = prefs.interval == seconds, onClick = { model.updatePreferences(prefs.copy(interval = seconds)) }, label = { Text("${seconds}s") }) } }
        Text("Calls wait for the recording to finish. Leaving the app pauses offline calling.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
    GameCard {
        Text("Your first game, made easy", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { model.navigate(Screen.TUTORIAL) }) { Text("Try the interactive tutorial") }
        listOf("1. Choose players, tickets, and prizes. Everyone can see the rules before play. Try custom patterns on sample tickets.", "2. Call numbers yourself or turn on automatic calling. Each number appears only once.", "3. Tap Mark ticket and dab the called numbers, or choose assisted marking before a round.", "4. Check claims to inspect the required numbers and verified winners. Same-call winners tie and get full points.", "5. Finish at the chosen house, or play all 90 calls. Rematch keeps your players and rules; every game deals new tickets.").forEach { Text(it, color = Muted) }
    }
    GameCard {
        Text("Your games and privacy", style = MaterialTheme.typography.titleLarge)
        Text("Solo and family rounds stay on this device. Online play sends your display name, tickets, calls and results to the room service; room members see names and wins, and only their own ticket numbers. Online sessions and cached history are encrypted on this device. No ads, analytics or purchases. Number recordings were generated with OpenAI.", color = Muted)
        OutlinedButton(onClick = { delete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all saved rounds") }
        Text("Tambola Together · ${BuildConfig.VERSION_NAME}\nHosted online release and Hindi interface are still in development.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete saved rounds?") }, text = { Text("This removes your offline current game, player names, offline history and offline badges. This cannot be undone. Online data and sound and display settings will stay.") }, confirmButton = { TextButton(onClick = { delete = false; model.deleteHistory() }) { Text("Delete rounds") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Keep rounds") } })
}

@Composable
fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium) }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun TablePlay(round: TableRound, preferences: io.github.sbshrey.tambola.game.data.Preferences,
    mark: (String, Int) -> Unit, repeatCall: () -> Unit) {
    var selected by rememberSaveable(round.id) { mutableIntStateOf(0) }
    var board by remember { mutableStateOf(false) }
    var claims by remember { mutableStateOf(false) }
    val index = selected.coerceIn(round.tickets.indices)
    val numberChipSize = 34.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Eyebrow(if (round.finished) "ROUND ${if (round.status == RoundStatus.COMPLETED) "COMPLETE" else "CANCELLED"}" else if (round.status == RoundStatus.PAUSED) "TAKE A BREATHER · PAUSED" else "LET THE GOOD TIMES ROLL")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(120.dp)) { NumberBall(round.latest, preferences.reducedMotion, compact = true) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (round.latest == null) "Ready when you are" else "Call ${round.called.size} of 90", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { board = true }) { Text("Number board") }
            TextButton(onClick = repeatCall, enabled = round.latest != null) { Text("Hear again") }
        }
    }
    LinearProgressIndicator(progress = { round.called.size / 90f }, modifier = Modifier.fillMaxWidth(), color = Jade, trackColor = Panel)
    if (round.called.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RECENT CALLS", fontSize = 10.sp, color = Muted, modifier = Modifier.fillMaxWidth())
            round.called.takeLast(5).reversed().forEachIndexed { i, n -> Box(Modifier.size(numberChipSize).background(if (i == 0) Saffron else Panel, CircleShape), contentAlignment = Alignment.Center) { Text("$n", fontSize = 14.sp, lineHeight = 18.sp, color = if (i == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) } }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Your table", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(pluralStringResource(R.plurals.ticket_count, round.tickets.size, round.tickets.size), color = Muted, fontSize = 12.sp)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        round.tickets.forEachIndexed { i, ticket -> FilterChip(selected = index == i, onClick = { selected = i }, label = { Text(round.ticketLabel(ticket.id)) }) }
    }
    TicketCard(round.tickets[index], round, preferences.haptics, mark)
    Text("Amber outline: called · Green: marked. Tap Mark ticket for large, comfortable number buttons.", color = Muted, style = MaterialTheme.typography.bodySmall)
    PrimaryAction("Check claims · ${round.awards.size + round.customAwards.size} verified") { claims = true }
    if (board) AlertDialog(onDismissRequest = { board = false }, title = { Text("The number board") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${round.called.size} called · ${90 - round.called.size} to go", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                (1..90).forEach { n -> Box(Modifier.size(numberChipSize + 2.dp).background(if (n == round.latest) Saffron else if (n in round.called) Jade else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("$n", color = if (n == round.latest) MaterialTheme.colorScheme.onPrimary else if (n in round.called) MaterialTheme.colorScheme.onSecondary else Muted, fontWeight = FontWeight.Bold) } }
            }
            Text("Call history", style = MaterialTheme.typography.titleMedium)
            Text(round.called.joinToString(" → ").ifEmpty { "No numbers yet" }, color = Muted)
        }
    }, confirmButton = { TextButton(onClick = { board = false }) { Text("Back to table") } })
    if (claims) AlertDialog(onDismissRequest = { claims = false }, title = { Text("Fair wins, happy faces") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Winners are verified from called numbers, even if someone forgets to mark. Tickets completing a prize on the same call tie.", color = Muted)
            Text(round.settings.endExplanation(), color = Saffron)
            Text(if (round.settings.assistedMarking) "Assisted marking is on for everyone." else if (round.settings.mode == GameMode.ONLINE) "Manual marking · mark your own called numbers." else "Manual marking · computer tickets mark automatically.", color = Muted)
            RuleList(round, round.tickets[index])
        }
    }, confirmButton = { TextButton(onClick = { claims = false }) { Text("Back to game") } })
}
