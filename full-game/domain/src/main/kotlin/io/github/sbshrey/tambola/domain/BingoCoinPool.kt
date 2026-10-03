package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable

@Serializable data class BingoCoinPrize(val pattern: BingoPattern, val coins: Long)
data class BingoCoinAllocation(val key: String, val playerId: String, val coins: Long, val pattern: BingoPattern? = null)

/** Versioned free-play coin policy: four legacy prizes or two equal quick prizes. */
@Serializable
data class BingoCoinPool(val soldCards: Int, val version: Int = 1) {
    init { require(version in 1..2 && soldCards in 1..300) }
    val coins: Long get() = soldCards * COIN_TICKET_PRICE
    val prizes: List<BingoCoinPrize> get() = (if (version == 2)
        listOf(BingoPattern.ANY_LINE, BingoPattern.FOUR_CORNERS) else BingoPattern.entries).map {
        BingoCoinPrize(it, coins * (if (version == 2) 50 else if (it == BingoPattern.BLACKOUT) 40 else 20) / 100)
    }

    /** Entitlements become immutable only after that category closes, including all same-call ties.
     * Unawarded pools return pro rata per purchased card at completion/cancellation. */
    fun allocations(round: BingoRound): List<BingoCoinAllocation> {
        require(round.cards.size == soldCards)
        require(round.version == version)
        val result = mutableListOf<BingoCoinAllocation>()
        for (prize in prizes) {
            val winners = round.claims.filter { it.pattern == prize.pattern }.map { it.playerId }
            if (winners.isEmpty() || !round.closed(prize.pattern)) continue
            coinShares(prize.coins, winners).forEach { (player, coins) ->
                if (coins > 0) result += BingoCoinAllocation("prize:${prize.pattern.name}", player, coins, prize.pattern)
            }
        }
        if (round.finished) {
            val remaining = coins - result.sumOf { it.coins }
            val owners = round.cards.associate { it.id to it.playerId }
            coinShares(remaining, round.cards.map { it.id }).entries.groupBy { owners.getValue(it.key) }.forEach { (player, shares) ->
                val refund = shares.sumOf { it.value }
                if (refund > 0) result += BingoCoinAllocation("return", player, refund)
            }
        }
        return result
    }
}
