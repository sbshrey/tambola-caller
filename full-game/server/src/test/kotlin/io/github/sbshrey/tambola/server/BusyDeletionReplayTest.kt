package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BusyDeletionReplayTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun journal(): DeletionJournal = DeletionJournal(additionalDatabase()).also {
        it.migrate(); service = RoomService(database, now::get, it); assertEquals(0, service.replayDeletions())
    }
    private fun locked(sql: String, parameter: String? = null, block: () -> Unit) {
        val ready = CountDownLatch(1); val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val holder = executor.submit {
            database.transaction { c ->
                if (parameter == null) c.query(sql) { true } else c.query(sql, parameter) { true }
                ready.countDown(); check(release.await(15, TimeUnit.SECONDS))
            }
        }
        try { check(ready.await(5, TimeUnit.SECONDS)); block() }
        finally { release.countDown(); holder.get(5, TimeUnit.SECONDS); executor.shutdownNow() }
    }
    private fun deferQuickly() {
        val start = System.nanoTime()
        assertEquals(0, service.replayDeletions(skipBusy = true))
        assertTrue("Background replay should not wait for the five-second lock timeout", System.nanoTime() - start < TimeUnit.SECONDS.toNanos(3))
    }

    @Test fun `busy profile defers replay without advancing the cursor or blocking an unrelated room`() {
        val journal = journal()
        val player = service.register(GuestRequest("Busy Asha"), id())
        val peer = service.register(GuestRequest("Unaffected Bina"), id())
        val room = service.create(peer.token, CreateRoomRequest(id())).snapshot
        service.join(player.token, room.code)
        val request = DeleteProfileRequest(id())
        journal.append(player.playerId, digest("tambola-delete-v1\n${player.token}\n${request.id}"), now.get(), now.get() + 30 * ROOM_LIFETIME)
        locked("SELECT id FROM guests WHERE id = ? FOR UPDATE", player.playerId) {
            deferQuickly()
            assertTrue(service.recoveryHealthy())
            assertEquals(0L, database.transaction { it.query("SELECT applied_sequence FROM deletion_recovery") { row -> row.getLong(1) }.single() })
            assertEquals(2, service.read(peer.token, room.code).snapshot.members.size)
        }
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.read(player.token, room.code) }.status)
        assertEquals(1, service.replayDeletions(skipBusy = true))
        assertEquals(request.id, service.deleteProfile(player.token, request, "busy").id)
        assertEquals(listOf(peer.playerId), service.read(peer.token, room.code).snapshot.members.map { it.playerId })
    }

    @Test fun `busy cursor cannot clear a prior genuine recovery failure`() {
        journal()
        database.transaction { it.execute("UPDATE deletion_recovery SET applied_sequence = 1") }
        assertThrows(SQLException::class.java) { service.replayDeletions() }
        database.transaction { it.execute("UPDATE deletion_recovery SET applied_sequence = 0") }
        locked("SELECT singleton FROM deletion_recovery FOR UPDATE") {
            deferQuickly()
            assertThrows(SQLException::class.java) { service.recoveryHealthy() }
        }
        assertEquals(0, service.replayDeletions(skipBusy = true))
        assertTrue(service.recoveryHealthy())
    }

    @Test fun `a missing recovery cursor fails both strict startup replay and background replay`() {
        journal()
        database.transaction { it.execute("DELETE FROM deletion_recovery") }
        assertThrows(SQLException::class.java) { service.replayDeletions() }
        assertThrows(SQLException::class.java) { service.replayDeletions(skipBusy = true) }
    }
}
