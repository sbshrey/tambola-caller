package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class WinMomentTest {
    @Test fun `another ticket from the same owner creates a separate claim celebration`() {
        var game = Round.create(players, RoundSettings(manualClaims = true, ticketsPerPlayer = 2, playAllNumbers = true), Random(12)).start()
        repeat(90) { game = game.draw() }
        val cards = game.tickets.filter { it.playerId == "a" }
        val first = game.claim("a", cards[0].numbers.toSet(), ClaimSelection(cards[0].id, Prize.TOP_LINE.name))
        val second = first.claim("a", cards[1].numbers.toSet(), ClaimSelection(cards[1].id, Prize.TOP_LINE.name))
        val moment = second.newWinMoment(first)!!
        assertEquals(listOf(players[0]), moment.players)
        assertEquals(1, moment.lines.single().ticketCount)
        assertNotEquals(first.newWinMoment(game)!!.id, moment.id)
        assertNull(second.newWinMoment(second))
    }
    @Test fun `a selected claim celebrates once between calls and a later tie celebrates only the new player`() {
        var before = Round.create(players, RoundSettings(manualClaims = true, playAllNumbers = true), Random(12)).start()
        repeat(90) { before = before.draw() }
        val first = before.claim("a", before.tickets.first().numbers.toSet(), ClaimSelection(before.tickets.first().id, Prize.EARLY_FIVE.name))
        assertEquals(listOf(players[0]), first.newWinMoment(before)!!.players)
        assertNull(first.newWinMoment(first))
        val secondTicket = before.tickets.first { it.playerId == "b" }
        val tied = first.claim("b", secondTicket.numbers.toSet(), ClaimSelection(secondTicket.id, Prize.EARLY_FIVE.name))
        assertEquals(listOf(players[1]), tied.newWinMoment(first)!!.players)
        assertEquals(90, tied.newWinMoment(first)!!.drawIndex)
        assertNull(tied.draw().newWinMoment(tied))
    }
    private val custom = CustomPrize("custom_all", "All my numbers", 25, TicketPattern(listOf(listOf(RuleCondition(NumberSelection.All)))))
    private val players = listOf(Player("a", "Asha", avatar = 1), Player("b", "Bina", avatar = 7))
    private val settings = RoundSettings(prizes = listOf(Prize.FULL_HOUSE), playAllNumbers = true, customPrizes = listOf(custom))
    private fun common(game: Round) = game.tickets.map { it.numbers.toSet() }.reduce { a, b -> a.intersect(b) }

    @Test fun `new strips preserve simultaneous winners across players and prize types`() {
        val ready = (0L..1000L).asSequence().map { Round.create(players, settings, Random(it), 1) }.first { common(it).isNotEmpty() }
        assertTiedPresentation(ready, 2)
    }

    @Test fun `legacy overlapping hands preserve all four tied tickets without awarding points again`() {
        // Old saves allow overlapping numbers within a hand. Build that fixture
        // explicitly rather than asking the new strip dealer for an impossible hand.
        val ready = (0L..1000L).asSequence().map { seed ->
            val dealer = TicketGenerator(Random(seed))
            val tickets = players.flatMap { player -> (1..2).map { dealer.generate("${player.id}-$it", player.id) } }
            Round.create(players, settings.copy(ticketsPerPlayer = 2), Random(seed), 1).copy(tickets = tickets).validated()
        }.first { common(it).isNotEmpty() }
        assertTiedPresentation(ready, 4)
    }

    private fun assertTiedPresentation(ready: Round, ticketCount: Int) {
        val last = common(ready).first()
        // Every selected ticket finishes together when its shared last number is called.
        var game = ready.copy(drawOrder = ready.drawOrder.filterNot { it == last } + last).start().validated()
        assertNull(game.winMoment())
        while (!game.finished) {
            game = game.draw()
            val moment = game.winMoment() ?: continue
            game.validated(); assertEquals(2, moment.lines.size)
            assertEquals(game.called.size, moment.drawIndex); assertEquals(game.latest, moment.number)
            assertEquals(players, moment.players)
            assertTrue(moment.lines.all { it.players == players && it.ticketCount == ticketCount })
            val before = game.players.map { game.score(it.id) }
            assertEquals(moment, game.winMoment()); assertEquals(before, game.players.map { game.score(it.id) })
            val redacted = moment.refreshPlayers(listOf(players[0].copy(name = "Deleted player", avatar = 0), players[1]))
            assertEquals("Deleted player", redacted.players.first().name); assertEquals(0, redacted.players.first().avatar)
            assertEquals(moment.id, redacted.id)
        }
    }
}
