package io.github.sbshrey.tambola.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class AvatarRoundTest {
    @Test fun `avatars survive saves without changing the game and invalid IDs fail closed`() {
        val players = listOf(Player("a", "Asha", avatar = 7), Player("b", "Bina", avatar = 3))
        var game = Round.create(players, RoundSettings(playAllNumbers = true), Random(91), 1).start()
        val number = game.tickets.first().numbers.first()
        while (number !in game.called) game = game.draw()
        game = game.toggleMark(game.tickets.first().id, number).pause()
        val restored = RoundCodec.decode(RoundCodec.encode(game))
        assertEquals(game, restored); assertEquals(3, restored.version)
        assertEquals(listOf(7, 3), restored.players.map { it.avatar })
        assertThrows(IllegalArgumentException::class.java) { Player("x", "Player", avatar = 8) }
        assertThrows(IllegalArgumentException::class.java) { Player("x", "Player", avatar = -1) }
        assertThrows(IllegalArgumentException::class.java) { RoundCodec.decode(RoundCodec.encode(game.copy(version = 2))) }
    }

    @Test fun `version two save without avatar fields retains tickets calls and marks`() {
        val raw = checkNotNull(javaClass.getResource("/round-v1.json")).readText().replace("\"version\":1", "\"version\":2")
        val before = RoundCodec.decode(raw)
        assertEquals(3, before.version); assertTrue(before.players.all { it.avatar == 0 })
        assertEquals("legacy-alpha-round", before.id)
        assertEquals(listOf(1, 2, 3, 4), before.called); assertEquals(setOf(1), before.marks["p0-1"])
        assertEquals(before, RoundCodec.decode(RoundCodec.encode(before)))
    }
}
