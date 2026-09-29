package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*

private const val DELETED_NAME = "Deleted player"

/** Preserve agreed tickets, calls and scores; only explicit profile fields are redacted. */
internal fun RoomRecord.redact(playerId: String): RoomRecord = copy(
    matchPowers = matchPowers - playerId,
    reactions = reactions - playerId,
    members = members.map { if (it.id == playerId) it.copy(name = DELETED_NAME, avatar = 0) else it },
    round = round?.let { game -> game.copy(players = game.players.map { if (it.id == playerId) it.copy(name = DELETED_NAME, avatar = 0) else it }) },
)

internal fun RoomUpdate.redact(playerId: String): RoomUpdate = copy(snapshot = snapshot.copy(
    members = snapshot.members.map { if (it.playerId == playerId) it.copy(displayName = DELETED_NAME, avatar = 0) else it },
    round = snapshot.round?.let { game -> game.copy(players = game.players.map { if (it.id == playerId) it.copy(name = DELETED_NAME, avatar = 0) else it }) },
))
