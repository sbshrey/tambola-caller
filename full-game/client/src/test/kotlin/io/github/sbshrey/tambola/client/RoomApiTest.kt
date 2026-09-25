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
