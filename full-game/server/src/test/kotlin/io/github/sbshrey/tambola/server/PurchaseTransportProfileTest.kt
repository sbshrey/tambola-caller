package io.github.sbshrey.tambola.server

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.Dispatcher
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PurchaseTransportProfileTest {
    @Test fun `real dispatcher queuing is separate from body delivery and no content is exported`() = runBlocking<Unit> {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val pool = Executors.newFixedThreadPool(4)
        val first = CountDownLatch(1)
        val body = CountDownLatch(1)
        val releaseHeaders = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.executor = pool
        server.createContext("/") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            first.countDown(); check(releaseHeaders.await(5, TimeUnit.SECONDS))
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use {
                it.write('{'.code); it.flush(); body.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                it.write('}'.code)
            }
        }
        server.start()
        val profile = PurchaseTransportProfile().apply { phase(1, true) }
        val client = profile.client { dispatcher(Dispatcher().apply { maxRequestsPerHost = 1 }) }
        try {
            val began = System.nanoTime()
            val times = coroutineScope {
                val calls = List(3) { async(Dispatchers.IO) {
                    val start = System.nanoTime()
                    assertEquals("{}", client.post("http://127.0.0.1:${server.address.port}/v1/matches") {
                        bearerAuth("secret-not-in-metrics"); setBody("private-purchase-body")
                    }.bodyAsText())
                    (System.nanoTime() - start) / 1e6
                } }
                assertTrue(withContext(Dispatchers.IO) { first.await(5, TimeUnit.SECONDS) })
                withTimeout(5000) { while (profile.queuedCalls.get() != 2) delay(5) }
                delay(200); releaseHeaders.countDown()
                assertTrue(withContext(Dispatchers.IO) { body.await(5, TimeUnit.SECONDS) })
                withTimeout(5000) { while (profile.responseHeadersObserved.get() == 0) delay(5) }
                delay(200); release.countDown(); calls.awaitAll()
            }
            val rows = profile.records()
            assertEquals(3, rows.size)
            assertTrue(rows.all { it.getValue("validSingleExchange").jsonPrimitive.boolean })
            assertEquals(2, rows.count { it.getValue("dispatcherQueueMs").jsonPrimitive.double >= 190 })
            assertTrue(rows.any { it.getValue("responseReadMs").jsonPrimitive.double >= 190 })
            for (row in rows) {
                val partition = listOf("dispatcherQueueMs", "nonQueueBeforeRequestMs", "requestWriteMs", "awaitResponseHeadersMs", "responseReadMs", "closingMs")
                    .sumOf { row.getValue(it).jsonPrimitive.double }
                assertEquals(row.getValue("callMs").jsonPrimitive.double, partition, .00001)
                assertTrue(row.getValue("callMs").jsonPrimitive.double <= (System.nanoTime() - began) / 1e6)
            }
            assertEquals(3, profile.summary(1, 3, times.sum()).getValue("count").jsonPrimitive.int)
            assertEquals("{}", client.get("http://127.0.0.1:${server.address.port}/v1/wallet").bodyAsText())
            assertEquals(3, profile.records().size)
            val file = Files.createTempFile("transport-profile-", ".json")
            try {
                profile.write(file)
                val text = Files.readString(file)
                assertFalse(text.contains("secret-not-in-metrics")); assertFalse(text.contains("private-purchase-body"))
                assertFalse(text.contains("127.0.0.1")); assertFalse(text.contains("/v1/"))
                val parsed = Json.parseToJsonElement(text).jsonObject
                assertEquals(setOf("schema", "scope", "dispatcherLimits", "samples"), parsed.keys)
                assertEquals(1, parsed.getValue("dispatcherLimits").jsonArray.single().jsonObject.getValue("maxRequestsPerHost").jsonPrimitive.int)
            } finally { Files.deleteIfExists(file) }
        } finally { releaseHeaders.countDown(); release.countDown(); client.close(); server.stop(0); pool.shutdownNow() }
    }

    @Test fun `failed connection remains invalid evidence and cannot pass coverage`() = runBlocking<Unit> {
        val port = ServerSocket(0).use { it.localPort }
        val profile = PurchaseTransportProfile().apply { phase(1, true) }
        val client = profile.client()
        try {
            assertTrue(runCatching { client.post("http://127.0.0.1:$port/v1/matches") { setBody("secret") }.bodyAsText() }.isFailure)
            withTimeout(5000) { while (profile.records().isEmpty()) delay(5) }
            val row = profile.records().single()
            assertFalse(row.getValue("succeeded").jsonPrimitive.boolean)
            assertFalse(row.getValue("validSingleExchange").jsonPrimitive.boolean)
            assertFalse(row.containsKey("callMs"))
            assertThrows(IllegalStateException::class.java) { profile.summary(1, 1, 5000.0) }
        } finally { client.close() }
    }
}
