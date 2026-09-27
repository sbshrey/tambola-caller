package io.github.sbshrey.tambola.server

import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Connection

class DatabaseTimeoutTest : PostgresTest() {
    @Test fun `timeouts apply to reads and writes and do not leak after commit or rollback`() {
        for (readOnly in listOf(false, true)) for (rollback in listOf(false, true)) {
            database.transaction(readOnly = readOnly) { connection ->
                val configured = settings(connection)
                assertEquals(mapOf("lock_timeout" to "5000", "statement_timeout" to "10000"),
                    configured.associate { it.first to it.second })
                val defaults = configured.associate { it.first to it.third }

                // Keep the physical connection leased while ending the transaction,
                // so the next read can detect settings leaking into a pooled session.
                if (rollback) connection.rollback() else connection.commit()
                assertEquals(defaults, settings(connection).associate { it.first to it.second })
            }
        }
    }

    private fun settings(connection: Connection) = connection.query("""
        SELECT name, setting, reset_val FROM pg_settings
        WHERE name IN ('lock_timeout', 'statement_timeout') ORDER BY name
    """.trimIndent()) { Triple(it.getString(1), it.getString(2), it.getString(3)) }
}
