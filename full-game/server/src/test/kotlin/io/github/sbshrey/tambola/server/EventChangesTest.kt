package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EventChangesTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest() = service.register(GuestRequest("Event changes fixture"), id())
    private fun create(actor: GuestCredentials) = service.create(actor.token, CreateRoomRequest(id())).snapshot
    private fun changes(actor: GuestCredentials, room: RoomView, after: Long? = room.revision) =
        service.pollEvents(actor.token, room.code, after, onlyIfChanged = true)
    private fun denied(code: String, block: () -> Unit) = assertEquals(code, assertThrows(ApiFailure::class.java, block).code)

    @Test fun `unchanged coin poll does not wait for event history or wallet relation locks`() {
        val actor = guest()
        val room = service.match(actor.token, MatchRequest(id(), 6)).snapshot
        val executor = Executors.newSingleThreadExecutor()
        try {
            database.transaction { connection ->
                connection.execute("LOCK TABLE room_events, coin_ledger IN ACCESS EXCLUSIVE MODE")
                // These locks block the SELECTs used to build a full update. Authentication
                // and room access remain available, so a quiet poll must complete now.
                assertNull(executor.submit(Callable { changes(actor, room) }).get(3, TimeUnit.SECONDS))
            }
        } finally { executor.shutdownNow() }
        val initial = service.pollEvents(actor.token, room.code, room.revision)
        assertEquals(900L, initial.snapshot.wallet!!.balance)
        assertEquals(6, initial.snapshot.coins!!.ownTickets)
    }

    @Test fun `initial snapshots deltas resync and invalid cursors retain their semantics`() {
        val actor = guest(); val room = create(actor)
        assertEquals(room, service.pollEvents(actor.token, room.code, room.revision).snapshot)
        assertNull(changes(actor, room))
        assertTrue(changes(actor, room, null)!!.resyncRequired)
        assertTrue(changes(actor, room, room.revision + 1)!!.resyncRequired)
        denied("invalid_cursor") { changes(actor, room, -1) }
        val ready = service.command(actor.token, room.code, CommandRequest(id(), room.revision, RoomAction.Ready(true))).snapshot
        val delta = changes(actor, room)!!
        assertEquals(ready, delta.snapshot)
        assertEquals(listOf("ready"), delta.events.map { it.type })
        assertNull(changes(actor, ready))
    }

    @Test fun `quiet presence renewals still persist and a reconnect still emits its revision`() {
        val actor = guest(); val room = create(actor)
        now.addAndGet(15_000)
        assertNull(changes(actor, room))
        val seen = database.transaction { it.query("SELECT (payload::jsonb->'members'->0->>'lastSeen')::bigint FROM rooms WHERE id = ?", room.roomId) { row -> row.getLong(1) }.single() }
        assertEquals(now.get(), seen)
        now.addAndGet(46_000); service.tick()
        val reconnected = changes(actor, room)!!
        assertTrue(reconnected.snapshot.members.single().connected)
        assertTrue(reconnected.events.any { it.type == "reconnected" })
        assertTrue(reconnected.snapshot.revision > room.revision)
    }

    @Test fun `matching revisions cannot bypass membership room lifetime or credential expiry`() {
        val host = guest(); val peer = guest(); val room = create(host)
        val joined = service.join(peer.token, room.code).snapshot
        val removed = service.command(host.token, room.code, CommandRequest(id(), joined.revision, RoomAction.Remove(peer.playerId))).snapshot
        denied("not_member") { changes(peer, removed) }
        now.addAndGet(ROOM_LIFETIME)
        denied("room_closed") { changes(host, removed) }
        now.set(host.expiresAt)
        denied("unauthorized") { changes(host, removed) }
    }

    @Test fun `matching revisions still check fresh journal suppression recovery state and availability`() {
        val journalDb = additionalDatabase()
        val journal = DeletionJournal(journalDb).also { it.migrate() }
        service = RoomService(database, now::get, journal); service.replayDeletions()
        val loggedOut = guest(); val deleted = guest(); val active = guest()
        val rooms = listOf(loggedOut, deleted, active).associateWith(::create)
        rooms.forEach { (actor, room) -> assertNull(changes(actor, room)) }
        journal.revoke(loggedOut.playerId, now.get())
        journal.append(deleted.playerId, digest(id()), now.get(), now.get() + ROOM_LIFETIME)
        denied("unauthorized") { changes(loggedOut, rooms.getValue(loggedOut)) }
        denied("unauthorized") { changes(deleted, rooms.getValue(deleted)) }
        database.transaction { it.execute("UPDATE deletion_recovery SET applied_sequence = 3") }
        assertEquals("55000", assertThrows(SQLException::class.java) { changes(active, rooms.getValue(active)) }.sqlState)
        database.transaction { it.execute("UPDATE deletion_recovery SET applied_sequence = 0") }
        assertNull(changes(active, rooms.getValue(active)))
        journalDb.close()
        assertThrows(SQLException::class.java) { changes(active, rooms.getValue(active)) }
    }
}
