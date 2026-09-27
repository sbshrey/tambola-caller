package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.R

/** Called only after the original claim receipt/save is confirmed. Keep labels localizable. */
fun verifiedClaimFeedback(selection: ClaimSelection, ownTickets: List<Ticket>, settings: RoundSettings): UiMessage {
    val ordinal = ownTickets.indexOfFirst { it.id == selection.ticketId } + 1
    val prize: Any? = settings.prizes.firstOrNull { it.name == selection.prizeId }
        ?: settings.customPrizes.firstOrNull { it.id == selection.prizeId }?.title
    if (ordinal == 0 || prize == null) return UiMessage(R.string.play_claim_confirmed)
    return UiMessage(R.string.play_claim_confirmed_for, listOf(ordinal, prize))
}
