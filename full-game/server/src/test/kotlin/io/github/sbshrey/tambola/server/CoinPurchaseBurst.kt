package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.HttpRoomApi
import io.github.sbshrey.tambola.client.RoomApiFailure
import io.github.sbshrey.tambola.domain.COIN_STARTER_BALANCE
import io.github.sbshrey.tambola.domain.COIN_TICKET_PRICE
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.system.exitProcess

/** Repeated public purchases/cancellations. Diagnostic only; no calls, claims or capacity pass. */
object CoinPurchaseBurst {
    private val prettyJson = Json { prettyPrint = true }
    private fun id() = UUID.randomUUID().toString()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @JvmStatic fun main(args: Array<String>) { require(args.isEmpty()); exitProcess(runBlocking { run() }) }

    private suspend fun run(): Int {
        val count = (System.getenv("TAMBOLA_COIN_LOAD_PLAYERS")?.toInt() ?: 320).also { require(it in 8..320 && it % 8 == 0) }
        val waves = (System.getenv("TAMBOLA_COIN_BURST_WAVES")?.toInt() ?: 2).also { require(it in 1..4) }
        val streams = System.getenv("TAMBOLA_COIN_BURST_STREAMS") == "true"
        val env = IsolatedLoadService()
        val clients = List(count / 8) { HttpRoomApi(env.base, true) }
        val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val evidence = linkedMapOf<String, JsonElement>()
        val rounds = mutableListOf<JsonElement>()
        var passed = false
        var clean = false
        fun record(key: String, value: Any?) { evidence[key] = when(value) {
            null -> JsonNull; is Number -> JsonPrimitive(value); is Boolean -> JsonPrimitive(value); else -> JsonPrimitive(value.toString())
        } }
        fun checkpoint(stage: String) {
            record("stage", stage); evidence["waves"] = JsonArray(rounds)
            Files.writeString(env.directory.resolve("burst-evidence.json"), prettyJson.encodeToString(JsonObject(evidence)) + "\n")
            println("coin-burst[${env.runId}]: $stage")
        }
        try {
            withTimeout(120_000) {
                record("runId", env.runId); record("players", count); record("requestedWaves", waves)
                record("scope", "Synthetic seeded profiles; native public HTTP purchase/read/leave/wallet routes, optional immediate WebSocket subscriptions; no registration, calls, claims, Android, TLS or capacity acceptance. Same clients/server reused between waves.")
                record("immediateStreams", streams)
                record("serverHeapMiB", 512); record("serverActiveProcessors", 4)
                record("sharedHttpTransports", count / 8)
                record("lifecycleSourceSha256", sha(Files.readAllBytes(env.root.resolve("server/src/test/kotlin/io/github/sbshrey/tambola/server/IsolatedLoadService.kt"))))
                record("clientJarSha256", sha(Files.readAllBytes(env.root.resolve("client/build/libs/client.jar"))))
                record("fixtureSourceSha256", sha(Files.readAllBytes(env.root.resolve("server/src/test/kotlin/io/github/sbshrey/tambola/server/CoinPurchaseBurst.kt"))))
                record("profilerSourceSha256", sha(Files.readAllBytes(env.root.resolve("server/src/test/kotlin/io/github/sbshrey/tambola/server/ProfiledCoinServer.kt"))))
                record("sqlProfiling", env.sqlProfiling); record("poolProfiling", System.getenv("TAMBOLA_COIN_LOAD_POOL_PROFILE") == "true")
                val requestProfiling = System.getenv("TAMBOLA_COIN_LOAD_REQUEST_PROFILE") == "true"
                check(!requestProfiling || env.sqlProfiling)
                record("requestProfiling", requestProfiling)
                env.runtimeIdentity.forEach(::record)
                checkpoint("starting")
                env.start(); record("serverPid", env.pid)
                val guests = env.seed(count)
                val times = DoubleArray(count)
                repeat(waves) { wave ->
                    val subscriptions = CopyOnWriteArrayList<Job>()
                    val connected = AtomicInteger()
                    val failure = AtomicReference<Exception?>()
                    checkpoint("wave-${wave + 1}-buy")
                    val start = System.currentTimeMillis()
                    val entries = coroutineScope { guests.mapIndexed { index, guest -> async(Dispatchers.IO) {
                        val quantity = (index + wave) % 6 + 1
                        val request = MatchRequest(id(), quantity)
                        val began = System.nanoTime()
                        val update = clients[index % clients.size].match(guest.token, request)
                        times[index] = (System.nanoTime() - began) / 1e6
                        check(update.snapshot.phase == RoomPhase.LOBBY)
                        check(update.snapshot.wallet!!.balance == COIN_STARTER_BALANCE - quantity * COIN_TICKET_PRICE)
                        check(update.snapshot.coins!!.ownTickets == quantity)
                        if (streams) subscriptions += streamScope.launch {
                            try {
                                var first = true
                                clients[index % clients.size].events(guest.token, update.snapshot.code, update.snapshot.revision).collect { next ->
                                    check(next.snapshot.coins!!.ownTickets == quantity)
                                    check(next.snapshot.phase == RoomPhase.LOBBY)
                                    if (first) { connected.incrementAndGet(); first = false }
                                }
                                error("Unexpected stream completion")
                            } catch (error: CancellationException) { throw error }
                            catch (error: Exception) { failure.compareAndSet(null, error) }
                        }
                        Entry(index, guest, request, update)
                    } }.awaitAll() }
                    val end = System.currentTimeMillis()
                    val groups = entries.groupBy { it.update.snapshot.roomId }
                    check(groups.size == count / 8 && groups.values.all { it.size == 8 })
                    if (streams) withTimeout(5000) {
                        while (connected.get() < count) { failure.get()?.let { throw it }; delay(10) }
                    }
                    subscriptions.forEach { it.cancel() }
                    subscriptions.joinAll()
                    failure.get()?.let { throw it }
                    checkpoint("wave-${wave + 1}-cancel")
                    coroutineScope { groups.values.map { group -> async(Dispatchers.IO) {
                        for (entry in group) {
                            val api = clients[entry.index % clients.size]
                            val code = entry.update.snapshot.code
                            // Presence revisions can advance; retry only an explicitly rejected stale command.
                            var left = false
                            repeat(8) {
                                if (!left) {
                                    val view = api.read(entry.guest.token, code).snapshot
                                    check(view.phase == RoomPhase.LOBBY) { "Cancellation exceeded sales deadline" }
                                    val command = CommandRequest(id(), view.revision, RoomAction.Leave)
                                    try {
                                        val result = api.command(entry.guest.token, code, command)
                                        check(result.snapshot.wallet!!.balance == COIN_STARTER_BALANCE)
                                        check(api.command(entry.guest.token, code, command) == result)
                                        check(api.match(entry.guest.token, entry.request) == entry.update)
                                        left = true
                                    } catch (error: RoomApiFailure) { if (error.code != "stale_revision") throw error }
                                }
                            }
                            check(left)
                        }
                    } }.awaitAll() }
                    env.primary { connection ->
                        check(connection.query("SELECT count(*) FROM rooms WHERE phase <> 'CLOSED'") { it.getInt(1) }.single() == 0)
                        check(connection.query("SELECT count(*) FROM match_receipts") { it.getInt(1) }.single() == count * (wave + 1))
                        check(connection.query("SELECT count(*) FROM coin_ledger WHERE amount < 0") { it.getInt(1) }.single() == count * (wave + 1))
                        check(connection.query("SELECT sum(amount) FROM coin_ledger") { it.getLong(1) }.single() == count * COIN_STARTER_BALANCE)
                    }
                    rounds += buildJsonObject {
                        put("wave", wave + 1); put("purchaseStartEpochMs", start); put("purchaseEndEpochMs", end)
                        put("tables", groups.size); put("allRefundsAndReceiptReplaysVerified", true)
                        put("streamsObservedBeforeCancellation", connected.get())
                        put("purchaseRoundTripMs", stats(times.toList()))
                    }
                    checkpoint("wave-${wave + 1}-verified")
                }
                // Allow the diagnostic recording stream to flush before the owned process is stopped.
                if (System.getenv("TAMBOLA_COIN_LOAD_POOL_PROFILE") == "true" || requestProfiling) delay(2500)
                env.alive(); check(serviceRuntimeIdentity(env.root, env.runtimeLib) == env.runtimeIdentity)
                passed = true
            }
        } catch (error: Exception) {
            val line = error.stackTrace.firstOrNull { it.className.startsWith("io.github.sbshrey.tambola") }
            record("failure", "${error.javaClass.simpleName}:${line?.fileName}:${line?.lineNumber}")
            if (error is RoomApiFailure) { record("httpStatus", error.status); record("httpCode", error.code) }
        } finally {
            streamScope.cancel(); streamScope.coroutineContext[Job]?.join()
            clients.forEach { runCatching { it.close() } }
            clean = runCatching { env.close(); true }.getOrDefault(false)
            record("passed", passed); record("cleanupComplete", clean); record("capacityAcceptance", false)
            checkpoint(if (passed) "completed" else "failed")
        }
        return if (passed && clean) 0 else 1
    }

    private data class Entry(val index: Int, val guest: GuestCredentials, val request: MatchRequest, val update: RoomUpdate)
    private fun stats(values: List<Double>) = buildJsonObject {
        val sorted = values.sorted()
        put("count", sorted.size)
        listOf("p50" to .5, "p95" to .95, "p99" to .99).forEach { (key, p) -> put(key, sorted[ceil(sorted.size * p).toInt() - 1]) }
        put("max", sorted.last())
    }
}
