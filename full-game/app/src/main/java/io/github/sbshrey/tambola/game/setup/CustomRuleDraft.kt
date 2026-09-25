package io.github.sbshrey.tambola.game.setup

import io.github.sbshrey.tambola.domain.*
import kotlinx.serialization.Serializable
import java.util.UUID
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.presentation.requireUi

@Serializable enum class SelectionKind(val title: String) { ALL("Whole ticket"), ROW("Row"), COLUMN("Column"), RANGE("Number range"), POSITIONS("Positions") }

@Serializable data class ConditionDraft(
    val kind: SelectionKind = SelectionKind.ALL,
    val index: Int = 0,
    val first: String = "1",
    val last: String = "90",
    val positions: List<Int> = listOf(0, 4, 10, 14),
    val all: Boolean = false,
    val minimum: String = "5",
) {
    fun condition(): RuleCondition {
        val selection = when (kind) {
            SelectionKind.ALL -> NumberSelection.All
            SelectionKind.ROW -> NumberSelection.Row(index)
            SelectionKind.COLUMN -> NumberSelection.Column(index)
            SelectionKind.RANGE -> {
                val low = first.toIntOrNull(); val high = last.toIntOrNull()
                requireUi(low != null && high != null && low in 1..90 && high in low..90, R.string.error_range)
                NumberSelection.Range(low, high)
            }
            SelectionKind.POSITIONS -> { requireUi(positions.isNotEmpty(), R.string.error_positions); NumberSelection.Positions(positions) }
        }
        val maximum = when (selection) {
            NumberSelection.All -> 15
            is NumberSelection.Row -> 5
            is NumberSelection.Column -> 3
            is NumberSelection.Positions -> selection.positions.size
            is NumberSelection.Range -> (0..8).sumOf { columnRange(it).count { n -> n in selection.first..selection.last }.coerceAtMost(3) }.coerceAtMost(15)
        }
        val count = minimum.toIntOrNull()
        requireUi(all || (count != null && count in 1..maximum), R.string.error_called_count, maximum)
        return RuleCondition(selection, if (all) null else count)
    }
    companion object {
        fun from(condition: RuleCondition): ConditionDraft {
            val base = ConditionDraft(all = condition.minimumCalled == null, minimum = (condition.minimumCalled ?: 1).toString())
            return when (val s = condition.selection) {
                NumberSelection.All -> base
                is NumberSelection.Row -> base.copy(kind = SelectionKind.ROW, index = s.index)
                is NumberSelection.Column -> base.copy(kind = SelectionKind.COLUMN, index = s.index)
                is NumberSelection.Range -> base.copy(kind = SelectionKind.RANGE, first = "${s.first}", last = "${s.last}")
                is NumberSelection.Positions -> base.copy(kind = SelectionKind.POSITIONS, positions = s.positions)
            }
        }
    }
}

@Serializable data class CustomRuleDraft(
    val id: String = "custom_" + UUID.randomUUID().toString(),
    val title: String = "",
    val points: String = "25",
    val groups: List<List<ConditionDraft>> = listOf(listOf(ConditionDraft())),
    val minimumTickets: Int = 1,
    val ticketOrdinals: List<Int> = emptyList(),
) {
    fun prize(tickets: Int): CustomPrize {
        requireUi(title.isNotBlank() && title.trim().length <= 40 && title.none(Char::isISOControl), R.string.error_prize_name)
        val score = points.toIntOrNull()
        requireUi(score != null && score in 1..1000, R.string.error_prize_points)
        requireUi(minimumTickets in 1..tickets && ticketOrdinals.all { it in 1..tickets }, R.string.error_prize_needs_tickets)
        requireUi(ticketOrdinals.isEmpty() || minimumTickets <= ticketOrdinals.size, R.string.error_select_tickets, minimumTickets)
        return CustomPrize(id, title.trim(), score, TicketPattern(groups.map { group -> group.map { it.condition() } }),
            minimumTickets = minimumTickets, ticketOrdinals = ticketOrdinals)
    }
    fun changeCondition(group: Int, index: Int, value: ConditionDraft) = copy(groups = groups.mapIndexed { g, list ->
        if (g == group) list.mapIndexed { i, old -> if (i == index) value else old } else list
    })
    companion object {
        fun from(prize: CustomPrize) = CustomRuleDraft(prize.id, prize.title, prize.points.toString(),
            prize.pattern.alternatives.map { group -> group.map(ConditionDraft::from) }, prize.minimumTickets, prize.ticketOrdinals)
    }
}
