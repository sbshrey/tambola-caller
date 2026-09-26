package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import io.ktor.server.testing.testApplication
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class DeletionJournalTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun configured(): Pair<Database, DeletionJournal> {
        val db = additionalDatabase()
        val journal = DeletionJournal(db).also { it.migrate(); it.migrate() }
        service = RoomService(database, now::get, journal)
        assertEquals(0, service.replayDeletions())
        return db to journal
    }
    private fun guest(name: String) = service.register(GuestRequest(name), id())
    private fun countGuests(player: String) = database.transaction { it.query("SELECT count(*) FROM guests WHERE id = ?", player) { row -> row.getInt(1) }.single() }

    @Test fun `durable intent survives primary rollback denies access and replay completes redaction`() {
        val (_, journal) = configured()
        val host = guest("Recovery Asha"); val peer = guest("Recovery Bina")
        val room = service.create(host.token, CreateRoomRequest(id())).snapshot
        service.join(peer.token, room.code)
        val deletion = DeleteProfileRequest(id())
        database.transaction {
            it.execute("CREATE FUNCTION reject_delete() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''injected primary rollback''; END;'")
            it.execute("CREATE TRIGGER reject_delete BEFORE DELETE ON guests FOR EACH ROW EXECUTE FUNCTION reject_delete()")
        }
        assertThrows(SQLException::class.java) { service.deleteProfile(host.token, deletion, "rollback") }
        assertEquals(1, countGuests(host.playerId))
        assertEquals(1L, journal.position().head)
        assertTrue(journal.suppresses(host.playerId))
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.read(host.token, room.code) }.status)
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.deleteProfile(host.token, DeleteProfileRequest(id()), "rollback") }.status)
        assertThrows(SQLException::class.java) { service.replayDeletions() }
        assertThrows(SQLException::class.java) { service.recoveryHealthy() }
        assertEquals(0L, database.transaction { it.query("SELECT applied_sequence FROM deletion_recovery") { row -> row.getLong(1) }.single() })
        database.transaction { it.execute("DROP TRIGGER reject_delete ON guests") }
        assertEquals(1, service.replayDeletions())
        assertTrue(service.recoveryHealthy())
        assertEquals(0, countGuests(host.playerId))
        val remaining = service.read(peer.token, room.code).snapshot
        assertEquals(peer.playerId, remaining.hostId)
        assertTrue(remaining.members.none { it.playerId == host.playerId })
        assertEquals(now.get(), service.deleteProfile(host.token, deletion, "retry").deletedAt)
        assertEquals(0, service.replayDeletions())
        assertEquals(remaining.revision, service.read(peer.token, room.code).snapshot.revision)
    }

    @Test fun `journal failure cannot confirm deletion or mutate primary state`() {
        val (journalDb, journal) = configured()
        val host = guest("Journal failure fixture")
        val room = service.create(host.token, CreateRoomRequest(id())).snapshot
        journalDb.transaction {
            it.execute("CREATE FUNCTION reject_intent() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''injected journal failure''; END;'")
            it.execute("CREATE TRIGGER reject_intent BEFORE INSERT ON profile_deletions FOR EACH ROW EXECUTE FUNCTION reject_intent()")
        }
        val request = DeleteProfileRequest(id())
        assertThrows(SQLException::class.java) { service.deleteProfile(host.token, request, "journal-failure") }
        assertEquals(0L, journal.position().head)
        assertEquals(1, countGuests(host.playerId))
        assertEquals(room.revision, service.read(host.token, room.code).snapshot.revision)
        journalDb.transaction { it.execute("DROP TRIGGER reject_intent ON profile_deletions") }
        service.deleteProfile(host.token, request, "journal-failure")
        assertEquals(1L, journal.position().head)
        assertEquals(0, countGuests(host.playerId))
    }

    @Test fun `different journal and rollback behind applied cursor fail closed`() {
        val (journalDb, journal) = configured()
        val host = guest("Identity fixture")
        service.deleteProfile(host.token, DeleteProfileRequest(id()), "identity")
        assertEquals(1, service.replayDeletions())
        val different = DeletionJournal(additionalDatabase()).also { it.migrate() }
        assertThrows(SQLException::class.java) { RoomService(database, now::get, different).replayDeletions() }
        journalDb.transaction {
            // Owner-only corruption fixture models restoring an older journal snapshot.
            it.execute("ALTER TABLE deletion_journal_identity DISABLE TRIGGER deletion_journal_head_guard")
            it.execute("UPDATE deletion_journal_identity SET head = 0")
            it.execute("ALTER TABLE deletion_journal_identity ENABLE TRIGGER deletion_journal_head_guard")
        }
        assertThrows(SQLException::class.java) { service.replayDeletions() }
        assertThrows(SQLException::class.java) { service.recoveryHealthy() }
        assertEquals(1L, database.transaction { it.query("SELECT applied_sequence FROM deletion_recovery") { row -> row.getLong(1) }.single() })
        journalDb.transaction { it.execute("UPDATE deletion_journal_identity SET head = 1") }
        assertEquals(1L, journal.position().head)
    }

    @Test fun `expired confirmation still suppresses identity and journal contains only recovery fields`() {
        val (journalDb, journal) = configured()
        val host = guest("Expired recovery fixture")
        val request = DeleteProfileRequest(id())
        val receipt = service.deleteProfile(host.token, request, "expiry")
        now.set(receipt.confirmUntil)
        service.cleanup()
        service.replayDeletions()
        assertTrue(journal.suppresses(host.playerId))
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.deleteProfile(host.token, request, "expiry") }.status)
        val columns = journalDb.transaction { it.query("SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'profile_deletions' ORDER BY ordinal_position") { row -> row.getString(1) } }
        assertEquals(listOf("sequence", "player_id", "confirmation_hash", "deleted_at", "confirm_until"), columns)
        val text = journalDb.transaction { it.query("SELECT row_to_json(d)::text FROM profile_deletions d") { row -> row.getString(1) }.single() }
        assertFalse(text.contains(host.token)); assertFalse(text.contains("Expired recovery fixture"))
    }

    @Test fun `concurrent journal writers produce contiguous committed sequence and duplicate intent is stable`() {
        val (_, journal) = configured()
        val pool = Executors.newFixedThreadPool(6)
        try {
            val futures = (1..24).map { pool.submit(Callable { journal.append(id(), digest(id()), now.get(), now.get() + 1000) }) }
            val entries = futures.map { it.get(20, TimeUnit.SECONDS) }.sortedBy { it.sequence }
            assertEquals((1L..24L).toList(), entries.map { it.sequence })
            entries.forEach { assertEquals(it, journal.next(it.sequence - 1, 24)) }
            val first = entries.first()
            assertEquals(first, journal.append(first.playerId, first.proof, now.get() + 1, now.get() + 2000))
            assertEquals(24L, journal.position().head)
        } finally { pool.shutdownNow() }
    }

    @Test fun `two replay workers and two retries redact shared rooms without skipping intents or deadlocking`() {
        val (_, journal) = configured()
        val first = guest("Concurrent deletion A"); val second = guest("Concurrent deletion B"); val remaining = guest("Continuing player")
        val room = service.create(first.token, CreateRoomRequest(id())).snapshot
        service.join(second.token, room.code); service.join(remaining.token, room.code)
        val requests = listOf(first, second).map { actor ->
            val request = DeleteProfileRequest(id())
            journal.append(actor.playerId, digest("tambola-delete-v1\n${actor.token}\n${request.id}"), now.get(), now.get() + 30 * ROOM_LIFETIME)
            actor to request
        }
        val pool = Executors.newFixedThreadPool(4)
        val gate = CountDownLatch(1)
        try {
            val jobs = requests.map { (actor, request) -> pool.submit(Callable { gate.await(); service.deleteProfile(actor.token, request, "concurrent-recovery") }) } +
                (1..2).map { pool.submit(Callable { gate.await(); service.replayDeletions() }) }
            gate.countDown()
            jobs.forEach { it.get(20, TimeUnit.SECONDS) }
            assertEquals(0, service.replayDeletions())
            val result = service.read(remaining.token, room.code).snapshot
            assertEquals(remaining.playerId, result.hostId)
            assertEquals(listOf(remaining.playerId), result.members.map { it.playerId })
            assertEquals(2L, database.transaction { it.query("SELECT applied_sequence FROM deletion_recovery") { row -> row.getLong(1) }.single() })
        } finally { pool.shutdownNow() }
    }

    @Test fun `a missing journal entry fails replay and readiness without advancing the cursor`() {
        val (journalDb, journal) = configured()
        journal.append(id(), digest(id()), now.get(), now.get() + 1000)
        journalDb.transaction { it.execute("DELETE FROM profile_deletions WHERE sequence = 1") }
        assertThrows(SQLException::class.java) { service.replayDeletions() }
        assertThrows(SQLException::class.java) { service.recoveryHealthy() }
        assertEquals(0L, database.transaction { it.query("SELECT applied_sequence FROM deletion_recovery") { row -> row.getLong(1) }.single() })
    }

    @Test fun `journal outage makes real HTTP readiness unavailable while liveness remains available`() = testApplication {
        val (journalDb, _) = configured()
        application { roomsModule(database, service, runWorker = false) }
        assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
        journalDb.close()
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/health/ready").status)
        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
    }

    @Test fun `journal is mandatory except for explicit isolated local development`() {
        val primary = "jdbc:postgresql://127.0.0.1:5432/tambola_test"
        assertThrows(IllegalArgumentException::class.java) { validateRecoveryConfiguration("127.0.0.1", primary, null, false) }
        assertThrows(IllegalArgumentException::class.java) { validateRecoveryConfiguration("0.0.0.0", primary, null, true) }
        assertThrows(IllegalArgumentException::class.java) { validateRecoveryConfiguration("127.0.0.1", primary.replace("_test", "_production"), null, true) }
        assertThrows(IllegalArgumentException::class.java) { validateRecoveryConfiguration("127.0.0.1", primary, primary + "?sslmode=require", false) }
        validateRecoveryConfiguration("127.0.0.1", primary, null, true)
        validateRecoveryConfiguration("0.0.0.0", primary, "jdbc:postgresql://127.0.0.1:5432/tambola_deletions", false)
    }

    @Test fun `database inspection rejects shared storage even when URL spellings differ`() {
        val (journalDb, journal) = configured()
        journal.verifyRestoreBoundary(database)
        assertThrows(SQLException::class.java) { journal.verifyRestoreBoundary(journalDb) }
        val misplaced = DeletionJournal(database).also { it.migrate() }
        assertThrows(SQLException::class.java) { misplaced.verifyRestoreBoundary(database) }
    }
}
