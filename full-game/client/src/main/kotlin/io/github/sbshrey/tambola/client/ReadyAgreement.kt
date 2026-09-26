package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.Serializable

/** What the player saw when tapping Ready, retained across an uncertain response or process death. */
@Serializable data class ReadyAgreement(
    val roomId: String,
    val hostId: String,
    val locked: Boolean,
    val options: RoomOptions,
    val members: List<MemberView>,
)

fun RoomView.readyAgreement(): ReadyAgreement? = if (phase != RoomPhase.LOBBY || round != null) null else
    ReadyAgreement(roomId, hostId, locked, options,
        members.map { it.copy(ready = false, connected = false) }.sortedBy { it.playerId })

/** Only call after a definitive stale_revision rejection, never after an uncertain network failure. */
fun PendingOperation.Command.rebaseReady(latest: RoomView, newRequestId: String): PendingOperation.Command? {
    if (request.action !is RoomAction.Ready || readyAgreement == null || latest.code != code ||
        latest.revision <= request.expectedRevision || readyAgreement != latest.readyAgreement()) return null
    return copy(request = request.copy(id = newRequestId, expectedRevision = latest.revision))
}
