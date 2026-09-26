package io.github.sbshrey.tambola.server

import com.sun.net.httpserver.HttpServer
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

/** Opt-in real-clock automatic game with two independent service JVMs and the shipped native client. */
object AutomaticRecoveryLoad {
    private val json = Json { prettyPrint = true }
    private fun id() = UUID.randomUUID().toString()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @JvmStatic fun main(args: Array<String>) { exitProcess(runBlocking { run() }) }

    private suspend fun run(): Int {
        val startedAt = System.nanoTime()
        fun required(key: String) = requireNotNull(System.getenv(key)?.takeIf(String::isNotBlank)) { "$key is required" }
        val url = required("TAMBOLA_TEST_DATABASE_URL")
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/tambola_test")))
        val user = required("TAMBOLA_TEST_DATABASE_USER")
        val password = required("TAMBOLA_TEST_DATABASE_PASSWORD")
        val root = Path.of("").toAbsolutePath()
        val runtimeIdentity = serviceRuntimeIdentity(root)
        val runId = id().replace("-", "").take(16)
        val directory = root.resolve(".test-workspace/automatic-$runId")
        Files.createDirectories(directory)
        val names = listOf("tambola_load_main_$runId", "tambola_load_journal_$runId")
        val owned = mutableSetOf<String>()
        fun databaseUrl(index: Int) = url.removeSuffix("tambola_test") + names[index]
        val jdbcOptions = Properties().apply {
            setProperty("user", user); setProperty("password", password)
            setProperty("connectTimeout", "5"); setProperty("socketTimeout", "30")
        }
        fun control(sql: String) = DriverManager.getConnection(url, jdbcOptions).use { it.createStatement().use { statement -> statement.execute(sql) } }
        fun <T> primary(block: (java.sql.Connection) -> T): T = DriverManager.getConnection(databaseUrl(0), jdbcOptions).use(block)
        val ports = List(2) { ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort } }
        check(ports.distinct().size == 2)
        val bases = ports.map { "http://127.0.0.1:$it" }
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        val children = arrayOfNulls<Process>(2)
        val childPids = mutableListOf<Long>()
        val clients = bases.map { HttpRoomApi(it, true) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val jobs = arrayOfNulls<Job>(32)
        val failures = AtomicReference<String?>(null)
        val connections = Array(32) { AtomicInteger() }
        val disconnects = Array(32) { AtomicInteger() }
        val transportFailures = ConcurrentHashMap<String, AtomicInteger>()
        val skippedSnapshots = AtomicInteger()
        val slowMillis = AtomicLong()
        val saved = Array(32) { AtomicReference<OnlineSaved?>(null) }
        val events = linkedMapOf<String, JsonElement>()
        val stages = mutableListOf<String>()
        var stage = "initialize"
        var success = false
        var cleanup = true
        var proxy: LostResponse? = null
        fun record(key: String, value: Any) { events[key] = when(value) {
            is Number -> JsonPrimitive(value); is Boolean -> JsonPrimitive(value); else -> JsonPrimitive(value.toString())
        } }
        fun persist() {
            events["stages"] = JsonArray(stages.map(::JsonPrimitive))
            Files.writeString(directory.resolve("evidence.json"), json.encodeToString(JsonObject(events)) + "\n")
        }
        fun checkpoint(value: String) { stage = value; stages += value; record("currentStage", value); persist(); println("automatic[$runId]: $value") }
        fun healthy() { check(failures.get() == null) { "native_validation_${failures.get()}" } }
        suspend fun awaitCondition(timeout: Long = 30_000, condition: () -> Boolean) {
            withTimeout(timeout) { while (!condition()) { healthy(); delay(50) } }; healthy()
        }
        suspend fun start(index: Int) {
            check(children[index]?.isAlive != true)
            val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
            val builder = ProcessBuilder(java.toString(), "-Xms64m", "-Xmx256m", "-XX:ActiveProcessorCount=2",
                "-cp", root.resolve("server/build/install/server/lib").toString() + "/*", "io.github.sbshrey.tambola.server.ServerKt")
                .directory(directory.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
            builder.environment().putAll(mapOf("PORT" to ports[index].toString(), "TAMBOLA_BIND_HOST" to "127.0.0.1", "TAMBOLA_LOCAL_DEVELOPMENT" to "true",
                "TAMBOLA_DATABASE_URL" to databaseUrl(0), "TAMBOLA_DATABASE_USER" to user, "TAMBOLA_DATABASE_PASSWORD" to password,
                "TAMBOLA_DELETION_DATABASE_URL" to databaseUrl(1), "TAMBOLA_DELETION_DATABASE_USER" to user, "TAMBOLA_DELETION_DATABASE_PASSWORD" to password))
            children[index] = builder.start().also { childPids += it.pid() }
            awaitCondition {
                check(children[index]?.isAlive == true) { "service_exited_during_start" }
                runCatching { http.send(HttpRequest.newBuilder(URI(bases[index] + "/health/ready")).timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200 }.getOrDefault(false)
            }
        }
        fun stop(index: Int) {
            val child = requireNotNull(children[index]); check(child.isAlive)
            child.destroyForcibly(); check(child.waitFor(10, TimeUnit.SECONDS))
        }
        try {
            withTimeout(18 * 60_000L) {
                record("runId", runId); record("completed", false); record("players", 32); record("ticketsPerPlayer", 6)
                record("automaticCalls", 90); record("intervalMs", 5_000); record("serviceProcesses", 2)
                record("heapMiBPerService", 256); record("activeProcessorsPerService", 2)
                record("scope", "One maximum-size room; real native HTTP/WebSocket clients and two loopback Java services sharing isolated primary/journal databases. No Android UI, hosted TLS, physical network, restricted-role or ten-room capacity acceptance.")
                runtimeIdentity.forEach { (key, value) -> record(key, value) }
                record("fixtureSourceSha256", hash(Files.readAllBytes(root.resolve("server/src/test/kotlin/io/github/sbshrey/tambola/server/AutomaticRecoveryLoad.kt"))))
                record("clientJarSha256", hash(Files.readAllBytes(root.resolve("client/build/libs/client.jar"))))
                checkpoint("start-two-independent-workers")
                names.forEach { check(it.matches(Regex("tambola_load_(main|journal)_[a-f0-9]{16}"))); control("CREATE DATABASE $it TEMPLATE template0"); owned += it }
                start(0); start(1)
                val players = List(32) { GuestCredentials(id(), secret(), System.currentTimeMillis() + SESSION_LIFETIME) }
                primary { c ->
                    c.autoCommit = false
                    c.prepareStatement("INSERT INTO guests(id, name, avatar, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)").use { statement ->
                        players.forEachIndexed { index, player ->
                            listOf(player.playerId, "Automatic player ${index + 1}", index % AVATAR_COUNT, digest(player.token), player.expiresAt).forEachIndexed { n, value -> statement.setObject(n + 1, value) }
                            statement.addBatch()
                        }; statement.executeBatch()
                    }; c.commit()
                }
                val room = clients[0].create(players[0].token, CreateRoomRequest(id(), RoomOptions(
                    game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, playAllNumbers = true), capacity = 32,
                    automaticCalling = true, intervalSeconds = 5))).snapshot
                val code = room.code
                players.drop(1).forEachIndexed { index, player -> clients[index % 2].join(player.token, code) }
                suspend fun command(index: Int, action: RoomAction, api: HttpRoomApi = clients[index % 2]): RoomUpdate {
                    repeat(10) {
                        val before = api.read(players[index].token, code).snapshot
                        try { return api.command(players[index].token, code, CommandRequest(id(), before.revision, action)) }
                        catch (e: RoomApiFailure) { if (e.code != "stale_revision") throw e }
                    }; error("repeated_stale_revision")
                }
                fun watch(index: Int) {
                    check(jobs[index]?.isActive != true)
                    jobs[index] = scope.launch {
                        while (isActive) {
                            var first = true
                            try {
                                val api = clients[index % 2]
                                api.events(players[index].token, code, saved[index].get()?.room?.revision).collect { update ->
                                    if (first) { connections[index].incrementAndGet(); first = false }
                                    val prior = saved[index].get() ?: OnlineSaved(bases[index % 2], players[index], "Automatic player ${index + 1}")
                                    val accepted = prior.accept(update, live = true)
                                    val before = prior.room?.round?.called?.size ?: 0
                                    val after = accepted.saved.room?.round?.called?.size ?: 0
                                    if (after - before > 1) { skippedSnapshots.incrementAndGet(); check(accepted.announcement == null) { "backlog_announced_as_live" } }
                                    check(after >= before)
                                    var next = accepted.saved
                                    if (prior.marks.isNotEmpty()) check(next.marks == prior.marks) { "marks_changed_during_recovery" }
                                    if (next.marks.isEmpty()) next.room?.round?.let { game ->
                                        game.ownTickets.firstOrNull { ticket -> ticket.numbers.any { it in game.called } }?.let { ticket ->
                                            next = next.mark(ticket.id, ticket.numbers.first { it in game.called })
                                        }
                                    }
                                    // The same serialized cache and acceptance code as the app, without claiming Android disk/process evidence.
                                    saved[index].set(WireJson.decodeFromString<OnlineSaved>(WireJson.encodeToString(next)))
                                    if (index == 31) delay(slowMillis.get())
                                }
                                disconnects[index].incrementAndGet()
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                val transportClosed = e is java.io.IOException ||
                                    e is kotlinx.coroutines.channels.ClosedReceiveChannelException ||
                                    e is kotlinx.coroutines.channels.ClosedSendChannelException
                                if (!transportClosed && !(e is RoomApiFailure && e.status in 500..599)) {
                                    failures.compareAndSet(null, e.javaClass.simpleName + (if (e is RoomApiFailure) "_${e.status}_${e.code}" else "")); return@launch
                                }
                                transportFailures.computeIfAbsent(e.javaClass.simpleName) { AtomicInteger() }.incrementAndGet()
                                disconnects[index].incrementAndGet()
                            }
                            delay(250)
                        }
                    }
                }
                fun calls(index: Int = 1) = saved[index].get()?.room?.round?.called?.size ?: 0
                fun storedCalls() = primary { c -> c.query("SELECT payload FROM rooms WHERE id = ?", room.roomId) { WireJson.decodeFromString<RoomRecord>(it.getString(1)).round!!.called.size }.single() }
                checkpoint("connect-32-clients-and-start-automatic-game")
                players.indices.forEach(::watch)
                awaitCondition { connections.all { it.get() == 1 } }
                players.indices.forEach { command(it, RoomAction.Ready(true)) }
                command(0, RoomAction.Start)
                awaitCondition { saved.all { it.get()?.room?.round?.ownTickets?.size == 6 } }
                check(saved.flatMap { it.get()!!.room!!.round!!.ownTickets }.map { it.cells }.distinct().size == 192)
                val manual = runCatching { command(0, RoomAction.Draw) }.exceptionOrNull()
                check(manual is RoomApiFailure && manual.code == "automatic_calling")
                awaitCondition { calls() >= 3 }
                checkpoint("lose-pause-response-and-retry-after-process-restart")
                proxy = LostResponse(bases[0])
                val lostApi = HttpRoomApi(proxy!!.base, true)
                var pending: CommandRequest? = null
                try {
                    repeat(10) {
                        if (pending == null) {
                            val before = clients[0].read(players[0].token, code).snapshot
                            val request = CommandRequest(id(), before.revision, RoomAction.Pause)
                            val error = runCatching { lostApi.command(players[0].token, code, request) }.exceptionOrNull()
                            if (error is RoomApiFailure && error.code == "stale_revision") return@repeat
                            check(error != null && error !is RoomApiFailure) { "lost_response_not_observed" }
                            check(proxy!!.dropped.get() > 0); pending = request
                        }
                    }
                } finally { record("successfulPauseResponsesDiscarded", proxy!!.dropped.get()); lostApi.close(); proxy!!.close(); proxy = null }
                val request = requireNotNull(pending)
                val paused = clients[1].read(players[1].token, code).snapshot
                check(paused.round!!.status == RoundStatus.PAUSED && paused.nextDrawAt == null)
                val initialConnections = connections[0].get()
                stop(0); delay(7_000)
                check(clients[1].read(players[1].token, code).snapshot.round!!.called == paused.round!!.called)
                start(0)
                awaitCondition { connections[0].get() > initialConnections }
                val retried = clients[1].command(players[0].token, code, request)
                check(retried == clients[0].command(players[0].token, code, request))
                check(retried.snapshot.round!!.called == paused.round!!.called)
                check(primary { c -> c.query("SELECT count(*) FROM command_receipts WHERE room_id = ? AND actor = ? AND command_id = ?", room.roomId, players[0].playerId, request.id) { it.getInt(1) }.single() } == 1)
                record("lostPauseResponseRecoveredExactlyOnce", true)
                command(0, RoomAction.Resume)
                checkpoint("disconnect-host-until-controls-transfer")
                jobs[0]!!.cancelAndJoin()
                val oldCount = calls()
                awaitCondition(65_000) { saved[1].get()?.room?.hostId == players[1].playerId }
                check(calls() > oldCount)
                watch(0)
                awaitCondition { calls(0) >= calls() }
                val rejectedHost = runCatching { command(0, RoomAction.Pause) }.exceptionOrNull()
                check(rejectedHost is RoomApiFailure && rejectedHost.code == "host_only")
                record("hostTransferWhileAutomaticCalling", true)
                checkpoint("slow-consumer-conflates-and-catches-up")
                val previousGaps = skippedSnapshots.get()
                val beforeSlow = calls()
                slowMillis.set(12_000)
                awaitCondition(45_000) { calls() >= beforeSlow + 6 }
                slowMillis.set(0)
                awaitCondition { calls(31) >= calls() }
                check(skippedSnapshots.get() > previousGaps)
                record("slowConsumerCaughtUpWithoutBacklogAnnouncements", true)
                checkpoint("kill-one-active-service-while-other-keeps-calling")
                val beforeKill = calls()
                val beforeKillAt = System.nanoTime()
                val beforeConnections = connections.filterIndexed { index, _ -> index % 2 == 0 }.sumOf { it.get() }
                stop(0)
                awaitCondition(40_000) { calls() >= beforeKill + 5 }
                record("fiveCallsWithOneSurvivingServiceMs", (System.nanoTime() - beforeKillAt) / 1_000_000.0)
                start(0)
                awaitCondition { connections.filterIndexed { index, _ -> index % 2 == 0 }.sumOf { it.get() } >= beforeConnections + 16 }
                awaitCondition { saved.all { (it.get()?.room?.round?.called?.size ?: 0) >= calls() } }
                record("survivingWorkerContinuedCalls", true)
                checkpoint("stop-both-workers-for-two-call-intervals")
                stop(0); stop(1)
                val beforeOutage = storedCalls()
                val downAt = System.nanoTime()
                delay(12_000)
                check(storedCalls() == beforeOutage)
                record("bothServicesDownMs", (System.nanoTime() - downAt) / 1_000_000.0)
                start(0); start(1)
                awaitCondition { saved.all { (it.get()?.room?.round?.called?.size ?: 0) > beforeOutage } }
                record("allClientsRecoveredAfterBothServicesRestarted", true)
                checkpoint("finish-all-90-automatic-calls")
                var lastProgress = calls() / 10
                withTimeout(12 * 60_000L) {
                    while (saved.any { it.get()?.room?.round?.called?.size != 90 }) {
                        healthy(); check(children.all { it?.isAlive == true }); delay(250)
                        val progress = calls() / 10
                        if (progress > lastProgress) { lastProgress = progress; checkpoint("delivered-${progress * 10}-automatic-calls") }
                    }
                }
                checkpoint("verify-common-results-and-durable-draw-cadence")
                val final = clients[1].read(players[1].token, code).snapshot
                check(final.phase == RoomPhase.FINISHED && final.round!!.status == RoundStatus.COMPLETED)
                check(final.round!!.called.sorted() == (1..90).toList())
                players.forEachIndexed { index, player ->
                    val view = clients[index % 2].read(player.token, code).snapshot
                    view.validateFor(player.playerId)
                    check(view.round!!.called == final.round!!.called && view.round!!.scores == final.round!!.scores && view.round!!.awards == final.round!!.awards)
                    check(view.round!!.drawCommitment == final.round!!.drawCommitment)
                    val cache = saved[index].get()!!
                    check(cache.marks.isNotEmpty() && cache.history.count { it.round?.id == final.round!!.id } == 1)
                    if (index == 0) check(cache.accept(retried, live = false).saved == cache)
                }
                val retainedEvents = primary { c -> c.query("SELECT payload FROM room_events WHERE room_id = ? ORDER BY revision", room.roomId) { WireJson.decodeFromString<RoomEvent>(it.getString(1)) } }
                check(retainedEvents.count { it.type == "paused" } == 1 && retainedEvents.count { it.type == "resumed" } == 1)
                val draws = retainedEvents.filter { it.type == "drawn" }
                check(draws.size == 90) { "durable_draw_count" }
                val gaps = draws.zipWithNext { a, b -> b.at - a.at }
                check(gaps.all { it >= 5_000 }) { "duplicate_or_burst_draw" }
                record("minimumDurableDrawGapMs", gaps.min()); record("maximumDurableDrawGapMs", gaps.max())
                record("durableDrawEvents", draws.size); record("matchingResultsForEveryPlayer", true)
                record("durablePauseEvents", 1); record("durableResumeEvents", 1)
                record("uniqueTicketsVerified", 192); record("manualMarksPreservedForEveryPlayer", true)
                record("noDuplicateOrCatchUpBurst", true)
                healthy(); check(serviceRuntimeIdentity(root) == runtimeIdentity); success = true
            }
        } catch (e: Exception) {
            record("failedStage", stage); record("failureType", e.javaClass.simpleName)
            record("failureLocation", e.stackTrace.firstOrNull { it.className.startsWith("io.github.sbshrey.tambola") }?.let { "${it.fileName}:${it.lineNumber}" }.orEmpty())
            failures.get()?.let { record("nativeFailure", it) }
        } finally {
            withContext(NonCancellable) {
                scope.cancel(); proxy?.close(); clients.forEach { runCatching { it.close() } }
                if (withTimeoutOrNull(10_000) { scope.coroutineContext[Job]?.join(); true } != true) cleanup = false
                children.forEach { child -> child?.let { if (it.isAlive) { it.destroyForcibly(); if (!it.waitFor(10, TimeUnit.SECONDS)) cleanup = false } } }
                owned.toList().forEach { name ->
                    check(name in names && name.matches(Regex("tambola_load_(main|journal)_[a-f0-9]{16}")))
                    runCatching { control("DROP DATABASE $name WITH (FORCE)"); owned.remove(name) }.onFailure { cleanup = false }
                }
            }
            events["servicePids"] = JsonArray(childPids.map(::JsonPrimitive))
            events["connectionsPerClient"] = JsonArray(connections.map { JsonPrimitive(it.get()) })
            events["disconnectsPerClient"] = JsonArray(disconnects.map { JsonPrimitive(it.get()) })
            events["expectedTransportFailureTypes"] = JsonObject(transportFailures.toSortedMap().mapValues { JsonPrimitive(it.value.get()) })
            events["finalCallsPerClient"] = JsonArray(saved.map { JsonPrimitive(it.get()?.room?.round?.called?.size ?: 0) })
            record("executionIncludingCleanupMs", (System.nanoTime() - startedAt) / 1_000_000.0)
            record("backlogSnapshotsWithoutLiveAnnouncements", skippedSnapshots.get())
            record("completed", success); record("cleanupComplete", cleanup); persist()
            println("automatic[$runId]: completed=$success cleanup=$cleanup evidence=${directory.resolve("evidence.json")}")
        }
        return if (success && cleanup) 0 else 1
    }

    /** A loopback HTTP-only fixture that commits commands upstream then closes every successful response. */
    private class LostResponse(private val upstream: String) : AutoCloseable {
        val dropped = AtomicInteger()
        private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                try {
                    require(exchange.requestMethod == "POST" && exchange.requestURI.path.matches(Regex("/v1/rooms/[A-HJ-NP-Z2-9]{8}/commands")))
                    val bytes = exchange.requestBody.readNBytes(32_769); require(bytes.size <= 32_768)
                    val request = HttpRequest.newBuilder(URI(upstream + exchange.requestURI.path)).timeout(Duration.ofSeconds(10))
                        .header("Authorization", exchange.requestHeaders.getFirst("Authorization"))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build()
                    val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    if (response.statusCode() == 200) dropped.incrementAndGet()
                    else { exchange.responseHeaders.set("Content-Type", "application/json"); exchange.sendResponseHeaders(response.statusCode(), response.body().size.toLong()); exchange.responseBody.write(response.body()) }
                } finally { exchange.close() }
            }; start()
        }
        val base get() = "http://127.0.0.1:${server.address.port}"
        override fun close() { server.stop(0) }
    }
}
