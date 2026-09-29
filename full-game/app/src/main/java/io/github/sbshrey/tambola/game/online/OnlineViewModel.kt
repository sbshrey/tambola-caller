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
import io.github.sbshrey.tambola.domain.PowerUp
import io.github.sbshrey.tambola.domain.ClaimSelection
import io.github.sbshrey.tambola.domain.MatchPower
import io.github.sbshrey.tambola.domain.PowerNotice
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.BuildConfig
import io.github.sbshrey.tambola.game.audio.CallAudio
import io.github.sbshrey.tambola.game.audio.SoundCue
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.presentation.*
import io.github.sbshrey.tambola.protocol.*
import io.github.sbshrey.tambola.game.telemetry.BetaTelemetry
import io.github.sbshrey.tambola.game.telemetry.TelemetryOperation
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
    val wallet: WalletView? = null,
    val loginRewards: LoginRewards? = null,
    val preferredTickets: Int = 3,
    val chosenPowerUp: PowerUp = PowerUp.NONE,
    val powersEnabled: Boolean = true,
    val serverTime: ServerTime? = null,
    val marks: Map<String, Set<Int>> = emptyMap(),
    val history: List<RoomView> = emptyList(),
    val badges: BadgeProgress = BadgeProgress(),
    val connection: Connection = Connection.IDLE,
    val busy: Boolean = false,
    val adActive: Boolean = false,
    val pending: Boolean = false,
    val markSending: Boolean = false,
    val pendingMarks: Map<String, Set<Int>> = emptyMap(),
    val deletingProfile: Boolean = false,
    val sessionExpired: Boolean = false,
    val storageFailure: Boolean = false,
    val error: UiMessage? = null,
    val notice: UiMessage? = null,
    val winMoment: WinMoment? = null,
    val claimMessage: UiMessage? = null,
    val reactions: ReactionSnapshot? = null,
    val reactionClock: ServerTime? = null,
    val reactionSending: Boolean = false,
    val reactionNotice: UiMessage? = null,
)

class OnlineViewModel(application: Application) : AndroidViewModel(application) {
    private val store = OnlineStore(application)
    private val api: RoomApi? = BuildConfig.ROOM_API_URL.takeIf { it.isNotEmpty() }?.let {
        HttpRoomApi(it, BuildConfig.DEBUG, discoveryUrl = BuildConfig.ROOM_DISCOVERY_URL.takeIf(String::isNotEmpty))
    }
    private val mutex = Mutex()
    private var saved: OnlineSaved? = null
    private val mutable = MutableStateFlow(OnlineUiState())
    val state = mutable.asStateFlow()
    private var active = false
    private var stream: Job? = null
    private var operation: Job? = null
    private var walletJob: Job? = null
    private var reactionRead: Job? = null
    private var reactionSend: Job? = null
    private var reactionVisibleAfter = Long.MAX_VALUE
    private var preferences = Preferences()
    private val audio = CallAudio(application) { mutable.update { it.copy(error = UiMessage(R.string.error_online_recording)) } }
    private val sessions = api?.let { transport -> DeviceSessions(transport, { saved }) { player, transform ->
        mutex.withLock {
            val current = requireNotNull(saved)
            check(current.credentials.playerId == player) { "Online identity changed" }
            transform(current).also { persist(it) }
        }
    } }

    init {
        viewModelScope.launch { PreferenceStore(application).values.catch { }.collect { preferences = it } }
        viewModelScope.launch {
            try {
                val restored = store.read()
                check(restored == null || restored.endpoint == BuildConfig.ROOM_API_URL)
                restored?.room?.validateFor(restored.credentials.playerId)
                restored?.wallet?.validate()
                restored?.deviceIdentity?.validate()
                saved = restored?.acceptWallet(restored.room?.wallet)?.let { it.copy(preferredTickets = it.ticketPreference()).pruneQueuedMarks() }
                publish()
            } catch (_: Exception) {
                mutable.update { it.copy(storageFailure = true, error = UiMessage(R.string.error_online_restore)) }
            }
            mutable.update { it.copy(loading = false) }
            connect()
            refreshWallet()
        }
    }
    private fun publish() {
        val value = saved
        mutable.update { it.copy(name = value?.displayName, avatar = value?.avatar ?: 0, playerId = value?.credentials?.playerId,
            room = value?.room, wallet = value?.wallet, marks = value?.marks.orEmpty(), history = value?.history.orEmpty(),
            loginRewards = it.loginRewards.takeIf { _ -> value?.credentials?.playerId == it.playerId && value != null },
            preferredTickets = value?.ticketPreference() ?: 3, serverTime = api?.serverTime,
            badges = value?.badgeProgress() ?: BadgeProgress(), pending = value?.pending != null || value?.queuedMarks?.isNotEmpty() == true,
            pendingMarks = value?.pendingMarkNumbers().orEmpty(),
            deletingProfile = value?.pending is PendingOperation.DeleteProfile,
            reactions = it.reactions.takeIf { snapshot -> snapshot?.roomId == value?.room?.roomId && snapshot?.roundId == value?.room?.round?.id && value?.room?.phase == RoomPhase.ACTIVE },
            reactionClock = it.reactionClock.takeIf { _ -> it.reactions?.roomId == value?.room?.roomId && it.reactions?.roundId == value?.room?.round?.id && value?.room?.phase == RoomPhase.ACTIVE },
            reactionSending = it.reactionSending && it.room?.roomId == value?.room?.roomId,
            reactionNotice = it.reactionNotice.takeIf { _ -> it.room?.round?.id == value?.room?.round?.id },
            claimMessage = it.claimMessage?.takeIf { _ -> it.room?.round?.id == value?.room?.round?.id },
            winMoment = it.winMoment?.takeIf { _ -> it.room?.round?.id == value?.room?.round?.id }
                ?.refreshPlayers(value?.room?.round?.players.orEmpty())) }
    }
    private suspend fun persist(value: OnlineSaved?) {
        val clean = value?.pruneQueuedMarks()
        try { store.write(clean) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(storageFailure = true) }; throw LocalStorageFailure() }
        saved = clean
        publish()
    }
    /** Reuse the exact game operation after a server-confirmed session rejection. */
    private suspend fun <T> authorized(operation: TelemetryOperation = TelemetryOperation.REQUEST, action: suspend (String) -> T): T = BetaTelemetry.measure(operation) {
        val manager = requireNotNull(sessions)
        val credentials = manager.credentials()
        try { action(credentials.token) }
        catch (error: RoomApiFailure) {
            if (error.status != 401) throw error
            val renewed = manager.credentials(rejectedToken = credentials.token)
            action(renewed.token)
        }
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) { connect(); refreshWallet() } else {
            stream?.cancel(); stream = null; reactionRead?.cancel(); reactionSend?.cancel(); audio.stop()
            mutable.update { it.copy(connection = if (saved?.room == null) Connection.IDLE else Connection.SUSPENDED,
                winMoment = null, reactions = null, reactionClock = null, reactionSending = false, reactionNotice = null) }
        }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    /** Resolve a share link without sending credentials or changing the current game. */
    suspend fun friendInvitation(code: String): String? = try { api?.friendInvitation(code) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null } // The share message always retains the manual code.
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
                collectDailyRewards()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun refreshWallet() {
        if (saved == null) return
        val api = api ?: return
        if (!active || mutable.value.storageFailure || mutable.value.sessionExpired || mutable.value.deletingProfile ||
            saved?.pending == PendingOperation.Logout || walletJob?.isActive == true) return
        walletJob = viewModelScope.launch {
            try {
                collectDailyRewards()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
        }
    }
    /** Save a device profile and a retryable purchase before entering the first table. */
    private suspend fun collectDailyRewards() {
        val player = saved?.credentials?.playerId ?: return
        val reward = authorized(TelemetryOperation.LOGIN) { requireNotNull(api).loginRewards(it) }
        mutex.withLock {
            saved?.takeIf { it.credentials.playerId == player }?.let {
                persist(it.acceptWallet(reward.wallet))
                mutable.update { state -> state.copy(loginRewards = reward) }
            }
        }
    }

    fun play(tickets: Int, friendTable: Boolean = false, friendCode: String? = null) {
        if (tickets !in 1..6 || api == null || mutable.value.adActive || mutable.value.busy || mutable.value.pending || mutable.value.storageFailure || mutable.value.sessionExpired) return
        val code = friendCode?.trim()?.uppercase(java.util.Locale.ROOT)
        if (code != null && !Regex("[A-HJ-NP-Z2-9]{8}").matches(code)) { mutable.update { it.copy(error = UiMessage(R.string.error_room_code)) }; return }
        val request = MatchRequest(UUID.randomUUID().toString(), tickets, friendTable, code, rulesVersion = 2,
            powersEnabled = mutable.value.powersEnabled, largeMatch = !friendTable, previewPowers = mutable.value.powersEnabled, roundSummary = true)
        if (saved != null) { begin(PendingOperation.Match(request)); return }
        mutable.update { it.copy(busy = true, error = null) }
        operation = viewModelScope.launch {
            try {
                val name = getApplication<Application>().getString(R.string.coin_guest, Random.nextInt(1000, 10000))
                val avatar = Random.nextInt(io.github.sbshrey.tambola.domain.AVATAR_COUNT)
                val credentials = api.guest(GuestRequest(name, avatar))
                mutex.withLock {
                    persist(OnlineSaved(BuildConfig.ROOM_API_URL, credentials, name, avatar)
                        .withPending(PendingOperation.Match(request)))
                }
                collectDailyRewards()
                performPending()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
            finally { mutable.update { it.copy(busy = false) }; connect() }
        }
    }
    fun beginAd(): Boolean {
        val state = mutable.value
        if (state.adActive || state.busy || state.pending || state.room?.phase in setOf(RoomPhase.LOBBY, RoomPhase.ACTIVE)) return false
        mutable.update { it.copy(adActive = true) }; return true
    }
    fun endAd() { mutable.update { it.copy(adActive = false) } }
    suspend fun prepareAd(): RewardAdIntent {
        check(BuildConfig.REWARDED_ADS_ENABLED && !BuildConfig.REWARDED_ADS_TEST)
        check(!mutable.value.busy && !mutable.value.pending && mutable.value.room?.phase !in setOf(RoomPhase.LOBBY, RoomPhase.ACTIVE))
        return authorized { requireNotNull(api).prepareAd(it) }.also {
            check(it.adUnit == BuildConfig.ADMOB_REWARD_UNIT && it.coins == AD_REWARD_COINS)
        }
    }
    suspend fun confirmAd(id: String): Boolean {
        val player = saved?.credentials?.playerId ?: return false
        repeat(15) {
            val result = authorized { requireNotNull(api).adStatus(it, id) }
            mutex.withLock {
                saved?.takeIf { it.credentials.playerId == player }?.let { persist(it.acceptWallet(result.wallet)) }
            }
            if (result.confirmed) return true
            delay(2_000)
        }
        return false
    }
    fun refill() = begin(PendingOperation.Refill(RefillRequest(UUID.randomUUID().toString())))
    fun choosePowerUp(powerUp: PowerUp) {
        if (!mutable.value.busy && !mutable.value.pending) mutable.update { it.copy(chosenPowerUp = powerUp) }
    }
    fun choosePowerRoom(enabled: Boolean) {
        if (!mutable.value.busy && !mutable.value.pending) mutable.update { it.copy(powersEnabled = enabled, chosenPowerUp = PowerUp.NONE) }
    }
    fun replayFriends(tickets: Int) {
        val room = saved?.room ?: return
        if (tickets !in 1..6 || room.phase != RoomPhase.FINISHED || room.coins?.friendTable != true) return
        val round = room.round ?: return
        begin(PendingOperation.Match(MatchRequest(UUID.randomUUID().toString(), tickets, true, room.code, round.id, rulesVersion = 2,
            powersEnabled = room.options.powersEnabled, previewPowers = room.options.previewPowers, roundSummary = room.options.roundSummary)))
    }
    fun create(options: RoomOptions = RoomOptions(game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 3, assistedMarking = false, manualClaims = true,
        prizes = listOf(Prize.EARLY_FIVE, Prize.CORNERS, Prize.TOP_LINE, Prize.MIDDLE_LINE, Prize.BOTTOM_LINE, Prize.FULL_HOUSE)), intervalSeconds = 10, computerPlayers = 2)) =
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
    fun claim(selection: ClaimSelection) { saved?.claimAction(selection)?.let(::command) }
    fun usePower(ticketId: String, power: MatchPower) {
        saved?.room?.round?.let { command(RoomAction.UsePower(it.id, ticketId, power)) }
    }
    fun logout() = begin(PendingOperation.Logout)
    fun deleteProfile() = begin(PendingOperation.DeleteProfile(DeleteProfileRequest(UUID.randomUUID().toString())))
    private fun begin(pending: PendingOperation) {
        if (saved == null || mutable.value.busy || mutable.value.pending || mutable.value.storageFailure || (mutable.value.sessionExpired && pending !is PendingOperation.DeleteProfile)) return
        if (pending is PendingOperation.Create || pending is PendingOperation.Join) {
            if (saved?.room != null) { mutable.update { it.copy(error = UiMessage(R.string.error_leave_first)) }; return }
        }
        if (pending is PendingOperation.Match && saved?.room?.phase in setOf(RoomPhase.LOBBY, RoomPhase.ACTIVE)) { connect(); return }
        if (pending.pausesRoomStream()) { stream?.cancel(); stream = null; audio.stop() }
        if (pending is PendingOperation.DeleteProfile || pending == PendingOperation.Logout) walletJob?.cancel()
        mutable.update { it.copy(busy = true, error = null, notice = null,
            deletingProfile = pending is PendingOperation.DeleteProfile,
            connection = if (pending is PendingOperation.DeleteProfile) Connection.SUSPENDED else it.connection) }
        operation = viewModelScope.launch {
            try {
                if (pending is PendingOperation.DeleteProfile || pending == PendingOperation.Logout) walletJob?.join()
                if (pending is PendingOperation.DeleteProfile) sessions?.settleBeforeDeletion()
                mutex.withLock { persist(saved!!.withPending(pending)) }; performPending()
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
            finally { mutable.update { it.copy(busy = false, deletingProfile = saved?.pending is PendingOperation.DeleteProfile) }; connect() }
        }
    }
    fun retry() {
        if (mutable.value.busy || mutable.value.storageFailure) return
        if (saved?.pending == null) { startMarkDrain(force = true); return }
        if (saved?.pending.pausesRoomStream()) { stream?.cancel(); stream = null; audio.stop() }
        mutable.update { it.copy(busy = true, error = null, markSending = saved?.pending.isMark()) }
        operation = viewModelScope.launch {
            try { performPending() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
            finally {
                mutable.update { it.copy(busy = false, markSending = false) }; connect()
                startMarkDrain()
            }
        }
    }
    private suspend fun performPending(readyRetries: Int = 3, friendModeRetry: Boolean = true) {
        val current = saved ?: return
        val pending = current.pending ?: return
        val api = api ?: return
        var deletionConfirmed = false
        var walletResult: WalletView? = null
        try {
            val result = when (pending) {
                is PendingOperation.Match -> authorized(TelemetryOperation.MATCH) { api.match(it, pending.request) }
                is PendingOperation.Refill -> { walletResult = authorized { api.refill(it, pending.request) }; null }
                is PendingOperation.Create -> authorized { api.create(it, pending.request) }
                is PendingOperation.Join -> authorized { api.join(it, pending.code) }
                is PendingOperation.Command -> authorized(if (pending.request.action is RoomAction.Claim) TelemetryOperation.CLAIM else TelemetryOperation.REQUEST) { api.command(it, pending.code, pending.request) }
                PendingOperation.Logout -> { authorized { api.logout(it) }; null }
                is PendingOperation.DeleteProfile -> { api.deleteProfile(current.credentials.token, pending.request); deletionConfirmed = true; null }
            }
            mutex.withLock {
                val latest = saved ?: return@withLock
                if (pending == PendingOperation.Logout || pending is PendingOperation.DeleteProfile) {
                    stream?.cancel(); stream = null; audio.stop(); persist(null)
                    mutable.update { it.copy(connection = Connection.IDLE, sessionExpired = false,
                        notice = if (deletionConfirmed) UiMessage(R.string.notice_profile_deleted) else null) }
                } else if (pending is PendingOperation.Refill) {
                    persist(latest.acceptWallet(requireNotNull(walletResult)).copy(pending = null))
                } else if (pending is PendingOperation.Command && pending.request.action == RoomAction.Leave) {
                    stream?.cancel(); stream = null; audio.stop(); persist(latest.acceptWallet(result?.snapshot?.wallet).copy(room = null, marks = emptyMap(), pending = null))
                    mutable.update { it.copy(connection = Connection.IDLE) }
                } else {
                    val accepted = latest.accept(requireNotNull(result), live = active && mutable.value.connection == Connection.LIVE,
                        allowRoomChange = pending is PendingOperation.Create || pending is PendingOperation.Join || pending is PendingOperation.Match)
                    persist(accepted.saved.copy(pending = null))
                    announceAccepted(latest, accepted)
                    if ((pending as? PendingOperation.Command)?.request?.action is RoomAction.Mark) mutable.update { it.copy(claimMessage = null) }
                    val claim = (pending as? PendingOperation.Command)?.request?.action as? RoomAction.Claim
                    if (claim != null) {
                        val powers = result.snapshot.round?.powers
                        val penalty = powers != null && result.snapshot.round?.awards.orEmpty().none {
                            it.prize.name == claim.selection.prizeId && claim.selection.ticketId in it.ticketIds
                        }
                        val message = if (penalty) UiMessage(if (powers?.notice == PowerNotice.SHIELD_SAVED) R.string.power_shield_saved else R.string.power_discarded)
                        else verifiedClaimFeedback(claim.selection,
                            result.snapshot.round?.ownTickets.orEmpty(), result.snapshot.options.game)
                        mutable.update { it.copy(claimMessage = message) }
                    }
                }
            }
        } catch (error: RoomApiFailure) {
            val friendJoin = if (friendModeRetry) (pending as? PendingOperation.Match)?.followFriendMode(error, UUID.randomUUID().toString(), supportsPreview = true) else null
            if (friendJoin != null) {
                val replaced = mutex.withLock {
                    val latest = saved ?: return@withLock false
                    if (latest.credentials.playerId != current.credentials.playerId || latest.pending != pending) return@withLock false
                    // The server definitively rejected the old mode without charging.
                    // Persist the replacement before sending it, including across process death.
                    persist(latest.withPending(friendJoin))
                    true
                }
                if (replaced) { performPending(readyRetries, friendModeRetry = false); return }
            }
            if (error.status == 401 && pending == PendingOperation.Logout) {
                mutex.withLock { persist(null) }; stream?.cancel(); stream = null
                mutable.update { it.copy(connection = Connection.IDLE) }; return
            }
            if (pending !is PendingOperation.DeleteProfile && error.status in 400..499 && error.status !in listOf(401, 408, 429)) mutex.withLock { saved?.let { persist(it.copy(pending = null)) } }
            if (pending is PendingOperation.Refill && error.code == "refill_wait") mutex.withLock { saved?.let { persist(it.copy(pending = null)) } }
            if (error.code in setOf("coins_low", "refill_not_needed", "refill_wait")) refreshWallet()
            if (error.status == 409 && current.room != null && pending is PendingOperation.Command) {
                val update = authorized { api.read(it, current.room!!.code) }
                val retryReady = mutex.withLock {
                    val latest = saved ?: return@withLock false
                    val refreshed = latest.accept(update, live = false).saved
                    val replacement = if (error.code == "stale_revision" && readyRetries > 0 && pending is PendingOperation.Command)
                        refreshed.room?.let { pending.rebaseReady(it, UUID.randomUUID().toString()) ?: pending.rebaseClaim(it, UUID.randomUUID().toString()) } else null
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
        if (!active || mutable.value.loading || mutable.value.storageFailure || mutable.value.sessionExpired ||
            mutable.value.deletingProfile || saved?.pending.pausesRoomStream() ||
            saved?.room == null || api == null || stream?.isActive == true) return
        stream = viewModelScope.launch {
            var attempts = 0
            var rejectedToken: String? = null
            var authRetried = false
            while (isActive && active && saved?.room != null) {
                val session = saved ?: break
                val room = session.room ?: break
                mutable.update { it.copy(connection = if (attempts == 0) Connection.CONNECTING else Connection.RECONNECTING) }
                var received = false
                var usedToken: String? = null
                try {
                    val token = requireNotNull(sessions).credentials(rejectedToken).token
                    usedToken = token; rejectedToken = null
                    api.events(token, room.code, room.revision).catch { error ->
                        // Resolve a closed socket via REST: an expired countdown may have refunded.
                        if (error is RoomStreamClosed) emit(authorized { api.read(it, room.code) })
                        throw error
                    }.collect { update ->
                        val firstSnapshot = !received
                        val newRound = saved?.room?.round?.id != update.snapshot.round?.id
                        if (firstSnapshot || newRound) reactionVisibleAfter = update.snapshot.serverTime
                        mutex.withLock {
                            val latest = saved ?: return@withLock
                            if (latest.room?.roomId != room.roomId) return@withLock
                            val accepted = latest.accept(update, live = received && active)
                            persist(accepted.saved)
                            mutable.update { it.copy(connection = Connection.LIVE) }
                            // Silent on first snapshot and catch-up. Explicit "Hear again" remains available.
                            announceAccepted(latest, accepted)
                            received = true; attempts = 0; authRetried = false
                        }
                        if (firstSnapshot || mutable.value.reactions != null || update.events.any { it.type == "reacted" || it.type == "started" }) {
                            refreshReactions(show = !firstSnapshot && !update.resyncRequired)
                        }
                        startMarkDrain()
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: RoomApiFailure) {
                    currentCoroutineContext().ensureActive()
                    if (saved?.credentials?.playerId != session.credentials.playerId || saved?.room?.roomId != room.roomId ||
                        saved?.pending.pausesRoomStream()) break
                    if (error.status == 401) {
                        if (usedToken != null && !authRetried) {
                            rejectedToken = usedToken; authRetried = true
                            mutable.update { it.copy(connection = Connection.RECONNECTING) }
                            continue
                        }
                        showFailure(error); mutable.update { it.copy(connection = Connection.SUSPENDED) }; break
                    }
                    if (error.code in setOf("not_member", "room_missing", "room_closed")) {
                        val detached = mutex.withLock {
                            currentCoroutineContext().ensureActive()
                            val latest = saved ?: return@withLock false
                            if (latest.credentials.playerId != session.credentials.playerId || latest.room?.roomId != room.roomId ||
                                latest.pending.pausesRoomStream()) return@withLock false
                            persist(latest.copy(room = null, marks = emptyMap(),
                                pending = latest.pending.takeUnless { pending -> pending is PendingOperation.Command && pending.code == room.code }))
                            mutable.update { it.copy(connection = Connection.IDLE, error = UiMessage(R.string.error_room_unavailable)) }
                            true
                        }
                        if (detached) refreshWallet()
                        break
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
        val moment = if (accepted.liveAwards) accepted.saved.room?.newWinMoment(previous.room) else null
        if (moment != null) mutable.update { it.copy(winMoment = moment) }
        accepted.announcement?.let { number ->
            mutable.update { it.copy(claimMessage = null, reactionNotice = null) }
            audio.play(number, preferences.language, celebration = moment != null)
        }
        if (accepted.announcement == null && moment != null) audio.effect(SoundCue.WIN)
        if (moment != null) audio.prizes(moment, preferences.language)
        if (accepted.announcement == null && moment == null && accepted.saved.marks != previous.marks) audio.effect(SoundCue.MARK)
    }
    private fun refreshReactions(show: Boolean) {
        val session = saved ?: return
        val room = session.room ?: return
        if (!active || room.coins?.friendTable != true || room.phase != RoomPhase.ACTIVE) return
        reactionRead?.cancel()
        reactionRead = viewModelScope.launch {
            try {
                val snapshot = authorized { requireNotNull(api).reactions(it, room.code) }
                if (!sameReactionRoom(session)) return@launch
                snapshot?.validateFor(requireNotNull(saved?.room))
                mutable.update { it.copy(reactionClock = snapshot?.let { value -> ServerTime(value.serverTime, System.nanoTime()) }, reactions = snapshot?.let { value -> value.copy(reactions =
                    if (show) value.reactions.filter { reaction -> reaction.at > reactionVisibleAfter } else emptyList()) }) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Optional social updates must not suspend gameplay. */ }
        }
    }
    private fun sameReactionRoom(session: OnlineSaved): Boolean = active &&
        mutable.value.connection == Connection.LIVE && !mutable.value.deletingProfile &&
        saved?.credentials?.playerId == session.credentials.playerId && saved?.room?.roomId == session.room?.roomId &&
        saved?.room?.round?.id == session.room?.round?.id && saved?.room?.phase == RoomPhase.ACTIVE

    fun react(kind: FriendReaction) {
        val session = saved ?: return
        val room = session.room ?: return
        val game = room.round ?: return
        val ui = mutable.value
        if (!sameReactionRoom(session) || ui.reactions == null || ui.reactionSending || ui.busy || ui.pending || ui.storageFailure || ui.sessionExpired) return
        val request = CommandRequest(UUID.randomUUID().toString(), room.revision, RoomAction.React(game.id, kind))
        mutable.update { it.copy(reactionSending = true, reactionNotice = null) }
        reactionSend = viewModelScope.launch {
            try {
                // No optimistic message: only the authenticated server event is displayed.
                authorized { requireNotNull(api).react(it, room.code, request) }
                if (sameReactionRoom(session)) refreshReactions(show = true)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (sameReactionRoom(session)) {
                    mutable.update { it.copy(reactionNotice = UiMessage(R.string.reaction_not_sent)) }
                    refreshReactions(show = false)
                }
            } finally {
                if (saved?.room?.roomId == room.roomId) mutable.update { it.copy(reactionSending = false) }
            }
        }
    }
    fun reconnect() { stream?.cancel(); stream = null; connect() }
    private fun startMarkDrain(force: Boolean = false) {
        val ui = mutable.value
        val current = saved ?: return
        if (!active || ui.connection != Connection.LIVE || ui.busy || ui.storageFailure || ui.sessionExpired ||
            current.pending != null || current.queuedMarks.isEmpty() || (!force && ui.error != null)) return
        mutable.update { it.copy(busy = true, markSending = true, error = null) }
        operation = viewModelScope.launch {
            try {
                while (active && mutable.value.connection == Connection.LIVE) {
                    val send = mutex.withLock {
                        val latest = saved ?: return@withLock false
                        if (latest.pending != null) return@withLock false
                        val promoted = latest.promoteMark(UUID.randomUUID().toString())
                        persist(promoted)
                        promoted.pending.isMark()
                    }
                    if (!send) break
                    performPending()
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
            finally {
                mutable.update { it.copy(busy = false, markSending = false) }
                connect()
                startMarkDrain()
            }
        }
    }
    fun mark(ticketId: String, number: Int) {
        if (saved?.pending is PendingOperation.DeleteProfile) return
        val game = saved?.room?.round ?: return
        if (number !in game.called) { mutable.update { it.copy(claimMessage = UiMessage(R.string.power_wrong_mark)) }; return }
        if (game.powers != null) {
            val ui = mutable.value
            if (ui.connection != Connection.LIVE || ui.storageFailure || ui.sessionExpired ||
                (ui.busy && !ui.markSending) || (ui.pending && !ui.markSending)) return
            viewModelScope.launch {
                try {
                    mutex.withLock {
                        val current = saved ?: return@withLock
                        val next = current.queueMark(ticketId, number)
                        if (next != current) {
                            persist(next)
                            mutable.update { it.copy(claimMessage = null) }
                            if (active) audio.effect(SoundCue.MARK)
                        }
                    }
                    startMarkDrain()
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { showFailure(error) }
            }
            return
        }
        viewModelScope.launch {
            try { mutex.withLock { saved?.let {
                val next = it.mark(ticketId, number)
                if (next != it) { persist(next); mutable.update { value -> value.copy(claimMessage = null) }; if (active) audio.effect(SoundCue.MARK) }
            } } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(error) }
        }
    }
    fun dabCalled() {
        if (saved?.pending is PendingOperation.DeleteProfile) return
        if (saved?.room?.options?.powersEnabled == true) return
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
        stream?.cancel(); stream = null; walletJob?.cancel(); audio.stop()
        viewModelScope.launch {
            try {
                mutex.withLock { persist(null) }
                mutable.value = OnlineUiState(loading = false)
            } catch (error: Exception) { showFailure(error) }
        }
    }
    private fun showFailure(error: Exception) {
        mutable.update { it.copy(serverTime = api?.serverTime) }
        if (error is RoomApiFailure && error.code in setOf("no_valid_claim", "claim_window_closed", "claim_round_changed", "invalid_claim_marks", "invalid_claim_selection")) {
            val resource = when (error.code) {
                "claim_window_closed", "claim_round_changed" -> R.string.play_claim_late
                "invalid_claim_marks", "invalid_claim_selection" -> R.string.play_claim_marks
                else -> R.string.play_claim_none
            }
            mutable.update { it.copy(claimMessage = UiMessage(resource)) }
            return
        }
        val message = when (error) {
            is RoomApiFailure -> when {
                error.code == "coins_low" -> UiMessage(R.string.coin_low)
                error.code == "refill_wait" -> UiMessage(R.string.coin_refill_wait)
                error.code == "refill_not_needed" -> UiMessage(R.string.coin_refill_not_needed)
                else -> when (error.status) {
                401 -> if (saved?.pending is PendingOperation.DeleteProfile) UiMessage(R.string.error_delete_unconfirmed)
                    else UiMessage(R.string.error_session_expired)
                409 -> if (error.code == "stale_revision") UiMessage(R.string.error_stale_revision) else roomFailureMessage(error)
                413 -> UiMessage(R.string.error_rules_large)
                else -> roomFailureMessage(error)
                }
            }
            is InvalidRoomResponse -> UiMessage(R.string.error_incompatible)
            is LocalStorageFailure -> UiMessage(R.string.error_online_save)
            else -> if (saved?.pending != null) UiMessage(R.string.error_action_unconfirmed) else UiMessage(R.string.error_service_unreachable)
        }
        mutable.update { it.copy(error = message, sessionExpired = it.sessionExpired || (error is RoomApiFailure && error.status == 401)) }
    }
    override fun onCleared() { stream?.cancel(); operation?.cancel(); walletJob?.cancel(); api?.close(); audio.stop(); super.onCleared() }
}

private class LocalStorageFailure : Exception("Local online storage unavailable")

/** A leave may commit before its HTTP receipt arrives. Keep that receipt retryable;
 * losing room membership is expected until the exact command is confirmed. */
private fun PendingOperation?.pausesRoomStream(): Boolean = this is PendingOperation.DeleteProfile ||
    this is PendingOperation.Match || this == PendingOperation.Logout ||
    (this is PendingOperation.Command && request.action == RoomAction.Leave)
