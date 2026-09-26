package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64

@Serializable data class DeviceIdentity(val key: String, val revision: Long? = null, val pending: RenewSessionRequest? = null) {
    fun validate() {
        require(key.matches(Regex("[A-Za-z0-9_-]{43}")))
        require(revision == null || revision >= 0)
        pending?.let {
            require(it.expectedRevision == (revision ?: 0L) && it.expectedRevision < Long.MAX_VALUE)
            require(it.token != key && it.token.matches(Regex("[A-Za-z0-9_-]{43}")))
        }
    }
    override fun toString() = "DeviceIdentity(revision=$revision, pending=${pending != null}, key=redacted)"
}

/** One renewal across wallet, command and stream callers. Every secret/intent is persisted
 * before transport; writes transform the latest state to retain concurrent marks/receipts. */
class DeviceSessions(
    private val api: RoomApi,
    private val read: () -> OnlineSaved?,
    private val update: suspend (String, (OnlineSaved) -> OnlineSaved) -> OnlineSaved,
) {
    private val mutex = Mutex()
    private val random = SecureRandom()
    private fun secret() = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun current(player: String? = null): OnlineSaved = requireNotNull(read()).also {
        check(player == null || it.credentials.playerId == player) { "Online identity changed" }
        it.deviceIdentity?.validate()
    }

    suspend fun credentials(rejectedToken: String? = null): GuestCredentials = mutex.withLock {
        val player = current().credentials.playerId
        // Deletion proof uses the original token; no background renewal may change it.
        if (current(player).pending is PendingOperation.DeleteProfile) return@withLock current(player).credentials
        if (current(player).deviceIdentity == null) {
            val key = secret()
            update(player) { it.copy(deviceIdentity = DeviceIdentity(key)) }
        }
        finishPending(player)
        var value = current(player)
        val identity = requireNotNull(value.deviceIdentity)
        if (identity.revision == null) {
            try {
                val response = api.enrollDevice(value.credentials.token, EnrollDeviceRequest(identity.key))
                if (response.playerId != player || response.revision < 0) throw InvalidRoomResponse()
                update(player) { it.copy(deviceIdentity = requireNotNull(it.deviceIdentity).copy(revision = response.revision)) }
            } catch (error: RoomApiFailure) {
                if (error.status != 401) throw error
                // Enrollment may have committed before its response was lost and the old session expired.
                beginRenewal(player)
                finishPending(player)
            }
        }
        value = current(player)
        // Only the server decides expiry; changing a phone's clock must not cause renewal loops.
        if (rejectedToken != null && value.credentials.token == rejectedToken) {
            beginRenewal(player)
            finishPending(player)
        }
        current(player).credentials
    }

    /** Finish an interrupted rotation before freezing a deletion's bearer proof. Does not
     * enroll or refresh an expired legacy session: deletion already accepts that session. */
    suspend fun settleBeforeDeletion(): GuestCredentials = mutex.withLock {
        val player = current().credentials.playerId
        try { finishPending(player) }
        catch (error: RoomApiFailure) {
            // An unenrolled expired legacy profile can still be deleted with its old token.
            // Only the deletion endpoint can confirm that; never clear local state here.
            if (error.status != 401) throw error
        }
        current(player).credentials
    }

    private suspend fun beginRenewal(player: String) {
        val identity = requireNotNull(current(player).deviceIdentity)
        if (identity.pending != null) return
        val request = RenewSessionRequest(identity.revision ?: 0L, secret())
        update(player) { it.copy(deviceIdentity = requireNotNull(it.deviceIdentity).copy(pending = request)) }
    }

    private suspend fun finishPending(player: String) {
        val identity = current(player).deviceIdentity ?: return
        val request = identity.pending ?: return
        val response = api.renewSession(identity.key, request)
        if (response.credentials.playerId != player || response.credentials.token != request.token ||
            response.credentials.expiresAt <= 0 || response.revision != request.expectedRevision + 1) throw InvalidRoomResponse()
        update(player) { latest ->
            check(latest.deviceIdentity?.pending == request) { "Session intent changed" }
            latest.copy(credentials = response.credentials,
                deviceIdentity = requireNotNull(latest.deviceIdentity).copy(revision = response.revision, pending = null))
        }
    }
}
