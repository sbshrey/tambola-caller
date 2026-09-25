package io.github.sbshrey.tambola.game.setup

import io.github.sbshrey.tambola.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class SetupDraftTest {
    @Test fun `rematch preserves players computers and exact custom and terminal rules`() {
        val rule = CustomRuleDraft(title = "Two top lines", groups = listOf(listOf(ConditionDraft(kind = SelectionKind.ROW, all = true))), minimumTickets = 2).prize(2)
        val settings = RoundSettings(ticketsPerPlayer = 2, assistedMarking = true, prizes = listOf(Prize.EARLY_FIVE, Prize.HOUSE_ONE, Prize.HOUSE_TWO, Prize.HOUSE_THREE), playAllNumbers = true, customPrizes = listOf(rule))
        val round = Round.create(listOf(Player("p0", "Asha", avatar = 7), Player("bot1", "Mango", true, 1)), settings, Random(2), 1)
        val draft = SetupDraft.from(round)
        assertEquals(settings, draft.settings())
        assertEquals(listOf("Asha"), draft.playerNames); assertEquals(1, draft.bots)
        assertEquals(7, draft.avatar(0)); assertEquals(2, draft.withAvatar(0, 2).avatar(0))
        assertEquals(draft, Json.decodeFromString<SetupDraft>(Json.encodeToString(draft)))
        assertEquals(rule, CustomRuleDraft.from(rule).prize(2))
    }

    @Test fun `reducing tickets keeps custom rule intact and explains incompatible setup`() {
        val rule = CustomRuleDraft(title = "Ticket three", ticketOrdinals = listOf(3)).prize(3)
        val draft = SetupDraft(tickets = 3, customPrizes = listOf(rule))
        assertTrue(draft.errors.isEmpty())
        val fewer = draft.copy(tickets = 1)
        assertTrue(fewer.errors.single().contains("Ticket three needs more tickets"))
        assertEquals(rule, fewer.customPrizes.single())
        assertThrows(IllegalArgumentException::class.java) { fewer.settings() }
    }

    @Test fun `editor rejects impossible counts invalid ranges and insufficient selected tickets`() {
        assertThrows(IllegalArgumentException::class.java) { ConditionDraft(kind = SelectionKind.ROW, minimum = "6").condition() }
        assertThrows(IllegalArgumentException::class.java) { ConditionDraft(kind = SelectionKind.RANGE, first = "1", last = "9", minimum = "4").condition() }
        assertThrows(IllegalArgumentException::class.java) { ConditionDraft(kind = SelectionKind.RANGE, first = "91").condition() }
        assertThrows(IllegalArgumentException::class.java) { ConditionDraft(kind = SelectionKind.POSITIONS, positions = emptyList(), all = true).condition() }
        assertThrows(IllegalArgumentException::class.java) { CustomRuleDraft(title = "Two", minimumTickets = 2, ticketOrdinals = listOf(1)).prize(2) }
        assertEquals(RuleCondition(NumberSelection.Positions(listOf(0, 4, 10, 14))), ConditionDraft(kind = SelectionKind.POSITIONS, all = true).condition())
    }

    @Test fun `house configuration avoids double full house points and invalid players are explained`() {
        val draft = SetupDraft(houses = 3)
        assertEquals(listOf(Prize.HOUSE_ONE, Prize.HOUSE_TWO, Prize.HOUSE_THREE), draft.settings().prizes.filter { it.isRankedHouse })
        assertFalse(Prize.FULL_HOUSE in draft.settings().prizes)
        assertTrue(draft.copy(bots = 0).errors.single().contains("at least 3 tickets"))
        assertTrue(SetupDraft.fresh(GameMode.FAMILY).copy(names = "Asha\nasha").errors.single().contains("different name"))
        assertTrue(SetupDraft(names = "").errors.single().contains("Enter your name"))
        val tooMany = SetupDraft.fresh(GameMode.FAMILY).copy(names = (1..9).joinToString("\n") { "Player $it" }).withAvatar(0, 7)
        assertEquals(8, tooMany.avatars.size)
        val corrected = tooMany.copy(names = tooMany.playerNames.take(8).joinToString("\n"))
        assertTrue(corrected.errors.isEmpty()); assertEquals(7, corrected.avatar(0))
    }
}
