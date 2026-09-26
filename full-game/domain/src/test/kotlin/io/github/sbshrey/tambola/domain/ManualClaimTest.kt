package io.github.sbshrey.tambola.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ManualClaimTest {
    private val humans = listOf(Player("a", "Asha"), Player("b", "Bina"))
    private fun game(prizes: List<Prize> = Prize.defaults, computers: Boolean = false, all: Boolean = true, tickets: Int = 2,
        custom: List<CustomPrize> = emptyList(), seed: Long = 41): Round = Round.create(
        if (computers) listOf(humans.first(), Player("bot", "Computer", computer = true)) else humans,
        RoundSettings(ticketsPerPlayer = tickets, prizes = prizes, playAllNumbers = all, customPrizes = custom, manualClaims = true), Random(seed), 1).start()
    private fun atAllNumbers(round: Round): Round = (1..90).fold(round) { next, _ -> next.draw() }
    private fun numbers(round: Round, player: String) = round.tickets.filter { it.playerId == player }.flatMap { it.numbers }.toSet()
    private fun restored(round: Round) = RoundCodec.decode(RoundCodec.encode(round))

    @Test fun `selected ticket and prize cannot silently award other schemes or tickets`() {
        val round = atAllNumbers(game())
        val first = round.tickets.first { it.playerId == "a" }
        val second = round.tickets.filter { it.playerId == "a" }[1]
        val choice = ClaimSelection(first.id, Prize.TOP_LINE.name)
        val claimed = round.claim("a", numbers(round, "a"), choice)
        assertEquals(listOf(Prize.TOP_LINE), claimed.awards.map { it.prize })
        assertEquals(listOf(first.id), claimed.awards.single().ticketIds)
        assertEquals(claimed, claimed.claim("a", numbers(round, "a"), choice))
        val next = claimed.claim("a", numbers(round, "a"), choice.copy(ticketId = second.id))
        assertEquals(listOf(first.id, second.id), next.awards.single().ticketIds)
        assertEquals(next, restored(next))
        assertThrows(IllegalArgumentException::class.java) { round.claim("b", numbers(round, "b"), choice) }
        assertThrows(IllegalArgumentException::class.java) { round.claim("a", numbers(round, "a"), choice.copy(prizeId = "missing")) }
    }

    @Test fun `selected custom prize preserves hand-wide minimum and ticket ordinals`() {
        val custom = CustomPrize("custom_pair", "Pair", 20, TicketPattern(listOf(listOf(RuleCondition(NumberSelection.Row(0))))),
            minimumTickets = 2, ticketOrdinals = listOf(2, 3))
        val round = atAllNumbers(game(tickets = 3, custom = listOf(custom)))
        val own = round.tickets.filter { it.playerId == "a" }
        val marks = own.flatMap { it.row(0) }.toSet()
        assertEquals(round, round.claim("a", marks, ClaimSelection(own[0].id, custom.id)))
        assertEquals(round, round.claim("a", own[1].row(0).toSet(), ClaimSelection(own[1].id, custom.id)))
        val claimed = round.claim("a", marks, ClaimSelection(own[1].id, custom.id))
        assertTrue(claimed.awards.isEmpty())
        assertEquals(own.drop(1).map { it.id }, claimed.customAwards.single().ticketIds)
        assertEquals(claimed, restored(claimed))
    }

    @Test fun `human prizes require a real valid claim and retry is idempotent`() {
        val called = atAllNumbers(game())
        assertTrue(called.awards.isEmpty())
        assertFalse(called.finished)
        assertEquals(called, called.claim("a"))
        val claimed = called.claim("a", numbers(called, "a"))
        assertEquals(Prize.defaults.toSet(), claimed.awards.map { it.prize }.toSet())
        assertEquals(claimed, claimed.claim("a", numbers(called, "a")))
        assertEquals(claimed, restored(claimed))
        assertEquals(90, claimed.draw().called.size)
        assertTrue(claimed.draw().finished)
        assertEquals(claimed.draw(), restored(claimed.draw()))
    }

    @Test fun `only owned called marks can be submitted`() {
        val ready = game().copy(status = RoundStatus.READY)
        assertThrows(IllegalArgumentException::class.java) { ready.claim("a") }
        val round = game().draw()
        assertThrows(IllegalArgumentException::class.java) { round.claim("missing") }
        assertThrows(IllegalArgumentException::class.java) { round.claim("a", setOf(91)) }
        val uncalled = numbers(round, "a").first { it !in round.called }
        assertThrows(IllegalArgumentException::class.java) { round.claim("a", setOf(uncalled)) }
        val all = atAllNumbers(game())
        val foreign = numbers(all, "b").first { it !in numbers(all, "a") }
        assertThrows(IllegalArgumentException::class.java) { all.claim("a", setOf(foreign)) }
    }

    @Test fun `same call ties are independent of arrival order and points are not per ticket`() {
        val round = atAllNumbers(game())
        val ab = round.claim("a", numbers(round, "a")).claim("b", numbers(round, "b"))
        val ba = round.claim("b", numbers(round, "b")).claim("a", numbers(round, "a"))
        assertEquals(ab, ba)
        assertTrue(ab.awards.all { it.playerIds == listOf("a", "b") && it.ticketIds.size == 4 })
        assertEquals(Prize.defaults.sumOf { it.points }, ab.score("a"))
        assertEquals(ab, restored(ab))
    }

    @Test fun `a closed prize cannot be claimed on a later number`() {
        var round = game(listOf(Prize.EARLY_FIVE))
        while (round.tickets.none { it.playerId == "a" && Prize.EARLY_FIVE.matches(it, round.called.toSet()) }) round = round.draw()
        round = round.claim("a", numbers(round, "a").intersect(round.called.toSet()))
        while (round.called.size < 90) round = round.draw()
        assertEquals(round, round.claim("b", numbers(round, "b")))
        assertEquals(round, restored(round))
    }

    @Test fun `terminal house keeps its call window open and stops before another number`() {
        var round = game(listOf(Prize.FULL_HOUSE), all = false)
        val ticket = round.tickets.first()
        while (!Prize.FULL_HOUSE.matches(ticket, round.called.toSet())) round = round.draw()
        val claimed = round.claim("a", ticket.numbers.toSet())
        assertFalse(claimed.finished)
        val finished = claimed.draw()
        assertTrue(finished.finished)
        assertEquals(claimed.called, finished.called)
        assertEquals(finished, restored(finished))
    }

    @Test fun `ranked houses cannot skip rank or reuse a completed ticket`() {
        var round = atAllNumbers(game(listOf(Prize.HOUSE_ONE, Prize.HOUSE_TWO, Prize.HOUSE_THREE), tickets = 3))
        round = round.claim("a", numbers(round, "a"))
        assertEquals(listOf(Prize.HOUSE_ONE), round.awards.map { it.prize })
        assertEquals(round, round.claim("a", numbers(round, "a")))
        round = round.claim("b", numbers(round, "b"))
        assertEquals(listOf(Prize.HOUSE_ONE), round.awards.map { it.prize })
        assertEquals(6, round.awards.single().ticketIds.size)
        assertEquals(round.draw(), restored(round.draw()))
    }

    @Test fun `computers submit under the same call window without revealing future numbers`() {
        var round = game(listOf(Prize.EARLY_FIVE), computers = true)
        while (round.tickets.none { it.playerId == "bot" && Prize.EARLY_FIVE.matches(it, round.called.toSet()) }) round = round.draw()
        assertTrue(round.awards.isEmpty())
        val drawCount = round.called.size
        val next = round.draw()
        assertEquals(drawCount, next.awards.single().drawIndex)
        assertEquals(listOf("bot"), next.awards.single().playerIds)
        assertTrue(next.claims.single().submissions.flatten().all { it in round.called })
        assertEquals(next, restored(next))
    }

    @Test fun `successive ranked houses use fresh tickets on later calls`() {
        var round = game(listOf(Prize.HOUSE_ONE, Prize.HOUSE_TWO, Prize.HOUSE_THREE), all = false, tickets = 3)
        val owned = round.tickets.filter { it.playerId == "a" }
        val first = owned.flatMap { it.numbers }
        round = round.copy(drawOrder = first + (1..90).filter { it !in first })
        owned.forEachIndexed { index, ticket ->
            while (round.called.size < (index + 1) * 15) round = round.draw()
            round = round.claim("a", first.take((index + 1) * 15).toSet())
            assertEquals(index + 1, round.awards.size)
            assertEquals(listOf(ticket.id), round.awards.last().ticketIds)
            assertEquals(round, restored(round))
        }
        assertTrue(round.draw().finished)
        assertEquals(45, round.draw().called.size)
    }

    @Test fun `computer and human share a terminal house on the same number`() {
        var round = (1L..100L).asSequence()
            .map { game(listOf(Prize.FULL_HOUSE), computers = true, all = false, tickets = 1, seed = it) }
            .first { numbers(it, "a").intersect(numbers(it, "bot")).isNotEmpty() }
        val a = numbers(round, "a"); val b = numbers(round, "bot")
        val last = a.intersect(b).first()
        val first = (a + b - last).toList() + last
        round = round.copy(drawOrder = first + (1..90).filter { it !in first })
        repeat(first.size) { round = round.draw() }
        round = round.claim("a", a)
        val closed = round.draw()
        assertTrue(closed.finished)
        assertEquals(round.called, closed.called)
        assertEquals(listOf("a", "bot"), closed.awards.single().playerIds)
        assertEquals(closed, restored(closed))
    }

    @Test fun `custom patterns and multiple ticket requirements use submitted marks`() {
        val custom = CustomPrize("custom_pair", "Two top rows", 30, TicketPattern(listOf(listOf(RuleCondition(NumberSelection.Row(0))))), minimumTickets = 2)
        val round = atAllNumbers(game(listOf(Prize.FULL_HOUSE), custom = listOf(custom)))
        val owned = round.tickets.filter { it.playerId == "a" }
        assertTrue(round.claim("a", owned.first().row(0).toSet()).customAwards.isEmpty())
        val claimed = round.claim("a", owned.flatMap { it.row(0) }.toSet())
        assertEquals(listOf("custom_pair"), claimed.customAwards.map { it.prizeId })
        assertTrue(claimed.awards.isEmpty())
        assertEquals(claimed, restored(claimed))
    }

    @Test fun `separate submissions never combine unmarked numbers into a new prize`() {
        val round = atAllNumbers(game(listOf(Prize.TOP_LINE, Prize.MIDDLE_LINE, Prize.EARLY_TEN), tickets = 1))
        val ticket = round.tickets.first()
        val one = round.claim("a", ticket.row(0).toSet())
        val two = one.claim("a", ticket.row(1).toSet())
        assertEquals(setOf(Prize.TOP_LINE, Prize.MIDDLE_LINE), two.awards.map { it.prize }.toSet())
        assertEquals(two, restored(two))
    }

    @Test fun `corrupted proof award and premature completion are rejected`() {
        val round = atAllNumbers(game()).claim("a", numbers(game(), "a"))
        assertThrows(IllegalArgumentException::class.java) { round.copy(claims = emptyList()).validated() }
        val forged = round.claims.single().copy(submissions = listOf(setOf(91)))
        assertThrows(IllegalArgumentException::class.java) { round.copy(claims = listOf(forged)).validated() }
        assertThrows(IllegalArgumentException::class.java) { round.copy(version = 3).validated() }
        assertThrows(IllegalArgumentException::class.java) { game().draw().copy(status = RoundStatus.COMPLETED).validated() }
    }

    @Test fun `undo removes claims for the removed call and preserves restored state`() {
        val round = atAllNumbers(game()).claim("a", numbers(game(), "a"))
        val undone = round.undo()
        assertEquals(89, undone.called.size)
        assertTrue(undone.claims.isEmpty())
        assertTrue(undone.awards.isEmpty())
        assertEquals(undone, restored(undone))
    }
}
