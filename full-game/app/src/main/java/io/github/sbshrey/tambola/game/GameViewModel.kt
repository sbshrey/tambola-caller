package io.github.sbshrey.tambola.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.audio.CallAudio
import io.github.sbshrey.tambola.game.audio.GameAudio
import io.github.sbshrey.tambola.game.audio.SoundCue
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.setup.*
import io.github.sbshrey.tambola.game.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class Screen { HOME, SETUP, GAME, RESULTS, HISTORY, SETTINGS, ONLINE, TUTORIAL, BADGES }
data class GameUiState(
    val loading: Boolean = true,
    val screen: Screen = Screen.HOME,
    val setupDraft: SetupDraft = SetupDraft(),
    val ruleDraft: CustomRuleDraft? = null,
    val originalRuleDraft: CustomRuleDraft? = null,
    val saving: Boolean = false,
    val round: Round? = null,
    val viewedResult: Round? = null,
    val history: List<SavedRound> = emptyList(),
    val badges: Map<BadgeMode, BadgeProgress> = emptyMap(),
    val preferences: Preferences = Preferences(),
    val error: UiMessage? = null,
    val auto: Boolean = false,
    val winMoment: WinMoment? = null,
)

class GameViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val draftJson = Json { encodeDefaults = true }
    private val database = (application as TambolaApplication).database
    private val repository = LocalGameRepository(database)
    private val preferences = PreferenceStore(application)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = mutable.asStateFlow()
    private val audio = CallAudio(application) { mutable.update { it.copy(error = UiMessage(R.string.error_recording)) } }
    private var timer: Job? = null
    private var lastDrawAt = -1_000L
    private var foreground = false

    init {
        viewModelScope.launch {
            try { mutable.update { it.copy(round = repository.restore(), loading = false) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { reportStorageFailure("restore", error); mutable.update { it.copy(loading = false, error = UiMessage(R.string.error_restore_round)) } }
        }
        viewModelScope.launch { preferences.values.catch { mutable.update { it.copy(error = UiMessage(R.string.error_load_settings)) } }.collect { prefs ->
            GameAudio.get(application).configure(prefs)
            mutable.update { it.copy(preferences = prefs) }
        } }
        viewModelScope.launch {
            var previousCompleted: List<SavedRound>? = null
            repository.history.catch { error -> reportStorageFailure("history", error); mutable.update { it.copy(error = UiMessage(R.string.error_load_history)) } }.collect { rows ->
            val completed = rows.filter { it.completed }
            val badges = if (completed == previousCompleted) mutable.value.badges else withContext(Dispatchers.Default) {
                val progress = mutableMapOf<BadgeMode, BadgeProgress>()
                completed.forEach { saved ->
                    runCatching { RoundCodec.decode(saved.payload) }.getOrNull()?.let { round ->
                        val mode = round.badgeMode()
                        progress[mode] = (progress[mode] ?: BadgeProgress()).record(round)
                    }
                }
                progress.toMap()
            }
            previousCompleted = completed
            mutable.update { it.copy(history = rows, badges = badges) }
        } }
    }

    fun navigate(screen: Screen) {
        if (screen != Screen.GAME) pause()
        mutable.update { it.copy(screen = screen, viewedResult = null) }
    }
    fun setup(mode: GameMode) {
        pause()
        val draft = savedState.get<String>("setup_$mode")?.let { runCatching { draftJson.decodeFromString<SetupDraft>(it) }.getOrNull() } ?: SetupDraft.fresh(mode)
        val editor = savedState.get<String>("rule_$mode")?.let { runCatching { draftJson.decodeFromString<CustomRuleDraft>(it) }.getOrNull() }
        mutable.update { it.copy(screen = Screen.SETUP, setupDraft = draft, ruleDraft = editor, originalRuleDraft = editor) }
    }
    fun rematch(round: Round) {
        pause()
        val draft = SetupDraft.from(round)
        updateSetup(draft)
        cancelRule()
        mutable.update { it.copy(screen = Screen.SETUP) }
    }
    fun updateSetup(draft: SetupDraft) {
        savedState["setup_${draft.mode}"] = draftJson.encodeToString(draft)
        mutable.update { it.copy(setupDraft = draft) }
    }
    fun editRule(prize: CustomPrize? = null) {
        if (prize == null && mutable.value.setupDraft.customPrizes.size >= 12) return
        val draft = prize?.let(CustomRuleDraft::from) ?: CustomRuleDraft()
        updateRule(draft)
        mutable.update { it.copy(originalRuleDraft = draft) }
    }
    fun updateRule(draft: CustomRuleDraft) {
        savedState["rule_${mutable.value.setupDraft.mode}"] = draftJson.encodeToString(draft)
        mutable.update { it.copy(ruleDraft = draft) }
    }
    fun cancelRule() {
        savedState.remove<String>("rule_${mutable.value.setupDraft.mode}")
        mutable.update { it.copy(ruleDraft = null, originalRuleDraft = null) }
    }
    fun saveRule() {
        val state = mutable.value
        val draft = state.ruleDraft ?: return
        try {
            val prize = draft.prize(state.setupDraft.tickets)
            val existing = state.setupDraft.customPrizes
            val rules = if (existing.any { it.id == prize.id }) existing.map { if (it.id == prize.id) prize else it } else existing + prize
            require(rules.size <= 12)
            updateSetup(state.setupDraft.copy(customPrizes = rules))
            cancelRule()
        } catch (error: IllegalArgumentException) { mutable.update { it.copy(error = error.uiMessage(R.string.error_prize_settings)) } }
    }
    fun removeRule(id: String) { updateSetup(mutable.value.setupDraft.let { it.copy(customPrizes = it.customPrizes.filterNot { prize -> prize.id == id }) }) }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun dismissWin() { mutable.update { it.copy(winMoment = null) } }
    fun tutorialCall() { if (foreground) audio.play(7, mutable.value.preferences.language) }
    fun finishTutorial(completed: Boolean) {
        viewModelScope.launch {
            try { preferences.finishTutorial(completed); navigate(Screen.HOME) }
            catch (_: Exception) { mutable.update { it.copy(error = UiMessage(R.string.error_save_tutorial)) } }
        }
    }

    fun quickPlay() {
        if (mutable.value.saving) return
        if (mutable.value.round?.finished == false) { navigate(Screen.GAME); resume(); return }
        updateSetup(SetupDraft(tickets = 3, bots = 2, assisted = true,
            prizes = listOf(Prize.EARLY_FIVE, Prize.CORNERS, Prize.TOP_LINE, Prize.MIDDLE_LINE, Prize.BOTTOM_LINE)))
        val fastPreferences = mutable.value.preferences.copy(interval = 5)
        mutable.update { it.copy(preferences = fastPreferences) }
        updatePreferences(fastPreferences)
        create(automatic = true)
    }

    fun create(automatic: Boolean = false) {
        if (mutable.value.saving) return
        val draft = mutable.value.setupDraft
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val settings = draft.settings()
                    val players = draft.playerNames.mapIndexed { i, name -> Player("p$i", name, avatar = draft.avatar(i)) } +
                        if (draft.mode == GameMode.PRACTICE) (1..draft.bots).map { Player("bot$it", listOf("Mango", "Chai", "Peacock", "Lotus", "Ladoo")[it - 1], true, it) } else emptyList()
                    val round = withContext(Dispatchers.Default) { Round.create(players, settings).start() }
                    // Keep any previous in-progress round as a cancelled history entry.
                    repository.replaceActive(mutable.value.round, round)
                    audio.stop(); stopTimer(); lastDrawAt = -1_000
                    mutable.update { it.copy(round = round, viewedResult = null, screen = Screen.GAME, error = null, winMoment = null) }
                    if (foreground) audio.effect(SoundCue.DEAL)
                    if (automatic) auto()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { reportStorageFailure("create", error); mutable.update { it.copy(error = error.uiMessage(R.string.error_save_round)) } }
                finally { mutable.update { it.copy(saving = false) } }
            }
        }
    }

    private fun mutate(speak: Boolean = false, markSound: Boolean = false, afterSave: () -> Unit = {}, transform: (Round) -> Round) {
        viewModelScope.launch {
            mutex.withLock {
                val previous = mutable.value.round ?: return@withLock
                try {
                    val next = transform(previous)
                    if (next == previous) return@withLock
                    repository.save(next)
                    val liveCall = foreground && mutable.value.screen == Screen.GAME && speak && next.latest != null && next.called.size > previous.called.size
                    val moment = if (liveCall) next.winMoment() else null
                    mutable.update { it.copy(round = next, winMoment = if (next.status in setOf(RoundStatus.PAUSED, RoundStatus.CANCELLED)) null else moment ?: it.winMoment) }
                    if (liveCall) audio.play(next.latest!!, mutable.value.preferences.language, celebration = moment != null)
                    if (foreground && markSound && next.marks != previous.marks) audio.effect(SoundCue.MARK)
                    if (next.finished) stopTimer()
                    afterSave()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { reportStorageFailure("save-progress", error); mutable.update { it.copy(error = error.uiMessage(R.string.error_save_progress)) } }
            }
        }
    }
    fun draw() {
        if (!foreground) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDrawAt < 500) return
        lastDrawAt = now
        mutate(speak = true) { it.draw() }
    }
    fun toggleMark(ticketId: String, number: Int) = mutate(markSound = true) { it.toggleMark(ticketId, number) }
    fun dabCalled(playerId: String) = mutate(markSound = true) { round ->
        if (round.finished || round.settings.assistedMarking) round else round.tickets.filter { it.playerId == playerId && round.players.none { player -> player.id == playerId && player.computer } }
            .fold(round) { next, ticket -> ticket.numbers.filter { it in next.called && it !in next.marks[ticket.id].orEmpty() }
                .fold(next) { marked, number -> marked.toggleMark(ticket.id, number) } }
    }
    fun undo() { stopTimer(); audio.stop(); dismissWin(); mutate { it.undo() } }
    fun resume() { mutate(afterSave = { if (savedState.get<String>("auto_round") == mutable.value.round?.id) auto() }) { it.start() } }
    fun pause() { stopTimer(); audio.stop(); dismissWin(); mutate { audio.stop(); it.pause() } }
    fun setForeground(value: Boolean) { foreground = value; if (!value) pause() }
    fun finish() { stopTimer(); audio.stop(); dismissWin(); mutate { it.cancel() } }
    fun repeatCall() { if (foreground) mutable.value.round?.latest?.let { audio.repeat(it, mutable.value.preferences.language) } }
    fun auto() {
        if (mutable.value.auto) { savedState.remove<String>("auto_round"); stopTimer(); return }
        if (mutable.value.round?.status != RoundStatus.PLAYING) return
        savedState["auto_round"] = mutable.value.round?.id
        mutable.update { it.copy(auto = true) }
        timer = viewModelScope.launch {
            while (isActive) {
                delay(mutable.value.preferences.interval * 1_000L)
                while (isActive && (audio.isPlaying || SystemClock.elapsedRealtime() - lastDrawAt < mutable.value.preferences.interval * 1_000L)) delay(250)
                draw()
            }
        }
    }
    private fun stopTimer() { timer?.cancel(); timer = null; mutable.update { it.copy(auto = false) } }
    fun updatePreferences(value: Preferences) {
        viewModelScope.launch { try { preferences.update(value) } catch (_: Exception) { mutable.update { it.copy(error = UiMessage(R.string.error_save_settings)) } } }
    }
    fun openHistory(saved: SavedRound) {
        try {
            val round = RoundCodec.decode(saved.payload)
            if (!round.finished) { mutable.update { it.copy(error = UiMessage(R.string.error_resume_home)) }; return }
            pause()
            mutable.update { it.copy(viewedResult = round, screen = Screen.RESULTS) }
        } catch (_: Exception) { mutable.update { it.copy(error = UiMessage(R.string.error_read_result)) } }
    }
    fun deleteHistory() {
        stopTimer(); audio.stop(); dismissWin()
        viewModelScope.launch { mutex.withLock {
            try { repository.deleteAll(); mutable.update { it.copy(round = null, viewedResult = null, screen = Screen.HOME) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { reportStorageFailure("delete", error); mutable.update { it.copy(error = UiMessage(R.string.error_delete_rounds)) } }
        } }
    }
    override fun onCleared() { audio.stop(); super.onCleared() }
}
