package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.InetAddress
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
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.system.exitProcess

/** Opt-in real automatic games and rematches; default workload lasts over an hour. */
object AutomaticSoakLoad {
    private val json = Json { prettyPrint = true }
    private fun id() = UUID.randomUUID().toString()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private class RoundTrace(players: Int) {
        val receivedAt = Array(players) { AtomicLongArray(91) }
        val cards = Array(players) { AtomicReference<List<Ticket>>(emptyList()) }
    }
    private class Table(val api: HttpRoomApi, val credentials: List<GuestCredentials>, val initial: RoomView, base: String) {
        val saved = credentials.mapIndexed { index, guest -> AtomicReference(OnlineSaved(base, guest, "Soak player ${index + 1}")) }
        val rounds = ConcurrentHashMap<String, RoundTrace>()
    }

    @JvmStatic fun main(args: Array<String>) { exitProcess(runBlocking { run() }) }
    private suspend fun run(): Int {
        fun required(key: String) = requireNotNull(System.getenv(key)?.takeIf(String::isNotBlank)) { "$key is required" }
        fun setting(key: String, default: Int, range: IntRange) = (System.getenv(key)?.toInt() ?: default).also { require(it in range) }
        val url = required("TAMBOLA_TEST_DATABASE_URL")
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/tambola_test")))
        val user = required("TAMBOLA_TEST_DATABASE_USER")
        val password = required("TAMBOLA_TEST_DATABASE_PASSWORD")
        val roomCount = setting("TAMBOLA_SOAK_ROOMS", 10, 1..10)
        val playerCount = setting("TAMBOLA_SOAK_PLAYERS", 32, 2..32)
        val roundCount = setting("TAMBOLA_SOAK_ROUNDS", 9, 1..9)
        // Short cancelled games exercise the fixture itself, never count as full-game acceptance.
        val calls = setting("TAMBOLA_SOAK_DRAWS", 90, 2..90)
        val root = Path.of("").toAbsolutePath()
        val runId = id().replace("-", "").take(16)
        val directory = root.resolve(".test-workspace/soak-$runId")
        Files.createDirectories(directory)
        val runtimeIdentity = serviceRuntimeIdentity(root)
        val clientHash = hash(Files.readAllBytes(root.resolve("client/build/libs/client.jar")))
        val names = listOf("tambola_load_main_$runId", "tambola_load_journal_$runId")
        val owned = mutableSetOf<String>()
        val props = Properties().apply { setProperty("user", user); setProperty("password", password); setProperty("connectTimeout", "5"); setProperty("socketTimeout", "30") }
        fun dbUrl(name: String) = url.removeSuffix("tambola_test") + name
        fun control(sql: String) = DriverManager.getConnection(url, props).use { it.createStatement().use { s -> s.execute(sql) } }
        fun <T> primary(block: (java.sql.Connection) -> T) = DriverManager.getConnection(dbUrl(names[0]), props).use(block)
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val bin = Path.of(System.getProperty("java.home"), "bin")
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val base = "http://127.0.0.1:$port"
        val metricsToken = secret()
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val failure = AtomicReference<String?>(null)
        val apis = mutableListOf<HttpRoomApi>()
        val tables = mutableListOf<Table>()
        val evidence = linkedMapOf<String, JsonElement>()
        val rounds = mutableListOf<JsonElement>()
        val observations = mutableListOf<JsonElement>()
        val heaps = mutableListOf<Int>()
        val latency = mutableListOf<Double>()
        var child: Process? = null
        var stage = "initialize"
        var success = false
        var cleanup = true
        var playStarted = 0L
        var playSeconds = 0.0
        val wallStart = System.currentTimeMillis()
        val monoStart = System.nanoTime()
        fun record(key: String, value: Any?) { evidence[key] = when (value) {
            is Boolean -> JsonPrimitive(value); is Number -> JsonPrimitive(value); null -> JsonNull; else -> JsonPrimitive(value.toString())
        } }
        fun persist() {
            evidence["rounds"] = JsonArray(rounds.toList()); evidence["observations"] = JsonArray(observations.toList())
            Files.writeString(directory.resolve("evidence.json"), json.encodeToString(JsonObject(evidence)) + "\n")
        }
        fun checkpoint(value: String) { stage = value; record("stage", stage); persist(); println("soak[$runId]: $value") }
        fun healthy() {
            check(failure.get() == null) { "native_stream_failure" }
            check(child?.isAlive == true) { "server_exited" }
            val drift = kotlin.math.abs((System.currentTimeMillis() - wallStart) - (System.nanoTime() - monoStart) / 1_000_000)
            check(drift <= 100) { "wall_clock_changed" }
        }
        suspend fun awaitCondition(timeout: Long = 30_000, condition: () -> Boolean) {
            withTimeout(timeout) { while (!condition()) { healthy(); delay(25) } }; healthy()
        }
        suspend fun command(table: Table, guest: GuestCredentials, action: RoomAction): RoomView {
            repeat(10) {
                val before = table.api.read(guest.token, table.initial.code).snapshot
                try { return table.api.command(guest.token, table.initial.code, CommandRequest(id(), before.revision, action)).snapshot }
                catch (e: RoomApiFailure) { if (e.code != "stale_revision") throw e }
            }; error("repeated_stale_revision")
        }
        fun sampleMetrics(validate: Boolean = true) {
            val response = http.send(HttpRequest.newBuilder(URI("$base/internal/metrics")).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer $metricsToken").GET().build(), HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200)
            val selected = response.body().lineSequence().mapNotNull { line ->
                val pieces = line.split(' ')
                if (pieces.size == 2 && pieces[0] in setOf("tambola_jvm_heap_used_bytes", "tambola_jvm_threads", "tambola_websocket_active",
                        "tambola_websocket_opened_total", "tambola_websocket_failures_total", "tambola_worker_ready", "tambola_process_cpu_seconds_total"))
                    pieces[0] to pieces[1].toDouble() else null
            }.toMap()
            observations += buildJsonObject {
                put("elapsedSeconds", (System.nanoTime() - monoStart) / 1e9)
                selected.forEach { (name, value) -> put(name, value) }
            }
            if (validate) {
                check(selected.size == 7 && selected["tambola_worker_ready"] == 1.0)
                check(selected["tambola_websocket_active"] == (roomCount * playerCount).toDouble())
                check(selected["tambola_websocket_opened_total"] == (roomCount * playerCount).toDouble())
                check(selected["tambola_websocket_failures_total"] == 0.0)
            }
        }
        fun collectHeap(round: Int) {
            healthy()
            // This diagnostic attaches only to the exact process created by this fixture.
            val output = directory.resolve("collection-$round.txt")
            val diagnostic = ProcessBuilder(bin.resolve("jcmd$suffix").toString(), child!!.pid().toString(), "GC.run")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start()
            if (!diagnostic.waitFor(20, TimeUnit.SECONDS)) { diagnostic.destroyForcibly(); diagnostic.waitFor(5, TimeUnit.SECONDS); error("collection_timeout") }
            check(diagnostic.exitValue() == 0 && Files.readString(output).contains("Command executed successfully"))
            val entries = Regex("Pause Full \\(Diagnostic Command\\).*?(\\d+)M->(\\d+)M\\((\\d+)M\\)")
                .findAll(Files.readString(directory.resolve("gc.log"))).toList()
            check(entries.size == round)
            heaps += entries.last().groupValues[2].toInt()
        }
        try {
            withTimeout(85 * 60_000L) {
                record("runId", runId); record("rooms", roomCount); record("playersPerRoom", playerCount)
                record("ticketsPerPlayer", 6); record("configuredRounds", roundCount); record("configuredDraws", calls)
                record("automaticIntervalMs", 5_000); record("serverHeapMaximumMiB", 512); record("serverActiveProcessorCount", 4)
                record("hostLogicalProcessors", Runtime.getRuntime().availableProcessors()); record("javaVersion", System.getProperty("java.version"))
                record("scope", "One real Java service, two isolated loopback PostgreSQL databases, native HTTP/WebSocket clients with OnlineSaved acceptance; no Android, hosted, TLS or physical-network evidence")
                record("latencyDefinition", "Same-host wall-clock time from durable draw event timestamp (before commit) to native snapshot receipt; drift checked against monotonic time within 100ms. Not an exact commit timestamp.")
                record("heapDefinition", "Server G1 heap immediately after explicit full collections in the same empty-lobby phase between rounds; whole-MiB GC log values. Diagnostic collections alter normal collection behavior; not Android or leak-proof evidence.")
                record("fixtureSourceSha256", hash(Files.readAllBytes(root.resolve("server/src/test/kotlin/io/github/sbshrey/tambola/server/AutomaticSoakLoad.kt"))))
                val fixtureHash = MessageDigest.getInstance("SHA-256")
                val classFolder = Path.of(AutomaticSoakLoad::class.java.protectionDomain.codeSource.location.toURI()).resolve("io/github/sbshrey/tambola/server")
                Files.list(classFolder).use { files -> files.filter { it.fileName.toString().startsWith("AutomaticSoakLoad") && it.toString().endsWith(".class") }.sorted().forEach {
                    fixtureHash.update(it.fileName.toString().toByteArray()); fixtureHash.update(Files.readAllBytes(it))
                } }
                record("fixtureClassesSha256", fixtureHash.digest().joinToString("") { "%02x".format(it) })
                runtimeIdentity.forEach { (key, value) -> record(key, value) }
                record("clientJarSha256", clientHash)
                names.forEach { check(it.matches(Regex("tambola_load_(main|journal)_[a-f0-9]{16}"))); control("CREATE DATABASE $it TEMPLATE template0"); owned += it }
                checkpoint("start-journal-enabled-service")
                val builder = ProcessBuilder(bin.resolve("java$suffix").toString(), "-Xms128m", "-Xmx512m", "-XX:ActiveProcessorCount=4",
                    "-Xlog:gc:file=gc.log:time,uptime:filecount=0", "-cp", root.resolve("server/build/install/server/lib").toString() + "/*",
                    "io.github.sbshrey.tambola.server.ServerKt").directory(directory.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
                builder.environment().putAll(mapOf("PORT" to "$port", "TAMBOLA_BIND_HOST" to "127.0.0.1", "TAMBOLA_LOCAL_DEVELOPMENT" to "true",
                    "TAMBOLA_DATABASE_URL" to dbUrl(names[0]), "TAMBOLA_DATABASE_USER" to user, "TAMBOLA_DATABASE_PASSWORD" to password,
                    "TAMBOLA_DELETION_DATABASE_URL" to dbUrl(names[1]), "TAMBOLA_DELETION_DATABASE_USER" to user,
                    "TAMBOLA_DELETION_DATABASE_PASSWORD" to password, "TAMBOLA_METRICS_TOKEN" to metricsToken))
                child = builder.start(); record("serverPid", child!!.pid())
                awaitCondition { runCatching { http.send(HttpRequest.newBuilder(URI("$base/health/ready")).timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200 }.getOrDefault(false) }
                val groups = List(roomCount) { List(playerCount) { GuestCredentials(id(), secret(), System.currentTimeMillis() + SESSION_LIFETIME) } }
                primary { connection ->
                    connection.autoCommit = false
                    connection.prepareStatement("INSERT INTO guests(id, name, avatar, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)").use { statement ->
                        groups.forEachIndexed { table, players -> players.forEachIndexed { member, guest ->
                            listOf(guest.playerId, "Soak player ${table + 1}-${member + 1}", member % AVATAR_COUNT, digest(guest.token), guest.expiresAt)
                                .forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                            statement.addBatch()
                        } }; statement.executeBatch()
                    }; connection.commit()
                }
                checkpoint("connect-${roomCount * playerCount}-persistent-clients")
                repeat(roomCount) { apis += HttpRoomApi(base, true) }
                tables += coroutineScope { groups.mapIndexed { index, guests -> async {
                    val api = apis[index]
                    val initial = api.create(guests.first().token, CreateRoomRequest(id(), RoomOptions(
                        game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, playAllNumbers = true), automaticCalling = true, intervalSeconds = 5))).snapshot
                    guests.drop(1).forEach { api.join(it.token, initial.code) }
                    val table = Table(api, guests, initial, base)
                    guests.forEachIndexed { member, guest -> scope.launch {
                        try {
                            val seenRounds = mutableSetOf<String>()
                            val seenDeals = mutableSetOf<List<List<Int>>>()
                            api.events(guest.token, initial.code, null).collect { update ->
                                val received = System.currentTimeMillis()
                                val prior = table.saved[member].get()
                                var next = prior.accept(update, live = true).saved
                                val game = next.room?.round
                                if (game == null) check(next.marks.isEmpty())
                                else {
                                    val changed = prior.room?.round?.id != game.id
                                    if (changed) {
                                        check(seenRounds.add(game.id) && next.marks.isEmpty())
                                        // Ticket IDs are player/ordinal keys scoped by round, not global IDs.
                                        check(seenDeals.add(game.ownTickets.map { it.cells }))
                                    } else if (prior.marks.isNotEmpty()) check(next.marks == prior.marks)
                                    val trace = table.rounds.computeIfAbsent(game.id) { RoundTrace(playerCount) }
                                    val cards = trace.cards[member].get()
                                    check(cards.isEmpty() || cards == game.ownTickets)
                                    trace.cards[member].set(game.ownTickets)
                                    val before = if (changed) 0 else prior.room!!.round!!.called.size
                                    for (call in before + 1..game.called.size) check(trace.receivedAt[member].compareAndSet(call, 0, received))
                                    if (next.marks.isEmpty()) game.ownTickets.firstOrNull { card -> card.numbers.any { it in game.called } }?.let { card ->
                                        next = next.mark(card.id, card.numbers.first { it in game.called })
                                    }
                                }
                                table.saved[member].set(next)
                            }
                            if (currentCoroutineContext().isActive) failure.compareAndSet(null, "unexpected_close")
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { failure.compareAndSet(null, "table_${index + 1}_member_${member + 1}_" + e.javaClass.simpleName + when (e) {
                            is RoomApiFailure -> "_${e.status}_${e.code}"
                            is RoomStreamClosed -> "_close_${e.closeCode}"
                            else -> ""
                        } + ":" + e.stackTrace.firstOrNull { it.className.startsWith("io.github.sbshrey.tambola") }?.lineNumber) }
                    } }
                    table
                } }.awaitAll() }
                awaitCondition { tables.all { table -> table.saved.all { it.get().room?.phase == RoomPhase.LOBBY } } }
                sampleMetrics()
                playStarted = System.nanoTime()
                repeat(roundCount) { roundIndex ->
                    val number = roundIndex + 1
                    checkpoint("round-$number-ready-and-start")
                    val ids = coroutineScope { tables.map { table -> async {
                        table.credentials.forEach { command(table, it, RoomAction.Ready(true)) }
                        command(table, table.credentials.first(), RoomAction.Start).round!!.id
                    } }.awaitAll() }
                    awaitCondition { tables.indices.all { i -> tables[i].saved.all { it.get().room?.round?.id == ids[i] } } }
                    tables.forEachIndexed { i, table -> check(table.rounds[ids[i]]!!.cards.flatMap { it.get() }.map { it.cells }.distinct().size == playerCount * 6) }
                    var lastProgress = 0
                    var lastMetrics = System.nanoTime()
                    withTimeout(12 * 60_000L) {
                        while (tables.any { table -> table.saved.any { (it.get().room?.round?.called?.size ?: 0) < calls } }) {
                            healthy(); delay(100)
                            val minimum = tables.minOf { table -> table.saved.minOf { it.get().room?.round?.called?.size ?: 0 } }
                            if (minimum / 10 > lastProgress) { lastProgress = minimum / 10; checkpoint("round-$number-delivered-$minimum-of-$calls") }
                            if (System.nanoTime() - lastMetrics >= 30_000_000_000) { sampleMetrics(); lastMetrics = System.nanoTime() }
                        }
                    }
                    if (calls < 90) coroutineScope { tables.map { table -> async { command(table, table.credentials.first(), RoomAction.End) } }.awaitAll() }
                    awaitCondition { tables.all { table -> table.saved.all { it.get().room?.phase == RoomPhase.FINISHED } } }
                    val roundLatency = mutableListOf<Double>()
                    val gaps = mutableListOf<Long>()
                    tables.forEachIndexed { i, table ->
                        val final = table.api.read(table.credentials.first().token, table.initial.code).snapshot
                        check(final.round!!.called.size == calls && final.round!!.id == ids[i])
                        if (calls == 90) check(final.round!!.status == RoundStatus.COMPLETED && final.round!!.called.sorted() == (1..90).toList())
                        table.credentials.forEachIndexed { member, guest ->
                            val view = table.api.read(guest.token, table.initial.code).snapshot
                            view.validateFor(guest.playerId)
                            check(view.round!!.called == final.round!!.called && view.round!!.scores == final.round!!.scores && view.round!!.awards == final.round!!.awards)
                            check(view.round!!.drawCommitment == final.round!!.drawCommitment)
                            val saved = table.saved[member].get()
                            check(saved.history.size == number && saved.history.map { it.round!!.id }.distinct().size == number)
                            if (calls == 90) check(saved.marks.isNotEmpty())
                        }
                        val events = primary { c -> c.query("SELECT payload FROM room_events WHERE room_id = ? ORDER BY revision", table.initial.roomId) { WireJson.decodeFromString<RoomEvent>(it.getString(1)) } }
                            .filter { it.type == "drawn" && it.roundId == ids[i] }
                        check(events.size == calls)
                        gaps += events.zipWithNext { a, b -> b.at - a.at }
                        val trace = table.rounds[ids[i]]!!
                        trace.receivedAt.forEach { received -> events.forEachIndexed { call, event ->
                            val at = received.get(call + 1); check(at >= event.at); roundLatency += (at - event.at).toDouble()
                        } }
                    }
                    check(gaps.all { it >= 5_000 })
                    latency += roundLatency
                    // A common empty-lobby state makes full-GC checkpoints comparable across rematches.
                    coroutineScope { tables.map { table -> async { command(table, table.credentials.first(), RoomAction.Rematch) } }.awaitAll() }
                    awaitCondition { tables.all { table -> table.saved.all { it.get().room?.phase == RoomPhase.LOBBY && it.get().marks.isEmpty() } } }
                    collectHeap(number); sampleMetrics()
                    rounds += buildJsonObject {
                        put("round", number); put("matchingResultsForEveryPlayer", true); put("uniqueCardsPerRoom", playerCount * 6)
                        put("minimumDurableDrawGapMs", gaps.min()); put("maximumDurableDrawGapMs", gaps.max())
                        put("deliveryMs", statistics(roundLatency)); put("serverPostFullGcHeapMiB", heaps.last())
                        put("allHistoriesContainExactlyThisManyRounds", number); put("allLobbyMarksCleared", true)
                    }
                    checkpoint("round-$number-verified-p95-${percentile(roundLatency, .95)}ms-heap-${heaps.last()}MiB")
                }
                playSeconds = (System.nanoTime() - playStarted) / 1e9
                check(tables.all { it.rounds.size == roundCount })
                tables.forEach { table -> check(primary { c -> c.query("SELECT COUNT(*) FROM finished_rounds WHERE room_id = ?", table.initial.roomId) { it.getInt(1) }.single() } == roundCount) }
                record("durableArchivesPerRoom", roundCount)
                check(serviceRuntimeIdentity(root) == runtimeIdentity)
                check(hash(Files.readAllBytes(root.resolve("client/build/libs/client.jar"))) == clientHash)
                healthy(); success = true
            }
        } catch (e: Exception) {
            if (playStarted > 0) record("playElapsedUntilFailureSeconds", (System.nanoTime() - playStarted) / 1e9)
            record("failedStage", stage); record("failureType", e.javaClass.simpleName)
            record("failureLocation", e.stackTrace.firstOrNull { it.className.startsWith("io.github.sbshrey.tambola") }?.let { "${it.fileName}:${it.lineNumber}" })
            record("nativeFailure", failure.get())
            if (child?.isAlive == true) runCatching { sampleMetrics(validate = false) }
                .onFailure { record("failureMetricsUnavailable", it.javaClass.simpleName) }
        } finally {
            withContext(NonCancellable) {
                scope.cancel(); apis.forEach { runCatching { it.close() } }
                if (withTimeoutOrNull(10_000) { scope.coroutineContext[Job]?.join(); true } != true) cleanup = false
                child?.let { if (it.isAlive) { it.destroyForcibly(); if (!it.waitFor(10, TimeUnit.SECONDS)) cleanup = false } }
                owned.toList().forEach { name ->
                    check(name in names && name.matches(Regex("tambola_load_(main|journal)_[a-f0-9]{16}")))
                    runCatching { control("DROP DATABASE $name WITH (FORCE)"); owned.remove(name) }.onFailure { cleanup = false }
                }
            }
            record("completed", success); record("cleanupComplete", cleanup); record("gameplaySeconds", playSeconds)
            record("executionIncludingCleanupSeconds", (System.nanoTime() - monoStart) / 1e9)
            record("completedRounds", rounds.size); record("expectedDeliverySamples", roomCount * playerCount * calls * roundCount)
            evidence["deliveryMs"] = statistics(latency)
            evidence["postFullGcHeapMiB"] = JsonArray(heaps.map(::JsonPrimitive))
            val stable = heaps.size >= 3 && heaps.drop(1).max() - heaps.drop(1).min() <= 16
            record("postWarmupRetainedHeapRangeWithin16MiB", stable)
            record("heapCriterion", "Diagnostic guard: checkpoints after round one must span at most 16MiB. Bounded warmup is allowed; this finite test cannot prove absence of leaks.")
            record("fullWorkloadForAtLeast60Minutes", success && roomCount == 10 && playerCount == 32 && calls == 90 && playSeconds >= 3_600)
            record("deliveryTargetUnder1000MsP95", success && latency.size == roomCount * playerCount * calls * roundCount && percentile(latency, .95) < 1_000)
            persist()
            println("soak[$runId]: completed=$success cleanup=$cleanup seconds=$playSeconds p95=${percentile(latency, .95)}ms evidence=${directory.resolve("evidence.json")}")
        }
        return if (success && cleanup && latency.size == roomCount * playerCount * calls * roundCount && percentile(latency, .95) < 1_000 &&
            (roundCount < 3 || heaps.drop(1).max() - heaps.drop(1).min() <= 16)) 0 else 1
    }

    private fun percentile(values: List<Double>, p: Double): Double = if (values.isEmpty()) 0.0 else values.sorted()[(ceil(values.size * p).toInt() - 1).coerceIn(values.indices)]
    private fun statistics(values: List<Double>) = buildJsonObject {
        put("count", values.size); put("p50", percentile(values, .50)); put("p95", percentile(values, .95)); put("p99", percentile(values, .99)); put("max", values.maxOrNull() ?: 0.0)
    }
}
