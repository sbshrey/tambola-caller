package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MatchRateAtomicityTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun configured(): DeletionJournal {
        val journal = DeletionJournal(additionalDatabase()).also { it.migrate() }
        service = RoomService(database, now::get, journal)
        service.replayDeletions()
        return journal
    }
    private fun guest() = service.register(GuestRequest("Purchase rate QA"), id())
    private fun attempts(actor: GuestCredentials) = database.transaction { connection ->
        connection.query("SELECT requests FROM rate_limits WHERE bucket = ? AND window_start = ?",
            "match:${actor.playerId}", now.get() / 60_000) { it.getInt(1) }.singleOrNull() ?: 0
    }
    private fun count(table: String) = database.transaction { it.query("SELECT count(*) FROM $table") { row -> row.getInt(1) }.single() }

    @Test fun `failed purchases retain the quota while leaving no purchase artifacts`() {
        configured()
        val actor = guest()
        database.transaction { CoinLedger.change(it, actor.playerId, "test:spend", -1500, now.get()) }
        repeat(20) {
            assertEquals("coins_low", assertThrows(ApiFailure::class.java) {
                service.match(actor.token, MatchRequest(id(), 1))
            }.code)
        }
        assertEquals(20, attempts(actor))
        assertEquals("rate_limited", assertThrows(ApiFailure::class.java) {
            service.match(actor.token, MatchRequest(id(), 1))
        }.code)
        assertEquals(20, attempts(actor))
        assertEquals(0, count("rooms")); assertEquals(0, count("match_receipts")); assertEquals(0, count("room_participants"))
        assertEquals(0L, service.wallet(actor.token).balance)
    }

    @Test fun `receipt storage failure keeps the attempt but rolls back the entire purchase for an exact retry`() {
        configured()
        val actor = guest(); val request = MatchRequest(id(), 3)
        database.transaction {
            it.execute("CREATE FUNCTION reject_match_receipt() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''injected receipt failure''; END;'")
            it.execute("CREATE TRIGGER reject_match_receipt BEFORE INSERT ON match_receipts FOR EACH ROW EXECUTE FUNCTION reject_match_receipt()")
        }
        assertThrows(SQLException::class.java) { service.match(actor.token, request) }
        assertEquals(1, attempts(actor))
        assertEquals(0, count("rooms")); assertEquals(0, count("match_receipts")); assertEquals(0, count("room_participants"))
        assertEquals(1, count("coin_ledger")); assertEquals(1500L, service.wallet(actor.token).balance)
        database.transaction { it.execute("DROP TRIGGER reject_match_receipt ON match_receipts") }
        val result = service.match(actor.token, request)
        assertEquals(result, service.match(actor.token, request))
        assertEquals(3, attempts(actor)); assertEquals(1, count("match_receipts")); assertEquals(2, count("coin_ledger"))
        assertEquals(1200L, service.wallet(actor.token).balance)
    }

    @Test fun `simultaneous purchases cannot spend the same final quota slot twice`() {
        configured()
        val actor = guest()
        database.transaction { it.execute("INSERT INTO rate_limits VALUES (?, ?, 19)", "match:${actor.playerId}", now.get() / 60_000) }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val outcomes = executor.invokeAll(List(4) { Callable {
                try { service.match(actor.token, MatchRequest(id(), 1)); "purchased" }
                catch (error: ApiFailure) { error.code }
            } }).map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, outcomes.count { it == "purchased" }); assertEquals(3, outcomes.count { it == "rate_limited" })
            assertEquals(20, attempts(actor)); assertEquals(1, count("match_receipts")); assertEquals(2, count("coin_ledger"))
            assertEquals(1400L, service.wallet(actor.token).balance)
        } finally { executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS) }
    }

    @Test fun `journal revocation rejects a purchase before consuming its quota without primary replay`() {
        val journal = configured(); val actor = guest()
        journal.revoke(actor.playerId, now.get())
        assertEquals("unauthorized", assertThrows(ApiFailure::class.java) {
            service.match(actor.token, MatchRequest(id(), 1))
        }.code)
        assertEquals(0, attempts(actor)); assertEquals(0, count("match_receipts")); assertEquals(0, count("rooms"))
    }
}
