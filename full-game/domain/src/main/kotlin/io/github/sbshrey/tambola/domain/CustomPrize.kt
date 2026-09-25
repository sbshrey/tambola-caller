package io.github.sbshrey.tambola.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Explicit selectors: an empty selection never satisfies a prize. */
@Serializable
sealed class NumberSelection {
    abstract fun numbers(ticket: Ticket): List<Int>
    abstract fun describe(): String

    @Serializable @SerialName("all") data object All : NumberSelection() {
        override fun numbers(ticket: Ticket) = ticket.numbers
        override fun describe() = "the ticket"
    }
    @Serializable @SerialName("row") data class Row(val index: Int) : NumberSelection() {
        init { require(index in 0..2) }
        override fun numbers(ticket: Ticket) = ticket.row(index)
        override fun describe() = listOf("the top row", "the middle row", "the bottom row")[index]
    }
    @Serializable @SerialName("column") data class Column(val index: Int) : NumberSelection() {
        init { require(index in 0..8) }
        override fun numbers(ticket: Ticket) = (0..2).map { ticket.cells[it * 9 + index] }.filter { it != 0 }
        override fun describe() = "column ${index + 1} (${columnRange(index).first}–${columnRange(index).last})"
    }
    @Serializable @SerialName("range") data class Range(val first: Int, val last: Int) : NumberSelection() {
        init { require(first in 1..90 && last in first..90) }
        override fun numbers(ticket: Ticket) = ticket.numbers.filter { it in first..last }
        override fun describe() = "numbers $first–$last on the ticket"
    }
    @Serializable @SerialName("positions") data class Positions(val positions: List<Int>) : NumberSelection() {
        init { require(positions.size in 1..15 && positions.distinct().size == positions.size && positions.all { it in 0..14 }) }
        override fun numbers(ticket: Ticket) = positions.sorted().map { ticket.numbers[it] }
        override fun describe() = "populated positions " + positions.sorted().joinToString { "row ${it / 5 + 1}, number ${it % 5 + 1}" }
    }
}

@Serializable
data class RuleCondition(val selection: NumberSelection, val minimumCalled: Int? = null) {
    init { require(minimumCalled == null || minimumCalled in 1..15) }
    fun matches(ticket: Ticket, called: Set<Int>): Boolean {
        val numbers = selection.numbers(ticket)
        return numbers.isNotEmpty() && numbers.count { it in called } >= (minimumCalled ?: numbers.size)
    }
    fun describe() = if (minimumCalled == null) "All of ${selection.describe()}" else "At least $minimumCalled from ${selection.describe()}"
}

/** OR between alternatives, AND within a group. Flat and bounded for safe decoding/evaluation. */
@Serializable
data class TicketPattern(val alternatives: List<List<RuleCondition>>) {
    init {
        require(alternatives.size in 1..4 && alternatives.all { it.size in 1..8 } && alternatives.sumOf { it.size } <= 16)
    }
    fun matches(ticket: Ticket, called: Set<Int>) = alternatives.any { group -> group.all { it.matches(ticket, called) } }
    fun describe() = alternatives.joinToString(" OR ") { group -> group.joinToString(" AND ", "(", ")") { it.describe() } }
}

@Serializable
data class CustomPrize(
    val id: String,
    val title: String,
    val points: Int,
    val pattern: TicketPattern,
    val version: Int = 1,
    val minimumTickets: Int = 1,
    val ticketOrdinals: List<Int> = emptyList(),
) {
    init {
        require(id.matches(Regex("custom_[A-Za-z0-9_-]{1,48}")))
        require(title.isNotBlank() && title.length <= 40 && title.none { it.isISOControl() })
        require(points in 1..1000 && version == 1 && minimumTickets in 1..6)
        require(ticketOrdinals.size <= 6 && ticketOrdinals.distinct().size == ticketOrdinals.size && ticketOrdinals.all { it in 1..6 })
        require(ticketOrdinals.isEmpty() || minimumTickets <= ticketOrdinals.size)
    }
    fun eligibleTickets(ownedTickets: List<Ticket>, called: Set<Int>): List<Ticket> {
        val matches = ownedTickets.filterIndexed { index, ticket ->
            (ticketOrdinals.isEmpty() || index + 1 in ticketOrdinals) && pattern.matches(ticket, called)
        }
        return if (matches.size >= minimumTickets) matches else emptyList()
    }
    fun describe(): String = pattern.describe() + "; on at least $minimumTickets ticket(s)" +
        if (ticketOrdinals.isEmpty()) "." else " among tickets ${ticketOrdinals.sorted().joinToString()}."
}

@Serializable
data class CustomAward(val prizeId: String, val ruleVersion: Int, val drawIndex: Int, val ticketIds: List<String>, val playerIds: List<String>)
