package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.domain.PowerUp

/** Only a definitive pre-purchase mismatch permits changing a join request.
 * Transport failures retain the original durable request and idempotency key. */
fun PendingOperation.Match.followFriendMode(error: RoomApiFailure, replacementId: String): PendingOperation.Match? {
    if (error.status != 409 || error.code != "power_room_mismatch" || !request.friendTable ||
        request.friendCode == null || request.previousFriendRound != null || request.rulesVersion != 2 ||
        request.powerUp != PowerUp.NONE) return null
    require(replacementId.isNotBlank() && replacementId != request.id)
    return PendingOperation.Match(request.copy(id = replacementId, powersEnabled = !request.powersEnabled))
}
