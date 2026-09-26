package io.github.sbshrey.tambola.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class InviteConfigurationTest {
    private val fingerprint = List(32) { "AB" }.joinToString(":")
    @Test fun `configuration is optional but rejects incomplete insecure or malformed metadata`() {
        assertNull(InviteSite.configured(null, null, null))
        assertThrows(IllegalArgumentException::class.java) { InviteSite.configured(null, fingerprint, null) }
        for (origin in listOf("http://public.example", "http://127.0.0.1:8080", "https://user:password@rooms.example", "https://rooms.example/path", "https://rooms.example?token=example"))
            assertThrows(IllegalArgumentException::class.java) { InviteSite.configured(origin, null, null) }
        assertEquals("http://127.0.0.1:8080", InviteSite.configured("http://127.0.0.1:8080", null, null, true)!!.origin)
        for (cert in listOf("invalid", "ab".repeat(32), (List(9) { fingerprint }).joinToString(",")))
            assertThrows(IllegalArgumentException::class.java) { InviteSite.configured("https://rooms.example", cert, null) }
        for (install in listOf("javascript:alert(1)", "http://download.example", "https://user@download.example", "https://download.example/#fragment", "https://download.example:99999"))
            assertThrows(IllegalArgumentException::class.java) { InviteSite.configured("https://rooms.example", null, install) }
    }
    @Test fun `association uses only configured package and normalized public fingerprints`() {
        val site = InviteSite.configured("https://ROOMS.example:443/", "$fingerprint,${fingerprint.lowercase()}", "https://play.google.com/store/apps/details?id=${InviteSite.PACKAGE}&hl=en")!!
        assertEquals("https://rooms.example", site.origin)
        val statement = Json.parseToJsonElement(site.associations()!!).jsonArray.single().jsonObject
        assertEquals("delegate_permission/common.handle_all_urls", statement["relation"]!!.jsonArray.single().jsonPrimitive.content)
        val target = statement["target"]!!.jsonObject
        assertEquals(InviteSite.PACKAGE, target["package_name"]!!.jsonPrimitive.content)
        assertEquals(listOf(fingerprint), target["sha256_cert_fingerprints"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(site.page("ABCDEFGH", true).contains("&amp;hl=en"))
        assertNull(InviteSite.configured("https://rooms.example", null, null)!!.associations())
    }
}

class InviteSiteTest : PostgresTest() {
    @Test fun `landing page is bilingual canonical private and does not inspect or create rooms`() = testApplication {
        application { roomsModule(database, service, runWorker = false, inviteSite = InviteSite.configured("https://rooms.example", null, null)) }
        // Even with no matching room and an unrelated Host header, the page is useful without leaking room state.
        val response = client.get("/invite/abcdefgh") { header(HttpHeaders.Host, "attacker.example") }
        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertTrue(html.contains("<code class=\"code\">ABCDEFGH</code>")); assertTrue(html.contains("lang=\"hi\""))
        assertTrue(html.contains("intent://rooms.example/invite/ABCDEFGH#Intent;scheme=https;package=${InviteSite.PACKAGE};"))
        assertTrue(html.contains("S.browser_fallback_url=https%3A%2F%2Frooms.example%2Finvite%2FABCDEFGH%3Finstall%3D1"))
        assertFalse(html.contains("attacker.example")); assertFalse(html.contains("<script"))
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
        assertEquals("noindex, nofollow", response.headers["X-Robots-Tag"])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        val css = html.substringAfter("<style>").substringBefore("</style>")
        val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(css.toByteArray(Charsets.UTF_8)))
        assertTrue(response.headers["Content-Security-Policy"]!!.contains("style-src 'sha256-$hash'"))
        assertTrue(response.headers["Content-Security-Policy"]!!.contains("frame-ancestors 'none'"))
        database.transaction { connection ->
            for (table in listOf("guests", "rooms")) connection.prepareStatement("SELECT count(*) FROM $table").use { query ->
                query.executeQuery().use { rows -> rows.next(); assertEquals(0, rows.getInt(1)) }
            }
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/assetlinks.json").status)
        val fallback = client.get("/invite/ABCDEFGH?install=1")
        assertEquals(HttpStatusCode.OK, fallback.status); assertTrue(fallback.bodyAsText().contains("App did not open?"))
    }
    @Test fun `malformed encoded or tracking links do not render an invitation`() = testApplication {
        application { roomsModule(database, service, runWorker = false, inviteSite = InviteSite.configured("https://rooms.example", null, null)) }
        for (path in listOf("/invite/INVALID", "/invite/ABCDEFGH/", "/invite/ABCDEFGH/extra", "/invite/ABCDEFGH?token=secret", "/invite/ABCDEFGH?install=1&extra=2", "/invite/%41BCDEFGH", "/invite/ABC%2FDEFGH")) {
            val response = client.get(path)
            assertEquals(path, HttpStatusCode.NotFound, response.status)
            assertFalse(response.bodyAsText().contains("intent://"))
        }
    }
    @Test fun `association endpoint returns configured metadata as JSON without redirect`() = testApplication {
        val fingerprint = List(32) { "42" }.joinToString(":")
        application { roomsModule(database, service, runWorker = false, inviteSite = InviteSite.configured("https://rooms.example", fingerprint, null)) }
        val response = client.get("/.well-known/assetlinks.json")
        assertEquals(HttpStatusCode.OK, response.status); assertNull(response.headers[HttpHeaders.Location])
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("application/json"))
        assertTrue(response.bodyAsText().contains(fingerprint)); assertTrue(response.bodyAsText().contains(InviteSite.PACKAGE))
    }
    @Test fun `public routes stay absent when no origin is configured`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        assertEquals(HttpStatusCode.NotFound, client.get("/invite/ABCDEFGH").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/assetlinks.json").status)
    }
}
