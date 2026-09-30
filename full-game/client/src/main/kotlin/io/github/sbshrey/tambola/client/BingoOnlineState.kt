package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import java.security.MessageDigest

/** Validate a private server projection before persisting it or presenting a prize. */
fun BingoRoomView.validateFor(actor: String) {
    fun check(value: Boolean) { if (!value) throw InvalidRoomResponse() }
    check(variant == GameVariant.BINGO_75 && roomId.isNotBlank() && revision >= 0)
    check(serverTime >= 0 && expiresAt > 0 && pool >= 0)
    check(players.size in 0..50 && players.map { it.id }.distinct().size == players.size)
    val ids = players.map { it.id }.toSet()
    check(cardCounts.keys == ids && cardCounts.values.all { it in 1..6 })
    check(members.size <= 50 && members.map { it.playerId }.distinct().size == members.size)
    check(members.all { member -> players.any { it.id == member.playerId && !it.computer } })
    check(phase == RoomPhase.CLOSED || members.any { it.playerId == actor })
    check(phase == RoomPhase.CLOSED || members.any { it.playerId == hostId })
    check(!friendTable || players.none { it.computer })
    check(pool == cardCounts.values.sum() * COIN_TICKET_PRICE)
    wallet?.validate()
    val game = round
    if (game == null) {
        check(phase in setOf(RoomPhase.LOBBY, RoomPhase.CLOSED) && nextDrawAt == null)
        check(phase != RoomPhase.LOBBY || (if (friendTable) startsAt == null else startsAt != null))
        return
    }
    check(phase != RoomPhase.LOBBY && startsAt == null)
    check(game.id.isNotBlank() && game.players == players && game.cardCounts == cardCounts)
    check(game.called.size <= 75 && game.called.distinct().size == game.called.size && game.called.all { it in 1..75 })
    check(game.winnersPerPattern == 2)
    check(game.prizes == BingoCoinPool(cardCounts.values.sum()).prizes)
    check(game.ownCards.size == (cardCounts[actor] ?: 0) && game.ownCards.all { it.playerId == actor })
    check(game.ownCards.map { it.id }.distinct().size == game.ownCards.size)
    check(game.ownCards.map { it.fingerprint }.distinct().size == game.ownCards.size)
    val cards = game.ownCards.associateBy { it.id }
    check(game.ownMarks.all { (card, marks) -> cards[card]?.let { marks.all { n -> n in it.numbers && n in game.called } } == true })
    check(game.claims.map { it.playerId to it.pattern }.distinct().size == game.claims.size)
    check(game.claims.zipWithNext().all { (a, b) -> a.drawCount <= b.drawCount })
    game.claims.forEachIndexed { index, claim ->
        check(claim.playerId in ids && claim.cardId.isNotBlank() && claim.drawCount in 1..game.called.size)
        val earlier = game.claims.take(index).filter { it.pattern == claim.pattern }
        check(earlier.size < game.winnersPerPattern || earlier.last().drawCount == claim.drawCount)
        if (claim.playerId == actor) check(cards[claim.cardId]?.let {
            claim.pattern.isComplete(it, game.called.take(claim.drawCount).toSet())
        } == true)
    }
    check(game.drawCommitment.matches(Regex("[a-f0-9]{64}")))
    val finished = game.status in setOf(RoundStatus.COMPLETED, RoundStatus.CANCELLED)
    check(if (finished) phase in setOf(RoomPhase.FINISHED, RoomPhase.CLOSED) && nextDrawAt == null
        else phase == RoomPhase.ACTIVE && game.status == RoundStatus.PLAYING && nextDrawAt != null)
    if (!finished) {
        check(game.revealedOrder == null && game.revealedNonce == null && game.winnings.isEmpty())
    } else {
        val order = game.revealedOrder ?: throw InvalidRoomResponse()
        val nonce = game.revealedNonce ?: throw InvalidRoomResponse()
        check(order.size == 75 && order.toSet() == (1..75).toSet() && order.take(game.called.size) == game.called)
        check(nonce.isNotBlank() && nonce.length <= 128)
        val input = "bingo-75-draw-v1\n${game.id}\n$nonce\n${order.joinToString(",")}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        check(game.drawCommitment == digest)
        check(game.winnings.keys == ids)
        check(game.winnings.values.all { it.bonus == 0L && it.prizes <= pool && it.returned <= pool })
        check(game.winnings.values.sumOf { it.prizes + it.returned } == pool)
        val prizeShares = game.prizes.flatMap { prize ->
            val winners = game.claims.filter { it.pattern == prize.pattern }.map { it.playerId }
            if (winners.isEmpty()) emptyList() else coinShares(prize.coins, winners).entries.toList()
        }
        check(game.winnings.all { (player, winnings) -> winnings.prizes == prizeShares.filter { it.key == player }.sumOf { it.value } })
    }
}

/** Old receipts can arrive after newer polling responses; neither cards nor the wallet may rewind. */
fun OnlineSaved.acceptBingo(next: BingoRoomView, previous: BingoRoomView?, allowRoomChange: Boolean = false): Pair<OnlineSaved, BingoRoomView> {
    next.validateFor(credentials.playerId)
    if (previous != null && previous.roomId != next.roomId && !allowRoomChange) throw InvalidRoomResponse()
    val updated = acceptWallet(next.wallet)
    if (previous?.roomId == next.roomId) {
        if (previous.revision > next.revision) return updated to previous
        if (previous.revision == next.revision && (previous.round != next.round || previous.phase != next.phase ||
                previous.players != next.players || previous.cardCounts != next.cardCounts || previous.pool != next.pool)) throw InvalidRoomResponse()
        val before = previous.round
        val after = next.round
        if (before != null && (after == null || before.id != after.id ||
                after.called.take(before.called.size) != before.called || before.ownCards != after.ownCards ||
                before.drawCommitment != after.drawCommitment || after.claims.take(before.claims.size) != before.claims)) throw InvalidRoomResponse()
    }
    return updated to next
}
