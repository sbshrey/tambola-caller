package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SessionRevocationTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun configured(): Pair<Database, DeletionJournal> {
        val db = additionalDatabase()
        val journal = DeletionJournal(db).also { it.migrate() }
        service = RoomService(database, now::get, journal)
        assertEquals(0, service.replayDeletions())
        return db to journal
    }
    private fun guest(name: String = "Revocation QA") = service.register(GuestRequest(name), id())
    private fun enroll(actor: GuestCredentials) = secret().also { service.enrollDevice(actor.token, EnrollDeviceRequest(it)) }
    private fun denied(block: () -> Unit) = assertEquals(401, assertThrows(ApiFailure::class.java, block).status)
    private fun ledger(player: String) = database.transaction {
        it.query("SELECT row_to_json(l)::text FROM coin_ledger l WHERE player_id = ? ORDER BY entry_key", player) { row -> row.getString(1) }
    }

    @Test fun `restoring a pre-rotation backup never revives signed-out access and preserves shared play`() {
        val (_, journal) = configured()
        val actor = guest(); val peer = guest("Continuing player"); val key = enroll(actor)
        val room = service.match(actor.token, MatchRequest(id(), 6)).snapshot
        service.match(peer.token, MatchRequest(id(), 2))
        now.addAndGet(MATCH_COUNTDOWN + 1); service.tick()
        val shared = service.read(peer.token, room.code).snapshot
        val balance = service.wallet(actor.token); val entries = ledger(actor.playerId)
        withPrimaryBackup { restore ->
            val rotation = RenewSessionRequest(0, secret())
            val renewed = service.renewSession(key, rotation, "rotate")
            service.revoke(renewed.credentials.token)
            assertFalse(journal.suppresses(actor.playerId)) // logout is not a deletion
            assertTrue(journal.blocksAccess(actor.playerId))
            restore(); service = RoomService(database, now::get, journal)
            denied { service.wallet(actor.token) }
            denied { service.wallet(renewed.credentials.token) }
            denied { service.enrollDevice(actor.token, EnrollDeviceRequest(key)) }
            denied { service.renewSession(key, rotation, "replay") }
            denied { service.renewSession(key, RenewSessionRequest(0, secret()), "replacement") }
            denied { service.deleteProfile(actor.token, DeleteProfileRequest(id()), "revoked") }
            denied { service.revoke(actor.token) }
            assertEquals(1, service.replayDeletions())
            assertEquals(0, service.replayDeletions())
            assertEquals(shared, service.read(peer.token, room.code).snapshot)
            assertEquals(entries, ledger(actor.playerId))
            assertEquals(balance, database.transaction { CoinLedger.open(it, actor.playerId, now.get()) })
            val revoked = database.transaction { it.query("SELECT revoked_at, device_key_hash FROM guests WHERE id = ?", actor.playerId) {
                row -> row.getLong(1) to row.getString(2)
            }.single() }
            assertEquals(now.get() to null, revoked)
            now.addAndGet(31 * ROOM_LIFETIME); service.cleanup()
            assertTrue(journal.blocksAccess(actor.playerId))
            assertEquals(0, database.transaction { it.query("SELECT count(*) FROM coin_wallets WHERE player_id = ?", actor.playerId) { row -> row.getInt(1) }.single() })
        }
    }

    @Test fun `journal revocation survives primary rollback and retry keeps original expiry`() {
        val (_, journal) = configured()
        val actor = guest(); val key = enroll(actor)
        database.transaction {
            it.execute("CREATE FUNCTION reject_revoke() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''QA rollback''; END;'")
            it.execute("CREATE TRIGGER reject_revoke BEFORE UPDATE OF revoked_at ON guests FOR EACH ROW EXECUTE FUNCTION reject_revoke()")
        }
        val revokedAt = now.get()
        assertThrows(SQLException::class.java) { service.revoke(actor.token) }
        denied { service.wallet(actor.token) }
        denied { service.renewSession(key, RenewSessionRequest(0, secret()), "rollback") }
        denied { service.deleteProfile(actor.token, DeleteProfileRequest(id()), "rollback") }
        assertThrows(SQLException::class.java) { service.replayDeletions() }
        assertThrows(SQLException::class.java) { service.recoveryHealthy() }
        assertEquals(0L, database.transaction { it.query("SELECT applied_sequence FROM deletion_recovery") { row -> row.getLong(1) }.single() })
        database.transaction { it.execute("DROP TRIGGER reject_revoke ON guests") }
        now.addAndGet(ROOM_LIFETIME)
        assertEquals(RevocationIntent(1, actor.playerId, revokedAt), journal.revoke(actor.playerId, now.get()))
        assertEquals(1, service.replayDeletions()); assertTrue(service.recoveryHealthy())
        assertEquals(revokedAt, database.transaction { it.query("SELECT expires_at FROM guests WHERE id = ?", actor.playerId) { row -> row.getLong(1) }.single() })
        assertEquals(1L, journal.position().head)
    }

    @Test fun `failed independent write cannot confirm logout or discard a usable device credential`() {
        val (journalDb, journal) = configured()
        val actor = guest(); val key = enroll(actor)
        journalDb.transaction {
            it.execute("CREATE FUNCTION reject_revoke() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''QA journal failure''; END;'")
            it.execute("CREATE TRIGGER reject_revoke BEFORE INSERT ON session_revocations FOR EACH ROW EXECUTE FUNCTION reject_revoke()")
        }
        assertThrows(SQLException::class.java) { service.revoke(actor.token) }
        assertEquals(0L, journal.position().head)
        assertEquals(1500L, service.wallet(actor.token).balance)
        val next = service.renewSession(key, RenewSessionRequest(0, secret()), "unchanged")
        journalDb.transaction { it.execute("DROP TRIGGER reject_revoke ON session_revocations") }
        service.revoke(next.credentials.token)
        denied { service.renewSession(key, RenewSessionRequest(1, secret()), "revoked") }
        journalDb.close()
        assertThrows(SQLException::class.java) { service.recoveryHealthy() }
        assertThrows(SQLException::class.java) { service.revoke(next.credentials.token) }
    }

    @Test fun `mixed concurrent journal writes share contiguous commit order and stable duplicate revocations`() {
        val (_, journal) = configured()
        val pool = Executors.newFixedThreadPool(6)
        try {
            val entries = pool.invokeAll((1..24).map { number -> Callable {
                if (number % 2 == 0) journal.revoke(id(), now.get())
                else journal.append(id(), digest(id()), now.get(), now.get() + ROOM_LIFETIME)
            } }).map { it.get(20, TimeUnit.SECONDS) }.sortedBy { it.sequence }
            assertEquals((1L..24L).toList(), entries.map { it.sequence })
            entries.forEach { assertEquals(it, journal.next(it.sequence - 1, 24)) }
            val first = entries.filterIsInstance<RevocationIntent>().first()
            val duplicates = pool.invokeAll(List(6) { Callable { journal.revoke(first.playerId, now.get() + 1000) } })
            duplicates.forEach { assertEquals(first, it.get(10, TimeUnit.SECONDS)) }
            assertEquals(24L, journal.position().head)
            assertEquals(24, service.replayDeletions())
            assertEquals(0, service.replayDeletions())
        } finally { pool.shutdownNow() }
    }

    @Test fun `missing revocation cannot be skipped and duplicate intent kinds cannot advance head`() {
        val (journalDb, journal) = configured()
        val actor = guest(); service.revoke(actor.token)
        val collision = assertThrows(SQLException::class.java) { journalDb.transaction {
            it.execute("INSERT INTO profile_deletions VALUES (2, ?, ?, ?, ?)", id(), digest(id()), now.get(), now.get() + 1000)
            it.execute("INSERT INTO session_revocations VALUES (2, ?, ?)", id(), now.get())
            it.execute("UPDATE deletion_journal_identity SET head = 2")
        } }
        assertEquals("23514", collision.sqlState)
        assertEquals(1L, journal.position().head)
        journalDb.transaction { it.execute("DELETE FROM session_revocations WHERE sequence = 1") }
        assertThrows(SQLException::class.java) { service.replayDeletions() }
        assertThrows(SQLException::class.java) { service.recoveryHealthy() }
        assertEquals(0L, database.transaction { it.query("SELECT applied_sequence FROM deletion_recovery") { row -> row.getLong(1) }.single() })
    }

    @Test fun `journal upgrade from version two retains identity history and primary cursor`() {
        val db = additionalDatabase()
        val old = Migrations("journal_migrations", 749023802, listOf("001_deletions.sql", "002_head_guard.sql").map { "/db/journal/$it" })
        val identity = id(); val deleted = id(); val proof = digest(id())
        db.transaction { c ->
            old.migrate(c) { if (it == 1) c.execute("INSERT INTO deletion_journal_identity(singleton, journal_id) VALUES (true, ?)", identity) }
            c.execute("INSERT INTO profile_deletions VALUES (1, ?, ?, ?, ?)", deleted, proof, now.get(), now.get() + 1000)
            c.execute("UPDATE deletion_journal_identity SET head = 1")
        }
        database.transaction { it.execute("UPDATE deletion_recovery SET journal_id = ?, applied_sequence = 1", identity) }
        val journal = DeletionJournal(db)
        assertThrows(IllegalStateException::class.java) { journal.verifyMigrations() }
        journal.migrate(); journal.migrate(); journal.verifyMigrations()
        assertEquals(JournalPosition(identity, 1), journal.position())
        assertEquals(deleted, journal.find(proof)!!.playerId)
        assertThrows(IllegalStateException::class.java) { db.transaction { old.verify(it) } }
        service = RoomService(database, now::get, journal)
        val actor = guest(); service.revoke(actor.token)
        assertEquals(2L, journal.position().head)
        assertEquals(1, service.replayDeletions())
        assertEquals(2L, database.transaction { it.query("SELECT applied_sequence FROM deletion_recovery") { row -> row.getLong(1) }.single() })
        val columns = db.transaction { it.query("SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'session_revocations' ORDER BY ordinal_position") { row -> row.getString(1) } }
        assertEquals(listOf("sequence", "player_id", "revoked_at"), columns)
    }
}
