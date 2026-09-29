package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.Player
import io.github.sbshrey.tambola.protocol.RoundWinnings
import java.util.Locale

data class RoundStanding(val rank: Int, val player: Player, val winnings: RoundWinnings)

/** Competition ranks include bonuses, exclude returned entry coins, and never pin the owner. */
fun roundStandings(players: List<Player>, winnings: Map<String, RoundWinnings>): List<RoundStanding> {
    require(winnings.keys == players.map { it.id }.toSet())
    val ordered = players.sortedWith(compareByDescending<Player> { winnings.getValue(it.id).total }
        .thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
    var rank = 0
    var previous: Long? = null
    return ordered.mapIndexed { index, player ->
        val amount = winnings.getValue(player.id)
        if (amount.total != previous) rank = index + 1
        previous = amount.total
        RoundStanding(rank, player, amount)
    }
}
