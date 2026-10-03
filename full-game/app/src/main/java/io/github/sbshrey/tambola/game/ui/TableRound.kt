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
    /** Server presence for human seats. Null for an offline round. */
    val connectedHumanIds: Set<String>? = null,
) {
    val latest: Int? get() = called.lastOrNull()
    val finished: Boolean get() = status == RoundStatus.COMPLETED || status == RoundStatus.CANCELLED
    fun score(playerId: String): Int = scores[playerId] ?: 0
    val livePeople: Int get() = players.count { !it.computer && it.id in connectedHumanIds.orEmpty() }
    val computerSeats: Int get() = players.count { it.computer }
    fun playerCountText(words: GameText): String = listOfNotNull(
        words(if (connectedHumanIds == null) io.github.sbshrey.tambola.game.R.string.arena_people_seated
            else io.github.sbshrey.tambola.game.R.string.arena_live_people,
            if (connectedHumanIds == null) players.count { !it.computer } else livePeople),
        words(io.github.sbshrey.tambola.game.R.string.arena_computer_seats, computerSeats).takeIf { computerSeats > 0 },
    ).joinToString(" · ")
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
        game.customAwards, game.status, game.scores, (ownLabels + game.winningTickets).distinctBy { it.id }, coins, nextDrawAt, serverTime, options.intervalSeconds, game.powers,
        connectedHumanIds = members.filter { it.connected }.map { it.playerId }.toSet())
}

/** Only correctly marked, already called numbers charge the nearest available standard prize. */
internal fun ticketPrizeProgress(table: TableRound, ticket: Ticket, prize: Prize): Float {
    val correct = table.marks[ticket.id].orEmpty().intersect(table.called.toSet())
    return prize.conditions().maxOf { condition ->
        val inspection = condition.inspect(ticket, correct)
        if (!inspection.possible || inspection.required <= 0) 0f
        else (inspection.called.size.toFloat() / inspection.required).coerceIn(0f, 1f)
    }
}

internal fun ticketClaimProgress(table: TableRound, ticket: Ticket): Float = table.settings.prizes
    .filter { prize -> table.awards.none { it.prize == prize &&
        (it.isClosed(table.settings, table.called.size) || ticket.id in it.ticketIds ||
            (table.settings.winnersPerPrize > 1 && ticket.playerId in it.playerIds)) } }
    .maxOfOrNull { ticketPrizeProgress(table, ticket, it) } ?: 0f
