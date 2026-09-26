package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DeviceSessionTest : PostgresTest() {
    private fun guest() = service.register(GuestRequest("Session QA"), UUID.randomUUID().toString())
    private fun enroll(actor: GuestCredentials, key: String = secret()): String {
        assertEquals(DeviceEnrollment(actor.playerId, 0), service.enrollDevice(actor.token, EnrollDeviceRequest(key)))
        return key
    }
    private fun denied(status: Int = 401, block: () -> Unit) = assertEquals(status, assertThrows(ApiFailure::class.java, block).status)

    @Test fun `expired session renews same earned wallet after cleanup without a new starter grant`() {
        val actor = guest(); val oldLegacy = guest(); val key = enroll(actor)
        database.transaction { CoinLedger.credit(it, actor.playerId, "qa-earned-prize", 850, now.get()) }
        val before = service.wallet(actor.token)
        now.addAndGet(90 * ROOM_LIFETIME)
        service.cleanup()
        denied { service.wallet(actor.token) }
        val renewed = service.renewSession(key, RenewSessionRequest(0, secret()), "returning")
        assertEquals(actor.playerId, renewed.credentials.playerId)
        assertEquals(before, service.wallet(renewed.credentials.token))
        assertEquals(2350L, before.balance)
        assertEquals(1L, renewed.revision)
        assertEquals(0, database.transaction { it.query("SELECT count(*) FROM guests WHERE id = ?", oldLegacy.playerId) { row -> row.getInt(1) }.single() })
        val hashes = database.transaction { it.query("SELECT token_hash, device_key_hash FROM guests") { row -> row.getString(1) to row.getString(2) }.single() }
        assertEquals(digest(renewed.credentials.token) to digest(key), hashes)
        denied { service.wallet(key) }
        denied { service.renewSession(renewed.credentials.token, RenewSessionRequest(1, secret()), "wrong-proof") }
    }

    @Test fun `lost renewal response retries exactly and stale requests cannot rotate back`() {
        val actor = guest(); val key = enroll(actor)
        val first = RenewSessionRequest(0, secret())
        val issued = service.renewSession(key, first, "retry")
        now.addAndGet(1000)
        assertEquals(issued, RoomService(database, now::get).renewSession(key, first, "retry"))
        denied { service.wallet(actor.token) }
        val second = service.renewSession(key, RenewSessionRequest(1, secret()), "next")
        denied(409) { service.renewSession(key, first, "old-replay") }
        assertEquals(1500L, service.wallet(second.credentials.token).balance)
        denied { service.wallet(issued.credentials.token) }
    }

    @Test fun `concurrent retries issue one exact session while competing replacements conflict`() {
        val actor = guest(); val key = enroll(actor)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val request = RenewSessionRequest(0, secret())
            val replies = pool.invokeAll(List(4) { Callable { service.renewSession(key, request, "parallel") } }).map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, replies.distinct().size)
            val alternatives = pool.invokeAll(List(2) { Callable {
                try { service.renewSession(key, RenewSessionRequest(1, secret()), "competing"); 200 }
                catch (error: ApiFailure) { error.status }
            } }).map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), alternatives.sorted())
            assertEquals(2L, database.transaction { it.query("SELECT session_revision FROM guests WHERE id = ?", actor.playerId) { row -> row.getLong(1) }.single() })
        } finally { pool.shutdownNow() }
    }

    @Test fun `enrollment cannot be changed or stolen by sharing keys between profiles`() {
        val actor = guest(); val peer = guest(); val key = enroll(actor)
        assertEquals(DeviceEnrollment(actor.playerId, 0), service.enrollDevice(actor.token, EnrollDeviceRequest(key)))
        denied(409) { service.enrollDevice(actor.token, EnrollDeviceRequest(secret())) }
        denied(409) { service.enrollDevice(peer.token, EnrollDeviceRequest(key)) }
        denied(400) { service.enrollDevice(peer.token, EnrollDeviceRequest(peer.token)) }
        denied(409) { service.renewSession(key, RenewSessionRequest(0, peer.token), "collision") }
        assertEquals(1500L, service.wallet(actor.token).balance)
        assertEquals(1500L, service.wallet(peer.token).balance)
        now.set(peer.expiresAt)
        denied { service.enrollDevice(peer.token, EnrollDeviceRequest(secret())) }
    }

    @Test fun `renewal rollback leaves original session and retry intent usable`() {
        val actor = guest(); val key = enroll(actor); val request = RenewSessionRequest(0, secret())
        database.transaction {
            it.execute("CREATE FUNCTION reject_session() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''QA rollback''; END;'")
            it.execute("CREATE TRIGGER reject_session BEFORE UPDATE OF token_hash ON guests FOR EACH ROW EXECUTE FUNCTION reject_session()")
        }
        assertThrows(SQLException::class.java) { service.renewSession(key, request, "rollback") }
        assertEquals(1500L, service.wallet(actor.token).balance)
        database.transaction { it.execute("DROP TRIGGER reject_session ON guests") }
        val renewed = service.renewSession(key, request, "retry")
        assertEquals(1L, renewed.revision)
        assertEquals(1500L, service.wallet(renewed.credentials.token).balance)
    }

    @Test fun `wallet purchase receipt belongs to the identity across session rotation`() {
        val actor = guest(); val key = enroll(actor)
        val purchase = MatchRequest(UUID.randomUUID().toString(), 6)
        val receipt = service.match(actor.token, purchase)
        val next = service.renewSession(key, RenewSessionRequest(0, secret()), "purchase")
        assertEquals(receipt, service.match(next.credentials.token, purchase))
        assertEquals(900L, service.wallet(next.credentials.token).balance)
        assertEquals(receipt.snapshot.roomId, service.read(next.credentials.token, receipt.snapshot.code).snapshot.roomId)
        assertFalse(WireJson.encodeToString(receipt).contains(key))
    }

    @Test fun `logout revokes durable proof and allows retention cleanup`() {
        val actor = guest(); val key = enroll(actor)
        val request = RenewSessionRequest(0, secret())
        val renewed = service.renewSession(key, request, "logout")
        service.revoke(renewed.credentials.token)
        denied { service.renewSession(key, request, "replay") }
        denied { service.renewSession(key, RenewSessionRequest(1, secret()), "new") }
        now.addAndGet(31 * ROOM_LIFETIME); service.cleanup()
        assertEquals(0, database.transaction { it.query("SELECT count(*) FROM coin_wallets") { row -> row.getInt(1) }.single() })
    }

    @Test fun `restoring an enrolled profile cannot bypass the live deletion journal`() {
        val journal = DeletionJournal(additionalDatabase()).also { it.migrate() }
        service = RoomService(database, now::get, journal); service.replayDeletions()
        val actor = guest(); val key = enroll(actor)
        withPrimaryBackup { restore ->
            service.deleteProfile(actor.token, DeleteProfileRequest(UUID.randomUUID().toString()), "deletion")
            denied { service.renewSession(key, RenewSessionRequest(0, secret()), "deleted") }
            restore(); service = RoomService(database, now::get, journal)
            denied { service.renewSession(key, RenewSessionRequest(0, secret()), "restored") }
            service.replayDeletions()
            assertEquals(0, database.transaction { it.query("SELECT count(*) FROM coin_wallets") { row -> row.getInt(1) }.single() })
        }
    }

    @Test fun `session endpoints authenticate strictly use no-store and never echo device proof`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        val actor = guest(); val key = secret()
        val enrollment = WireJson.encodeToString(EnrollDeviceRequest(key))
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/guests/me/device") { contentType(ContentType.Application.Json); setBody(enrollment) }.status)
        val enrolled = client.post("/v1/guests/me/device") { bearerAuth(actor.token); contentType(ContentType.Application.Json); setBody(enrollment) }
        assertEquals(HttpStatusCode.OK, enrolled.status)
        assertEquals("no-store", enrolled.headers[HttpHeaders.CacheControl])
        assertFalse(enrolled.bodyAsText().contains(key))
        now.set(actor.expiresAt)
        val request = RenewSessionRequest(0, secret())
        val renewed = client.post("/v1/guests/me/session") { bearerAuth(key); contentType(ContentType.Application.Json); setBody(WireJson.encodeToString(request)) }
        assertEquals(HttpStatusCode.OK, renewed.status)
        assertEquals("no-store", renewed.headers[HttpHeaders.CacheControl])
        assertFalse(renewed.bodyAsText().contains(key))
        assertEquals(request.token, WireJson.decodeFromString<RenewedSession>(renewed.bodyAsText()).credentials.token)
        val malformed = client.post("/v1/guests/me/session") { bearerAuth(key); contentType(ContentType.Application.Json); setBody("{\"expectedRevision\":-1,\"token\":\"${secret()}\"}") }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }
}
