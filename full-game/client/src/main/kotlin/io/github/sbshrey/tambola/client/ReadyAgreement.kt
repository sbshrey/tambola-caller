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

/** Another claimant/presence update can advance the room while this exact call is still open. */
fun PendingOperation.Command.rebaseClaim(latest: RoomView, newRequestId: String): PendingOperation.Command? {
    val action = request.action as? RoomAction.Claim ?: return null
    val game = latest.round ?: return null
    if (latest.code != code || latest.revision <= request.expectedRevision || latest.phase != RoomPhase.ACTIVE ||
        game.id != action.roundId || game.called.size != action.drawIndex ||
        game.ownTickets.none { it.id == action.selection.ticketId }) return null
    val award = game.awards.firstOrNull { it.prize.name == action.selection.prizeId }
    val custom = game.customAwards.firstOrNull { it.prizeId == action.selection.prizeId }
    if (award != null && (award.drawIndex != action.drawIndex || action.selection.ticketId in award.ticketIds)) return null
    if (custom != null && (custom.drawIndex != action.drawIndex || action.selection.ticketId in custom.ticketIds)) return null
    return copy(request = request.copy(id = newRequestId, expectedRevision = latest.revision))
}
