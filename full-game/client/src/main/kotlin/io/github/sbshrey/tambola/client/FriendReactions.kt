package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*

/** Reactions never carry names, free text, ticket data, or an identity supplied by the sender. */
fun ReactionSnapshot.validateFor(room: RoomView) {
    if (room.coins?.friendTable != true || room.phase != RoomPhase.ACTIVE || roundId == null ||
        roomId != room.roomId || roundId != room.round?.id || revision < 0 || serverTime < 0 ||
        nextAllowedAt < 0 || nextAllowedAt > serverTime + REACTION_COOLDOWN_MS || reactions.size > 50 ||
        reactions.map { it.playerId }.distinct().size != reactions.size || reactions.any {
            it.roundId != roundId || it.playerId !in room.members.map { member -> member.playerId } ||
                it.at < 0 || it.at > serverTime || serverTime - it.at >= REACTION_LIFETIME_MS
        }) throw InvalidRoomResponse()
}
