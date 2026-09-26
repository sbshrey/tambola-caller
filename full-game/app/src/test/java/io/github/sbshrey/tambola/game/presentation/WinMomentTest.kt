package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class WinMomentTest {
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
