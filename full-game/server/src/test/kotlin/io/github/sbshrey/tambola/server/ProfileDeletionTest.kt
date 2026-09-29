package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProfileDeletionTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest(name: String) = service.register(GuestRequest(name, 6), id())
    private fun request() = DeleteProfileRequest(id())
    private fun command(actor: GuestCredentials, code: String, action: RoomAction): RoomView =
        service.command(actor.token, code, CommandRequest(id(), service.read(actor.token, code).snapshot.revision, action)).snapshot
    private fun room(host: GuestCredentials, peer: GuestCredentials): RoomView {
        val room = service.create(host.token, CreateRoomRequest(id(), RoomOptions(automaticCalling = false))).snapshot
        service.join(peer.token, room.code)
        return room
    }
    private fun start(host: GuestCredentials, peer: GuestCredentials, code: String): RoomView {
        command(host, code, RoomAction.Ready(true)); command(peer, code, RoomAction.Ready(true))
        return command(host, code, RoomAction.Start)
    }
    private fun storedText() = database.transaction { connection ->
        connection.query("SELECT payload FROM rooms UNION ALL SELECT payload FROM finished_rounds UNION ALL SELECT response FROM command_receipts") { it.getString(1) }.sorted().joinToString()
    }
    private fun count(table: String, column: String, value: String) = database.transaction {
        require(table in setOf("guests", "command_receipts", "room_participants", "create_receipts"))
        require(column in setOf("id", "actor", "player_id"))
        it.query("SELECT count(*) FROM $table WHERE $column = ?", value) { row -> row.getInt(1) }.single()
    }

    @Test fun `deleting an active host redacts all stored profile copies and transfers control without changing the game`() {
        val host = guest("Private Asha 2026"); val peer = guest("Bina")
        val code = room(host, peer).code
        start(host, peer, code); command(host, code, RoomAction.Draw); command(host, code, RoomAction.End)
        command(host, code, RoomAction.Rematch)
        val oldRequest = CommandRequest(id(), service.read(peer.token, code).snapshot.revision, RoomAction.Ready(true))
        service.command(peer.token, code, oldRequest)
        command(host, code, RoomAction.Ready(true)); command(host, code, RoomAction.Start)
        val before = command(host, code, RoomAction.Draw)
        assertEquals(6, before.round!!.players.first { it.id == host.playerId }.avatar)
        val deletion = request()
        val receipt = service.deleteProfile(host.token, deletion, "deletion-test")
        assertEquals(receipt, RoomService(database, now::get).deleteProfile(host.token, deletion, "deletion-test"))
        val after = service.read(peer.token, code).snapshot
        after.validateFor(peer.playerId)
        assertEquals(peer.playerId, after.hostId)
        assertEquals(before.round!!.called, after.round!!.called)
        assertEquals(before.round!!.scores, after.round!!.scores)
        assertEquals(before.round!!.drawCommitment, after.round!!.drawCommitment)
        assertEquals("Deleted player", after.round!!.players.first { it.id == host.playerId }.name)
        assertEquals(0, after.round!!.players.first { it.id == host.playerId }.avatar)
        assertEquals(6, after.round!!.players.first { it.id == peer.playerId }.avatar)
        assertTrue(after.members.none { it.playerId == host.playerId })
        assertTrue(after.round!!.ownTickets.all { it.playerId == peer.playerId })
        assertEquals(2, command(peer, code, RoomAction.Draw).round!!.called.size)
        val retry = service.command(peer.token, code, oldRequest)
        assertFalse(WireJson.encodeToString(retry).contains("Private Asha 2026"))
        assertTrue(retry.snapshot.members.any { it.playerId == host.playerId && it.avatar == 0 })
        assertFalse(storedText().contains("Private Asha 2026"))
        database.transaction { connection ->
            connection.query("SELECT payload FROM rooms UNION ALL SELECT payload FROM finished_rounds") {
                WireJson.decodeFromString<RoomRecord>(it.getString(1))
            }.forEach { record -> record.round?.players?.filter { it.id == host.playerId }?.forEach { assertEquals(0, it.avatar) } }
            connection.query("SELECT response FROM command_receipts") { WireJson.decodeFromString<RoomUpdate>(it.getString(1)) }
                .forEach { update -> update.snapshot.round?.players?.filter { it.id == host.playerId }?.forEach { assertEquals(0, it.avatar) } }
        }
        assertEquals(0, count("guests", "id", host.playerId))
        assertEquals(0, count("command_receipts", "actor", host.playerId))
        assertEquals(0, count("create_receipts", "actor", host.playerId))
        assertEquals(0, count("room_participants", "player_id", host.playerId))
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.read(host.token, code) }.status)
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.deleteProfile(host.token, request(), "deletion-test") }.status)
    }

    @Test fun `redaction is tied to identity rather than replacing another player's matching display name`() {
        val host = guest("Same name"); val peer = guest("Same name")
        val code = room(host, peer).code
        start(host, peer, code)
        service.deleteProfile(host.token, request(), "names")
        val result = service.read(peer.token, code).snapshot
        assertEquals("Same name", result.round!!.players.first { it.id == peer.playerId }.name)
        assertEquals("Deleted player", result.round!!.players.first { it.id == host.playerId }.name)
        assertEquals(1, count("guests", "id", peer.playerId))
    }

    @Test fun `failure while scrubbing an audit rolls back profile room receipts and confirmation together`() {
        val host = guest("Rollback Asha"); val peer = guest("Bina")
        val code = room(host, peer).code
        start(host, peer, code); command(host, code, RoomAction.End)
        val before = storedText()
        database.transaction {
            it.execute("CREATE FUNCTION reject_redaction() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''injected rollback''; END;'")
            it.execute("CREATE TRIGGER reject_redaction BEFORE UPDATE ON finished_rounds FOR EACH ROW EXECUTE FUNCTION reject_redaction()")
        }
        val deletion = request()
        assertThrows(SQLException::class.java) { service.deleteProfile(host.token, deletion, "rollback") }
        assertEquals(before, storedText())
        assertEquals(1, count("guests", "id", host.playerId))
        assertEquals(0, database.transaction { it.query("SELECT count(*) FROM deletion_receipts") { row -> row.getInt(1) }.single() })
        database.transaction { it.execute("DROP TRIGGER reject_redaction ON finished_rounds") }
        service.deleteProfile(host.token, deletion, "rollback")
        assertFalse(storedText().contains("Rollback Asha"))
    }

    @Test fun `concurrent duplicate deletion returns one confirmation and racing joins cannot restore the name`() {
        val host = guest("Concurrent Asha"); val peer = guest("Bina")
        val code = service.create(peer.token, CreateRoomRequest(id())).snapshot.code
        val deletion = request()
        val pool = Executors.newFixedThreadPool(3)
        val gate = CountDownLatch(1)
        try {
            val deletes = (1..2).map { pool.submit(Callable { gate.await(); service.deleteProfile(host.token, deletion, "concurrent") }) }
            val joining = pool.submit(Callable { gate.await(); runCatching { service.join(host.token, code) }.exceptionOrNull() })
            gate.countDown()
            assertEquals(deletes[0].get(20, TimeUnit.SECONDS), deletes[1].get(20, TimeUnit.SECONDS))
            joining.get(20, TimeUnit.SECONDS)?.let { assertTrue(it is ApiFailure && it.status == 401) }
            assertTrue(service.read(peer.token, code).snapshot.members.none { it.playerId == host.playerId })
            assertFalse(storedText().contains("Concurrent Asha"))
        } finally { pool.shutdownNow() }
    }

    @Test fun `expired unrevoked sessions may delete but signed out sessions cannot and confirmations expire`() {
        val expired = guest("Expired profile")
        val revoked = guest("Signed out profile")
        service.revoke(revoked.token)
        now.addAndGet(SESSION_LIFETIME + 1)
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.create(expired.token, CreateRoomRequest(id())) }.status)
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.deleteProfile(revoked.token, request(), "expiry") }.status)
        val deletion = request()
        val receipt = service.deleteProfile(expired.token, deletion, "expiry")
        assertEquals(now.get() + 30 * ROOM_LIFETIME, receipt.confirmUntil)
        now.set(receipt.confirmUntil)
        service.cleanup()
        assertEquals(0, database.transaction { it.query("SELECT count(*) FROM deletion_receipts") { row -> row.getInt(1) }.single() })
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.deleteProfile(expired.token, deletion, "expiry") }.status)
    }

    @Test fun `deleting the final member closes the room and an unrelated profile remains intact`() {
        val host = guest("Only player"); val unrelated = guest("Unaffected")
        val room = service.create(host.token, CreateRoomRequest(id())).snapshot
        val result = service.deleteProfile(host.token, request(), "last-member")
        val stored = database.transaction { it.query("SELECT payload FROM rooms WHERE id = ?", room.roomId) { row -> WireJson.decodeFromString<RoomRecord>(row.getString(1)) }.single() }
        assertEquals(RoomPhase.CLOSED, stored.phase); assertTrue(stored.members.isEmpty()); assertNull(stored.nextDrawAt)
        assertEquals(1, count("guests", "id", unrelated.playerId))
        assertFalse(WireJson.encodeToString(result).contains(host.playerId))
        val hash = database.transaction { it.query("SELECT confirmation_hash FROM deletion_receipts") { row -> row.getString(1) }.single() }
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun `migration backfills former members from archived rounds and verifies immutable checksums`() {
        // Migration 002 uses PostgreSQL's wall clock to revoke already-expired sessions.
        // Seed this migration fixture against that same clock, not the fixed gameplay clock.
        now.set(database.transaction { connection ->
            connection.query("SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint") { it.getLong(1) }.single()
        })
        val host = guest("Migration Asha"); val peer = guest("Bina")
        val code = room(host, peer).code
        start(host, peer, code); command(host, code, RoomAction.End); command(host, code, RoomAction.Rematch)
        command(peer, code, RoomAction.Leave)
        database.transaction {
            it.execute("DROP TABLE room_participants"); it.execute("DROP TABLE deletion_receipts")
            it.execute("ALTER TABLE guests DROP COLUMN revoked_at")
            it.execute("DELETE FROM schema_migrations WHERE version = 2")
        }
        database.migrate(); database.migrate()
        assertEquals(1, count("room_participants", "player_id", peer.playerId))
        service.deleteProfile(peer.token, request(), "migration")
        assertFalse(storedText().contains("Bina"))
        assertTrue(storedText().contains("Migration Asha"))
        database.transaction { it.execute("UPDATE schema_migrations SET checksum = 'tampered' WHERE version = 2") }
        assertThrows(IllegalStateException::class.java) { database.migrate() }
    }
}
