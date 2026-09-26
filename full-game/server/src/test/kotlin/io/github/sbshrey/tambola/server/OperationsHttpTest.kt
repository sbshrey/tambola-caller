package io.github.sbshrey.tambola.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicLong

class OperationsHttpTest : PostgresTest() {
    @Test fun `protected scrape counts HTTP outcomes without exposing request data or creating dynamic labels`() = testApplication {
        val token = secret()
        val operations = ServiceOperations(false, token)
        application { roomsModule(database, service, runWorker = false, operations = operations) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/internal/metrics").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/internal/metrics?token=$token").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/internal/metrics") { bearerAuth(secret()) }.status)
        val unique = "PrivateFixtureIdentity"
        assertEquals(HttpStatusCode.Created, client.post("/v1/guests") { contentType(ContentType.Application.Json); setBody("{\"displayName\":\"$unique\"}") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/rooms/SECRET?after=123") { bearerAuth("PrivateFixtureSession") }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/unknown-$unique").status)
        val response = client.get("/internal/metrics") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status); assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertTrue(response.headers[HttpHeaders.ContentType]!!.contains("version=0.0.4"))
        val body = response.bodyAsText()
        listOf(token, unique, "SECRET", "PrivateFixtureSession", "after=", "unknown-").forEach { assertFalse(body.contains(it)) }
        assertTrue(body.contains("tambola_http_duration_seconds_count{route=\"guest\",status=\"2xx\"} 1"))
        assertTrue(body.contains("tambola_http_duration_seconds_count{route=\"read\",status=\"4xx\"} 1"))
        assertTrue(body.contains("tambola_http_duration_seconds_count{route=\"other\",status=\"4xx\"} 1"))
    }

    @Test fun `readiness reports a failed or stalled worker even when database health succeeds and recovers after a pass`() = testApplication {
        val clock = AtomicLong()
        val token = secret()
        val operations = ServiceOperations(true, token, clock::get)
        val worker = RoomWorker(service, operations)
        application { roomsModule(database, service, runWorker = false, operations = operations) }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/health/ready").status)
        worker.runPass(); assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
        database.transaction { it.execute("ALTER TABLE rooms RENAME TO unavailable_rooms") }
        assertTrue(database.healthy())
        assertThrows(SQLException::class.java) { worker.runPass() }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/health/ready").status)
        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
        val metrics = client.get("/internal/metrics") { bearerAuth(token) }.bodyAsText()
        assertTrue(metrics.contains("tambola_worker_ready 0"))
        assertTrue(metrics.contains("tambola_worker_step_duration_seconds_count{step=\"tick\",outcome=\"failure\"} 1"))
        assertFalse(metrics.contains("unavailable_rooms"))
        database.transaction { it.execute("ALTER TABLE unavailable_rooms RENAME TO rooms") }
        worker.runPass(); assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
        clock.set(31_000_000_000); assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/health/ready").status)
        worker.runPass(); assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
    }

    @Test fun `metrics are disabled without operator configuration`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        assertEquals(HttpStatusCode.NotFound, client.get("/internal/metrics") { bearerAuth(secret()) }.status)
    }
}
