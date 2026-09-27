package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.R
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ClaimFeedbackTest {
    private val round = Round.create(listOf(Player("peer", "Noor"), Player("me", "Mira")),
        RoundSettings(ticketsPerPlayer = 3), Random(81))
    private val own = round.tickets.filter { it.playerId == "me" }

    @Test fun `confirmation uses the player's ticket ordinal and keeps prize localizable`() {
        val message = verifiedClaimFeedback(ClaimSelection(own[2].id, Prize.FULL_HOUSE.name), own, round.settings)
        assertEquals(R.string.play_claim_confirmed_for, message.resource)
        assertEquals(listOf(3, Prize.FULL_HOUSE), message.arguments)
    }

    @Test fun `custom prize title is retained verbatim`() {
        val custom = CustomPrize("custom_all", "Family special", 25, TicketPattern(listOf(listOf(RuleCondition(NumberSelection.All)))))
        val message = verifiedClaimFeedback(ClaimSelection(own[0].id, custom.id), own, round.settings.copy(customPrizes = listOf(custom)))
        assertEquals(listOf(1, "Family special"), message.arguments)
    }

    @Test fun `unavailable receipt details never invent a ticket or prize`() {
        assertEquals(UiMessage(R.string.play_claim_confirmed), verifiedClaimFeedback(ClaimSelection("missing", Prize.FULL_HOUSE.name), own, round.settings))
        assertEquals(UiMessage(R.string.play_claim_confirmed), verifiedClaimFeedback(ClaimSelection(own[0].id, "missing"), own, round.settings))
    }
}
