package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.ServerSocket
import java.net.InetAddress
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.system.exitProcess

/** Opt-in synthetic gameplay load. Owns only fresh loopback databases and its own server process. */
object ServiceLoad {
    private val evidenceJson = Json { prettyPrint = true }
    @JvmStatic fun main(args: Array<String>) { exitProcess(runBlocking { run() }) }

    private data class Table(val api: HttpRoomApi, val players: List<GuestCredentials>, val code: String) {
        val dispatched = AtomicLongArray(91)
        val acknowledged = AtomicLongArray(91)
        val received = Array(players.size) { AtomicLongArray(91) }
        val delivered = Array(players.size) { AtomicInteger(0) }
        val first = Array(players.size) { AtomicInteger(0) }
        val deliveryMs = ConcurrentLinkedQueue<Double>()
        val commandMs = ConcurrentLinkedQueue<Double>()
        val observedTickets = Array(players.size) { AtomicReference<List<Ticket>>(emptyList()) }
    }

    private suspend fun run(): Int {
        fun required(key: String) = requireNotNull(System.getenv(key)?.takeIf(String::isNotBlank)) { "$key is required" }
        fun setting(key: String, default: Int, range: IntRange) = (System.getenv(key)?.toInt() ?: default).also { require(it in range) }
        val url = required("TAMBOLA_TEST_DATABASE_URL")
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/tambola_test")))
        val user = required("TAMBOLA_TEST_DATABASE_USER")
        val password = required("TAMBOLA_TEST_DATABASE_PASSWORD")
        val rooms = setting("TAMBOLA_LOAD_ROOMS", 10, 1..10)
        val playersPerRoom = setting("TAMBOLA_LOAD_PLAYERS", 32, 2..32)
        val tickets = setting("TAMBOLA_LOAD_TICKETS", 6, 1..6)
        val draws = setting("TAMBOLA_LOAD_DRAWS", 90, 1..90)
        val interval = setting("TAMBOLA_LOAD_INTERVAL_MS", 5_000, 2_000..10_000)
        val runId = UUID.randomUUID().toString().replace("-", "").take(16)
        val root = Path.of("").toAbsolutePath()
        val runtimeIdentity = serviceRuntimeIdentity(root)
        Files.createDirectories(root.resolve(".test-workspace"))
        val directory = root.resolve(".test-workspace/load-$runId")
        Files.createDirectory(directory)
        val names = listOf("tambola_load_main_$runId", "tambola_load_journal_$runId")
        val owned = mutableSetOf<String>()
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val base = "http://127.0.0.1:$port"
        var child: Process? = null
        val clients = mutableListOf<HttpRoomApi>()
        // Ktor 3.3.3's handshake nonce bridge blocks its caller. Many simulated clients
        // must not occupy the Default workers needed by nonce production itself.
        val streams = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val streamFailure = AtomicReference<String?>(null)
        val tables = mutableListOf<Table>()
        var stage = "initialize"
        var success = false
        var cleanup = true
        val evidence = linkedMapOf<String, JsonElement>()
        fun record(key: String, value: Any?) { evidence[key] = when (value) {
            is Boolean -> JsonPrimitive(value); is Number -> JsonPrimitive(value); null -> JsonNull; else -> JsonPrimitive(value.toString())
        } }
        fun checkpoint(value: String) { stage = value; println("load[$runId]: $value") }
        fun control(sql: String) = DriverManager.getConnection(url, user, password).use { it.createStatement().use { statement -> statement.execute(sql) } }
        fun checked(name: String) { require(name in names && name.matches(Regex("tambola_load_(main|journal)_[a-f0-9]{16}"))) }
        fun drop(name: String) { checked(name); check(name in owned); control("DROP DATABASE $name WITH (FORCE)"); owned.remove(name) }
        fun databaseUrl(name: String) = url.removeSuffix("tambola_test") + name
        fun checkStreams() { check(streamFailure.get() == null) { "stream_${streamFailure.get()}" }; check(child?.isAlive == true) { "server_exited" } }
        suspend fun awaitCondition(timeout: Long, condition: () -> Boolean) {
            withTimeout(timeout) { while (!condition()) { checkStreams(); delay(25) } }
            checkStreams()
        }
        suspend fun command(table: Table, actor: GuestCredentials, action: RoomAction): RoomView {
            // A presence transition can race a snapshot. Retry a rejected stale request only, never an uncertain mutation.
            repeat(5) {
                val before = table.api.read(actor.token, table.code).snapshot
                try { return table.api.command(actor.token, table.code, CommandRequest(UUID.randomUUID().toString(), before.revision, action)).snapshot }
                catch (failure: RoomApiFailure) { if (failure.code != "stale_revision") throw failure }
            }
            error("repeated_stale_revision")
        }
        try {
            record("runId", runId); record("rooms", rooms); record("playersPerRoom", playersPerRoom); record("ticketsPerPlayer", tickets)
            record("configuredDraws", draws); record("minimumCallIntervalMs", interval)
            record("scope", "Real isolated Java service and native HTTP/WebSocket clients on one local host; profiles pre-seeded, gameplay uses public APIs; no hosted/TLS/mobile-network acceptance")
            record("serverHeapMaximumMiB", 512); record("serverActiveProcessorCount", 4)
            record("hostLogicalProcessors", Runtime.getRuntime().availableProcessors())
            record("javaVersion", System.getProperty("java.version")); record("os", System.getProperty("os.name"))
            val fixtureHash = MessageDigest.getInstance("SHA-256")
            val classFolder = Path.of(ServiceLoad::class.java.protectionDomain.codeSource.location.toURI()).resolve("io/github/sbshrey/tambola/server")
            Files.list(classFolder).use { files -> files.filter { it.fileName.toString().startsWith("ServiceLoad") && it.toString().endsWith(".class") }.sorted().forEach {
                fixtureHash.update(it.fileName.toString().toByteArray()); fixtureHash.update(Files.readAllBytes(it))
            } }
            record("fixtureClassesSha256", fixtureHash.digest().joinToString("") { "%02x".format(it) })
            runtimeIdentity.forEach { (key, value) -> record(key, value) }
            DriverManager.getConnection(url, user, password).use { connection ->
                check(connection.query("SELECT rolsuper OR rolcreatedb FROM pg_roles WHERE rolname = current_user") { it.getBoolean(1) }.single())
            }
            names.forEach { checked(it); control("CREATE DATABASE $it TEMPLATE template0"); owned += it }
            checkpoint("start-journal-enabled-service")
            val builder = ProcessBuilder(java.toString(), "-Xms128m", "-Xmx512m", "-XX:ActiveProcessorCount=4", "-Xlog:gc:file=gc.log:time,uptime",
                "-cp", root.resolve("server/build/install/server/lib").toString() + "/*", "io.github.sbshrey.tambola.server.ServerKt")
                .directory(directory.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
            builder.environment().putAll(mapOf("PORT" to port.toString(), "TAMBOLA_BIND_HOST" to "127.0.0.1", "TAMBOLA_LOCAL_DEVELOPMENT" to "true",
                "TAMBOLA_DATABASE_URL" to databaseUrl(names[0]), "TAMBOLA_DATABASE_USER" to user, "TAMBOLA_DATABASE_PASSWORD" to password,
                "TAMBOLA_DELETION_DATABASE_URL" to databaseUrl(names[1]), "TAMBOLA_DELETION_DATABASE_USER" to user, "TAMBOLA_DELETION_DATABASE_PASSWORD" to password))
            child = builder.start()
            record("serverPid", child.pid())
            awaitCondition(30_000) {
                runCatching { http.send(HttpRequest.newBuilder(URI("$base/health/ready")).timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200 }.getOrDefault(false)
            }
            checkpoint("seed-fictional-profiles")
            val players = (0 until rooms).map { (0 until playersPerRoom).map {
                GuestCredentials(UUID.randomUUID().toString(), secret(), System.currentTimeMillis() + SESSION_LIFETIME)
            } }
            DriverManager.getConnection(databaseUrl(names[0]), user, password).use { connection ->
                connection.autoCommit = false
                connection.prepareStatement("INSERT INTO guests(id, name, avatar, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)").use { statement ->
                    players.forEachIndexed { room, group -> group.forEachIndexed { member, player ->
                        listOf(player.playerId, "Load player ${room + 1}-${member + 1}", member % AVATAR_COUNT, digest(player.token), player.expiresAt)
                            .forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                        statement.addBatch()
                    } }
                    statement.executeBatch()
                }
                connection.commit()
            }
            checkpoint("create-rooms-and-connect-clients")
            // One client transport per table bounds shared-client dispatcher contention in the load generator.
            val apis = List(rooms) { HttpRoomApi(base, true).also(clients::add) }
            tables += coroutineScope { players.mapIndexed { index, group -> async {
                val api = apis[index]
                val room = api.create(group.first().token, CreateRoomRequest(UUID.randomUUID().toString(), RoomOptions(
                    game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = tickets, playAllNumbers = true), automaticCalling = false))).snapshot
                group.drop(1).forEach { api.join(it.token, room.code) }
                val table = Table(api, group, room.code)
                group.forEachIndexed { member, player -> streams.launch {
                    try {
                        var previous = emptyList<Int>()
                        api.events(player.token, room.code, null).collect { update ->
                            val received = System.nanoTime()
                            val snapshot = update.snapshot
                            snapshot.validateFor(player.playerId)
                            table.first[member].set(1)
                            snapshot.round?.let { round ->
                                check(round.ownTickets.size == tickets && round.ownTickets.all { it.playerId == player.playerId })
                                check(round.called.take(previous.size) == previous)
                                check(round.called.size in previous.size..draws)
                                if (round.status != RoundStatus.COMPLETED) check(round.revealedOrder == null && round.revealedNonce == null)
                                table.observedTickets[member].set(round.ownTickets)
                                for (call in previous.size + 1..round.called.size) {
                                    val dispatch = table.dispatched.get(call)
                                    check(dispatch > 0) { "draw_without_dispatch" }
                                    table.received[member].set(call, received)
                                    table.deliveryMs.add((received - dispatch) / 1_000_000.0)
                                }
                                previous = round.called
                                table.delivered[member].set(round.called.size)
                            }
                        }
                        if (currentCoroutineContext().isActive) streamFailure.compareAndSet(null, "unexpected_close")
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) { streamFailure.compareAndSet(null, if (error is RoomApiFailure) "http_${error.status}_${error.code}" else error.javaClass.simpleName) }
                } }
                awaitCondition(30_000) { table.first.all { it.get() == 1 } }
                group.forEach { command(table, it, RoomAction.Ready(true)) }
                command(table, group.first(), RoomAction.Start)
                table
            } }.awaitAll() }
            awaitCondition(30_000) { tables.all { table -> table.observedTickets.all { it.get().size == tickets } } }
            tables.forEach { table -> check(table.observedTickets.flatMap { it.get() }.map { it.cells }.distinct().size == playersPerRoom * tickets) }
            record("liveConnections", rooms * playersPerRoom); record("uniqueTicketsVerified", rooms * playersPerRoom * tickets)
            checkpoint("play-$draws-calls")
            val started = System.nanoTime()
            val cpuStart = child.info().totalCpuDuration().orElse(Duration.ZERO).toMillis()
            for (call in 1..draws) {
                val dispatched = System.nanoTime()
                coroutineScope { tables.map { table -> async {
                    table.dispatched.set(call, System.nanoTime())
                    val round = command(table, table.players.first(), RoomAction.Draw).round!!
                    table.acknowledged.set(call, System.nanoTime())
                    table.commandMs.add((System.nanoTime() - table.dispatched.get(call)) / 1_000_000.0)
                    check(round.called.size == call)
                } }.awaitAll() }
                awaitCondition(20_000) { tables.all { table -> table.delivered.all { it.get() == call } } }
                if (call % 10 == 0 || call == draws) checkpoint("delivered-$call-of-$draws-to-${rooms * playersPerRoom}-clients")
                if (call < draws) delay((interval - (System.nanoTime() - dispatched) / 1_000_000).coerceAtLeast(0))
            }
            record("gameplaySeconds", (System.nanoTime() - started) / 1_000_000_000.0)
            record("serverCpuSecondsDuringPlay", (child.info().totalCpuDuration().orElse(Duration.ZERO).toMillis() - cpuStart) / 1000.0)
            checkpoint("verify-results-and-private-cards")
            coroutineScope { tables.map { table -> async {
                val final = table.api.read(table.players.first().token, table.code).snapshot
                check(final.round!!.called.distinct().size == draws)
                if (draws == 90) { check(final.phase == RoomPhase.FINISHED && final.round!!.status == RoundStatus.COMPLETED); check(final.round!!.called.sorted() == (1..90).toList()) }
                table.players.forEach { player ->
                    val snapshot = table.api.read(player.token, table.code).snapshot
                    snapshot.validateFor(player.playerId)
                    check(snapshot.round!!.called == final.round!!.called && snapshot.round!!.scores == final.round!!.scores && snapshot.round!!.awards == final.round!!.awards)
                    check(snapshot.round!!.drawCommitment == final.round!!.drawCommitment)
                }
            } }.awaitAll() }
            checkStreams()
            record("completedDrawsPerRoom", draws); record("matchingResultsForEveryPlayer", true)
            check(serviceRuntimeIdentity(root) == runtimeIdentity)
            success = true
        } catch (error: Exception) {
            record("failedStage", stage)
            record("failureType", if (error is RoomApiFailure) "http_${error.status}_${error.code}" else error.javaClass.simpleName)
            record("failureLocation", error.stackTrace.firstOrNull { it.className.startsWith("io.github.sbshrey.tambola") }?.let { "${it.fileName}:${it.lineNumber}" })
            record("streamFailure", streamFailure.get())
        } finally {
            withContext(NonCancellable) {
                streams.cancel()
                clients.forEach { runCatching { it.close() } }
                if (withTimeoutOrNull(10_000) { streams.coroutineContext[Job]?.join(); true } != true) cleanup = false
                child?.let { if (it.isAlive) { it.destroyForcibly(); if (!it.waitFor(10, TimeUnit.SECONDS)) cleanup = false } }
                owned.toList().forEach { runCatching { drop(it) }.onFailure { cleanup = false } }
            }
            record("completed", success); record("cleanupComplete", cleanup)
            evidence["dispatchToSnapshotMs"] = statistics(tables.flatMap { it.deliveryMs })
            evidence["commandRoundTripIncludingReadMs"] = statistics(tables.flatMap { it.commandMs })
            evidence["acknowledgementToSnapshotMs"] = statistics(tables.flatMap { table -> table.received.flatMap { received ->
                (1..draws).mapNotNull { call ->
                    val at = received.get(call); val ack = table.acknowledged.get(call)
                    if (at > 0 && ack > 0) ((at - ack) / 1_000_000.0).coerceAtLeast(0.0) else null
                }
            } })
            evidence["tables"] = JsonArray(tables.mapIndexed { index, table -> buildJsonObject {
                put("table", index + 1); put("minimumDeliveredCalls", table.delivered.minOf { it.get() }); put("dispatchToSnapshotMs", statistics(table.deliveryMs.toList()))
            } })
            val samples = tables.flatMap { it.deliveryMs }
            record("expectedDeliverySamples", rooms * playersPerRoom * draws)
            record("deliveryTargetUnder1000MsP95", success && samples.size == rooms * playersPerRoom * draws && percentile(samples, .95) < 1_000)
            record("latencyDefinition", "Upper bound from command dispatch (before HTTP revision read) to native-client snapshot receipt; includes database commit and polling. Same-host monotonic clock. Each delivered call is sampled per player.")
            record("acknowledgementLatencyDefinition", "Lower bound from successful command response (after commit) to snapshot receipt, clamped at zero when a stream beats the HTTP response. A p95 above 1000ms proves the local commit-to-delivery target was missed.")
            val gc = directory.resolve("gc.log")
            if (Files.exists(gc)) {
                val text = Files.readString(gc)
                val heaps = Regex("(\\d+)M->(\\d+)M\\((\\d+)M\\)").findAll(text).map { it.groupValues[2].toInt() }.toList()
                evidence["postGcHeapMiB"] = JsonArray(heaps.map(::JsonPrimitive))
                record("gcObservation", "Post-collection heap samples from the owned server GC log; no forced collection. Short run, not a 60-minute memory soak.")
            }
            Files.writeString(directory.resolve("evidence.json"), evidenceJson.encodeToString(JsonObject(evidence)) + "\n")
            println("load[$runId]: completed=$success cleanup=$cleanup evidence=${directory.resolve("evidence.json")}")
            println("load[$runId]: delivery p95=${percentile(samples, .95)} ms samples=${samples.size}")
        }
        val samples = tables.flatMap { it.deliveryMs }
        return if (success && cleanup && samples.size == rooms * playersPerRoom * draws && percentile(samples, .95) < 1_000) 0 else 1
    }

    private fun percentile(values: List<Double>, p: Double): Double = if (values.isEmpty()) 0.0 else values.sorted()[(ceil(values.size * p).toInt() - 1).coerceIn(values.indices)]
    private fun statistics(values: List<Double>): JsonObject = buildJsonObject {
        put("count", values.size); put("p50", percentile(values, .50)); put("p95", percentile(values, .95)); put("p99", percentile(values, .99)); put("max", values.maxOrNull() ?: 0.0)
    }
}
