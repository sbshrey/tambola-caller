@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault

@Serializable enum class MatchPower { SHIELD, AUTO_DAB, PRIZE_BONUS }
@Serializable enum class PowerNotice { DROP, FULL, SHIELD_SAVED, TICKET_DISCARDED, ACTIVATED }

/** Private to one hand. Persisted by the room owner with its command receipt. */
@Serializable data class MatchPowers(
    val marks: Map<String, Set<Int>> = emptyMap(),
    val correctMarks: Int = 0,
    val inventory: List<MatchPower> = emptyList(),
    val used: Map<String, Set<MatchPower>> = emptyMap(),
    val discarded: Set<String> = emptySet(),
    val autoUntil: Map<String, Long> = emptyMap(),
    val armedBonus: Set<String> = emptySet(),
    val bonusPrizes: Map<Prize, String> = emptyMap(),
    val notice: PowerNotice? = null,
    val noticeSequence: Int = 0,
    /** Null preserves the original hidden-drop/passive-shield contract. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val nextPower: MatchPower? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val armedShield: Set<String> = emptySet(),
) {
    /** Correct dabs are permanent: retries, auto-dabs and repeated taps earn nothing. */
    fun mark(ticket: Ticket, number: Int, called: List<Int>, drop: () -> MatchPower): MatchPowers {
        require(ticket.id !in discarded && number in ticket.numbers && number in called)
        if (number in marks[ticket.id].orEmpty()) return this
        val count = correctMarks + 1
        val milestone = count % 5 == 0
        val space = inventory.size < 2
        return copy(marks = marks + (ticket.id to (marks[ticket.id].orEmpty() + number)), correctMarks = count,
            inventory = if (milestone && space) inventory + (nextPower ?: drop()) else inventory,
            nextPower = if (milestone && space && nextPower != null) drop() else nextPower,
            notice = if (milestone) if (space) PowerNotice.DROP else PowerNotice.FULL else notice,
            noticeSequence = noticeSequence + if (milestone) 1 else 0)
    }

    fun activate(ticket: Ticket, power: MatchPower, called: List<Int>, now: Long): MatchPowers {
        require(ticket.id !in discarded && power in inventory && power !in used[ticket.id].orEmpty())
        require(power != MatchPower.SHIELD || nextPower != null)
        val next = copy(inventory = inventory.toMutableList().also { it.remove(power) },
            used = used + (ticket.id to (used[ticket.id].orEmpty() + power)),
            autoUntil = if (power == MatchPower.AUTO_DAB) autoUntil + (ticket.id to now + 15_000L) else autoUntil,
            armedBonus = if (power == MatchPower.PRIZE_BONUS) armedBonus + ticket.id else armedBonus,
            armedShield = if (power == MatchPower.SHIELD) armedShield + ticket.id else armedShield,
            notice = PowerNotice.ACTIVATED, noticeSequence = noticeSequence + 1)
        return next.autoMark(listOf(ticket), called, now)
    }

    fun autoMark(tickets: List<Ticket>, called: List<Int>, now: Long): MatchPowers {
        val next = marks.toMutableMap()
        tickets.filter { it.id !in discarded && now < (autoUntil[it.id] ?: 0) }.forEach { ticket ->
            next[ticket.id] = next[ticket.id].orEmpty() + ticket.numbers.filter { it in called }
        }
        return copy(marks = next)
    }

    fun falseClaim(ticketId: String): MatchPowers {
        require(ticketId !in discarded)
        val passive = nextPower == null && MatchPower.SHIELD in inventory && MatchPower.SHIELD !in used[ticketId].orEmpty()
        val shield = ticketId in armedShield || passive
        return copy(inventory = if (passive) inventory.toMutableList().also { it.remove(MatchPower.SHIELD) } else inventory,
            armedShield = armedShield - ticketId,
            used = if (shield) used + (ticketId to (used[ticketId].orEmpty() + MatchPower.SHIELD)) else used,
            discarded = if (shield) discarded else discarded + ticketId,
            autoUntil = if (shield) autoUntil else autoUntil - ticketId,
            armedBonus = if (shield) armedBonus else armedBonus - ticketId,
            notice = if (shield) PowerNotice.SHIELD_SAVED else PowerNotice.TICKET_DISCARDED,
            noticeSequence = noticeSequence + 1)
    }

    fun won(ticketId: String, prize: Prize): MatchPowers = if (ticketId !in armedBonus) this else
        copy(armedBonus = armedBonus - ticketId, bonusPrizes = bonusPrizes + (prize to ticketId))
}

/** Promotional coins are added after the final fair pool split; the pool is never reduced. */
fun matchPowerBonus(powers: MatchPowers?, awards: List<Award>, prizes: List<CoinPrize>, finished: Boolean): Long {
    if (!finished || powers == null) return 0
    return powers.bonusPrizes.entries.sumOf { (prize, ticket) ->
        val award = awards.firstOrNull { it.prize == prize && ticket in it.ticketIds }
        val coins = prizes.firstOrNull { it.prize == prize }?.coins
        if (award == null || coins == null) 0L else coinShares(coins, award.ticketIds).getValue(ticket) / 4
    }
}
