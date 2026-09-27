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
