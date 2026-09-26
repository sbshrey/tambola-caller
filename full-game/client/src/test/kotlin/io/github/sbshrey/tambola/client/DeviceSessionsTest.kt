package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

class DeviceSessionsTest {
    private class Fixture : AutoCloseable {
        val original = GuestCredentials(UUID.randomUUID().toString(), "a".repeat(43), 7)
        var stored: OnlineSaved? = OnlineSaved("https://rooms.example", original, "QA", wallet = WalletView(900, 2, 0),
            pending = PendingOperation.Match(MatchRequest(UUID.randomUUID().toString(), 6)))
        var key: String? = null
        var current = original
        var revision = 0L
        var enrollments = 0
        var rotations = 0
        var dropEnrollment = false
        var rejectEnrollment = false
        var dropRenewal = false
        var failRenewalSave = false
        var denyDevice = false
        var failWrite = false
        var mismatch = false
        var concurrentMark = false
        val requests = mutableListOf<RenewSessionRequest>()
        val api = HttpRoomApi("https://rooms.example", client = HttpClient(MockEngine { call ->
            assertFalse(call.url.toString().contains(current.token))
            val body = (call.body as TextContent).text
            when (call.url.encodedPath) {
                "/v1/guests/me/device" -> {
                    val request = WireJson.decodeFromString<EnrollDeviceRequest>(body)
                    assertEquals(stored!!.deviceIdentity!!.key, request.deviceKey) // saved before HTTP
                    assertEquals("Bearer ${current.token}", call.headers[HttpHeaders.Authorization])
                    if (rejectEnrollment) respond(WireJson.encodeToString(ApiError("unauthorized", "Expired")), HttpStatusCode.Unauthorized)
                    else {
                        if (key != null) assertEquals(key, request.deviceKey)
                        key = request.deviceKey; enrollments++
                        if (dropEnrollment) { dropEnrollment = false; throw IOException("QA dropped enrollment response") }
                        respond(WireJson.encodeToString(DeviceEnrollment(original.playerId, revision)), HttpStatusCode.OK)
                    }
                }
                "/v1/guests/me/session" -> {
                    if (denyDevice) respond(WireJson.encodeToString(ApiError("unauthorized", "Unavailable")), HttpStatusCode.Unauthorized)
                    else {
                        assertEquals("Bearer $key", call.headers[HttpHeaders.Authorization])
                        val request = WireJson.decodeFromString<RenewSessionRequest>(body); requests += request
                        assertEquals(request, stored!!.deviceIdentity!!.pending) // saved before rotation
                        if (request.expectedRevision == revision) {
                            revision++; rotations++
                            current = GuestCredentials(original.playerId, request.token, 999)
                        } else assertEquals(revision - 1, request.expectedRevision)
                        if (concurrentMark) stored = stored!!.copy(marks = mapOf("kept-ticket" to setOf(12)))
                        if (dropRenewal) { dropRenewal = false; throw IOException("QA dropped renewal response") }
                        if (failRenewalSave) { failRenewalSave = false; failWrite = true }
                        respond(WireJson.encodeToString(RenewedSession(if (mismatch) current.copy(playerId = "wrong-player") else current, revision)), HttpStatusCode.OK)
                    }
                }
                else -> error("Unexpected fixture route")
            }
        }))
        fun coordinator() = DeviceSessions(api, { stored }) { player, change ->
            if (failWrite) throw IOException("QA storage failure")
            check(stored!!.credentials.playerId == player)
            change(stored!!).also { stored = it }
        }
        fun reload() { stored = WireJson.decodeFromString<OnlineSaved>(WireJson.encodeToString(requireNotNull(stored))) }
        override fun close() = api.close()
    }

    @Test fun `legacy enrollment keeps wallet and pending purchase then concurrent 401s rotate once`() = runBlocking {
        Fixture().use { f ->
            val initial = f.stored!!; val sessions = f.coordinator()
            assertEquals(initial.credentials, sessions.credentials())
            assertEquals(1, f.enrollments)
            assertEquals(0, f.rotations) // Device wall-clock age cannot force a renewal loop.
            val replies = coroutineScope { List(4) { async { sessions.credentials(f.original.token) } }.awaitAll() }
            assertEquals(1, replies.distinct().size); assertEquals(1, f.rotations)
            assertEquals(initial.pending, f.stored!!.pending); assertEquals(initial.wallet, f.stored!!.wallet)
            assertEquals(initial.credentials.playerId, f.stored!!.credentials.playerId)
        }
    }

    @Test fun `lost enrollment response retries the persisted device credential after process reconstruction`() = runBlocking {
        Fixture().use { f ->
            f.dropEnrollment = true
            try { f.coordinator().credentials(); fail() } catch (_: IOException) { }
            val key = f.stored!!.deviceIdentity!!.key
            f.reload(); f.coordinator().credentials()
            assertEquals(key, f.key); assertEquals(0L, f.stored!!.deviceIdentity!!.revision)
            assertEquals(900L, f.stored!!.wallet!!.balance)
        }
    }

    @Test fun `lost renewal response replays exact token after process reconstruction and retains concurrent state`() = runBlocking {
        Fixture().use { f ->
            val sessions = f.coordinator(); sessions.credentials(); f.dropRenewal = true
            try { sessions.credentials(f.original.token); fail() } catch (_: IOException) { }
            val pending = f.stored!!.deviceIdentity!!.pending!!
            f.reload(); f.concurrentMark = true
            val restored = f.coordinator().credentials()
            assertEquals(pending.token, restored.token)
            assertEquals(listOf(pending, pending), f.requests)
            assertEquals(1, f.rotations)
            assertEquals(setOf(12), f.stored!!.marks["kept-ticket"])
            assertNotNull(f.stored!!.pending); assertEquals(900L, f.stored!!.wallet!!.balance)
        }
    }

    @Test fun `lost enrollment followed by session expiry recovers with the already enrolled proof`() = runBlocking {
        Fixture().use { f ->
            f.dropEnrollment = true
            try { f.coordinator().credentials(); fail() } catch (_: IOException) { }
            f.reload(); f.rejectEnrollment = true
            val renewed = f.coordinator().credentials()
            assertEquals(f.original.playerId, renewed.playerId)
            assertNotEquals(f.original.token, renewed.token)
            assertEquals(1L, f.stored!!.deviceIdentity!!.revision)
            assertEquals(900L, f.stored!!.wallet!!.balance)
        }
    }

    @Test fun `failed response save leaves durable intent for exact recovery`() = runBlocking {
        Fixture().use { f ->
            val sessions = f.coordinator(); sessions.credentials(); f.failRenewalSave = true
            try { sessions.credentials(f.original.token); fail() } catch (_: IOException) { }
            assertEquals(f.original, f.stored!!.credentials)
            assertNotNull(f.stored!!.deviceIdentity!!.pending)
            f.failWrite = false; f.reload()
            assertEquals(f.current, f.coordinator().credentials())
            assertEquals(1, f.rotations)
            assertEquals(f.requests.first(), f.requests.last())
        }
    }

    @Test fun `storage failure prevents enrollment or rotation and wrong-profile response is never saved`() = runBlocking {
        Fixture().use { f ->
            f.failWrite = true
            try { f.coordinator().credentials(); fail() } catch (_: IOException) { }
            assertEquals(0, f.enrollments)
            f.failWrite = false; val sessions = f.coordinator(); sessions.credentials()
            f.failWrite = true
            try { sessions.credentials(f.original.token); fail() } catch (_: IOException) { }
            assertEquals(0, f.rotations)
            f.failWrite = false; f.mismatch = true
            try { sessions.credentials(f.original.token); fail() } catch (_: InvalidRoomResponse) { }
            assertEquals(f.original, f.stored!!.credentials)
            assertNotNull(f.stored!!.deviceIdentity!!.pending)
        }
    }

    @Test fun `deletion settles a lost renewal first then freezes its token across retries`() = runBlocking {
        Fixture().use { f ->
            val sessions = f.coordinator(); sessions.credentials(); f.dropRenewal = true
            try { sessions.credentials(f.original.token); fail() } catch (_: IOException) { }
            val renewed = f.coordinator().settleBeforeDeletion()
            f.stored = f.stored!!.copy(pending = PendingOperation.DeleteProfile(DeleteProfileRequest(UUID.randomUUID().toString())))
            assertEquals(renewed, sessions.credentials(renewed.token))
            assertEquals(1, f.rotations)
        }
    }

    @Test fun `failed unknown device renewal still permits explicit legacy deletion without resetting wallet`() = runBlocking {
        Fixture().use { f ->
            val sessions = f.coordinator(); sessions.credentials(); f.denyDevice = true
            try { sessions.credentials(f.original.token); fail() } catch (error: RoomApiFailure) { assertEquals(401, error.status) }
            assertEquals(f.original, sessions.settleBeforeDeletion())
            assertEquals(900L, f.stored!!.wallet!!.balance)
            assertNotNull(f.stored!!.deviceIdentity!!.pending)
        }
    }
}
