package io.github.sbshrey.tambola.game.ui

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
        listOf(Player(lessonTicket.playerId, "Sample player")), listOf(lessonTicket), calls,
        mapOf(lessonTicket.id to markedNumbers.toSet().intersect(calls.toSet())), award, emptyList(), RoundStatus.PLAYING,
        mapOf(lessonTicket.playerId to if (won) Prize.TOP_LINE.points else 0), listOf(WinningTicket(lessonTicket.id, lessonTicket.playerId, 1)))
    Eyebrow("YOUR FIRST GAME · ${step + 1} OF 5")
    LinearProgressIndicator(progress = { (step + 1) / 5f }, modifier = Modifier.fillMaxWidth(), color = Jade)
    Text(listOf("A happy place to start.", "Meet your ticket.", "Hear it. Find it. Dab it.", "Fair wins, every time.", "You're ready for game night.")[step], style = MaterialTheme.typography.headlineLarge)
    Text("Tutorial sample · your real games and badges stay untouched.", color = Muted, style = MaterialTheme.typography.bodySmall)
    when (step) {
        0 -> {
            Text("Tambola is a race to complete the patterns agreed before play. Numbers are called from 1 to 90, with no repeats.", color = Muted)
            GameCard {
                Text("Make yourself comfortable", style = MaterialTheme.typography.titleLarge)
                SettingSwitch("Tutorial voice", "Hear a sample call using the same offline voice as your games.", prefs.voice) { model.updatePreferences(prefs.copy(voice = it)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "English", "hi" to "हिन्दी", "hinglish" to "Hinglish").forEach { (id, label) ->
                        FilterChip(prefs.language == id, { model.updatePreferences(prefs.copy(language = id)) }, label = { Text(label) })
                    }
                }
                Text("Number voices are AI-generated. Sound is optional; every call appears on screen.", color = Muted)
            }
        }
        1 -> {
            Text("A ticket has 15 numbers: five in each of its three rows. Empty squares are spaces, not missing numbers. The columns group numbers from small to large.", color = Muted)
            TicketCard(lessonTicket, table, false) { _, _ -> }
            Text("This sample ticket is only for learning. Every real round deals fresh tickets.", color = Saffron)
        }
        2 -> {
            NumberBall(if (called) 7 else null, prefs.reducedMotion, compact = true)
            if (!called) PrimaryAction("Try calling a number") { called = true; model.tutorialCall() }
            else TextButton(onClick = model::tutorialCall, enabled = prefs.voice) { Text("Hear 7 again") }
            Text(if (!called) "Make your first sample call." else if (!marked) "Seven is on your top row. Open Mark ticket, tap 7, then tap Done." else "Nice dab! Green means marked. Only called numbers can be marked; tap again to undo a dab.", color = if (marked) Jade else Muted)
            TicketCard(lessonTicket, table, prefs.haptics) { _, number -> if (number in calls) markedNumbers = if (number in markedNumbers) markedNumbers - number else markedNumbers + number }
            Text("Prefer a helping hand? Choose assisted marking before a real round.", color = Muted)
        }
        3 -> {
            Text("Let's finish the sample top line. A line wins when all five of its numbers have been called.", color = Muted)
            if (!topLine) PrimaryAction("Try a top-line win") { topLine = true }
            TicketCard(lessonTicket, table, false) { _, number -> if (number in calls) markedNumbers = if (number in markedNumbers) markedNumbers - number else markedNumbers + number }
            if (won) GameCard {
                Eyebrow("TOP LINE VERIFIED", Jade)
                Text("15 points · all five numbers called", style = MaterialTheme.typography.titleLarge)
                Text("The whole line has been called. The game verifies wins from calls, so a missed dab cannot take away a prize. Players qualifying on the same call tie and each receive full points.", color = Muted)
            }
            Text("Read the prizes before you ready up. Full house means all 15 numbers; a custom prize can use a different agreed pattern.", color = Muted)
        }
        4 -> {
            GameCard {
                Text("Choose your table", style = MaterialTheme.typography.titleLarge)
                Text("Solo: take your time, with optional computer players.\n\nOne device: pass the phone around for family tickets.\n\nOnline: join a private room with a code; everyone sees their own tickets.", color = Muted)
            }
            GameCard {
                Text("Keep the good moments", style = MaterialTheme.typography.titleLarge)
                Text("Completed rounds earn badges. Ending early saves a cancelled result. Results show verified prizes and points; sharing asks you what to include. A rematch keeps the rules and deals fresh tickets.", color = Muted)
            }
            Text("Use How to play any time to try this lesson again.", color = Jade)
        }
    }
    if (step < 4) PrimaryAction(if (step == 0) "Show me the ticket" else "Next lesson", enabled = when (step) { 2 -> marked; 3 -> won; else -> true }) { step++ }
    else PrimaryAction("Finish tutorial") { model.finishTutorial(true) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (step > 0) TextButton(onClick = { step-- }) { Text("Previous lesson") }
        TextButton(onClick = { model.finishTutorial(false) }) { Text("Skip for now") }
    }
}

@Composable
fun BadgesScreen(state: GameUiState, online: OnlineUiState) {
    var mode by rememberSaveable { mutableStateOf(BadgeMode.SOLO) }
    Eyebrow("LITTLE MILESTONES. LOVELY MEMORIES.")
    Text("Your badges", style = MaterialTheme.typography.headlineLarge)
    Text("Free social play, with something to smile about. Each mode has its own milestones.", color = Muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BadgeMode.entries.forEach { item -> FilterChip(mode == item, { mode = item }, label = { Text(item.title) }) }
    }
    Text(mode.description, color = Muted)
    if (mode == BadgeMode.ONLINE && online.name == null) Text("Open online play to set up a profile. Badges stay with that profile's encrypted data on this device.", color = Saffron)
    else if (mode == BadgeMode.ONLINE) Text("Playing as ${online.name}", color = Jade)
    BadgeCollection(if (mode == BadgeMode.ONLINE) online.badges else state.badges[mode] ?: BadgeProgress(), state.preferences.reducedMotion)
    Text(if (mode == BadgeMode.ONLINE) "Reconnecting or revisiting a result never counts it twice. Online badges remain when older cached results roll out of history; signing out or resetting online data removes them from this device. They are not a global leaderboard."
        else "Badges come from your saved completed rounds. Cancelled rounds and the tutorial do not count. Deleting offline rounds also clears these offline badges.", color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun BadgeCollection(progress: BadgeProgress, reducedMotion: Boolean) {
    Badge.entries.forEach { badge ->
        val earned = progress.earned(badge)
        var shown by remember(badge, earned) { mutableStateOf(false) }
        LaunchedEffect(badge, earned) { shown = true }
        val scale by animateFloatAsState(if (shown || !earned) 1f else .85f, tween(if (reducedMotion) 0 else 200), label = "badge reveal")
        GameCard(Modifier.testTag("badge-${badge.name}").semantics(mergeDescendants = true) { stateDescription = if (earned) "Earned" else "Not yet earned" }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(58.dp).scale(if (reducedMotion) 1f else scale).background(if (earned) Saffron else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    Text(badge.symbol, color = if (earned) MaterialTheme.colorScheme.onPrimary else Muted, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text(badge.title, style = MaterialTheme.typography.titleLarge)
                    Text(if (earned) "Earned" else "Still to come", color = if (earned) Jade else Muted)
                }
            }
            Text(badge.explanation, color = Muted)
            if (badge == Badge.FIVE_ROUNDS && !earned) {
                LinearProgressIndicator(progress = { progress.completedRoundIds.size / 5f }, modifier = Modifier.fillMaxWidth(), color = Jade)
                Text("${progress.completedRoundIds.size} of 5 completed rounds", color = Jade)
            }
        }
    }
}
