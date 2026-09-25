package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.protocol.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NativeClientTest : PostgresTest() {
    @Test fun `production client exchanges live snapshots with real Netty websocket`() = runBlocking<Unit> {
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) { roomsModule(database, service, runWorker = false) }.start(wait = false)
        val port = server.engine.resolvedConnectors().single().port
        val api = HttpRoomApi("http://127.0.0.1:$port", true)
        try {
            val host = api.guest(GuestRequest("Native Asha"))
            val peer = api.guest(GuestRequest("Native Bina"))
            val room = api.create(host.token, CreateRoomRequest(UUID.randomUUID().toString(), RoomOptions(automaticCalling = false))).snapshot
            api.join(peer.token, room.code)
            val first = withTimeout(10_000) { api.events(host.token, room.code, room.revision).first() }
            first.snapshot.validateFor(host.playerId)
            assertEquals(2, first.snapshot.members.size)
            suspend fun command(token: String, action: RoomAction): RoomView {
                val revision = api.read(token, room.code).snapshot.revision
                return api.command(token, room.code, CommandRequest(UUID.randomUUID().toString(), revision, action)).snapshot
            }
            command(host.token, RoomAction.Ready(true)); command(peer.token, RoomAction.Ready(true)); command(host.token, RoomAction.Start)
            val updates = async { withTimeout(10_000) { api.events(peer.token, room.code, null).first { it.snapshot.round!!.called.size == 1 } } }
            command(host.token, RoomAction.Draw)
            val drawn = updates.await().snapshot
            drawn.validateFor(peer.playerId)
            assertTrue(drawn.round!!.ownTickets.all { it.playerId == peer.playerId })
            assertNull(drawn.round!!.revealedOrder)
            command(host.token, RoomAction.End).validateFor(host.playerId)
            val deletion = DeleteProfileRequest(UUID.randomUUID().toString())
            val receipt = api.deleteProfile(host.token, deletion)
            assertEquals(receipt, api.deleteProfile(host.token, deletion))
            val remaining = api.read(peer.token, room.code).snapshot
            assertEquals("Deleted player", remaining.round!!.players.first { it.id == host.playerId }.name)
            remaining.validateFor(peer.playerId)
        } finally { api.close(); server.stop(0, 2_000) }
    }
}
