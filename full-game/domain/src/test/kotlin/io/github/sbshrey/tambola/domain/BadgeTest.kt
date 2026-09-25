package io.github.sbshrey.tambola.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class BadgeTest {
    @Test fun `milestones are bounded idempotent and only count completed rounds`() {
        val empty = BadgeProgress()
        RoundStatus.entries.filter { it != RoundStatus.COMPLETED }.forEach { assertEquals(empty, empty.record("r1", it, true)) }
        val first = empty.record("r1", RoundStatus.COMPLETED, false)
        assertTrue(first.earned(Badge.FIRST_ROUND)); assertFalse(first.earned(Badge.FIRST_HOUSE))
        assertEquals(first, first.record("r1", RoundStatus.COMPLETED, false))
        val four = (2..4).fold(first) { p, n -> p.record("r$n", RoundStatus.COMPLETED, false) }
        assertFalse(four.earned(Badge.FIVE_ROUNDS))
        val many = (5..1000).fold(four) { p, n -> p.record("r$n", RoundStatus.COMPLETED, n == 900) }
        assertEquals(5, many.completedRoundIds.size)
        assertTrue(many.earned(Badge.FIVE_ROUNDS)); assertEquals("r900", many.houseRoundId)
        assertEquals(many, Json.decodeFromString<BadgeProgress>(Json.encodeToString(many)))
    }

    @Test fun `computer house wins never earn a human badge and modes stay separate`() {
        val players = listOf(Player("me", "Player"), Player("bot", "Mango", true))
        val initial = Round.create(players, RoundSettings(), random = Random(14))
        val botNumbers = initial.tickets.first { it.playerId == "bot" }.numbers
        var game = initial.copy(drawOrder = botNumbers + initial.drawOrder.filterNot { it in botNumbers }).start()
        repeat(15) { game = game.draw() }
        assertEquals(RoundStatus.COMPLETED, game.status)
        assertEquals(BadgeMode.COMPUTER, game.badgeMode())
        val progress = BadgeProgress().record(game)
        assertTrue(progress.earned(Badge.FIRST_ROUND)); assertFalse(progress.earned(Badge.FIRST_HOUSE))
        assertEquals(BadgeMode.SOLO, Round.create(players.take(1), RoundSettings()).badgeMode())
        assertEquals(BadgeMode.FAMILY, game.copy(settings = game.settings.copy(mode = GameMode.FAMILY)).badgeMode())
    }

    @Test fun `all house ranks and tied human winners count but a line does not`() {
        val ids = setOf("me", "friend")
        (listOf(Prize.FULL_HOUSE) + Prize.entries.filter { it.isRankedHouse }).forEach { prize ->
            assertTrue(listOf(Award(prize, 80, listOf("a", "b"), ids.toList())).hasHouseFor(setOf("me")))
        }
        assertFalse(listOf(Award(Prize.TOP_LINE, 80, listOf("a"), listOf("me"))).hasHouseFor(ids))
        assertFalse(listOf(Award(Prize.HOUSE_TWO, 80, listOf("a"), listOf("opponent"))).hasHouseFor(ids))
    }
}
