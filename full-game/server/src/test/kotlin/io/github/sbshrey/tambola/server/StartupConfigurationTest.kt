package io.github.sbshrey.tambola.server

import org.junit.Assert.*
import org.junit.Test

class StartupConfigurationTest {
    @Test fun `local migration and privilege exception requires two explicit loopback fixtures`() {
        val primary = "jdbc:postgresql://127.0.0.1:5432/tambola_test"
        assertTrue(localFixtureMode("127.0.0.1", primary, null, true))
        assertTrue(localFixtureMode("127.0.0.1", "jdbc:postgresql://127.0.0.1:5432/tambola_load_main_0123456789abcdef",
            "jdbc:postgresql://127.0.0.1:5432/tambola_load_journal_0123456789abcdef", true))
        listOf("jdbc:postgresql://remote:5432/tambola_test", "jdbc:postgresql://127.0.0.1:5432/production_journal").forEach { journal ->
            assertThrows(IllegalArgumentException::class.java) { localFixtureMode("127.0.0.1", primary, journal, true) }
        }
        assertThrows(IllegalArgumentException::class.java) { localFixtureMode("0.0.0.0", primary, null, true) }
        assertThrows(IllegalArgumentException::class.java) { localFixtureMode("127.0.0.1", primary + "?currentSchema=production", null, true) }
    }

    @Test fun `ordinary serving never opts into fixture permissions implicitly`() {
        assertFalse(localFixtureMode("127.0.0.1", "jdbc:postgresql://127.0.0.1:5432/tambola_test", null, false))
        assertFalse(localFixtureMode("0.0.0.0", "jdbc:postgresql://remote:5432/rooms", "jdbc:postgresql://remote:5432/journal", false))
    }
}
