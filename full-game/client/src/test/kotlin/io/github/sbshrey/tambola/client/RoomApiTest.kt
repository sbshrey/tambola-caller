package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RoomApiTest {
    @Test fun `HTTP date anchors server time even on cooldown rejection and ignores malformed dates`() = runBlocking {
        var date = "Sat, 26 Sep 2026 20:54:43 GMT"
        var rejected = false
        val engine = MockEngine {
            val headers = headersOf(HttpHeaders.Date, date)
            if (rejected) respond(WireJson.encodeToString(ApiError("refill_wait", "Wait.")), HttpStatusCode.TooManyRequests, headers)
            else respond(WireJson.encodeToString(WalletView(0, 2, 0)), HttpStatusCode.OK, headers)
        }
        val api = HttpRoomApi("https://rooms.example", client = HttpClient(engine))
        assertNull(api.serverTime)
        api.wallet("private-token")
        val first = requireNotNull(api.serverTime)
        assertEquals(1790456083000L, first.epochMillis)
        assertEquals(first.epochMillis + 1200, first.currentTimeMillis(first.receivedNanos + 1_200_000_000L))
        date = "Sat, 26 Sep 2026 20:54:45 GMT"; rejected = true
        try { api.refill("private-token", RefillRequest(UUID.randomUUID().toString())); fail() }
        catch (error: RoomApiFailure) { assertEquals("refill_wait", error.code) }
        val fresh = requireNotNull(api.serverTime)
        assertEquals(first.epochMillis + 2000, fresh.epochMillis)
        date = "invalid"; rejected = false
        api.wallet("private-token")
        assertEquals(fresh, api.serverTime)
        api.close()
    }

    @Test fun `wallet refill and purchase use authenticated routes with exact retry identity`() = runBlocking {
        val match = MatchRequest(UUID.randomUUID().toString(), 6)
        val refill = RefillRequest(UUID.randomUUID().toString())
        val bodies = mutableListOf<Pair<String, String>>()
        val wallet = WalletView(1500, 1, 0)
        val engine = MockEngine { call ->
            assertEquals("Bearer private-token", call.headers[HttpHeaders.Authorization])
            assertFalse(call.url.toString().contains("private-token"))
            if (call.url.encodedPath == "/v1/wallet") {
                assertEquals(HttpMethod.Get, call.method)
                respond(WireJson.encodeToString(wallet), HttpStatusCode.OK)
            } else {
                assertEquals(HttpMethod.Post, call.method)
                bodies += call.url.encodedPath to (call.body as TextContent).text
                if (call.url.encodedPath == "/v1/wallet/refill") respond(WireJson.encodeToString(wallet), HttpStatusCode.OK)
                else respond(WireJson.encodeToString(ApiError("database_unavailable", "Retry.")), HttpStatusCode.ServiceUnavailable)
            }
        }
        val api = HttpRoomApi("https://rooms.example", client = HttpClient(engine))
        assertEquals(wallet, api.wallet("private-token"))
        repeat(2) { api.refill("private-token", refill) }
        repeat(2) { try { api.match("private-token", match); fail() } catch (error: RoomApiFailure) { assertEquals(503, error.status) } }
        assertEquals(bodies[0], bodies[1]); assertEquals(bodies[2], bodies[3])
        assertEquals("/v1/matches" to WireJson.encodeToString(match), bodies.last())
        api.close()
    }
    @Test fun `deletion retry keeps the encrypted request identity and refuses another request's confirmation`() = runBlocking {
        val pending = PendingOperation.DeleteProfile(DeleteProfileRequest(UUID.randomUUID().toString()))
        val restored = WireJson.decodeFromString<PendingOperation>(WireJson.encodeToString<PendingOperation>(pending)) as PendingOperation.DeleteProfile
        assertEquals(pending, restored)
        var responseId = pending.request.id
        val bodies = mutableListOf<String>()
        val engine = MockEngine { call ->
            assertEquals("Bearer private-token", call.headers[HttpHeaders.Authorization])
            assertEquals("/v1/guests/me/delete", call.url.encodedPath)
            bodies += (call.body as TextContent).text
            respond(WireJson.encodeToString(DeleteProfileReceipt(responseId, 100, 200)), HttpStatusCode.OK)
        }
        val api = HttpRoomApi("https://rooms.example", client = HttpClient(engine))
        val receipt = api.deleteProfile("private-token", pending.request)
        assertEquals(receipt, api.deleteProfile("private-token", restored.request))
        assertEquals(bodies[0], bodies[1])
        responseId = UUID.randomUUID().toString()
        try { api.deleteProfile("private-token", pending.request); fail("Mismatched receipt accepted") } catch (_: InvalidRoomResponse) { }
        api.close()
    }
    @Test fun `command credentials stay in headers and retry preserves exact request body`() = runBlocking {
        val bodies = mutableListOf<String>()
        val request = CommandRequest(UUID.randomUUID().toString(), 7, RoomAction.Draw)
        val engine = MockEngine { call ->
            assertEquals("Bearer private-token", call.headers[HttpHeaders.Authorization])
            assertFalse(call.url.toString().contains("private-token"))
            assertEquals("/v1/rooms/ABCD2345/commands", call.url.encodedPath)
            bodies += (call.body as TextContent).text
            respond(WireJson.encodeToString(ApiError("database_unavailable", "Retry the same command.")), HttpStatusCode.ServiceUnavailable)
        }
        val api = HttpRoomApi("https://rooms.example", client = HttpClient(engine) { followRedirects = false })
        repeat(2) {
            try { api.command("private-token", "ABCD2345", request); fail("Expected unavailable") }
            catch (error: RoomApiFailure) { assertEquals(503, error.status) }
        }
        assertEquals(listOf(WireJson.encodeToString(request), WireJson.encodeToString(request)), bodies)
        api.close()
    }

    @Test fun `oversized response is refused and proxy HTML is never surfaced`() = runBlocking {
        var large = true
        val engine = MockEngine {
            if (large) respond("x".repeat(MAX_RESPONSE_BYTES + 1), HttpStatusCode.OK)
            else respond("<html>proxy with sensitive diagnostics</html>", HttpStatusCode.BadGateway)
        }
        val api = HttpRoomApi("https://rooms.example", client = HttpClient(engine))
        try { api.guest(GuestRequest("Asha")); fail("Expected bounds check") } catch (_: InvalidRoomResponse) { }
        large = false
        try { api.guest(GuestRequest("Asha")); fail("Expected proxy failure") }
        catch (error: RoomApiFailure) { assertFalse(error.userMessage.contains("sensitive")); assertEquals(502, error.status) }
        api.close()
    }
}
