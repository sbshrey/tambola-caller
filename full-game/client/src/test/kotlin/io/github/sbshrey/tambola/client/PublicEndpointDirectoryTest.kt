package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PublicEndpointDirectoryTest {
    @Test fun `sharing resolves only the trusted directory without a room or wallet request`() = runBlocking {
        var requests = 0
        val api = HttpRoomApi("https://sbshrey.github.io", client = HttpClient(MockEngine { call ->
            requests++
            assertEquals("raw.githubusercontent.com", call.url.host)
            assertNull(call.headers[HttpHeaders.Authorization])
            respond(entry(expires = System.currentTimeMillis() + 600_000))
        }), discoveryUrl = PublicEndpointDirectory.DIRECTORY_URL)
        try {
            assertEquals("https://one-table.trycloudflare.com/friends/ABCDEFG2", api.friendInvitation("ABCDEFG2"))
            assertEquals(1, requests)
            try { api.friendInvitation("ABCDEFG2?token=x"); fail() } catch (_: IllegalArgumentException) { }
            assertEquals(1, requests)
        } finally { api.close() }
    }
    private fun entry(origin: String = "https://one-table.trycloudflare.com", expires: Long = 100_000) =
        """{"version":1,"service":"${PublicEndpointDirectory.SERVICE}","origin":"$origin","expiresAt":$expires}"""

    @Test fun `directory caches briefly then follows a tunnel rotation without sending credentials`() = runBlocking {
        var time = 1_000L
        var lookups = 0
        val engine = MockEngine { call ->
            assertEquals("raw.githubusercontent.com", call.url.host)
            assertNull(call.headers[HttpHeaders.Authorization])
            lookups++
            respond(entry(if (lookups == 1) "https://one-table.trycloudflare.com" else "https://two-table.trycloudflare.com"))
        }
        val client = HttpClient(engine)
        val directory = PublicEndpointDirectory(PublicEndpointDirectory.DIRECTORY_URL, client) { time }
        assertEquals("https://one-table.trycloudflare.com", directory.origin())
        assertEquals(directory.origin(), directory.origin())
        assertEquals(1, lookups)
        time += 60_001
        assertEquals("https://two-table.trycloudflare.com", directory.origin())
        assertEquals(2, lookups)
        client.close()
    }

    @Test fun `HTTP transport resolves before authorization and never automatically repeats a purchase`() = runBlocking {
        var lookups = 0
        var purchases = 0
        val engine = MockEngine { call ->
            if (call.url.host == "raw.githubusercontent.com") {
                assertNull(call.headers[HttpHeaders.Authorization]); lookups++
                respond(entry(expires = System.currentTimeMillis() + 600_000))
            } else {
                assertEquals("one-table.trycloudflare.com", call.url.host)
                assertEquals("Bearer secret", call.headers[HttpHeaders.Authorization]); purchases++
                respond("{}", HttpStatusCode.BadGateway)
            }
        }
        val api = HttpRoomApi("https://sbshrey.github.io", client = HttpClient(engine), discoveryUrl = PublicEndpointDirectory.DIRECTORY_URL)
        try { api.match("secret", MatchRequest(UUID.randomUUID().toString(), 3)); fail() }
        catch (_: RoomApiFailure) { }
        assertEquals(1, lookups); assertEquals(1, purchases)
        api.close()
    }

    @Test fun `rejects expired oversized redirected and unsafe directory responses`() = runBlocking {
        val invalid = listOf(
            entry(expires = 1000), entry(expires = Long.MAX_VALUE),
            entry("http://one-table.trycloudflare.com"), entry("https://127.0.0.1"),
            entry("https://evil.example"), entry("https://one.trycloudflare.com.evil.example"),
            entry("https://user@one.trycloudflare.com"), entry("https://one.trycloudflare.com/path"),
            entry("https://one.trycloudflare.com:443"), entry().replace("public-beta-v1", "other-service"),
            "x".repeat(4097),
        )
        for (payload in invalid) {
            val client = HttpClient(MockEngine { respond(payload) })
            try {
                PublicEndpointDirectory(PublicEndpointDirectory.DIRECTORY_URL, client) { 1000 }.origin()
                fail("Unsafe directory accepted")
            } catch (_: Exception) { } finally { client.close() }
        }
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://evil.example")) }) { followRedirects = false }
        try { PublicEndpointDirectory(PublicEndpointDirectory.DIRECTORY_URL, client).origin(); fail() }
        catch (_: InvalidRoomResponse) { } finally { client.close() }
    }
}
