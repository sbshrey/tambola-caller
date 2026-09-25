package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable sealed class PendingOperation {
    @Serializable @SerialName("create") data class Create(val request: CreateRoomRequest) : PendingOperation()
    @Serializable @SerialName("join") data class Join(val code: String) : PendingOperation()
    @Serializable @SerialName("command") data class Command(val code: String, val request: CommandRequest) : PendingOperation()
    @Serializable @SerialName("logout") data object Logout : PendingOperation()
    @Serializable @SerialName("delete_profile") data class DeleteProfile(val request: DeleteProfileRequest) : PendingOperation()
}

@Serializable data class OnlineSaved(
    val endpoint: String,
    val credentials: GuestCredentials,
    val displayName: String,
    val avatar: Int = 0,
    val room: RoomView? = null,
    val marks: Map<String, Set<Int>> = emptyMap(),
    val pending: PendingOperation? = null,
    val history: List<RoomView> = emptyList(),
    val badges: BadgeProgress = BadgeProgress(),
) {
    override fun toString(): String = "OnlineSaved(session=redacted, room=${room?.code}, pending=${pending != null})"
}

data class AcceptedRoom(val saved: OnlineSaved, val announcement: Int?)

/** Apply only current snapshots. Receipt replay may legitimately contain an older revision. */
fun OnlineSaved.accept(update: RoomUpdate, live: Boolean, allowRoomChange: Boolean = false): AcceptedRoom {
    val next = update.snapshot
    next.validateFor(credentials.playerId)
    val previous = room
    if (previous != null && previous.roomId != next.roomId && !allowRoomChange) throw InvalidRoomResponse()
    if (previous?.roomId == next.roomId && previous.revision > next.revision) return AcceptedRoom(this, null)
    val previousGame = previous?.round
    val nextGame = next.round
    val sameRound = previousGame != null && previousGame.id == nextGame?.id
    if (sameRound && nextGame!!.called.take(previousGame!!.called.size) != previousGame.called) throw InvalidRoomResponse()
    val nextMarks = if (!sameRound) emptyMap() else marks.filterKeys { key -> nextGame!!.ownTickets.any { it.id == key } }
    val finished = next.round?.status in setOf(RoundStatus.COMPLETED, RoundStatus.CANCELLED)
    val archive = if (finished) (listOf(next) + history.filterNot { it.round?.id == next.round?.id }).take(50) else history
    val number = if (live && !update.resyncRequired && sameRound && nextGame!!.called.size == previousGame!!.called.size + 1)
        nextGame.called.last() else null
    val progress = badgeProgress().let { current -> nextGame?.let { current.record(it.id, it.status,
        it.awards.hasHouseFor(setOf(credentials.playerId))) } ?: current }
    return AcceptedRoom(copy(room = next, marks = nextMarks, history = archive, badges = progress,
        avatar = next.members.firstOrNull { it.playerId == credentials.playerId }?.avatar ?: avatar), number)
}

/** Lazily includes pre-badge cached results; later writes retain milestones beyond the 50-result cache. */
fun OnlineSaved.badgeProgress(): BadgeProgress = history.fold(badges) { progress, entry ->
    entry.round?.let { progress.record(it.id, it.status, it.awards.hasHouseFor(setOf(credentials.playerId))) } ?: progress
}

fun OnlineSaved.mark(ticketId: String, number: Int): OnlineSaved {
    val game = room?.round ?: return this
    if (game.status !in setOf(RoundStatus.PLAYING, RoundStatus.PAUSED) || room.options.game.assistedMarking) return this
    if (number !in game.called || game.ownTickets.none { it.id == ticketId && number in it.numbers }) return this
    val old = marks[ticketId].orEmpty()
    return copy(marks = marks + (ticketId to if (number in old) old - number else old + number))
}

/** Reject malformed or privacy-breaking snapshots at the boundary, before saving/rendering. */
fun RoomView.validateFor(playerId: String) {
    try {
        // Version-one cached receipts/snapshots predate round avatars; Player defaults them to zero.
        require(protocolVersion in 1..PROTOCOL_VERSION && revision >= 0 && roomId.isNotBlank())
        require(Regex("[A-HJ-NP-Z2-9]{8}").matches(code))
        require(members.size <= 32 && members.map { it.playerId }.distinct().size == members.size)
        require(members.all { it.displayName.isNotBlank() && it.displayName.length <= 40 && it.avatar in 0 until AVATAR_COUNT })
        require(phase == RoomPhase.CLOSED || members.any { it.playerId == playerId })
        round?.let { game ->
            require(phase in setOf(RoomPhase.ACTIVE, RoomPhase.FINISHED, RoomPhase.CLOSED))
            require(game.players.size in 2..32 && game.players.map { it.id }.distinct().size == game.players.size)
            require(game.players.none { it.computer } && game.players.any { it.id == playerId })
            require(game.ownTickets.size == options.game.ticketsPerPlayer && game.ownTickets.all { it.playerId == playerId })
            require(game.ownTickets.map { it.id }.distinct().size == game.ownTickets.size)
            require(game.called.size <= 90 && game.called.distinct() == game.called && game.called.all { it in 1..90 })
            require(game.status != RoundStatus.READY)
            val finished = game.status in setOf(RoundStatus.COMPLETED, RoundStatus.CANCELLED)
            require(phase != RoomPhase.ACTIVE || !finished)
            require(phase != RoomPhase.FINISHED || finished)
            require(Regex("[0-9a-f]{64}").matches(game.drawCommitment))
            if (finished) {
                val order = requireNotNull(game.revealedOrder)
                val nonce = requireNotNull(game.revealedNonce)
                require(order.size == 90 && order.toSet() == (1..90).toSet() && order.take(game.called.size) == game.called)
                val input = "tambola-draw-v1\n${game.id}\n$nonce\n${order.joinToString(",")}"
                val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                require(digest == game.drawCommitment)
            } else require(game.revealedOrder == null && game.revealedNonce == null)
            val players = game.players.map { it.id }.toSet()
            val winners = (game.awards.flatMap { it.ticketIds } + game.customAwards.flatMap { it.ticketIds }).toSet()
            require(game.winningTickets.map { it.id }.toSet() == winners && game.winningTickets.size == winners.size)
            require(game.winningTickets.all { it.playerId in players && it.ordinal in 1..options.game.ticketsPerPlayer })
            require(game.awards.map { it.prize }.distinct().size == game.awards.size)
            require(game.awards.all { it.prize in options.game.prizes && it.drawIndex in 1..game.called.size })
            require(game.customAwards.map { it.prizeId }.distinct().size == game.customAwards.size)
            require(game.customAwards.all { award -> options.game.customPrizes.any { it.id == award.prizeId && it.version == award.ruleVersion } && award.drawIndex in 1..game.called.size })
            val groups = game.awards.map { it.ticketIds to it.playerIds } + game.customAwards.map { it.ticketIds to it.playerIds }
            require(groups.all { (tickets, owners) -> tickets.isNotEmpty() && tickets.distinct().size == tickets.size &&
                owners.isNotEmpty() && owners.distinct().size == owners.size && owners.toSet() == game.winningTickets.filter { it.id in tickets }.map { it.playerId }.toSet() })
            val scores = players.associateWith { id -> game.awards.filter { id in it.playerIds }.sumOf { it.prize.points } +
                game.customAwards.filter { id in it.playerIds }.sumOf { award -> options.game.customPrizes.first { it.id == award.prizeId }.points } }
            require(scores == game.scores)
        } ?: require(phase in setOf(RoomPhase.LOBBY, RoomPhase.CLOSED))
    } catch (_: IllegalArgumentException) { throw InvalidRoomResponse() }
}
