package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AvatarTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest(name: String, avatar: Int = 0) = service.register(GuestRequest(name, avatar), id())
    private fun profileAvatar(player: GuestCredentials) = database.transaction { it.query("SELECT avatar FROM guests WHERE id = ?", player.playerId) { row -> row.getInt(1) }.single() }
    private fun command(player: GuestCredentials, code: String, action: RoomAction) = service.command(player.token, code,
        CommandRequest(id(), service.read(player.token, code).snapshot.revision, action)).snapshot

    @Test fun `own lobby avatar is durable locked in a round and original receipts cannot roll it back`() {
        val host = guest("Asha", 1); val peer = guest("Bina", 3); val outsider = guest("Chitra")
        val code = service.create(host.token, CreateRoomRequest(id(), RoomOptions(automaticCalling = false))).snapshot.code
        service.join(peer.token, code)
        command(host, code, RoomAction.Ready(true)); command(peer, code, RoomAction.Ready(true))
        val request = CommandRequest(id(), service.read(peer.token, code).snapshot.revision, RoomAction.ChooseAvatar(7))
        val first = service.command(peer.token, code, request)
        assertEquals(7, profileAvatar(peer)); assertEquals(1, profileAvatar(host))
        assertFalse(first.snapshot.members.first { it.playerId == peer.playerId }.ready)
        assertTrue(first.snapshot.members.first { it.playerId == host.playerId }.ready)
        val second = command(peer, code, RoomAction.ChooseAvatar(2))
        assertEquals(first, service.command(peer.token, code, request))
        assertEquals(2, profileAvatar(peer)); assertEquals(second.revision, service.read(peer.token, code).snapshot.revision)
        assertEquals(409, assertThrows(ApiFailure::class.java) { service.command(peer.token, code, request.copy(id = id())) }.status)
        assertEquals(400, assertThrows(ApiFailure::class.java) { command(peer, code, RoomAction.ChooseAvatar(8)) }.status)
        assertEquals(403, assertThrows(ApiFailure::class.java) { service.command(outsider.token, code, request.copy(id = id(), expectedRevision = second.revision)) }.status)
        command(peer, code, RoomAction.Ready(true))
        val started = command(host, code, RoomAction.Start)
        assertEquals(2, started.round!!.players.first { it.id == peer.playerId }.avatar)
        assertEquals(409, assertThrows(ApiFailure::class.java) { command(peer, code, RoomAction.ChooseAvatar(6)) }.status)
        command(host, code, RoomAction.End); command(peer, code, RoomAction.Leave)
        val next = service.create(peer.token, CreateRoomRequest(id())).snapshot
        assertEquals(2, next.members.single().avatar)
        assertEquals(2, service.read(host.token, code).snapshot.round!!.players.first { it.id == peer.playerId }.avatar)
    }

    @Test fun `concurrent duplicate avatar commands have one receipt and no shared-lock upgrade deadlock`() {
        val host = guest("Asha")
        val room = service.create(host.token, CreateRoomRequest(id())).snapshot
        val request = CommandRequest(id(), room.revision, RoomAction.ChooseAvatar(5))
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val jobs = (1..2).map { pool.submit(Callable { gate.await(); service.command(host.token, room.code, request) }) }
            gate.countDown()
            assertEquals(jobs[0].get(15, TimeUnit.SECONDS), jobs[1].get(15, TimeUnit.SECONDS))
            assertEquals(5, profileAvatar(host)); assertEquals(room.revision + 1, service.read(host.token, room.code).snapshot.revision)
            assertEquals(1, database.transaction { it.query("SELECT count(*) FROM command_receipts") { row -> row.getInt(1) }.single() })
        } finally { pool.shutdownNow() }
    }

    @Test fun `failure saving room rolls back profile avatar and retry uses original intent`() {
        val host = guest("Asha", 1)
        val room = service.create(host.token, CreateRoomRequest(id())).snapshot
        val request = CommandRequest(id(), room.revision, RoomAction.ChooseAvatar(4))
        database.transaction {
            it.execute("CREATE FUNCTION reject_avatar() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''injected rollback''; END;'")
            it.execute("CREATE TRIGGER reject_avatar BEFORE UPDATE ON rooms FOR EACH ROW EXECUTE FUNCTION reject_avatar()")
        }
        assertThrows(SQLException::class.java) { service.command(host.token, room.code, request) }
        assertEquals(1, profileAvatar(host))
        assertEquals(0, database.transaction { it.query("SELECT count(*) FROM command_receipts") { row -> row.getInt(1) }.single() })
        database.transaction { it.execute("DROP TRIGGER reject_avatar ON rooms") }
        val retried = service.command(host.token, room.code, request)
        assertEquals(4, profileAvatar(host)); assertEquals(4, retried.snapshot.members.single().avatar)
    }
}
