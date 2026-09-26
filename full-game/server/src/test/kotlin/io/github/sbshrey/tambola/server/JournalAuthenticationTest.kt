package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.UUID

class JournalAuthenticationTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun configured(): Pair<Database, DeletionJournal> {
        val db = additionalDatabase()
        val journal = DeletionJournal(db).also { it.migrate() }
        service = RoomService(database, now::get, journal)
        service.replayDeletions()
        return db to journal
    }
    private fun guest() = service.register(GuestRequest("Journal access fixture"), id())
    private fun match(actor: GuestCredentials) = service.match(actor.token, MatchRequest(id(), 3)).snapshot
    private fun unavailable(block: () -> Unit) = assertEquals("55000", assertThrows(SQLException::class.java, block).sqlState)
    private fun denied(block: () -> Unit) = assertEquals("unauthorized", assertThrows(ApiFailure::class.java, block).code)

    @Test fun `independent logout denies a previously admitted stream and purchases before primary replay`() {
        val (_, journal) = configured()
        val actor = guest(); val room = match(actor)
        service.admitEvents(actor.token)
        service.pollEvents(actor.token, room.code)
        journal.revoke(actor.playerId, now.get())
        denied { service.pollEvents(actor.token, room.code) }
        denied { service.match(actor.token, MatchRequest(id(), 6)) }
        denied { service.wallet(actor.token) }
        database.transaction {
            assertEquals(1, it.query("SELECT count(*) FROM guests WHERE revoked_at IS NULL") { row -> row.getInt(1) }.single())
            assertEquals(1, it.query("SELECT count(*) FROM coin_ledger WHERE amount < 0") { row -> row.getInt(1) }.single())
        }
    }

    @Test fun `uninitialized mismatched and ahead recovery cursors fail authenticated access closed`() {
        val (_, journal) = configured()
        val actor = guest(); val room = match(actor)
        val original = journal.position()
        listOf(null to 0L, id() to 0L, original.id to 1L).forEach { (identity, sequence) ->
            database.transaction { it.execute("UPDATE deletion_recovery SET journal_id = ?, applied_sequence = ?", identity, sequence) }
            unavailable { service.pollEvents(actor.token, room.code) }
            unavailable { service.wallet(actor.token) }
        }
        database.transaction { it.execute("UPDATE deletion_recovery SET journal_id = ?, applied_sequence = 0", original.id) }
        assertEquals(room.roomId, service.pollEvents(actor.token, room.code).snapshot.roomId)
    }

    @Test fun `changing both stored identities cannot bypass the running journal identity guard`() {
        val (journalDb, _) = configured()
        val actor = guest(); val room = match(actor)
        val replacement = id()
        // Owner-only fixture: runtime grants prohibit these identity mutations.
        journalDb.transaction {
            it.execute("ALTER TABLE deletion_journal_identity DISABLE TRIGGER deletion_journal_head_guard")
            it.execute("UPDATE deletion_journal_identity SET journal_id = ?", replacement)
            it.execute("ALTER TABLE deletion_journal_identity ENABLE TRIGGER deletion_journal_head_guard")
        }
        database.transaction { it.execute("UPDATE deletion_recovery SET journal_id = ?", replacement) }
        unavailable { service.pollEvents(actor.token, room.code) }
        unavailable { service.wallet(actor.token) }
    }

    @Test fun `journal rollback during authentication cannot pass a previously checked head`() {
        val (journalDb, journal) = configured()
        val actor = guest(); val room = match(actor)
        journal.revoke(id(), now.get())
        assertEquals(1, service.replayDeletions())
        var restored = false
        val interleaved = RoomService(database, {
            // The primary recovery cursor was read; the credential lookup now observes
            // an independently restored older journal, retaining its original identity.
            if (!restored) {
                restored = true
                journalDb.transaction {
                    it.execute("DELETE FROM session_revocations")
                    it.execute("ALTER TABLE deletion_journal_identity DISABLE TRIGGER deletion_journal_head_guard")
                    it.execute("UPDATE deletion_journal_identity SET head = 0")
                    it.execute("ALTER TABLE deletion_journal_identity ENABLE TRIGGER deletion_journal_head_guard")
                }
            }
            now.get()
        }, journal)
        unavailable { interleaved.pollEvents(actor.token, room.code) }
        assertTrue(restored)
        assertEquals(1L, database.transaction { it.query("SELECT applied_sequence FROM deletion_recovery") { row -> row.getLong(1) }.single() })
    }

    @Test fun `journal outage rejects live and missing credentials without cached stream authorization`() {
        val (journalDb, _) = configured()
        val actor = guest(); val room = match(actor)
        service.pollEvents(actor.token, room.code)
        journalDb.close()
        assertThrows(SQLException::class.java) { service.pollEvents(actor.token, room.code) }
        assertThrows(SQLException::class.java) { service.wallet(secret()) }
    }
}
