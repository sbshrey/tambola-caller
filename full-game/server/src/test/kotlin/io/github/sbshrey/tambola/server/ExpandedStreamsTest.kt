package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Fifty concurrent private streams against real PostgreSQL; this is not a physical-network benchmark. */
class ExpandedStreamsTest : PostgresTest() {
    @Test fun `fifty connected players receive the same call and only their own six tickets`() = testApplication {
        val actors = (1..50).map { service.register(GuestRequest("Stream $it"), "stream-$it") }
        val host = actors.first()
        val lobby = service.match(host.token, MatchRequest(UUID.randomUUID().toString(), 6, friendTable = true, rulesVersion = 2)).snapshot
        actors.drop(1).forEach { service.match(it.token, MatchRequest(UUID.randomUUID().toString(), 6, friendTable = true, friendCode = lobby.code, rulesVersion = 2)) }
        val revision = service.read(host.token, lobby.code).snapshot.revision
        val active = service.command(host.token, lobby.code, CommandRequest(UUID.randomUUID().toString(), revision, RoomAction.Start)).snapshot
        application { roomsModule(database, service, runWorker = false) }
        val socketClient = createClient { install(WebSockets) }
        try {
            withTimeout(30_000) {
                val ready = CompletableDeferred<Unit>()
                val count = AtomicInteger()
                val calls = actors.map { actor -> async {
                    var called: List<Int> = emptyList()
                    socketClient.webSocket("/v1/rooms/${lobby.code}/events", request = { bearerAuth(actor.token) }) {
                        var initial = true
                        while (called.isEmpty()) {
                            val update = WireJson.decodeFromString<RoomUpdate>((incoming.receive() as Frame.Text).readText())
                            update.snapshot.validateFor(actor.playerId)
                            val round = requireNotNull(update.snapshot.round)
                            assertEquals(50, round.players.size)
                            assertEquals(6, round.ownTickets.size)
                            assertTrue(round.ownTickets.all { it.playerId == actor.playerId })
                            assertNull(round.revealedOrder)
                            send(Frame.Text(WireJson.encodeToString(EventAck(update.snapshot.revision))))
                            if (initial) { initial = false; if (count.incrementAndGet() == 50) ready.complete(Unit) }
                            called = round.called
                        }
                        close()
                    }
                    called
                } }
                ready.await()
                now.set(requireNotNull(active.nextDrawAt)); withContext(Dispatchers.IO) { service.tick() }
                val received = calls.awaitAll()
                assertTrue(received.all { it.size == 1 })
                assertEquals(1, received.distinct().size)
            }
        } finally { socketClient.close() }
    }
}
