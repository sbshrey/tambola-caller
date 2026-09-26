package io.github.sbshrey.tambola.game.online

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.domain.RoundStatus
import io.github.sbshrey.tambola.domain.BadgeProgress
import io.github.sbshrey.tambola.domain.RoundSettings
import io.github.sbshrey.tambola.domain.GameMode
import io.github.sbshrey.tambola.domain.Prize
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.BuildConfig
import io.github.sbshrey.tambola.game.audio.CallAudio
import io.github.sbshrey.tambola.game.audio.SoundCue
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.presentation.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.random.Random

enum class Connection { IDLE, CONNECTING, LIVE, RECONNECTING, SUSPENDED }
data class OnlineUiState(
    val loading: Boolean = true,
    val available: Boolean = BuildConfig.ROOM_API_URL.isNotEmpty(),
    val name: String? = null,
    val avatar: Int = 0,
    val playerId: String? = null,
    val room: RoomView? = null,
    val marks: Map<String, Set<Int>> = emptyMap(),
    val history: List<RoomView> = emptyList(),
    val badges: BadgeProgress = BadgeProgress(),
    val connection: Connection = Connection.IDLE,
    val busy: Boolean = false,
    val pending: Boolean = false,
    val deletingProfile: Boolean = false,
    val sessionExpired: Boolean = false,
    val storageFailure: Boolean = false,
    val error: UiMessage? = null,
    val notice: UiMessage? = null,
    val winMoment: WinMoment? = null,
)

class OnlineViewModel(application: Application) : AndroidViewModel(application) {
    private val store = OnlineStore(application)
    private val api: RoomApi? = BuildConfig.ROOM_API_URL.takeIf { it.isNotEmpty() }?.let { HttpRoomApi(it, BuildConfig.DEBUG) }
    private val mutex = Mutex()
    private var saved: OnlineSaved? = null
    private val mutable = MutableStateFlow(OnlineUiState())
    val state = mutable.asStateFlow()
    private var active = false
    private var stream: Job? = null
    private var operation: Job? = null
    private var preferences = Preferences()
    private val audio = CallAudio(application) { mutable.update { it.copy(error = UiMessage(R.string.error_online_recording)) } }

    init {
        viewModelScope.launch { PreferenceStore(application).values.catch { }.collect { preferences = it } }
        viewModelScope.launch {
            try {
                val restored = store.read()
                check(restored == null || restored.endpoint == BuildConfig.ROOM_API_URL)
                restored?.room?.validateFor(restored.credentials.playerId)
                saved = restored
                publish()
            } catch (_: Exception) {
                mutable.update { it.copy(storageFailure = true, error = UiMessage(R.string.error_online_restore)) }
            }
            mutable.update { it.copy(loading = false) }
            connect()
        }
    }
    private fun publish() {
        val value = saved
        mutable.update { it.copy(name = value?.displayName, avatar = value?.avatar ?: 0, playerId = value?.credentials?.playerId,
            room = value?.room, marks = value?.marks.orEmpty(), history = value?.history.orEmpty(),
            badges = value?.badgeProgress() ?: BadgeProgress(), pending = value?.pending != null,
            deletingProfile = value?.pending is PendingOperation.DeleteProfile,
            winMoment = it.winMoment?.takeIf { _ -> it.room?.round?.id == value?.room?.round?.id }
                ?.refreshPlayers(value?.room?.round?.players.orEmpty())) }
    }
    private suspend fun persist(value: OnlineSaved?) {
        try { store.write(value) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(storageFailure = true) }; throw LocalStorageFailure() }
        saved = value
        publish()
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) connect() else {
            stream?.cancel(); stream = null; audio.stop()
            mutable.update { it.copy(connection = if (saved?.room == null) Connection.IDLE else Connection.SUSPENDED, winMoment = null) }
        }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun clearNotice() { mutable.update { it.copy(notice = null) } }
    fun dismissWin() { mutable.update { it.copy(winMoment = null) } }
    fun register(name: String, avatar: Int = 0) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 40 || trimmed.any(Char::isISOControl) || avatar !in 0 until io.github.sbshrey.tambola.domain.AVATAR_COUNT) {
            mutable.update { it.copy(error = UiMessage(R.string.error_online_name)) }; return
        }
        if (mutable.value.busy || saved != null || api == null || mutable.value.storageFailure) return
        mutable.update { it.copy(busy = true, error = null) }
        operation = viewModelScope.launch {
            try {
                val credentials = api.guest(GuestRequest(trimmed, avatar))
                mutex.withLock { persist(OnlineSaved(BuildConfig.ROOM_API_URL, credentials, trimmed, avatar)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun create(options: RoomOptions = RoomOptions(game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 3, assistedMarking = true,
        prizes = listOf(Prize.EARLY_FIVE, Prize.CORNERS, Prize.TOP_LINE, Prize.MIDDLE_LINE, Prize.BOTTOM_LINE, Prize.FULL_HOUSE)), intervalSeconds = 5)) =
        begin(PendingOperation.Create(CreateRoomRequest(UUID.randomUUID().toString(), options)))
    fun join(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (!Regex("[A-HJ-NP-Z2-9]{8}").matches(code)) { mutable.update { it.copy(error = UiMessage(R.string.error_room_code)) }; return }
        begin(PendingOperation.Join(code))
    }
    fun command(action: RoomAction) {
        val room = saved?.room ?: return
        if (mutable.value.connection != Connection.LIVE) { mutable.update { it.copy(error = UiMessage(R.string.error_reconnect)) }; return }
        begin(PendingOperation.Command(room.code, CommandRequest(UUID.randomUUID().toString(), room.revision, action),
            readyAgreement = if (action is RoomAction.Ready) room.readyAgreement() else null))
    }
    fun logout() = begin(PendingOperation.Logout)
    fun deleteProfile() = begin(PendingOperation.DeleteProfile(DeleteProfileRequest(UUID.randomUUID().toString())))
    private fun begin(pending: PendingOperation) {
        if (saved == null || mutable.value.busy || mutable.value.pending || mutable.value.storageFailure || (mutable.value.sessionExpired && pending !is PendingOperation.DeleteProfile)) return
        if (pending is PendingOperation.Create || pending is PendingOperation.Join) {
            if (saved?.room != null) { mutable.update { it.copy(error = UiMessage(R.string.error_leave_first)) }; return }
        }
        if (pending is PendingOperation.DeleteProfile) { stream?.cancel(); stream = null; audio.stop() }
        mutable.update { it.copy(busy = true, error = null, notice = null,
            connection = if (pending is PendingOperation.DeleteProfile) Connection.SUSPENDED else it.connection) }
        operation = viewModelScope.launch {
            try { mutex.withLock { persist(saved!!.copy(pending = pending)) }; performPending() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
            finally { mutable.update { it.copy(busy = false) }; connect() }
        }
    }
    fun retry() {
        if (mutable.value.busy || saved?.pending == null || mutable.value.storageFailure) return
        mutable.update { it.copy(busy = true, error = null) }
        operation = viewModelScope.launch {
            try { performPending() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
            finally { mutable.update { it.copy(busy = false) }; connect() }
        }
    }
    private suspend fun performPending(readyRetries: Int = 3) {
        val current = saved ?: return
        val pending = current.pending ?: return
        val api = api ?: return
        var deletionConfirmed = false
        try {
            val result = when (pending) {
                is PendingOperation.Create -> api.create(current.credentials.token, pending.request)
                is PendingOperation.Join -> api.join(current.credentials.token, pending.code)
                is PendingOperation.Command -> api.command(current.credentials.token, pending.code, pending.request)
                PendingOperation.Logout -> { api.logout(current.credentials.token); null }
                is PendingOperation.DeleteProfile -> { api.deleteProfile(current.credentials.token, pending.request); deletionConfirmed = true; null }
            }
            mutex.withLock {
                val latest = saved ?: return@withLock
                if (pending == PendingOperation.Logout || pending is PendingOperation.DeleteProfile) {
                    stream?.cancel(); stream = null; audio.stop(); persist(null)
                    mutable.update { it.copy(connection = Connection.IDLE, sessionExpired = false,
                        notice = if (deletionConfirmed) UiMessage(R.string.notice_profile_deleted) else null) }
                } else if (pending is PendingOperation.Command && pending.request.action == RoomAction.Leave) {
                    stream?.cancel(); stream = null; audio.stop(); persist(latest.copy(room = null, marks = emptyMap(), pending = null))
                    mutable.update { it.copy(connection = Connection.IDLE) }
                } else {
                    val accepted = latest.accept(requireNotNull(result), live = active && mutable.value.connection == Connection.LIVE,
                        allowRoomChange = pending is PendingOperation.Create || pending is PendingOperation.Join)
                    persist(accepted.saved.copy(pending = null))
                    announceAccepted(latest, accepted)
                }
            }
        } catch (error: RoomApiFailure) {
            if (error.status == 401 && pending == PendingOperation.Logout) {
                mutex.withLock { persist(null) }; stream?.cancel(); stream = null
                mutable.update { it.copy(connection = Connection.IDLE) }; return
            }
            if (pending !is PendingOperation.DeleteProfile && error.status in 400..499 && error.status !in listOf(401, 408, 429)) mutex.withLock { saved?.let { persist(it.copy(pending = null)) } }
            if (error.status == 409 && current.room != null) {
                val update = api.read(current.credentials.token, current.room!!.code)
                val retryReady = mutex.withLock {
                    val latest = saved ?: return@withLock false
                    val refreshed = latest.accept(update, live = false).saved
                    val replacement = if (error.code == "stale_revision" && readyRetries > 0 && pending is PendingOperation.Command)
                        refreshed.room?.let { pending.rebaseReady(it, UUID.randomUUID().toString()) } else null
                    // Persist a new id only after the previous command was definitively rejected.
                    persist(refreshed.copy(pending = replacement ?: refreshed.pending))
                    replacement != null
                }
                if (retryReady) { performPending(readyRetries - 1); return }
            }
            throw error
        } catch (error: LocalStorageFailure) {
            if (deletionConfirmed) mutable.update { it.copy(notice = UiMessage(R.string.notice_deletion_storage)) }
            throw error
        }
    }
    private fun connect() {
        if (!active || mutable.value.loading || mutable.value.storageFailure || mutable.value.sessionExpired || saved?.pending is PendingOperation.DeleteProfile || saved?.room == null || api == null || stream?.isActive == true) return
        stream = viewModelScope.launch {
            var attempts = 0
            while (isActive && active && saved?.room != null) {
                val session = saved ?: break
                val room = session.room ?: break
                mutable.update { it.copy(connection = if (attempts == 0) Connection.CONNECTING else Connection.RECONNECTING) }
                var received = false
                try {
                    api.events(session.credentials.token, room.code, room.revision).collect { update ->
                        mutex.withLock {
                            val latest = saved ?: return@withLock
                            if (latest.room?.roomId != room.roomId) return@withLock
                            val accepted = latest.accept(update, live = received && active)
                            persist(accepted.saved)
                            mutable.update { it.copy(connection = Connection.LIVE) }
                            // Silent on first snapshot and catch-up. Explicit "Hear again" remains available.
                            announceAccepted(latest, accepted)
                            received = true; attempts = 0
                        }
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: RoomApiFailure) {
                    if (error.status == 401) { showFailure(error); mutable.update { it.copy(connection = Connection.SUSPENDED) }; break }
                    if (error.code in setOf("not_member", "room_missing", "room_closed")) {
                        mutex.withLock { saved?.let { persist(it.copy(room = null, marks = emptyMap())) } }
                        mutable.update { it.copy(connection = Connection.IDLE, error = UiMessage(R.string.error_room_unavailable)) }; break
                    }
                } catch (error: LocalStorageFailure) { showFailure(error); mutable.update { it.copy(connection = Connection.SUSPENDED) }; break }
                catch (error: InvalidRoomResponse) { showFailure(error); mutable.update { it.copy(connection = Connection.SUSPENDED) }; break }
                catch (error: Exception) {
                    // Class names aid local diagnosis without recording headers, URLs or payloads.
                    if (BuildConfig.DEBUG) android.util.Log.w("TambolaRooms", "Reconnect: ${error.javaClass.simpleName}")
                }
                mutable.update { it.copy(connection = Connection.RECONNECTING) }
                attempts++
                delay((1_000L shl attempts.coerceAtMost(5)).coerceAtMost(30_000) + Random.nextLong(500))
            }
        }
    }
    private fun announceAccepted(previous: OnlineSaved, accepted: AcceptedRoom) {
        if (!active) return
        val nextStatus = accepted.saved.room?.round?.status
        if (nextStatus != previous.room?.round?.status && nextStatus in setOf(RoundStatus.PAUSED, RoundStatus.CANCELLED)) { audio.stop(); dismissWin() }
        accepted.announcement?.let { number ->
            val moment = accepted.saved.room?.winMoment()
            if (moment != null) mutable.update { it.copy(winMoment = moment) }
            audio.play(number, preferences.language, celebration = moment != null)
        }
    }
    fun reconnect() { stream?.cancel(); stream = null; connect() }
    fun mark(ticketId: String, number: Int) {
        if (saved?.pending is PendingOperation.DeleteProfile) return
        viewModelScope.launch {
            try { mutex.withLock { saved?.let {
                val next = it.mark(ticketId, number)
                if (next != it) { persist(next); if (active) audio.effect(SoundCue.MARK) }
            } } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
        }
    }
    fun dabCalled() {
        if (saved?.pending is PendingOperation.DeleteProfile) return
        viewModelScope.launch {
            try { mutex.withLock { saved?.let { current ->
                val room = current.room ?: return@let
                val game = room.round ?: return@let
                if (game.status !in setOf(RoundStatus.PLAYING, RoundStatus.PAUSED) || room.options.game.assistedMarking) return@let
                val next = game.ownTickets.fold(current) { value, ticket ->
                    ticket.numbers.filter { it in game.called && it !in value.marks[ticket.id].orEmpty() }
                        .fold(value) { marked, number -> marked.mark(ticket.id, number) }
                }
                if (next != current) { persist(next); if (active) audio.effect(SoundCue.MARK) }
            } } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
        }
    }
    fun repeatCall() { if (active) saved?.room?.round?.called?.lastOrNull()?.let { audio.repeat(it, preferences.language) } }
    /** Explicit recovery action in the UI; never called automatically on a read/decryption failure. */
    fun resetLocalData() {
        if (mutable.value.busy) return
        stream?.cancel(); stream = null; audio.stop()
        viewModelScope.launch {
            try {
                mutex.withLock { persist(null) }
                mutable.value = OnlineUiState(loading = false)
            } catch (error: Exception) { showFailure(error) }
        }
    }
    private fun showFailure(error: Exception) {
        val message = when (error) {
            is RoomApiFailure -> when (error.status) {
                401 -> if (saved?.pending is PendingOperation.DeleteProfile) UiMessage(R.string.error_delete_unconfirmed)
                    else UiMessage(R.string.error_session_expired)
                409 -> if (error.code == "stale_revision") UiMessage(R.string.error_stale_revision) else roomFailureMessage(error)
                413 -> UiMessage(R.string.error_rules_large)
                else -> roomFailureMessage(error)
            }
            is InvalidRoomResponse -> UiMessage(R.string.error_incompatible)
            is LocalStorageFailure -> UiMessage(R.string.error_online_save)
            else -> if (saved?.pending != null) UiMessage(R.string.error_action_unconfirmed) else UiMessage(R.string.error_service_unreachable)
        }
        mutable.update { it.copy(error = message, sessionExpired = it.sessionExpired || (error is RoomApiFailure && error.status == 401)) }
    }
    override fun onCleared() { stream?.cancel(); operation?.cancel(); api?.close(); audio.stop(); super.onCleared() }
}

private class LocalStorageFailure : Exception("Local online storage unavailable")
