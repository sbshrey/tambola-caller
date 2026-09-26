package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.RoomView

data class WinLine(val title: String, val points: Int, val players: List<Player>, val ticketCount: Int, val prize: Prize? = null)
data class WinMoment(val id: String, val number: Int, val drawIndex: Int, val lines: List<WinLine>) {
    val players: List<Player> get() = lines.flatMap { it.players }.distinctBy { it.id }
    fun refreshPlayers(current: List<Player>): WinMoment = copy(lines = lines.map { line ->
        line.copy(players = line.players.mapNotNull { player -> current.firstOrNull { it.id == player.id } })
    })
}

/** Presentation from verified awards on this draw only. Callers gate it to a newly committed live call. */
private fun moment(id: String, called: List<Int>, players: List<Player>, awards: List<Award>,
    customAwards: List<CustomAward>, rules: List<CustomPrize>): WinMoment? {
    if (called.isEmpty()) return null
    fun owners(ids: List<String>) = players.filter { it.id in ids }
    val lines = awards.filter { it.drawIndex == called.size }.map {
        WinLine(it.prize.title, it.prize.points, owners(it.playerIds), it.ticketIds.size, it.prize)
    } + customAwards.filter { it.drawIndex == called.size }.map { award ->
        val rule = rules.first { it.id == award.prizeId }
        WinLine(rule.title, rule.points, owners(award.playerIds), award.ticketIds.size)
    }
    return lines.takeIf { it.isNotEmpty() }?.let { WinMoment("$id:${called.size}", called.last(), called.size, it) }
}

fun Round.winMoment(): WinMoment? = moment(id, called, players, awards, customAwards, settings.customPrizes)
fun RoomView.winMoment(): WinMoment? = round?.let { moment(it.id, it.called, it.players, it.awards, it.customAwards, options.game.customPrizes) }

/** Claims arrive between calls. Receipts must not announce an already seen win again. */
private fun newMoment(id: String, called: List<Int>, players: List<Player>, awards: List<Award>, custom: List<CustomAward>,
    previousAwards: List<Award>, previousCustom: List<CustomAward>, rules: List<CustomPrize>, owners: Map<String, String>): WinMoment? {
    val fresh = awards.mapNotNull { award ->
        val tickets = award.ticketIds - previousAwards.firstOrNull { it.prize == award.prize }?.ticketIds.orEmpty().toSet()
        if (tickets.isEmpty()) null else award.copy(playerIds = award.playerIds.filter { id -> tickets.any { owners[it] == id } }, ticketIds = tickets)
    }
    val freshCustom = custom.mapNotNull { award ->
        val tickets = award.ticketIds - previousCustom.firstOrNull { it.prizeId == award.prizeId }?.ticketIds.orEmpty().toSet()
        if (tickets.isEmpty()) null else award.copy(playerIds = award.playerIds.filter { id -> tickets.any { owners[it] == id } }, ticketIds = tickets)
    }
    val draw = (fresh.map { it.drawIndex } + freshCustom.map { it.drawIndex }).maxOrNull() ?: return null
    val event = moment(id, called.take(draw), players, fresh, freshCustom, rules) ?: return null
    val suffix = fresh.joinToString { "${it.prize}:${it.ticketIds.joinToString()}" } + freshCustom.joinToString { "${it.prizeId}:${it.ticketIds.joinToString()}" }
    return event.copy(id = "${event.id}:$suffix")
}

fun Round.newWinMoment(previous: Round): WinMoment? = if (id != previous.id) null else newMoment(id, called, players,
    awards, customAwards, previous.awards, previous.customAwards, settings.customPrizes, tickets.associate { it.id to it.playerId })

fun RoomView.newWinMoment(previous: RoomView?): WinMoment? {
    val next = round ?: return null
    val before = previous?.round?.takeIf { it.id == next.id } ?: return null
    return newMoment(next.id, next.called, next.players, next.awards, next.customAwards, before.awards, before.customAwards,
        options.game.customPrizes, next.winningTickets.associate { it.id to it.playerId })
}
