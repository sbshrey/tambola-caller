package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable

const val COIN_TICKET_PRICE = 100L
const val COIN_STARTER_BALANCE = 1_500L

@Serializable data class CoinPrize(val prize: Prize, val coins: Long)
data class CoinAllocation(val key: String, val ticketId: String, val playerId: String, val coins: Long, val prize: Prize? = null)

/** Immutable policy v1, fixed before any numbers are revealed. These coins have no cash value. */
@Serializable
data class CoinPool(val soldTickets: Int, val version: Int = 1) {
    init { require(version == 1 && soldTickets in 2..192) }
    val coins: Long get() = soldTickets * COIN_TICKET_PRICE
    val prizes: List<CoinPrize> get() {
        val small = listOf(Prize.EARLY_FIVE, Prize.CORNERS, Prize.TOP_LINE, Prize.MIDDLE_LINE, Prize.BOTTOM_LINE)
            .map { CoinPrize(it, coins / 10) }
        val houses = when {
            soldTickets < 12 -> listOf(CoinPrize(Prize.FULL_HOUSE, coins / 2))
            soldTickets < 24 -> listOf(CoinPrize(Prize.HOUSE_ONE, coins * 35 / 100), CoinPrize(Prize.HOUSE_TWO, coins * 15 / 100))
            else -> listOf(CoinPrize(Prize.HOUSE_ONE, coins * 30 / 100), CoinPrize(Prize.HOUSE_TWO, coins * 15 / 100), CoinPrize(Prize.HOUSE_THREE, coins * 5 / 100))
        }
        return small + houses
    }

    /** Cumulative entitlements, never deltas: the durable ledger deduplicates each key per round.
     * Current-call wins stay provisional. Finished rounds close that window and return only
     * the unawarded remainder. Sorting ticket IDs makes ties independent of arrival order.
     */
    fun allocations(round: Round): List<CoinAllocation> {
        require(round.settings.manualClaims && round.settings.mode == GameMode.ONLINE)
        require(round.tickets.size == soldTickets && round.settings.customPrizes.isEmpty())
        require(round.settings.prizes.toSet() == prizes.map { it.prize }.toSet())
        val result = mutableListOf<CoinAllocation>()
        prizes.forEach { slot ->
            val award = round.awards.firstOrNull { it.prize == slot.prize } ?: return@forEach
            if (!round.finished && award.drawIndex == round.called.size) return@forEach
            val winning = round.tickets.filter { it.id in award.ticketIds }
            require(winning.isNotEmpty() && winning.size == award.ticketIds.size)
            result += share(slot.coins, winning, "prize:${slot.prize.name}", slot.prize)
        }
        if (round.finished) result += share(coins - result.sumOf { it.coins }, round.tickets, "refund", null)
        return result
    }

    private fun share(amount: Long, tickets: List<Ticket>, prefix: String, prize: Prize?): List<CoinAllocation> {
        require(amount >= 0 && tickets.isNotEmpty())
        val ordered = tickets.sortedBy { it.id }
        val base = amount / ordered.size
        val extra = amount % ordered.size
        return ordered.mapIndexedNotNull { index, ticket ->
            val value = base + if (index < extra) 1 else 0
            if (value == 0L) null else CoinAllocation("$prefix:${ticket.id}", ticket.id, ticket.playerId, value, prize)
        }
    }
}
