package io.github.sbshrey.tambola.server

import org.junit.After
import org.junit.Before
import org.junit.Test

class HistoryDeletionTest {
    private val fixture = HistoryDeletionFixture()
    @Before fun prepare() = fixture.prepareDatabase()
    @After fun cleanup() = fixture.disposeDatabase()

    @Test fun `all retained rooms archives and peer responses redact across cursor pages`() {
        fixture.seed(70, 70, 130)
        fixture.deleteAndVerify()
    }

    @Test fun `failure on the final response rolls back earlier pages and durable replay succeeds`() {
        fixture.seed(70, 70, 130)
        fixture.lateFailureRollsBackAndJournalReplayCompletes()
    }
}
