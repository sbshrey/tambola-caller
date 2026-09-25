package io.github.sbshrey.tambola.game.presentation

import io.github.sbshrey.tambola.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class WinMomentTest {
    @Test fun `presentation preserves every tied winner across standard and custom awards without awarding points again`() {
        val custom = CustomPrize("custom_all", "All my numbers", 25, TicketPattern(listOf(listOf(RuleCondition(NumberSelection.All)))))
        val players = listOf(Player("a", "Asha", avatar = 1), Player("b", "Bina", avatar = 7))
        val settings = RoundSettings(ticketsPerPlayer = 2, prizes = listOf(Prize.FULL_HOUSE), playAllNumbers = true, customPrizes = listOf(custom))
        fun common(game: Round) = game.tickets.map { it.numbers.toSet() }.reduce { a, b -> a.intersect(b) }
        val ready = (0L..1000L).asSequence().map { Round.create(players, settings, Random(it), 1) }.first { common(it).isNotEmpty() }
        val last = common(ready).first()
        // Four different valid tickets, with a shared number placed last in the test draw order.
        var game = ready.copy(drawOrder = ready.drawOrder.filterNot { it == last } + last).start().validated()
        assertNull(game.winMoment())
        while (!game.finished) {
            game = game.draw()
            val moment = game.winMoment() ?: continue
            game.validated(); assertEquals(2, moment.lines.size)
            assertEquals(game.called.size, moment.drawIndex); assertEquals(game.latest, moment.number)
            assertEquals(players, moment.players)
            assertTrue(moment.lines.all { it.players == players && it.ticketCount == 4 })
            val before = game.players.map { game.score(it.id) }
            assertEquals(moment, game.winMoment()); assertEquals(before, game.players.map { game.score(it.id) })
            val redacted = moment.refreshPlayers(listOf(players[0].copy(name = "Deleted player", avatar = 0), players[1]))
            assertEquals("Deleted player", redacted.players.first().name); assertEquals(0, redacted.players.first().avatar)
            assertEquals(moment.id, redacted.id)
        }
    }
}
