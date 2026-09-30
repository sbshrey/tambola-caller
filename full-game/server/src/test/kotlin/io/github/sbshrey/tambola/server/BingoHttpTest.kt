package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BingoHttpTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()

    @Test fun `native HTTP transport purchases reconnects marks and leaves private Bingo tables`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        val api = HttpRoomApi("http://localhost", allowLocalHttp = true, client = client)
        val actor = api.guest(GuestRequest("Bingo HTTP"))
        val purchase = BingoMatchRequest(id(), 6)
        val waiting = api.bingoMatch(actor.token, purchase)
        waiting.validateFor(actor.playerId)
        assertEquals(waiting, api.bingoMatch(actor.token, purchase))
        assertEquals(900L, waiting.wallet!!.balance)
        now.set(waiting.startsAt!!); service.tick()
        var current = api.bingoRead(actor.token, waiting.code)
        current.validateFor(actor.playerId)
        assertEquals(6, current.round!!.ownCards.size)
        assertNull(current.round!!.revealedOrder)
        val target = current.round!!.ownCards.first()
        while (current.round!!.called.none { it in target.numbers }) {
            now.set(current.nextDrawAt!!); service.tick()
            current = api.bingoRead(actor.token, current.code)
        }
        val number = current.round!!.called.first { it in target.numbers }
        val mark = BingoCommandRequest(id(), current.revision, BingoAction.Mark(current.round!!.id, target.id, number))
        val marked = api.bingoCommand(actor.token, current.code, mark)
        marked.validateFor(actor.playerId)
        assertTrue(number in marked.round!!.ownMarks.getValue(target.id))
        assertEquals(marked, api.bingoCommand(actor.token, current.code, mark))
        service = RoomService(database, now::get)
        assertEquals(marked, api.bingoRead(actor.token, current.code))
        val other = api.guest(GuestRequest("Leave HTTP"))
        val lobby = api.bingoMatch(other.token, BingoMatchRequest(id(), 2, friendTable = true))
        val left = api.bingoCommand(other.token, lobby.code, BingoCommandRequest(id(), lobby.revision, BingoAction.Leave))
        left.validateFor(other.playerId)
        assertEquals(1500L, left.wallet!!.balance)
    }

    @Test fun `Bingo routes authenticate reject forged variants and prices and preserve legacy separation`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        val actor = service.register(GuestRequest("Protocol QA"), id())
        val request = BingoMatchRequest(id(), 1)
        suspend fun buy(body: String, auth: Boolean = true) = client.post("/v1/bingo/matches") {
            if (auth) bearerAuth(actor.token)
            contentType(ContentType.Application.Json); setBody(body)
        }
        assertEquals(HttpStatusCode.Unauthorized, buy(WireJson.encodeToString(request), false).status)
        assertEquals(HttpStatusCode.BadRequest, buy("{\"id\":\"${request.id}\",\"cards\":1,\"price\":0}").status)
        assertEquals(HttpStatusCode.BadRequest, buy("{\"id\":\"${request.id}\",\"cards\":7}").status)
        assertEquals(HttpStatusCode.BadRequest, buy("{\"id\":\"${request.id}\",\"cards\":1,\"variant\":\"TAMBOLA_90\"}").status)
        val response = buy(WireJson.encodeToString(request))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertFalse(response.bodyAsText().contains(actor.token))
        val room = WireJson.decodeFromString<BingoRoomView>(response.bodyAsText())
        assertTrue(client.get("/v1/rooms/${room.code}") { bearerAuth(actor.token) }.status.value in 400..499)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/bingo/rooms/ABCD2345") { bearerAuth(actor.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/bingo/rooms/${room.code}").status)
    }
}
