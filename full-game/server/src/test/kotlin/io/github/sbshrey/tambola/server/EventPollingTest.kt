package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class EventPollingTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest() = service.register(GuestRequest("Polling fixture"), id())
    private fun create(actor: GuestCredentials) = service.create(actor.token, CreateRoomRequest(id())).snapshot
    private fun failure(code: String, block: () -> Unit) = assertEquals(code, assertThrows(ApiFailure::class.java, block).code)
    private fun versions() = database.transaction(readOnly = true) { connection ->
        listOf("guests", "rooms", "rate_limits").associateWith { table ->
            connection.query("SELECT xmin::text, xmax::text, ctid::text FROM $table ORDER BY ctid") {
                listOf(it.getString(1), it.getString(2), it.getString(3))
            }
        }
    }

    @Test fun `quiet authenticated polls leave database tuples unchanged and do not consume HTTP quota`() {
        val actor = guest(); val room = create(actor)
        service.admitEvents(actor.token)
        val before = versions()
        repeat(320) {
            val update = service.pollEvents(actor.token, room.code, room.revision)
            assertEquals(room.revision, update.snapshot.revision)
            assertTrue(update.events.isEmpty())
        }
        assertEquals(before, versions())
        repeat(300) { service.read(actor.token, room.code) }
        failure("rate_limited") { service.read(actor.token, room.code) }
        assertEquals(room.code, service.pollEvents(actor.token, room.code).snapshot.code)
    }

    @Test fun `admission quota is persisted across workers and expires at the next minute`() {
        val actor = guest()
        val second = RoomService(database, now::get)
        repeat(10) { service.admitEvents(actor.token); second.admitEvents(actor.token) }
        failure("rate_limited") { second.admitEvents(actor.token) }
        now.addAndGet(60_000)
        second.admitEvents(actor.token)
    }

    @Test fun `polls renew presence and reconnect with a durable revision`() {
        val actor = guest(); val room = create(actor)
        val before = versions()
        now.addAndGet(15_000)
        assertEquals(room.revision, service.pollEvents(actor.token, room.code).snapshot.revision)
        assertNotEquals(before.getValue("rooms"), versions().getValue("rooms"))
        now.addAndGet(46_000)
        service.tick()
        val connected = service.pollEvents(actor.token, room.code, room.revision)
        assertTrue(connected.snapshot.members.single().connected)
        assertTrue(connected.events.any { it.type == "reconnected" })
        assertTrue(connected.snapshot.revision > room.revision)
    }

    @Test fun `every poll rechecks revocation expiry membership and room lifetime`() {
        val host = guest(); val peer = guest(); val room = create(host)
        service.join(peer.token, room.code)
        service.pollEvents(peer.token, room.code)
        val revision = service.read(host.token, room.code).snapshot.revision
        service.command(host.token, room.code, CommandRequest(id(), revision, RoomAction.Remove(peer.playerId)))
        failure("not_member") { service.pollEvents(peer.token, room.code) }
        service.revoke(host.token)
        failure("unauthorized") { service.pollEvents(host.token, room.code) }
        val next = guest(); val nextRoom = create(next)
        now.addAndGet(ROOM_LIFETIME)
        failure("room_closed") { service.pollEvents(next.token, nextRoom.code) }
        now.set(next.expiresAt)
        failure("unauthorized") { service.pollEvents(next.token, nextRoom.code) }
    }

    @Test fun `quiet poll checks independent deletion suppression before primary replay`() {
        val journal = DeletionJournal(additionalDatabase()).also { it.migrate() }
        service = RoomService(database, now::get, journal)
        service.replayDeletions()
        val actor = guest(); val room = create(actor)
        service.pollEvents(actor.token, room.code)
        journal.append(actor.playerId, digest(id()), now.get(), now.get() + ROOM_LIFETIME)
        failure("unauthorized") { service.pollEvents(actor.token, room.code) }
        assertEquals(1, database.transaction { it.query("SELECT count(*) FROM guests") { row -> row.getInt(1) }.single() })
    }

    @Test fun `an event committed after reading the room is deferred to the next snapshot`() {
        val actor = guest(); val room = create(actor)
        val reads = AtomicInteger()
        val interleaved = RoomService(database, {
            // Authentication reads the clock first; load reads it after obtaining the payload.
            if (reads.incrementAndGet() == 2)
                service.command(actor.token, room.code, CommandRequest(id(), room.revision, RoomAction.Ready(true)))
            now.get()
        })
        val before = interleaved.pollEvents(actor.token, room.code, room.revision)
        assertEquals(room.revision, before.snapshot.revision)
        assertTrue(before.events.isEmpty())
        val after = service.pollEvents(actor.token, room.code, before.snapshot.revision)
        assertEquals(listOf("ready"), after.events.map { it.type })
        assertEquals(after.snapshot.revision, after.events.single().revision)
    }
}
