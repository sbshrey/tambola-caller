package io.github.sbshrey.tambola.domain

import org.junit.Assert.*
import org.junit.Test

class PracticePersonasTest {
    @Test fun `every full roster has distinct stable fictional names and preserved computer identity`() {
        repeat(256) { seed ->
            val roster = (1..49).map { practicePersona("room-$seed", it) }
            assertEquals(49, roster.map { it.name }.distinct().size)
            assertEquals(49, roster.map { it.id }.distinct().size)
            assertTrue(roster.all { it.computer && it.avatar in 0 until AVATAR_COUNT && it.name.length <= 40 })
            assertEquals(roster, (1..49).map { practicePersona("room-$seed", it) })
        }
        assertNotEquals(practicePersona("a", 1), practicePersona("b", 1))
    }
}
