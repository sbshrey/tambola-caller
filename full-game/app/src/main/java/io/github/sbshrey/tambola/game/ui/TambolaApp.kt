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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
fun TambolaApp(state: GameUiState, model: GameViewModel, onlineState: OnlineUiState, online: OnlineViewModel,
    bingoState: BingoUiState, bingo: BingoViewModel,
    invitation: RoomInviteState = RoomInviteState(), dismissInvitation: () -> Unit = {}) {
    val words = gameText()
    var profile by rememberSaveable { mutableStateOf(false) }
    var roomDetails by rememberSaveable(onlineState.room?.round?.id) { mutableStateOf(false) }
    BackHandler(state.screen != Screen.HOME && state.ruleDraft == null) { model.navigate(Screen.HOME) }
    LaunchedEffect(state.screen) {
        if (state.screen in setOf(Screen.SETUP, Screen.GAME, Screen.RESULTS, Screen.HISTORY, Screen.TUTORIAL, Screen.BADGES))
            model.navigate(Screen.HOME)
    }
    BackHandler(roomDetails && state.screen == Screen.ONLINE) { roomDetails = false }
    val pageScroll = key(state.screen, state.round?.id,
        onlineState.room?.roomId.takeIf { state.screen == Screen.ONLINE },
        invitation.revision.takeIf { state.screen == Screen.ONLINE },
        onlineState.room?.round?.id.takeIf { state.screen == Screen.ONLINE }) { rememberScrollState() }
    Surface(Modifier.fillMaxSize().testTag("app-background").semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
        if (!state.loading && state.screen in setOf(Screen.SETUP, Screen.GAME, Screen.RESULTS, Screen.HISTORY, Screen.TUTORIAL, Screen.BADGES)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (!state.loading && state.screen == Screen.BINGO) {
            BingoOnlineScreen(onlineState, online, state.preferences.reducedMotion) { model.navigate(Screen.HOME) }
        } else if (!state.loading && state.screen == Screen.HOME && invitation.code == null) {
            GameHub(onlineState.name, onlineState.wallet?.balance, onlineState.avatar, state.lastGame, profile = { profile = true },
                tambola = { if (onlineState.name.isNullOrBlank()) profile = true else model.navigate(Screen.ONLINE) },
                bingo = { if (onlineState.name.isNullOrBlank()) profile = true else model.navigate(Screen.BINGO) },
                settings = { model.navigate(Screen.SETTINGS) },
                connected = onlineState.available && onlineState.connection == Connection.LIVE)
        } else if (!state.loading && state.screen == Screen.SETTINGS) {
            GameSettings(state.preferences, model::updatePreferences) { model.navigate(Screen.HOME) }
        } else if (!state.loading && state.screen == Screen.GAME && state.round != null) {
            OfflineArena(state.round, state, model)
        } else if (!state.loading && state.screen == Screen.ONLINE && onlineState.room?.phase == io.github.sbshrey.tambola.protocol.RoomPhase.FINISHED &&
            onlineState.room.options.roundSummary && onlineState.room.round?.winnings?.isNotEmpty() == true &&
            !onlineState.storageFailure && !onlineState.deletingProfile && invitation.code == null) {
            key(requireNotNull(onlineState.room.round).id) {
                CoinRoundSummary(onlineState, lobby = { model.navigate(Screen.HOME) },
                    replay = { tickets -> if (onlineState.room.coins?.friendTable == true) online.replayFriends(tickets) else online.play(tickets) },
                    retry = online::retry, clearError = online::clearError, reducedMotion = state.preferences.reducedMotion)
            }
        } else if (!state.loading && state.screen == Screen.ONLINE && onlineState.room?.round != null &&
            (onlineState.room.phase == io.github.sbshrey.tambola.protocol.RoomPhase.ACTIVE ||
                (onlineState.room.phase == io.github.sbshrey.tambola.protocol.RoomPhase.FINISHED && !onlineState.room.options.coinGame)) &&
            !onlineState.storageFailure && !onlineState.deletingProfile && invitation.code == null && !roomDetails) {
            OnlineArena(onlineState, online, state.preferences, { model.navigate(Screen.HOME) }, { roomDetails = true })
        } else if (!state.loading && state.screen in setOf(Screen.HOME, Screen.ONLINE) && (invitation.code == null || invitation.friendTable) &&
            (onlineState.room == null || onlineState.room.options.coinGame)) {
            CoinLobby(onlineState, online, play = { tickets -> roomDetails = false; model.navigate(Screen.ONLINE); online.play(tickets) },
                friends = { tickets, code -> roomDetails = false; model.navigate(Screen.ONLINE); online.play(tickets, friendTable = true, friendCode = code) },
                replayFriends = { tickets -> roomDetails = false; model.navigate(Screen.ONLINE); online.replayFriends(tickets) },
                resume = { roomDetails = false; model.navigate(Screen.ONLINE) }, settings = { model.navigate(Screen.SETTINGS) }, reducedMotion = state.preferences.reducedMotion,
                home = { model.navigate(Screen.HOME) })
        } else BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            // A fixed action panel must not consume the reading area at large text sizes or
            // in a short window. Keep those actions in the same scroll flow as the room.
            val inlineOnlineControls = LocalDensity.current.fontScale >= 1.3f || maxHeight < 480.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.screen != Screen.HOME) TextButton(onClick = { model.navigate(Screen.HOME) }, modifier = Modifier.testTag("home")) { Text(words(R.string.ui_home)) }
                    else Box(Modifier.size(36.dp).background(Saffron, CircleShape), contentAlignment = Alignment.Center) { Text("T", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary, fontSize = 22.sp) }
                    Text(if (state.screen == Screen.HOME) words(R.string.ui_tambola_together) else words.screen(state.screen), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (state.screen != Screen.SETTINGS) TextButton(onClick = { model.navigate(Screen.SETTINGS) }) { Text(words(R.string.ui_settings), fontSize = 12.sp) }
                }
                if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else Column(Modifier.weight(1f).verticalScroll(pageScroll).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(if (state.screen == Screen.GAME) 12.dp else 20.dp)) {
                    SoundNotice()
                    when (state.screen) {
                        Screen.HOME -> QuickHome(state, model)
                        Screen.BINGO -> Unit
                        Screen.SETUP -> Setup(state, model)
                        Screen.GAME -> Unit // Live games use the bounded arena above.
                        Screen.RESULTS -> (state.viewedResult ?: state.round)?.let { Results(it, model) }
                        Screen.HISTORY -> History(state.history, model)
                        Screen.SETTINGS -> Unit // Bounded landscape controls above.
                        Screen.ONLINE -> {
                            if (roomDetails && onlineState.room?.round != null) TextButton(onClick = { roomDetails = false }) { Text(words(R.string.ui_back_to_game)) }
                            OnlineScreen(onlineState, online, state.preferences, invitation, dismissInvitation, inlineOnlineControls)
                        }
                        Screen.TUTORIAL -> TutorialScreen(state, model) { pageScroll.scrollTo(0) }
                        Screen.BADGES -> BadgesScreen(state, onlineState)
                    }
                }
                if (!state.loading && state.screen == Screen.ONLINE && !inlineOnlineControls) OnlineControls(onlineState, online)
            }
        }
    }
    if (!state.loading && invitation.friendTable && invitation.code != null) FriendInviteDialog(invitation.code, onlineState,
        join = { tickets -> roomDetails = false; model.navigate(Screen.ONLINE); online.play(tickets, true, invitation.code) },
        close = dismissInvitation, resume = { dismissInvitation(); roomDetails = false; model.navigate(Screen.ONLINE) })
    if (profile) SharedPlayerProfile(onlineState, online) { profile = false }
    if (state.screen == Screen.SETUP && state.ruleDraft != null) CustomRuleEditor(state, model)
    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text(words(R.string.ui_a_quick_heads_up)) }, text = { Text(words.message(error)) }, confirmButton = { TextButton(onClick = model::clearError) { Text(words(R.string.ui_got_it)) } }) }
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
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(player.name, style = MaterialTheme.typography.titleMedium) }
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
fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val words = gameText()
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium) }
        Switch(checked = checked, onCheckedChange = null)
    }
}
