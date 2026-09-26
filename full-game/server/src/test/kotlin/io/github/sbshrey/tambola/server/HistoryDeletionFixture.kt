package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

/** Synthetic retained records; seeds in small batches, so the fixture does not retain the history itself. */
internal class HistoryDeletionFixture : PostgresTest() {
    val observations = linkedMapOf<String, JsonElement>()
    private fun id() = UUID.randomUUID().toString()
    private val removedName = "History deletion Asha"
    private val peerName = "History continuing Bina"
    private lateinit var removed: GuestCredentials
    private lateinit var peer: GuestCredentials
    private lateinit var current: RoomRecord
    private lateinit var originalRequest: CommandRequest
    private lateinit var journal: DeletionJournal
    private var receiptCount = 0
    private var roomCount = 0
    private var archiveCount = 0

    fun seed(rooms: Int, archives: Int, receipts: Int): Long {
        require(rooms in 1..200 && archives in 1..500 && receipts in 1..50_000)
        roomCount = rooms; archiveCount = archives; receiptCount = receipts
        journal = DeletionJournal(additionalDatabase()).also { it.migrate() }
        service = RoomService(database, now::get, journal)
        check(service.replayDeletions() == 0)
        removed = service.register(GuestRequest(removedName, 6), id())
        peer = service.register(GuestRequest(peerName, 3), id())
        val players = listOf(Player(removed.playerId, removedName, avatar = 6), Player(peer.playerId, peerName, avatar = 3))
        val settings = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, playAllNumbers = true)
        fun record(index: Int): RoomRecord {
            val game = Round.create(players, settings, now = now.get()).start().draw().pause()
            val nonce = secret()
            return RoomRecord("00000000-0000-4000-8000-%012d".format(index), roomCode(), removed.playerId,
                RoomOptions(settings, automaticCalling = false), players.mapIndexed { n, p -> Member(p.id, p.name, p.avatar, now.get() + n, true, true, now.get()) },
                now.get() + ROOM_LIFETIME, revision = receipts + 100L,
                phase = if (index == 0) RoomPhase.ACTIVE else RoomPhase.CLOSED,
                round = if (index == 0) game else game.cancel(), nonce = nonce, drawCommitment = commitment(game, nonce))
        }
        database.transaction { c ->
            repeat(rooms) { index ->
                val record = record(index)
                if (index == 0) current = record
                c.execute("INSERT INTO rooms(id, code, phase, expires_at, payload) VALUES (?, ?, ?, ?, ?)", record.id, record.code, record.phase.name, record.expiresAt, WireJson.encodeToString(record))
                for (p in players) c.execute("INSERT INTO room_participants VALUES (?, ?)", record.id, p.id)
                if (index != 0) c.execute("INSERT INTO finished_rounds VALUES (?, ?, ?)", record.id, record.round!!.id, WireJson.encodeToString(record))
            }
            repeat(archives) { index ->
                val old = record(index + rooms).copy(id = current.id, code = current.code)
                c.execute("INSERT INTO finished_rounds VALUES (?, ?, ?)", current.id, old.round!!.id, WireJson.encodeToString(old))
            }
        }
        // The continuing player previously hosted these paused snapshots. A later host transfer
        // is represented by the current record. All request hashes remain valid for exact retry.
        database.transaction { c ->
            c.prepareStatement("INSERT INTO command_receipts VALUES (?, ?, ?, ?, ?)").use { insert ->
                repeat(receipts) { index ->
                    val request = CommandRequest(id(), index.toLong(), RoomAction.Pause)
                    if (index == 0) originalRequest = request
                    val response = RoomUpdate(current.copy(hostId = peer.playerId, revision = index + 1L).view(peer.playerId, now.get()), emptyList(), true)
                    listOf(current.id, peer.playerId, request.id, digest(WireJson.encodeToString(request)), WireJson.encodeToString(response))
                        .forEachIndexed { n, value -> insert.setString(n + 1, value) }
                    insert.addBatch()
                    if ((index + 1) % 32 == 0) { insert.executeBatch(); insert.clearBatch() }
                }
                insert.executeBatch()
            }
        }
        return database.transaction { c -> c.query("SELECT coalesce(sum(octet_length(response)), 0) FROM command_receipts") { it.getLong(1) }.single() }
    }

    fun deleteAndVerify(): Map<String, JsonElement> {
        val unrelated = service.register(GuestRequest("Unrelated Chitra"), id())
        val elsewhere = service.create(unrelated.token, CreateRoomRequest(id())).snapshot.code
        val running = AtomicBoolean(true)
        val peak = AtomicLong(0)
        val samples = AtomicLong(0)
        val progress = AtomicLong(0)
        val otherFailures = AtomicLong(0)
        val workerFailures = AtomicLong(0)
        val maxOtherMs = AtomicLong(0)
        val executor = Executors.newFixedThreadPool(3)
        val memory = executor.submit {
            while (running.get()) {
                peak.accumulateAndGet(ManagementFactory.getMemoryMXBean().heapMemoryUsage.used, ::maxOf)
                samples.incrementAndGet(); Thread.sleep(20)
            }
        }
        val other = executor.submit {
            while (running.get()) {
                val start = System.nanoTime()
                try {
                    val room = service.read(unrelated.token, elsewhere).snapshot
                    service.command(unrelated.token, elsewhere, CommandRequest(id(), room.revision, RoomAction.Ready(progress.get() % 2 == 0L)))
                    maxOtherMs.accumulateAndGet((System.nanoTime() - start) / 1_000_000, ::maxOf)
                    progress.incrementAndGet()
                } catch (_: Exception) { otherFailures.incrementAndGet() }
                Thread.sleep(1_000)
            }
        }
        val worker = executor.submit {
            val worker = RoomWorker(service, ServiceOperations(workerEnabled = true))
            while (running.get()) {
                try { worker.runPass() } catch (_: Exception) { workerFailures.incrementAndGet() }
                Thread.sleep(1_000)
            }
        }
        val request = DeleteProfileRequest(id())
        val start = System.nanoTime()
        val receipt = try { service.deleteProfile(removed.token, request, "history-fixture") }
        finally {
            observations["deletionAttemptMs"] = JsonPrimitive((System.nanoTime() - start) / 1_000_000.0)
            running.set(false)
            executor.shutdown()
            check(executor.awaitTermination(15, TimeUnit.SECONDS))
            observations.putAll(mapOf("sampledPeakHeapBytes" to JsonPrimitive(peak.get()),
                "heapSamples" to JsonPrimitive(samples.get()), "unrelatedReadAndCommandPairs" to JsonPrimitive(progress.get()),
                "unrelatedPairMaxMs" to JsonPrimitive(maxOtherMs.get()), "unrelatedFailures" to JsonPrimitive(otherFailures.get()),
                "workerFailures" to JsonPrimitive(workerFailures.get()), "workerEnabled" to JsonPrimitive(true)))
            memory.get(); other.get(); worker.get()
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        check(service.deleteProfile(removed.token, request, "history-fixture") == receipt)
        verifyRedaction()
        check(progress.get() > 0)
        check(otherFailures.get() == 0L && workerFailures.get() == 0L)
        return observations + ("deleteAndMonitorJoinMs" to JsonPrimitive(elapsed))
    }

    fun lateFailureRollsBackAndJournalReplayCompletes() {
        fun primaryHashes() = database.transaction { c ->
            listOf(Triple("rooms", "payload", "id"), Triple("finished_rounds", "payload", "room_id, round_id"),
                Triple("command_receipts", "response", "room_id, actor, command_id")).map { (table, column, order) ->
                c.query("SELECT md5(coalesce(string_agg(md5($column), '' ORDER BY $order), '')) FROM $table") { it.getString(1) }.single()
            }
        }
        val before = primaryHashes()
        database.transaction { c ->
            val last = c.query("SELECT command_id FROM command_receipts WHERE room_id = ? ORDER BY actor DESC, command_id DESC LIMIT 1", current.id) { it.getString(1) }.single()
            check(UUID.fromString(last).toString() == last)
            c.execute("CREATE FUNCTION reject_last_history() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN IF OLD.command_id = ''$last'' THEN RAISE EXCEPTION ''injected late history failure''; END IF; RETURN NEW; END;'")
            c.execute("CREATE TRIGGER reject_last_history BEFORE UPDATE ON command_receipts FOR EACH ROW EXECUTE FUNCTION reject_last_history()")
        }
        val request = DeleteProfileRequest(id())
        val error = runCatching { service.deleteProfile(removed.token, request, "late-failure") }.exceptionOrNull()
        check(error is SQLException && error.sqlState == "P0001")
        check(primaryHashes() == before)
        database.transaction { c ->
            check(c.query("SELECT count(*) FROM guests WHERE id = ?", removed.playerId) { it.getInt(1) }.single() == 1)
            check(c.query("SELECT count(*) FROM room_participants WHERE player_id = ?", removed.playerId) { it.getInt(1) }.single() == roomCount)
            check(c.query("SELECT count(*) FROM deletion_receipts") { it.getInt(1) }.single() == 0)
            c.execute("DROP TRIGGER reject_last_history ON command_receipts")
        }
        check(journal.suppresses(removed.playerId))
        val denied = runCatching { service.read(removed.token, current.code) }.exceptionOrNull()
        check(denied is ApiFailure && denied.status == 401)
        check(service.replayDeletions() == 1)
        val receipt = service.deleteProfile(removed.token, request, "late-failure")
        check(receipt.id == request.id && service.deleteProfile(removed.token, request, "late-failure") == receipt)
        verifyRedaction()
    }

    private fun verifyRedaction() {
        check(journal.suppresses(removed.playerId) && journal.position().head == 1L)
        val view = service.read(peer.token, current.code).snapshot
        check(view.hostId == peer.playerId && view.members.none { it.playerId == removed.playerId })
        check(view.round!!.called == current.round!!.called)
        check(view.round!!.ownTickets == current.round!!.tickets.filter { it.playerId == peer.playerId })
        val retry = service.command(peer.token, current.code, originalRequest)
        check(retry.snapshot.round!!.players.first { it.id == removed.playerId }.name == "Deleted player")
        check(retry.snapshot.round!!.players.first { it.id == peer.playerId }.name == peerName)
        database.transaction { c ->
            check(c.query("SELECT count(*) FROM guests WHERE id = ?", removed.playerId) { it.getInt(1) }.single() == 0)
            check(c.query("SELECT count(*) FROM room_participants WHERE player_id = ?", removed.playerId) { it.getInt(1) }.single() == 0)
            check(c.query("SELECT count(*) FROM command_receipts WHERE room_id = ?", current.id) { it.getInt(1) }.single() == receiptCount)
            check(c.query("SELECT count(*) FROM finished_rounds") { it.getInt(1) }.single() == archiveCount + roomCount - 1)
            for ((table, column) in listOf("rooms" to "payload", "finished_rounds" to "payload", "command_receipts" to "response")) {
                check(c.query("SELECT count(*) FROM $table WHERE $column LIKE ?", "%$removedName%") { it.getInt(1) }.single() == 0)
            }
        }
        val resumed = service.command(peer.token, current.code, CommandRequest(id(), view.revision, RoomAction.Resume)).snapshot
        val drawn = service.command(peer.token, current.code, CommandRequest(id(), resumed.revision, RoomAction.Draw)).snapshot
        check(drawn.round!!.called.size == current.round!!.called.size + 1)
    }
}

/** Opt-in 128 MiB process. Direct service/JDBC workload; not hosted HTTP or independent-provider evidence. */
object HistoryDeletionLoad {
    private val evidenceJson = Json { prettyPrint = true }
    @JvmStatic fun main(args: Array<String>) {
        require(System.getenv("TAMBOLA_DATABASE_URL").orEmpty().matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/tambola_test")))
        val receipts = (System.getenv("TAMBOLA_HISTORY_RECEIPTS")?.toInt() ?: 20_000).also { require(it in 130..50_000) }
        val directory = Path.of(".test-workspace", "history-delete-" + UUID.randomUUID().toString().take(8))
        Files.createDirectories(directory)
        val report = linkedMapOf<String, JsonElement>("receipts" to JsonPrimitive(receipts), "rooms" to JsonPrimitive(70), "extraArchives" to JsonPrimitive(70),
            "maxHeapBytes" to JsonPrimitive(Runtime.getRuntime().maxMemory()), "scope" to JsonPrimitive("Synthetic retained records; direct RoomService/JDBC with same-database separate journal schema; not HTTP/provider/load-soak acceptance"))
        report["roomServiceClassSha256"] = JsonPrimitive(RoomService::class.java.getResourceAsStream("RoomService.class")!!.use {
            MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }
        })
        val fixture = HistoryDeletionFixture()
        var success = false
        var cleanup = true
        var stage = "prepare"
        try {
            fixture.prepareDatabase()
            stage = "seed"; println("history-delete: seed $receipts receipts")
            report["receiptPayloadBytes"] = JsonPrimitive(fixture.seed(70, 70, receipts))
            stage = "delete-and-verify"; println("history-delete: delete and concurrent unrelated room")
            report.putAll(fixture.deleteAndVerify())
            success = true
        } catch (error: Throwable) {
            report["failedStage"] = JsonPrimitive(stage); report["failureType"] = JsonPrimitive(error.javaClass.simpleName)
            report["causeTypes"] = JsonArray(generateSequence(error) { it.cause }.take(5).map { JsonPrimitive(it.javaClass.simpleName) }.toList())
            report["failureLocation"] = JsonPrimitive(error.stackTrace.firstOrNull { it.className.startsWith("io.github.sbshrey.tambola") }?.let { "${it.fileName}:${it.lineNumber}" }.orEmpty())
        } finally {
            report.putAll(fixture.observations)
            try { fixture.disposeDatabase() } catch (_: Throwable) { cleanup = false }
            report["completed"] = JsonPrimitive(success); report["cleanupComplete"] = JsonPrimitive(cleanup)
            Files.writeString(directory.resolve("evidence.json"), evidenceJson.encodeToString(JsonObject(report)) + "\n")
            println("history-delete: completed=$success cleanup=$cleanup evidence=$directory/evidence.json")
        }
        exitProcess(if (success && cleanup) 0 else 1)
    }
}
