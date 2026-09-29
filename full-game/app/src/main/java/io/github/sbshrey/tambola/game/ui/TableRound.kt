package io.github.sbshrey.tambola.game.ui

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*

/** Renderable information only; an online table never contains the unrevealed draw order. */
data class TableRound(
    val id: String,
    val settings: RoundSettings,
    val players: List<Player>,
    val tickets: List<Ticket>,
    val called: List<Int>,
    val marks: Map<String, Set<Int>>,
    val awards: List<Award>,
    val customAwards: List<CustomAward>,
    val status: RoundStatus,
    val scores: Map<String, Int>,
    val ticketOwners: List<WinningTicket>,
    val coins: CoinTableView? = null,
    val nextDrawAt: Long? = null,
    val serverTime: Long? = null,
    val callIntervalSeconds: Int = 10,
    val powers: MatchPowers? = null,
    val pendingMarks: Map<String, Set<Int>> = emptyMap(),
) {
    val latest: Int? get() = called.lastOrNull()
    val finished: Boolean get() = status == RoundStatus.COMPLETED || status == RoundStatus.CANCELLED
    fun score(playerId: String): Int = scores[playerId] ?: 0
}

fun Round.toTable(): TableRound = TableRound(id, settings, players, tickets, called, marks, awards,
    customAwards, status, players.associate { it.id to score(it.id) }, players.flatMap { player ->
        tickets.filter { it.playerId == player.id }.mapIndexed { index, ticket -> WinningTicket(ticket.id, player.id, index + 1) }
    })

fun RoomView.toTable(localMarks: Map<String, Set<Int>>): TableRound? = round?.let { game ->
    val marks = game.ownTickets.associate { ticket -> ticket.id to
        (if (options.game.assistedMarking) ticket.numbers.intersect(game.called.toSet())
        else (game.powers?.marks ?: localMarks)[ticket.id].orEmpty().intersect(ticket.numbers.toSet())) }
    val ownLabels = game.ownTickets.mapIndexed { index, ticket -> WinningTicket(ticket.id, ticket.playerId, index + 1) }
    TableRound(game.id, options.game, game.players, game.ownTickets, game.called, marks, game.awards,
        game.customAwards, game.status, game.scores, (ownLabels + game.winningTickets).distinctBy { it.id }, coins, nextDrawAt, serverTime, options.intervalSeconds, game.powers)
}
