package io.github.sbshrey.tambola.protocol

import kotlinx.serialization.Serializable

const val REACTION_LIFETIME_MS = 6_000L
const val REACTION_COOLDOWN_MS = 10_000L

@Serializable enum class FriendReaction { GOOD_LUCK, NICE_WIN, WELL_PLAYED, THANKS }
@Serializable data class RoomReaction(val playerId: String, val roundId: String, val kind: FriendReaction, val at: Long)
/** Separate optional endpoint keeps the strict v6 room snapshot readable by existing clients. */
@Serializable data class ReactionSnapshot(val roomId: String, val roundId: String?, val revision: Long,
    val serverTime: Long, val nextAllowedAt: Long, val reactions: List<RoomReaction>)
