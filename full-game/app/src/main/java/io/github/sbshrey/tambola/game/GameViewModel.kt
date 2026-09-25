package io.github.sbshrey.tambola.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.audio.CallAudio
import io.github.sbshrey.tambola.game.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Screen { HOME, SETUP, GAME, RESULTS, HISTORY, SETTINGS }
data class GameUiState(
    val loading: Boolean = true,
    val screen: Screen = Screen.HOME,
    val setupMode: GameMode = GameMode.PRACTICE,
    val round: Round? = null,
    val viewedResult: Round? = null,
    val history: List<SavedRound> = emptyList(),
    val preferences: Preferences = Preferences(),
    val error: String? = null,
    val auto: Boolean = false,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val database = GameDatabase.open(application)
    private val repository = LocalGameRepository(database)
    private val preferences = PreferenceStore(application)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = mutable.asStateFlow()
    private val audio = CallAudio(application) { mutable.update { it.copy(error = "The recording could not play. You can keep playing and read the number on screen.") } }
    private var timer: Job? = null
    private var lastDrawAt = -1_000L
    private var foreground = false

    init {
        viewModelScope.launch {
            try { mutable.update { it.copy(round = repository.restore(), loading = false) } }
            catch (_: Exception) { mutable.update { it.copy(loading = false, error = "The saved round could not be restored. Your saved data has been kept.") } }
        }
        viewModelScope.launch { preferences.values.catch { mutable.update { it.copy(error = "Settings could not be loaded.") } }.collect { prefs -> mutable.update { it.copy(preferences = prefs) } } }
        viewModelScope.launch { repository.history.catch { mutable.update { it.copy(error = "History could not be loaded.") } }.collect { rows -> mutable.update { it.copy(history = rows) } } }
    }

    fun navigate(screen: Screen) {
        if (screen != Screen.GAME) pause()
        mutable.update { it.copy(screen = screen, viewedResult = null) }
    }
    fun setup(mode: GameMode) { pause(); mutable.update { it.copy(screen = Screen.SETUP, setupMode = mode) } }
    fun clearError() { mutable.update { it.copy(error = null) } }

    fun create(names: List<String>, count: Int, assisted: Boolean, prizes: List<Prize>, bots: Int) {
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val mode = mutable.value.setupMode
                    require(names.isNotEmpty() && names.all { it.isNotBlank() && it.trim().length <= 40 }) { "Enter names of 1–40 characters." }
                    require(mode != GameMode.FAMILY || names.size in 2..8) { "Family play needs 2–8 players." }
                    require(mode != GameMode.PRACTICE || (names.size == 1 && bots in 0..5))
                    val players = names.mapIndexed { i, name -> Player("p$i", name.trim()) } +
                        if (mode == GameMode.PRACTICE) (1..bots).map { Player("bot$it", listOf("Mango", "Chai", "Peacock", "Lotus", "Ladoo")[it - 1], true) } else emptyList()
                    val round = withContext(Dispatchers.Default) { Round.create(players, RoundSettings(mode, count, assisted, prizes)).start() }
                    // Keep any previous in-progress round as a cancelled history entry.
                    repository.replaceActive(mutable.value.round, round)
                    audio.stop(); stopTimer(); lastDrawAt = -1_000
                    mutable.update { it.copy(round = round, viewedResult = null, screen = Screen.GAME, error = null) }
                } catch (error: Exception) { mutable.update { it.copy(error = error.message ?: "The round could not be saved. Please try again.") } }
            }
        }
    }

    private fun mutate(speak: Boolean = false, transform: (Round) -> Round) {
        viewModelScope.launch {
            mutex.withLock {
                val previous = mutable.value.round ?: return@withLock
                try {
                    val next = transform(previous)
                    if (next == previous) return@withLock
                    repository.save(next)
                    mutable.update { it.copy(round = next) }
                    if (foreground && speak && next.latest != null && next.called.size > previous.called.size && mutable.value.preferences.voice) audio.play(next.latest!!, mutable.value.preferences.language)
                    if (next.finished) stopTimer()
                } catch (error: Exception) { mutable.update { it.copy(error = error.message ?: "Progress could not be saved. Please try again.") } }
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
    fun toggleMark(ticketId: String, number: Int) = mutate { it.toggleMark(ticketId, number) }
    fun undo() { stopTimer(); audio.stop(); mutate { it.undo() } }
    fun resume() { mutate { it.start() } }
    fun pause() { stopTimer(); audio.stop(); mutate { audio.stop(); it.pause() } }
    fun setForeground(value: Boolean) { foreground = value; if (!value) pause() }
    fun finish() { stopTimer(); audio.stop(); mutate { it.cancel() } }
    fun repeatCall() { mutable.value.round?.latest?.let { audio.play(it, mutable.value.preferences.language) } }
    fun auto() {
        if (mutable.value.auto) { stopTimer(); return }
        if (mutable.value.round?.status != RoundStatus.PLAYING) return
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
        if (!value.voice) audio.stop()
        viewModelScope.launch { try { preferences.update(value) } catch (_: Exception) { mutable.update { it.copy(error = "Settings could not be saved.") } } }
    }
    fun openHistory(saved: SavedRound) {
        try {
            val round = RoundCodec.decode(saved.payload)
            if (!round.finished) { mutable.update { it.copy(error = "Resume the current round from Home.") }; return }
            pause()
            mutable.update { it.copy(viewedResult = round, screen = Screen.RESULTS) }
        } catch (_: Exception) { mutable.update { it.copy(error = "This saved result could not be read. It has been kept.") } }
    }
    fun deleteHistory() {
        stopTimer(); audio.stop()
        viewModelScope.launch { mutex.withLock {
            try { repository.deleteAll(); mutable.update { it.copy(round = null, viewedResult = null, screen = Screen.HOME) } }
            catch (_: Exception) { mutable.update { it.copy(error = "Saved rounds could not be deleted.") } }
        } }
    }
    override fun onCleared() { audio.stop(); database.close(); super.onCleared() }
}
