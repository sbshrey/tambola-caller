package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.domain.BingoPattern
import io.github.sbshrey.tambola.domain.RoundStatus
import io.github.sbshrey.tambola.protocol.*

fun PendingOperation?.isBingoMark(): Boolean =
    (this as? PendingOperation.BingoCommand)?.request?.action is BingoAction.Mark

private fun OnlineSaved.validBingoMark(mark: BingoAction.Mark): Boolean {
    val view = bingoRoom ?: return false
    val game = view.round ?: return false
    return view.phase == RoomPhase.ACTIVE && game.status == RoundStatus.PLAYING &&
        game.prizes.map { it.pattern } == listOf(BingoPattern.ANY_LINE, BingoPattern.FOUR_CORNERS) &&
        game.id == mark.roundId && mark.number in game.called &&
        game.ownCards.any { it.id == mark.cardId && it.playerId == credentials.playerId && mark.number in it.numbers }
}

/** Persist each tap before sending; one in-flight receipt can be retried after process death. */
fun OnlineSaved.queueBingoMark(cardId: String, number: Int): OnlineSaved {
    val game = bingoRoom?.round ?: return this
    if (number !in 1..75) return this
    val mark = BingoAction.Mark(game.id, cardId, number)
    if (!validBingoMark(mark) || (pending != null && !pending.isBingoMark()) ||
        queuedBingoMarks.size == 150 || number in game.ownMarks[cardId].orEmpty() ||
        mark in queuedBingoMarks || (pending as? PendingOperation.BingoCommand)?.request?.action == mark) return this
    return copy(queuedBingoMarks = queuedBingoMarks + mark)
}

fun OnlineSaved.pruneQueuedBingoMarks(): OnlineSaved = copy(queuedBingoMarks = queuedBingoMarks.filter {
    validBingoMark(it) && it.number !in bingoRoom!!.round!!.ownMarks[it.cardId].orEmpty()
})

fun OnlineSaved.promoteBingoMark(id: String): OnlineSaved {
    if (pending != null) return this
    val clean = pruneQueuedBingoMarks()
    val mark = clean.queuedBingoMarks.firstOrNull() ?: return clean
    val view = requireNotNull(clean.bingoRoom)
    return clean.copy(queuedBingoMarks = clean.queuedBingoMarks.drop(1),
        pending = PendingOperation.BingoCommand(view.code, BingoCommandRequest(id, view.revision, mark)))
}

/** Shown as queued dabs, never counted as verified marks for prize claims. */
fun OnlineSaved.pendingBingoMarkNumbers(): Map<String, Set<Int>> {
    val inFlight = (pending as? PendingOperation.BingoCommand)?.request?.action as? BingoAction.Mark
    return (queuedBingoMarks + listOfNotNull(inFlight)).filter { validBingoMark(it) &&
        it.number !in bingoRoom!!.round!!.ownMarks[it.cardId].orEmpty() }
        .groupBy { it.cardId }.mapValues { (_, marks) -> marks.map { it.number }.toSet() }
}
