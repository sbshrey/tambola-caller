package io.github.sbshrey.tambola.domain

/** The same selectors power rule previews and explanations of actual called numbers. */
fun Prize.condition(): RuleCondition = when (this) {
    Prize.EARLY_FIVE -> RuleCondition(NumberSelection.All, 5)
    Prize.EARLY_TEN -> RuleCondition(NumberSelection.All, 10)
    Prize.TOP_LINE -> RuleCondition(NumberSelection.Row(0))
    Prize.MIDDLE_LINE -> RuleCondition(NumberSelection.Row(1))
    Prize.BOTTOM_LINE -> RuleCondition(NumberSelection.Row(2))
    Prize.CORNERS -> RuleCondition(NumberSelection.Positions(listOf(0, 4, 10, 14)))
    else -> RuleCondition(NumberSelection.All)
}

data class ConditionInspection(val selected: List<Int>, val called: List<Int>, val required: Int) {
    val matches get() = selected.isNotEmpty() && called.size >= required
    val possible get() = selected.isNotEmpty() && required <= selected.size
    val remaining get() = (required - called.size).coerceAtLeast(0)
    val uncalled get() = selected.filterNot { it in called }
}

fun RuleCondition.inspect(ticket: Ticket, called: Set<Int>): ConditionInspection {
    val selected = selection.numbers(ticket)
    return ConditionInspection(selected, selected.filter { it in called }, minimumCalled ?: selected.size)
}

/** Construct a concrete positive example when these tickets can satisfy the pattern. */
fun CustomPrize.exampleCalls(tickets: List<Ticket>): Set<Int>? {
    val examples = tickets.mapIndexedNotNull { index, ticket ->
        if (ticketOrdinals.isNotEmpty() && index + 1 !in ticketOrdinals) return@mapIndexedNotNull null
        pattern.alternatives.firstOrNull { group -> group.all { it.inspect(ticket, emptySet()).possible } }
            ?.flatMap { it.inspect(ticket, emptySet()).let { detail -> detail.selected.take(detail.required) } }?.toSet()
    }
    if (examples.size < minimumTickets) return null
    return examples.take(minimumTickets).flatten().toSet()
}

fun RoundSettings.endExplanation(): String = if (playAllNumbers) "All 90 numbers will be called, even after a full house."
else when {
    Prize.HOUSE_THREE in prizes -> "Ends after House three, or at 90 calls if fewer groups finish."
    Prize.HOUSE_TWO in prizes -> "Ends after House two, or at 90 calls if fewer groups finish."
    Prize.FULL_HOUSE in prizes || Prize.HOUSE_ONE in prizes -> "Ends at the first full house. Same-call winners tie."
    else -> "Ends when every selected prize is awarded, or at 90 calls."
}
