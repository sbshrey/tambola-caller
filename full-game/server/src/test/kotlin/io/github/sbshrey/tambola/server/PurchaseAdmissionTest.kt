package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class PurchaseAdmissionTest : PostgresTest() {
    @Test fun `concurrent HTTP failures preserve other purchases and exact retries`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        val actors = List(4) { service.register(GuestRequest("Mixed purchase QA $it"), "mixed-$it") }
        val requests = List(4) { MatchRequest(UUID.randomUUID().toString(), 3) }
        database.transaction {
            CoinLedger.change(it, actors[1].playerId, "test:spend", -1500, now.get())
            it.execute("CREATE FUNCTION reject_one_purchase() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN IF NEW.actor = ''${actors[2].playerId}'' THEN RAISE EXCEPTION ''injected receipt failure''; END IF; RETURN NEW; END;'")
            it.execute("CREATE TRIGGER reject_one_purchase BEFORE INSERT ON match_receipts FOR EACH ROW EXECUTE FUNCTION reject_one_purchase()")
        }
        assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
        suspend fun buy(index: Int) = client.post("/v1/matches") {
            bearerAuth(actors[index].token); contentType(ContentType.Application.Json)
            setBody(WireJson.encodeToString(requests[index]))
        }
        val replies = withTimeout(10_000) {
            coroutineScope {
                val start = CompletableDeferred<Unit>()
                val pending = actors.indices.map { index -> async(Dispatchers.IO) { start.await(); buy(index) } }
                start.complete(Unit); pending.awaitAll()
            }
        }
        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.Conflict, HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK), replies.map { it.status })
        assertEquals("coins_low", WireJson.decodeFromString<ApiError>(replies[1].bodyAsText()).code)
        assertEquals("database_unavailable", WireJson.decodeFromString<ApiError>(replies[2].bodyAsText()).code)
        assertEquals(listOf(1200L, 0L, 1500L, 1200L), actors.map { service.wallet(it.token).balance })
        database.transaction { connection ->
            assertEquals(2, connection.query("SELECT count(*) FROM match_receipts") { it.getInt(1) }.single())
            assertEquals(2, connection.query("SELECT count(*) FROM room_participants") { it.getInt(1) }.single())
            actors.forEach { actor ->
                assertEquals(1, connection.query("SELECT requests FROM rate_limits WHERE bucket = ?", "match:${actor.playerId}") { it.getInt(1) }.single())
            }
            connection.execute("DROP TRIGGER reject_one_purchase ON match_receipts")
        }
        for (index in listOf(0, 3)) assertEquals(replies[index].bodyAsText(), buy(index).bodyAsText())
        val retried = buy(2)
        assertEquals(HttpStatusCode.OK, retried.status)
        assertEquals(retried.bodyAsText(), buy(2).bodyAsText())
        assertEquals(1200L, service.wallet(actors[2].token).balance)
        database.transaction { connection ->
            assertEquals(3, connection.query("SELECT count(*) FROM match_receipts") { it.getInt(1) }.single())
            assertEquals(3, connection.query("SELECT count(*) FROM room_participants") { it.getInt(1) }.single())
            assertEquals(3, connection.query("SELECT requests FROM rate_limits WHERE bucket = ?", "match:${actors[2].playerId}") { it.getInt(1) }.single())
        }
    }

    @Test fun `queued quick purchases leave wallets and friend tables available`() = testApplication {
        val arrived = AtomicInteger()
        val arrivals = createApplicationPlugin("PurchaseArrivalCounter") {
            onCall { if (it.request.path() == "/v1/matches") arrived.incrementAndGet() }
        }
        application { install(arrivals); roomsModule(database, service, runWorker = false) }
        val quick = List(16) { service.register(GuestRequest("Queue QA $it"), "queue-$it") }
        val host = service.register(GuestRequest("Friends host QA"), "friend-host")
        val friend = service.register(GuestRequest("Friends guest QA"), "friend-guest")
        val table = service.match(host.token, MatchRequest(UUID.randomUUID().toString(), 3, true)).snapshot
        assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
        val requests = quick.map { MatchRequest(UUID.randomUUID().toString(), 2) }
        coroutineScope {
            val locked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val blocker = async(Dispatchers.IO) {
                database.transaction { connection ->
                    connection.query("SELECT pg_advisory_xact_lock(749023809)") { true }
                    locked.complete(Unit)
                    runBlocking { release.await() }
                }
            }
            locked.await()
            val purchases = quick.mapIndexed { index, guest -> async(Dispatchers.IO) {
                client.post("/v1/matches") {
                    bearerAuth(guest.token); contentType(ContentType.Application.Json)
                    setBody(WireJson.encodeToString(requests[index]))
                }
            } }
            try {
                withTimeout(2_000) { while (arrived.get() < quick.size) delay(10) }
                // Let the admitted requests reach the intentionally blocked allocator.
                // The blocker remains held throughout the independent wallet and friends requests.
                delay(400)
                withTimeout(1_500) {
                    val wallet = client.get("/v1/wallet") { bearerAuth(friend.token) }
                    assertEquals(HttpStatusCode.OK, wallet.status)
                    assertEquals(1500L, WireJson.decodeFromString<WalletView>(wallet.bodyAsText()).balance)
                    val joined = client.post("/v1/matches") {
                        bearerAuth(friend.token); contentType(ContentType.Application.Json)
                        setBody(WireJson.encodeToString(MatchRequest(UUID.randomUUID().toString(), 2, true, table.code)))
                    }
                    assertEquals(HttpStatusCode.OK, joined.status)
                    val view = WireJson.decodeFromString<RoomUpdate>(joined.bodyAsText()).snapshot
                    assertEquals(table.code, view.code)
                    assertEquals(500L, view.coins!!.pool)
                    assertEquals(1300L, view.wallet!!.balance)
                }
            } finally {
                release.complete(Unit)
                blocker.await()
                purchases.awaitAll()
            }
            val replies = purchases.map { it.await().also { reply -> assertEquals(HttpStatusCode.OK, reply.status) }.bodyAsText() }
            val groups = replies.map { WireJson.decodeFromString<RoomUpdate>(it).snapshot }.groupBy { it.roomId }
            assertEquals(2, groups.size); assertTrue(groups.values.all { it.size == 8 })
            quick.forEachIndexed { index, guest ->
                val replay = client.post("/v1/matches") {
                    bearerAuth(guest.token); contentType(ContentType.Application.Json)
                    setBody(WireJson.encodeToString(requests[index]))
                }
                assertEquals(replies[index], replay.bodyAsText())
                assertEquals(1300L, service.wallet(guest.token).balance)
            }
        }
    }

    @Test fun `a rejected purchase releases admission for the next player`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        val poor = service.register(GuestRequest("No coins QA"), "poor")
        val next = service.register(GuestRequest("Next player QA"), "next")
        database.transaction { CoinLedger.change(it, poor.playerId, "test:spend", -1500, now.get()) }
        suspend fun buy(guest: GuestCredentials) = client.post("/v1/matches") {
            bearerAuth(guest.token); contentType(ContentType.Application.Json)
            setBody(WireJson.encodeToString(MatchRequest(UUID.randomUUID().toString(), 1)))
        }
        withTimeout(5_000) {
            // More failures than admission permits expose a leaked permit as a stuck request.
            repeat(8) { assertEquals(HttpStatusCode.Conflict, buy(poor).status) }
            val response = buy(next)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1400L, WireJson.decodeFromString<RoomUpdate>(response.bodyAsText()).snapshot.wallet!!.balance)
        }
    }
}
