package io.github.sbshrey.tambola.game

import android.app.Application
import android.util.AtomicFile
import io.github.sbshrey.tambola.game.audio.CallAudio
import io.github.sbshrey.tambola.game.audio.SoundCue
import io.github.sbshrey.tambola.game.data.PreferenceStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sbshrey.tambola.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class BingoUiState(val loading: Boolean = true, val round: BingoRound? = null,
    val cards: Int = 1, val error: Boolean = false, val invalidClaim: Boolean = false, val winSequence: Int = 0)

/** Practice saves are separate from Tambola and from the authoritative online wallet. */
class BingoViewModel(application: Application) : AndroidViewModel(application) {
    private val file = AtomicFile(File(application.filesDir, "bingo-practice.json"))
    private val preferences = application.getSharedPreferences("bingo", 0)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(BingoUiState(cards = preferences.getInt("cards", 1).coerceIn(1, 6)))
    val state = mutable.asStateFlow()
    private var timer: Job? = null
    private var language = "en"
    private var active = false
    private val audio = CallAudio(application) {}
    init {
        viewModelScope.launch { PreferenceStore(application).values.catch { }.collect { language = it.language } }
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val round = withContext(Dispatchers.IO) {
                        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists())
                            file.openRead().use { require(it.channel.size() <= 1_000_000); BingoRoundCodec.decode(it.readBytes().decodeToString()) }.pause() else null
                    }
                    mutable.value = mutable.value.copy(loading = false, round = round)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, error = true) }
            }
        }
    }
    /** Only invoked after an explicit reset confirmation; never touches the wallet or Tambola save. */
    fun resetFailedSave() {
        if (!mutable.value.error) return
        active = false; timer?.cancel(); audio.stop()
        viewModelScope.launch {
            mutex.withLock {
                try {
                    withContext(Dispatchers.IO) { file.delete(); check(!file.baseFile.exists()) }
                    mutable.value = BingoUiState(loading = false, cards = mutable.value.cards)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
            }
        }
    }
    fun chooseCards(count: Int) {
        require(count in 1..6)
        preferences.edit().putInt("cards", count).apply()
        mutable.value = mutable.value.copy(cards = count)
    }
    private suspend fun persist(round: BingoRound) = withContext(Dispatchers.IO) {
        val stream = file.startWrite()
        try { stream.write(BingoRoundCodec.encode(round).toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun change(action: (BingoRound?) -> BingoRound) = viewModelScope.launch {
        mutex.withLock {
            if (mutable.value.loading || mutable.value.error) return@withLock
            try {
                val round = action(mutable.value.round)
                persist(round)
                val previous = mutable.value.round
                val freshWin = previous?.id == round.id && round.claims.count { it.playerId == "me" } > previous.claims.count { it.playerId == "me" }
                mutable.value = mutable.value.copy(round = round, invalidClaim = false,
                    winSequence = if (previous?.id != round.id) 0 else mutable.value.winSequence + if (freshWin) 1 else 0)
                if (active && previous?.id == round.id && round.draw.count > previous.draw.count)
                    round.draw.latest?.let { audio.play(it, language, freshWin) }
                else if (active && freshWin) audio.effect(SoundCue.WIN)
            } catch (e: CancellationException) { throw e }
            catch (_: IllegalArgumentException) { mutable.value = mutable.value.copy(invalidClaim = true) }
            catch (_: Exception) { timer?.cancel(); mutable.value = mutable.value.copy(error = true) }
        }
    }
    fun newRound(name: String) {
        active = false
        audio.stop()
        timer?.cancel()
        change { BingoRound.practice(Player("me", name.take(40).ifBlank { "Player" }), mutable.value.cards, System.currentTimeMillis()) }
    }
    fun resume() {
        active = true
        timer?.cancel()
        timer = viewModelScope.launch {
            change { requireNotNull(it).start() }.join()
            while (isActive && mutable.value.round?.status == RoundStatus.PLAYING && !mutable.value.error) {
                delay(8000)
                change { requireNotNull(it).next().playComputers() }.join()
            }
        }
    }
    fun pause() { active = false; timer?.cancel(); audio.stop(); mutable.value = mutable.value.copy(winSequence = 0); if (mutable.value.round != null) change { requireNotNull(it).pause() } }
    fun mark(card: String, number: Int) { change { requireNotNull(it).mark("me", card, number) } }
    fun claim(card: String, pattern: BingoPattern) { change { requireNotNull(it).claim("me", card, pattern) } }
    fun dismissWin(sequence: Int) { if (mutable.value.winSequence == sequence) mutable.value = mutable.value.copy(winSequence = 0) }
    fun repeatCall() { mutable.value.round?.draw?.latest?.let { audio.repeat(it, language) } }
    override fun onCleared() { audio.stop(); super.onCleared() }
    fun clearClaimMessage() { mutable.value = mutable.value.copy(invalidClaim = false) }
}
