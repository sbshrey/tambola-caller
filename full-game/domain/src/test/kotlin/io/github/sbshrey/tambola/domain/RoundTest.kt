package io.github.sbshrey.tambola.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class RoundTest {
    private val players = listOf(Player("a", "Asha"), Player("b", "Bina"))
    private fun game(seed: Long = 42, settings: RoundSettings = RoundSettings()) = Round.create(players, settings, Random(seed), 1)

    @Test fun `one hundred thousand tickets obey structural invariants`() {
        val generator = TicketGenerator(Random(93028))
        repeat(100_000) { i ->
            val ticket = generator.generate("$i", "a")
            assertEquals(15, ticket.numbers.toSet().size)
            assertEquals(listOf(5, 5, 5), (0..2).map { ticket.row(it).size })
            (0..8).forEach { c ->
                val col = (0..2).map { ticket.cells[it * 9 + c] }.filter { it != 0 }
                assertTrue(col.size in 1..3)
                assertEquals(col.sorted(), col)
                assertTrue(col.all { it in columnRange(c) })
            }
        }
    }
    @Test fun `maximum room receives unique tickets with equal allocations`() {
        val group = (1..32).map { Player("$it", "Player $it") }
        val tickets = TicketGenerator(Random(8)).deal(group, 6)
        assertEquals(192, tickets.map { it.fingerprint }.toSet().size)
        assertTrue(group.all { p -> tickets.count { it.playerId == p.id } == 6 })
    }
    @Test fun `all ninety numbers occur once and terminal draw is idempotent`() {
        var round = game(settings = RoundSettings(playAllNumbers = true)).start()
        repeat(90) { round = round.draw(); assertEquals(it + 1, round.called.size) }
        assertEquals((1..90).toSet(), round.called.toSet())
        assertEquals(RoundStatus.COMPLETED, round.status)
        assertEquals(round, round.draw())
        assertEquals(round, RoundCodec.decode(RoundCodec.encode(round)))
    }
    @Test fun `pause resume and ready cannot accidentally draw`() {
        val ready = game()
        assertEquals(ready, ready.draw())
        val paused = ready.start().draw().pause()
        assertEquals(paused, paused.draw())
        assertEquals(2, paused.start().draw().called.size)
    }
    @Test fun `same draw ties retain all tickets and deduplicate player points`() {
        val original = game(settings = RoundSettings(ticketsPerPlayer = 6, prizes = listOf(Prize.EARLY_FIVE), playAllNumbers = true))
        // Search deterministic rounds until a real simultaneous eligibility event occurs.
        var tied: Round? = null
        for (seed in 0L..100L) {
            var round = game(seed, original.settings).start()
            while (round.awards.isEmpty()) round = round.draw()
            if (round.awards.first().ticketIds.size > 1) { tied = round; break }
        }
        assertNotNull(tied)
        val award = tied!!.awards.single()
        assertEquals(award.playerIds.distinct(), award.playerIds)
        award.playerIds.forEach { assertEquals(10, tied.score(it)) }
        assertEquals(tied, RoundCodec.decode(RoundCodec.encode(tied)))
    }
    @Test fun `claims use called numbers regardless of manual marks`() {
        var round = game().start()
        while (round.awards.isEmpty()) round = round.draw()
        assertTrue(round.marks.isEmpty())
        assertTrue(round.awards.isNotEmpty())
        val award = round.awards.first()
        award.ticketIds.forEach { id -> assertTrue(award.prize.matches(round.tickets.first { it.id == id }, round.called.toSet())) }
    }
    @Test fun `uncalled marks are rejected and correct toggles never change calls`() {
        val round = game().start().draw()
        val ticket = round.tickets.first()
        val uncalled = ticket.numbers.first { it !in round.called }
        val mistaken = round.toggleMark(ticket.id, uncalled)
        assertEquals(round, mistaken)
        assertEquals(mistaken, RoundCodec.decode(RoundCodec.encode(mistaken)))
        assertEquals(round.called, mistaken.called)
        assertThrows(IllegalArgumentException::class.java) { round.toggleMark(ticket.id, 0) }
        var playable = round
        while (ticket.numbers.none { it in playable.called }) playable = playable.draw()
        val number = ticket.numbers.first { it in playable.called }
        val marked = playable.toggleMark(ticket.id, number)
        assertEquals(playable.called, marked.called)
        assertEquals(playable.marks[ticket.id].orEmpty(), marked.toggleMark(ticket.id, number).marks[ticket.id])
    }
    @Test fun `practice undo invalidates same draw awards and marks`() {
        var round = game(settings = RoundSettings(assistedMarking = true)).start()
        while (round.awards.isEmpty()) round = round.draw()
        val undone = round.undo()
        assertTrue(undone.awards.isEmpty())
        assertTrue(undone.marks.values.none { round.latest in it })
        assertEquals(round.awards, undone.start().draw().awards)
        assertThrows(IllegalArgumentException::class.java) { game(settings = RoundSettings(mode = GameMode.FAMILY)).undo() }
    }
    @Test fun `ranked houses never award a completed ticket twice`() {
        var round = game(settings = RoundSettings(ticketsPerPlayer = 3, prizes = listOf(Prize.HOUSE_ONE, Prize.HOUSE_TWO, Prize.HOUSE_THREE))).start()
        while (!round.finished) round = round.draw()
        val ticketIds = round.awards.flatMap { it.ticketIds }
        assertEquals(ticketIds.distinct(), ticketIds)
        assertEquals(listOf(Prize.HOUSE_ONE, Prize.HOUSE_TWO, Prize.HOUSE_THREE), round.awards.map { it.prize })
        assertEquals(round, RoundCodec.decode(RoundCodec.encode(round)))
    }
    @Test fun `corrupt saved awards and future calls are rejected`() {
        val round = game().start().draw()
        assertThrows(IllegalArgumentException::class.java) { RoundCodec.decode(RoundCodec.encode(round.copy(awards = listOf(Award(Prize.FULL_HOUSE, 1, listOf("a-1"), listOf("a")))))) }
        assertThrows(IllegalArgumentException::class.java) { round.copy(called = listOf(round.drawOrder[1])).validated() }
        assertThrows(IllegalArgumentException::class.java) { round.copy(drawOrder = List(90) { 1 }).validated() }
        assertThrows(IllegalArgumentException::class.java) { round.copy(status = RoundStatus.COMPLETED).validated() }
    }
    @Test fun `every standard rule rejects a missing required number`() {
        val ticket = game().tickets.first()
        listOf(Prize.TOP_LINE to ticket.row(0), Prize.MIDDLE_LINE to ticket.row(1), Prize.BOTTOM_LINE to ticket.row(2), Prize.CORNERS to ticket.corners, Prize.FULL_HOUSE to ticket.numbers).forEach { (prize, numbers) ->
            assertTrue(prize.matches(ticket, numbers.toSet()))
            numbers.forEach { missing -> assertFalse(prize.matches(ticket, numbers.toSet() - missing)) }
        }
        assertFalse(Prize.EARLY_FIVE.matches(ticket, ticket.numbers.take(4).toSet()))
        assertTrue(Prize.EARLY_FIVE.matches(ticket, ticket.numbers.take(5).toSet()))
        assertFalse(Prize.EARLY_TEN.matches(ticket, ticket.numbers.take(9).toSet()))
        assertTrue(Prize.EARLY_TEN.matches(ticket, ticket.numbers.take(10).toSet()))
    }
}
