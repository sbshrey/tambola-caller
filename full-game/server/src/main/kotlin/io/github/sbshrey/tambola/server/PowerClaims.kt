package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*

/** A rejected pattern is a committed outcome so retrying its receipt cannot punish twice. */
internal fun RoomRecord.powerClaim(actor: String, action: RoomAction.Claim): RoomRecord {
    val game = requireNotNull(round)
    val choice = action.selection
    val powers = matchPowers[actor] ?: MatchPowers()
    demand(choice.ticketId !in powers.discarded, 409, "ticket_discarded", "This ticket was discarded for this round.")
    val prize = game.settings.prizes.first { it.name == choice.prizeId }
    val previous = game.awards.firstOrNull { it.prize == prize }
    // Closed/previously won schemes are not false claims. Keep race losers' tickets.
    demand(previous == null || (!previous.isClosed(game.settings, game.called.size) && actor !in previous.playerIds),
        409, "claim_window_closed", "This prize is already closed or claimed by you.")
    val marks = powers.marks.filterKeys { it !in powers.discarded }.values.flatten().toSet()
    val claimed = game.claim(actor, marks, choice)
    val next = if (claimed == game) powers.falseClaim(choice.ticketId) else powers.won(choice.ticketId, prize)
    return copy(round = claimed, matchPowers = matchPowers + (actor to next))
}
