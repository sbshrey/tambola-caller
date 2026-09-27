package io.github.sbshrey.tambola.benchmark

import io.github.sbshrey.tambola.protocol.PublicRound

/** Public winning-ticket identities survive receipts observed after a call boundary. */
internal fun closedHouseOrdinals(round: PublicRound, playerId: String): Set<Int> {
    val closedTickets = round.awards.filter { it.prize.isRankedHouse && it.drawIndex < round.called.size }
        .flatMap { it.ticketIds }.toSet()
    return round.winningTickets.filter { it.playerId == playerId && it.id in closedTickets }.map { it.ordinal }.toSet()
}
