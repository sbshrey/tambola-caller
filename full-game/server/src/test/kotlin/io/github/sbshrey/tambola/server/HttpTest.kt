package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HttpTest : PostgresTest() {
    @Test fun `profile deletion authenticates validates and confirms the same request after access is removed`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        val guest = service.register(GuestRequest("Delete via HTTP"), "http-delete")
        val deletion = WireJson.encodeToString(DeleteProfileRequest(UUID.randomUUID().toString()))
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/guests/me/delete") { contentType(ContentType.Application.Json); setBody(deletion) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/guests/me/delete") { bearerAuth(guest.token); contentType(ContentType.Application.Json); setBody("{\"id\":\"invalid\"}") }.status)
        suspend fun remove() = client.post("/v1/guests/me/delete") { bearerAuth(guest.token); contentType(ContentType.Application.Json); setBody(deletion) }
        val response = remove()
        assertEquals(HttpStatusCode.OK, response.status); assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(response.bodyAsText(), remove().bodyAsText())
        assertFalse(response.bodyAsText().contains(guest.token))
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/guests/me/logout") { bearerAuth(guest.token) }.status)
    }
    @Test fun `HTTP validates requests authentication and health without exposing secrets`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
        val created = client.post("/v1/guests") { contentType(ContentType.Application.Json); setBody(WireJson.encodeToString(GuestRequest("Asha"))) }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("no-store", created.headers[HttpHeaders.CacheControl])
        val guest = WireJson.decodeFromString<GuestCredentials>(created.bodyAsText())
        val room = client.post("/v1/rooms") {
            bearerAuth(guest.token); contentType(ContentType.Application.Json)
            setBody(WireJson.encodeToString(CreateRoomRequest(UUID.randomUUID().toString())))
        }
        assertEquals(HttpStatusCode.Created, room.status)
        val code = WireJson.decodeFromString<RoomUpdate>(room.bodyAsText()).snapshot.code
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/rooms/$code").status)
        val badCursor = client.get("/v1/rooms/$code?after=-1") { bearerAuth(guest.token) }
        assertEquals(HttpStatusCode.BadRequest, badCursor.status)
        val invalid = client.post("/v1/guests") { contentType(ContentType.Application.Json); setBody("{\"displayName\":\"Asha\",\"unexpected\":true}") }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertFalse(invalid.bodyAsText().contains("Exception"))
        val oversized = client.post("/v1/guests") { contentType(ContentType.Application.Json); setBody(" ".repeat(33_000)) }
        assertEquals(HttpStatusCode.PayloadTooLarge, oversized.status)
        val wrongType = client.post("/v1/guests") { contentType(ContentType.Text.Plain); setBody("test") }
        assertEquals(HttpStatusCode.UnsupportedMediaType, wrongType.status)
        assertEquals(HttpStatusCode.NoContent, client.post("/v1/guests/me/logout") { bearerAuth(guest.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/rooms/$code") { bearerAuth(guest.token) }.status)
    }

    @Test fun `two authenticated websocket clients receive private snapshots and durable draw replay`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        val host = service.register(GuestRequest("Asha"), "host")
        val other = service.register(GuestRequest("Bina"), "other")
        var room = service.create(host.token, CreateRoomRequest(UUID.randomUUID().toString(), RoomOptions(automaticCalling = false))).snapshot
        service.join(other.token, room.code)
        fun command(actor: GuestCredentials, action: RoomAction): RoomView {
            val revision = service.read(actor.token, room.code).snapshot.revision
            return service.command(actor.token, room.code, CommandRequest(UUID.randomUUID().toString(), revision, action)).snapshot
        }
        command(host, RoomAction.Ready(true)); command(other, RoomAction.Ready(true)); room = command(host, RoomAction.Start)
        val websocketClient = createClient { install(WebSockets) }
        withTimeout(15_000) {
            websocketClient.webSocket("/v1/rooms/${room.code}/events", request = { bearerAuth(host.token) }) {
                val hostSnapshot = WireJson.decodeFromString<RoomUpdate>((incoming.receive() as Frame.Text).readText())
                assertTrue(hostSnapshot.snapshot.round!!.ownTickets.all { it.playerId == host.playerId })
                send(Frame.Text(WireJson.encodeToString(EventAck(hostSnapshot.snapshot.revision))))
                websocketClient.webSocket("/v1/rooms/${room.code}/events?after=${room.revision}", request = { bearerAuth(other.token) }) {
                    val otherSnapshot = WireJson.decodeFromString<RoomUpdate>((incoming.receive() as Frame.Text).readText())
                    assertTrue(otherSnapshot.snapshot.round!!.ownTickets.all { it.playerId == other.playerId })
                    val response = client.post("/v1/rooms/${room.code}/commands") {
                        bearerAuth(host.token); contentType(ContentType.Application.Json)
                        setBody(WireJson.encodeToString(CommandRequest(UUID.randomUUID().toString(), room.revision, RoomAction.Draw)))
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                    val update = WireJson.decodeFromString<RoomUpdate>((incoming.receive() as Frame.Text).readText())
                    assertEquals(1, update.snapshot.round!!.called.size)
                    assertEquals(listOf("drawn"), update.events.map { it.type })
                    assertNull(update.snapshot.round!!.revealedOrder)
                    close()
                }
                val hostUpdate = WireJson.decodeFromString<RoomUpdate>((incoming.receive() as Frame.Text).readText())
                assertEquals(1, hostUpdate.snapshot.round!!.called.size)
                close()
            }
        }
        websocketClient.close()
    }

    @Test fun `websocket rejects spectators without room membership`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        val host = service.register(GuestRequest("Asha"), "host")
        val stranger = service.register(GuestRequest("Bina"), "stranger")
        val code = service.create(host.token, CreateRoomRequest(UUID.randomUUID().toString())).snapshot.code
        val websocketClient = createClient { install(WebSockets) }
        withTimeout(5_000) {
            websocketClient.webSocket("/v1/rooms/$code/events", request = { bearerAuth(stranger.token) }) {
                val reason = closeReason.await()
                assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason!!.code)
                assertEquals("not_member", reason.message)
            }
        }
        websocketClient.close()
    }
}
