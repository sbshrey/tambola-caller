package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.domain.RoundStatus
import io.github.sbshrey.tambola.protocol.*

fun PendingOperation?.isMark(): Boolean = (this as? PendingOperation.Command)?.request?.action is RoomAction.Mark

private fun OnlineSaved.validMark(mark: RoomAction.Mark): Boolean {
    val game = room?.round ?: return false
    val powers = game.powers ?: return false
    return room.phase == RoomPhase.ACTIVE && game.status == RoundStatus.PLAYING && game.id == mark.roundId &&
        mark.ticketId !in powers.discarded && mark.number in game.called &&
        game.ownTickets.any { it.id == mark.ticketId && it.playerId == credentials.playerId && mark.number in it.numbers }
}

/** Queue entries are intentions; only a persisted promoted command owns a receipt ID. */
fun OnlineSaved.queueMark(ticketId: String, number: Int): OnlineSaved {
    val game = room?.round ?: return this
    val mark = RoomAction.Mark(game.id, ticketId, number)
    if (!validMark(mark) || (pending != null && !pending.isMark()) || queuedMarks.size == 90 ||
        number in game.powers!!.marks[ticketId].orEmpty() || mark in queuedMarks ||
        (pending as? PendingOperation.Command)?.request?.action == mark) return this
    return copy(queuedMarks = queuedMarks + mark)
}

fun OnlineSaved.pruneQueuedMarks(): OnlineSaved = copy(queuedMarks = queuedMarks.filter {
    validMark(it) && it.number !in room!!.round!!.powers!!.marks[it.ticketId].orEmpty()
})

/** Persist this result before sending. A restart then retries the same command ID. */
fun OnlineSaved.promoteMark(id: String): OnlineSaved {
    if (pending != null) return this
    val clean = pruneQueuedMarks()
    val mark = clean.queuedMarks.firstOrNull() ?: return clean
    val view = requireNotNull(clean.room)
    return clean.copy(queuedMarks = clean.queuedMarks.drop(1),
        pending = PendingOperation.Command(view.code, CommandRequest(id, view.revision, mark)))
}

/** Visual pending indicators only: never feed these into claims or power progress. */
fun OnlineSaved.pendingMarkNumbers(): Map<String, Set<Int>> {
    val inFlight = (pending as? PendingOperation.Command)?.request?.action as? RoomAction.Mark
    return (queuedMarks + listOfNotNull(inFlight)).filter { validMark(it) &&
        it.number !in room!!.round!!.powers!!.marks[it.ticketId].orEmpty() }
        .groupBy { it.ticketId }.mapValues { (_, marks) -> marks.map { it.number }.toSet() }
}
