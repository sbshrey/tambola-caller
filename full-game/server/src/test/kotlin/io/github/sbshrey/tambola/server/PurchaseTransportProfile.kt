package io.github.sbshrey.tambola.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/** Test-only OkHttp observations. No headers, URLs, bodies, credentials or exception text are retained. */
internal class PurchaseTransportProfile : EventListener.Factory {
    private val prettyJson = Json { prettyPrint = true }
    private data class Phase(val wave: Int, val primary: Boolean)
    @Volatile private var phase = Phase(0, false)
    private val samples = ConcurrentLinkedQueue<JsonObject>()
    val queuedCalls = AtomicInteger()
    val responseHeadersObserved = AtomicInteger()
    private val configurations = ConcurrentLinkedQueue<Pair<Int, Int>>()

    fun phase(wave: Int, primary: Boolean) { require(wave in 1..4); phase = Phase(wave, primary) }
    fun records(): List<JsonObject> = samples.toList()

    // Mirrors RoomApi.kt's existing client defaults; only the event listener is added.
    fun client(configure: OkHttpClient.Builder.() -> Unit = {}): HttpClient = HttpClient(OkHttp) {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 30_000 }
        install(WebSockets)
        engine { config {
            followRedirects(false); followSslRedirects(false); pingInterval(20, TimeUnit.SECONDS)
            configure(); eventListenerFactory(this@PurchaseTransportProfile)
            build().dispatcher.let { configurations += it.maxRequests to it.maxRequestsPerHost }
        } }
    }

    override fun create(call: Call): EventListener {
        val current = phase
        return if (current.wave > 0 && call.request().method == "POST" && call.request().url.encodedPath == "/v1/matches") Trace(current)
        else EventListener.NONE
    }

    private inner class Trace(private val phase: Phase) : EventListener() {
        private var start = 0L
        private var queueStart = 0L
        private var queueNs = 0L
        private var queueStarts = 0
        private var queueEnds = 0
        private var connectStart = 0L
        private var connectNs = 0L
        private var connections = 0
        private var headers = 0L
        private var requests = 0
        private var sent = 0L
        private var response = 0L
        private var responses = 0
        private var received = 0L
        override fun callStart(call: Call) { start = System.nanoTime() }
        override fun dispatcherQueueStart(call: Call, dispatcher: Dispatcher) {
            queueStarts++; queueStart = System.nanoTime(); queuedCalls.incrementAndGet()
        }
        override fun dispatcherQueueEnd(call: Call, dispatcher: Dispatcher) { queueEnds++; queueNs += System.nanoTime() - queueStart }
        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { connectStart = System.nanoTime() }
        override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) { connectNs += System.nanoTime() - connectStart }
        override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) { connectNs += System.nanoTime() - connectStart }
        override fun connectionAcquired(call: Call, connection: Connection) { connections++ }
        override fun requestHeadersStart(call: Call) { requests++; headers = System.nanoTime() }
        override fun requestBodyEnd(call: Call, byteCount: Long) { sent = System.nanoTime() }
        override fun responseHeadersStart(call: Call) { responses++; response = System.nanoTime(); responseHeadersObserved.incrementAndGet() }
        override fun responseBodyEnd(call: Call, byteCount: Long) { received = System.nanoTime() }
        override fun callEnd(call: Call) { finish(true) }
        override fun callFailed(call: Call, ioe: IOException) { finish(false) }
        private fun finish(succeeded: Boolean) {
            val end = System.nanoTime()
            val points = listOf(start, headers, sent, response, received, end)
            val valid = succeeded && connections == 1 && requests == 1 && responses == 1 && queueStarts == queueEnds &&
                points.all { it != 0L } && points.zipWithNext().all { (a, b) -> a <= b } && queueNs in 0..(headers - start)
            samples += buildJsonObject {
                put("wave", phase.wave); put("primary", phase.primary); put("succeeded", succeeded); put("validSingleExchange", valid)
                put("queueCount", queueStarts); put("connectionCount", connections); put("requestCount", requests); put("responseCount", responses)
                if (valid) {
                    put("callMs", (end - start) / 1e6)
                    put("dispatcherQueueMs", queueNs / 1e6)
                    put("nonQueueBeforeRequestMs", (headers - start - queueNs) / 1e6)
                    put("requestWriteMs", (sent - headers) / 1e6)
                    put("awaitResponseHeadersMs", (response - sent) / 1e6)
                    put("responseReadMs", (received - response) / 1e6)
                    put("closingMs", (end - received) / 1e6)
                    put("connectMsNested", connectNs / 1e6)
                }
            }
        }
    }

    fun summary(wave: Int, count: Int, apiSumMs: Double): JsonObject {
        val rows = records().filter { it.getValue("wave").jsonPrimitive.int == wave && it.getValue("primary").jsonPrimitive.boolean }
        check(rows.size == count && rows.all { it.getValue("validSingleExchange").jsonPrimitive.boolean }) { "Incomplete purchase transport capture" }
        val callSum = rows.sumOf { it.getValue("callMs").jsonPrimitive.double }
        check(apiSumMs >= callSum) { "Transport observation extends beyond the measured API requests" }
        return buildJsonObject {
            put("count", count); put("queuedCalls", rows.count { it.getValue("queueCount").jsonPrimitive.int > 0 })
            put("apiSumMs", apiSumMs); put("callSumMs", callSum); put("outsideOkHttpSumMs", apiSumMs - callSum)
            for (key in listOf("callMs", "dispatcherQueueMs", "nonQueueBeforeRequestMs", "requestWriteMs", "awaitResponseHeadersMs", "responseReadMs", "closingMs", "connectMsNested")) {
                val values = rows.map { it.getValue(key).jsonPrimitive.double }.sorted()
                put(key, buildJsonObject {
                    put("sum", values.sum()); put("p50", values[ceil(count * .5).toInt() - 1]);
                    put("p95", values[ceil(count * .95).toInt() - 1]); put("max", values.last())
                })
            }
        }
    }

    fun write(path: Path) {
        val output = buildJsonObject {
            put("schema", 1); put("scope", "Test-only loopback HTTP transport phases; no identifiers or request content; connect time is nested, not additive.")
            put("dispatcherLimits", JsonArray(configurations.distinct().sortedWith(compareBy({ it.first }, { it.second })).map { (all, host) ->
                buildJsonObject { put("maxRequests", all); put("maxRequestsPerHost", host) }
            }))
            put("samples", JsonArray(records()))
        }
        Files.writeString(path, prettyJson.encodeToString(JsonObject.serializer(), output) + "\n")
    }
}
