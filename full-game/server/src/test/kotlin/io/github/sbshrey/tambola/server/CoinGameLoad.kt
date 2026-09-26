package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.system.exitProcess

/** The actual eight-seat coin matchmaker, mixed owned hands and manual selected claims over HTTP/WS. */
object CoinGameLoad {
    private val json = Json { prettyPrint = true }
    private fun id() = UUID.randomUUID().toString()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private data class Delivery(val roomId: String, val actor: Int, val call: Int, val receivedAt: Long)
    private data class Span(val startNanos: Long, val endNanos: Long, val startEpochMs: Long, val endEpochMs: Long) {
        val milliseconds get() = (endNanos - startNanos) / 1_000_000.0
    }
    private class Actor(val index: Int, val credentials: GuestCredentials, val api: HttpRoomApi, val base: String) {
        val quantity = index % 6 + 1
        val purchase = MatchRequest(id(), quantity)
        val saved = AtomicReference(WireJson.decodeFromString<OnlineSaved>(WireJson.encodeToString(
            OnlineSaved(base, credentials, "Coin load ${index + 1}").withPending(PendingOperation.Match(purchase)))))
        val calls = AtomicInteger()
        val connected = AtomicInteger()
        val turns = Channel<RoomView>(Channel.CONFLATED)
        fun accept(update: RoomUpdate) { saved.updateAndGet { it.accept(update, live = false, allowRoomChange = it.room == null).saved } }
        fun view(): RoomView = requireNotNull(saved.get().room)
    }

    @JvmStatic fun main(args: Array<String>) { require(args.isEmpty()); exitProcess(runBlocking { run() }) }
    private suspend fun run(): Int {
        val playerCount = (System.getenv("TAMBOLA_COIN_LOAD_PLAYERS")?.toInt() ?: 80).also { require(it in 8..320 && it % 8 == 0) }
        val probeCalls = (System.getenv("TAMBOLA_COIN_LOAD_PROBE_CALLS")?.toInt() ?: 0).also { require(it in 0..5) }
        val prepareWallets = System.getenv("TAMBOLA_COIN_LOAD_PREPARE_WALLETS") == "true"
        val diagnostics = System.getenv("TAMBOLA_COIN_LOAD_DIAGNOSTICS") == "true"
        val firstCohort = (System.getenv("TAMBOLA_COIN_LOAD_FIRST_COHORT")?.toInt() ?: 0).also {
            require(it == 0 || (it in 8 until playerCount && it % 8 == 0 && (playerCount - it) % 16 == 0 && probeCalls == 0))
        }
        val laterCohortSize = (playerCount - firstCohort) / 2
        val joinTrigger = CompletableDeferred<Long>()
        val triggerAt = java.util.concurrent.atomic.AtomicLong()
        val purchaseSpans = List(3) { ConcurrentLinkedQueue<Span>() }
        val issued = List(3) { AtomicInteger() }
        val pending = List(3) { AtomicInteger() }
        val peakPending = List(3) { AtomicInteger() }
        val existingClaimSpans = ConcurrentLinkedQueue<Span>()
        val overlapDeliveryMs = mutableListOf<Double>()
        fun cohort(actor: Actor) = when {
            firstCohort == 0 || actor.index < firstCohort -> 0
            actor.index < firstCohort + laterCohortSize -> 1
            else -> 2
        }
        fun window(group: Int): Span? = purchaseSpans[group].takeIf { it.isNotEmpty() }?.let {
            Span(it.minOf { s -> s.startNanos }, it.maxOf { s -> s.endNanos },
                it.minOf { s -> s.startEpochMs }, it.maxOf { s -> s.endEpochMs })
        }
        val env = IsolatedLoadService()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val failure = AtomicReference<String?>(null)
        val clients = mutableListOf<HttpRoomApi>()
        val actors = mutableListOf<Actor>()
        val deliveries = ConcurrentLinkedQueue<Delivery>()
        val purchaseMs = ConcurrentLinkedQueue<Double>()
        val claimMs = ConcurrentLinkedQueue<Double>()
        val receiptMs = ConcurrentLinkedQueue<Double>()
        val claimReactionMs = ConcurrentLinkedQueue<Double>()
        val receiptChecks = AtomicInteger()
        val discardedAcknowledgements = AtomicInteger()
        val successfulClaims = AtomicInteger()
        val closedWindows = AtomicInteger()
        val inflight = AtomicInteger()
        val peakClaims = AtomicInteger()
        val tables = ConcurrentHashMap<String, List<Actor>>()
        val waitSamples = ConcurrentHashMap<String, AtomicInteger>()
        val waitPolls = AtomicInteger()
        var stage = "initializing"
        var success = false
        var cleanup = false
        var expectedSamples = 0
        val deliveryMs = mutableListOf<Double>()
        val evidence = linkedMapOf<String, JsonElement>()
        fun record(key: String, value: Any?) { evidence[key] = when (value) {
            null -> JsonNull; is Boolean -> JsonPrimitive(value); is Number -> JsonPrimitive(value); else -> JsonPrimitive(value.toString())
        } }
        fun checkpoint(value: String) {
            stage = value; record("stage", value)
            Files.writeString(env.directory.resolve("evidence.json"), json.encodeToString(JsonObject(evidence)) + "\n")
            println("coin-load[${env.runId}]: $value")
        }
        fun healthy() { env.alive(); check(failure.get() == null) { "Client failure: ${failure.get()}" } }
        suspend fun awaitCondition(timeout: Long = 30_000, condition: () -> Boolean) {
            withTimeout(timeout) { while (!condition()) { healthy(); delay(25) } }; healthy()
        }
        suspend fun <T> measured(samples: MutableCollection<Double>, spans: MutableCollection<Span>? = null, block: suspend () -> T): T {
            val epoch = System.currentTimeMillis()
            val start = System.nanoTime()
            return block().also {
                val span = Span(start, System.nanoTime(), epoch, System.currentTimeMillis())
                samples.add(span.milliseconds); spans?.add(span)
            }
        }
        suspend fun purchase(actor: Actor): RoomUpdate {
            val group = cohort(actor)
            val active = pending[group].incrementAndGet(); peakPending[group].accumulateAndGet(active, ::maxOf)
            val started = issued[group].incrementAndGet()
            if (firstCohort > 0 && group > 0 && started == 1) println("coin-load[${env.runId}]: joining-cohort-$group-started")
            try { return measured(purchaseMs, purchaseSpans[group]) { actor.api.match(actor.credentials.token, actor.purchase) } }
            finally { pending[group].decrementAndGet() }
        }
        fun launchChecked(block: suspend CoroutineScope.() -> Unit) = scope.launch {
            try { block() } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure.compareAndSet(null, safeFailure(error)) }
        }
        try {
            withTimeout((if (firstCohort > 0) 14 else 10) * 60_000L) {
                record("runId", env.runId); record("completed", false); record("players", playerCount)
                record("expectedTables", playerCount / 8); record("probeCalls", probeCalls); record("intervalMs", 5000)
                record("serverHeapMiB", 512); record("serverActiveProcessors", 4)
                record("runtimeSource", env.runtimeSource); record("sharedHttpTransports", playerCount / 8)
                record("walletsPreparedBeforePurchase", prepareWallets); record("databaseWaitSampling", diagnostics)
                record("firstCohortPlayers", firstCohort)
                if (firstCohort > 0) {
                    record("laterCohortPlayers", laterCohortSize)
                    record("mixedTrafficDefinition", "Existing players trigger two equally sized joining cohorts at the first valid selected claim and its announced next-draw deadline. The triggering claimant waits for at least eight issued primary purchases and one still pending before submitting. Claims are measured when their requests start inside an observed joining window; deliveries are measured for existing-table draw events timestamped inside a joining window. Window bounds use successful primary request start/end times, exclude the gap between cohorts, and do not assert uninterrupted server CPU work. Profiles seeded; no physical network or Android claim.")
                }
                record("sqlProfiling", env.sqlProfiling)
                if (env.sqlProfiling) record("sqlProfilerSourceSha256", sha(Files.readAllBytes(env.root.resolve("server/src/test/kotlin/io/github/sbshrey/tambola/server/ProfiledCoinServer.kt"))))
                record("scope", "Real isolated journal-enabled Java service; public match/wallet/selected-claim HTTP and native WebSocket clients. Synthetic clients mark revealed own numbers. Profiles seeded; no signup, Android UI, TLS, physical-network or restricted-role claim.")
                record("fixtureSourceSha256", sha(Files.readAllBytes(env.root.resolve("server/src/test/kotlin/io/github/sbshrey/tambola/server/CoinGameLoad.kt"))))
                record("lifecycleSourceSha256", sha(Files.readAllBytes(env.root.resolve("server/src/test/kotlin/io/github/sbshrey/tambola/server/IsolatedLoadService.kt"))))
                record("clientJarSha256", sha(Files.readAllBytes(env.root.resolve("client/build/libs/client.jar"))))
                env.runtimeIdentity.forEach(::record)
                checkpoint("start-isolated-service")
                env.start(); record("serverPid", env.pid)
                val credentials = env.seed(playerCount)
                clients += List(playerCount / 8) { HttpRoomApi(env.base, true) }
                actors += credentials.mapIndexed { index, player -> Actor(index, player, clients[index % clients.size], env.base) }
                if (prepareWallets) {
                    checkpoint("prepare-registered-wallets-through-api")
                    coroutineScope { actors.map { actor -> async(Dispatchers.IO) {
                        check(actor.api.wallet(actor.credentials.token).balance == COIN_STARTER_BALANCE)
                    } }.awaitAll() }
                }
                checkpoint("concurrent-mixed-ticket-purchases")
                val sampler = if (diagnostics) launchChecked {
                    env.primary { connection ->
                        while (isActive) {
                            connection.query("SELECT coalesce(wait_event, 'executing'), count(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid() AND state = 'active' GROUP BY wait_event") {
                                it.getString(1) to it.getInt(2)
                            }.forEach { (key, count) -> waitSamples.computeIfAbsent(key) { AtomicInteger() }.addAndGet(count) }
                            waitPolls.incrementAndGet(); Thread.sleep(25)
                        }
                    }
                } else null
                coroutineScope { actors.map { actor -> async(Dispatchers.IO) {
                    val group = cohort(actor)
                    if (group > 0) {
                        val nextDraw = withTimeout(180_000) { joinTrigger.await() }
                        if (group == 2) delay((nextDraw - System.currentTimeMillis()).coerceAtLeast(0))
                    }
                    val response = if (actor.index % 8 == 1) coroutineScope {
                        val first = async { purchase(actor) }
                        val duplicate = async { measured(receiptMs) { actor.api.match(actor.credentials.token, actor.purchase) } }
                        first.await().also { check(it == duplicate.await()); receiptChecks.incrementAndGet() }
                    } else purchase(actor)
                    check(response.snapshot.coins!!.ownTickets == actor.quantity)
                    check(response.snapshot.wallet!!.balance == COIN_STARTER_BALANCE - actor.quantity * COIN_TICKET_PRICE)
                    if (actor.index % 8 == 0) {
                        // Delay applying the acknowledgement, then replay the identical committed request.
                        delay(200); discardedAcknowledgements.incrementAndGet()
                        check(actor.saved.get().room == null && actor.saved.get().pending == PendingOperation.Match(actor.purchase))
                        val retry = measured(receiptMs) { actor.api.match(actor.credentials.token, actor.purchase) }
                        check(response == retry); actor.accept(retry); receiptChecks.incrementAndGet()
                    } else actor.accept(response)
                    actor.saved.updateAndGet { it.copy(pending = null) }
                    launchChecked {
                        var seen = 0
                        var frozenPool: CoinTableView? = null
                        actor.api.events(actor.credentials.token, actor.view().code, actor.view().revision).collect { update ->
                            val received = System.currentTimeMillis()
                            actor.accept(update)
                            val view = actor.view()
                            actor.connected.set(1)
                            check(view.options.coinGame && view.options.intervalSeconds == 5)
                            view.round?.let { game ->
                                check(game.ownTickets.size == actor.quantity)
                                check(game.ownTickets.flatMap { it.numbers }.distinct().size == actor.quantity * 15)
                                check(game.ownTickets.all { it.playerId == actor.credentials.playerId })
                                if (view.phase == RoomPhase.ACTIVE) check(game.revealedOrder == null && game.revealedNonce == null)
                                val pool = requireNotNull(view.coins)
                                if (frozenPool == null) frozenPool = pool
                                else check(pool.pool == frozenPool.pool && pool.prizes == frozenPool.prizes && pool.tickets == frozenPool.tickets)
                                check(game.called.size >= seen)
                                for (call in seen + 1..game.called.size) deliveries.add(Delivery(view.roomId, actor.index, call, received))
                                if (game.called.size > seen && probeCalls == 0 && view.phase == RoomPhase.ACTIVE) actor.turns.trySend(view)
                                seen = game.called.size; actor.calls.set(seen)
                            }
                        }
                        error("Unexpected stream completion")
                    }
                    if (probeCalls == 0) launchChecked {
                        for (turn in actor.turns) {
                            val reaction = System.nanoTime()
                            val round = requireNotNull(turn.round)
                            val marks = round.ownTickets.flatMap { it.numbers }.intersect(round.called.toSet())
                            val closedRanks = round.awards.filter { it.prize.isRankedHouse && it.drawIndex < round.called.size }
                            val nextRank = turn.options.game.prizes.filter { it.isRankedHouse && closedRanks.none { award -> award.prize == it } }.minByOrNull { it.ordinal }
                            val previousHouses = closedRanks.flatMap { it.ticketIds }.toSet()
                            val selections = round.ownTickets.flatMap { ticket -> turn.options.game.prizes.filter { prize ->
                                val awarded = round.awards.firstOrNull { it.prize == prize }
                                (awarded == null || (awarded.drawIndex == round.called.size && ticket.id !in awarded.ticketIds)) &&
                                    (!prize.isRankedHouse || (prize == nextRank && ticket.id !in previousHouses)) && prize.matches(ticket, marks)
                            }.map { ClaimSelection(ticket.id, it.name) } }
                            val triggered = selections.isNotEmpty() && firstCohort > 0 && actor.index < firstCohort &&
                                joinTrigger.complete(requireNotNull(turn.nextDrawAt))
                            if (triggered) {
                                triggerAt.set(System.currentTimeMillis())
                                println("coin-load[${env.runId}]: existing-claim-triggered-joining-cohorts")
                                check(withTimeoutOrNull(2_000) {
                                    while (issued[1].get() < minOf(8, laterCohortSize) || pending[1].get() == 0) { healthy(); delay(5) }
                                    true
                                } == true) { "Joining requests did not overlap the triggering claim" }
                            } else if (selections.isNotEmpty() && actor.index % 8 == 0) delay(200)
                            for (selection in selections) {
                                if (actor.view().round!!.called.size != round.called.size) { closedWindows.incrementAndGet(); break }
                                val request = CommandRequest(id(), turn.revision, RoomAction.Claim(round.id, round.called.size, marks, selection))
                                val active = inflight.incrementAndGet(); peakClaims.accumulateAndGet(active, ::maxOf)
                                try {
                                    val result = measured(claimMs, existingClaimSpans.takeIf { firstCohort > 0 && actor.index < firstCohort }) {
                                        actor.api.command(actor.credentials.token, turn.code, request)
                                    }
                                    actor.accept(result); successfulClaims.incrementAndGet()
                                    claimReactionMs.add((System.nanoTime() - reaction) / 1_000_000.0)
                                    if (actor.index % 8 == 0) {
                                        val replay = measured(receiptMs) { actor.api.command(actor.credentials.token, turn.code, request) }
                                        check(result == replay); actor.accept(replay); receiptChecks.incrementAndGet()
                                    }
                                } catch (error: RoomApiFailure) {
                                    if (error.code == "claim_window_closed") closedWindows.incrementAndGet() else throw error
                                } finally { inflight.decrementAndGet() }
                            }
                        }
                    }
                } }.awaitAll() }
                sampler?.cancelAndJoin()
                if (firstCohort > 0) {
                    check(purchaseSpans[0].size == firstCohort && purchaseSpans.drop(1).all { it.size == laterCohortSize })
                    record("joinTriggerEpochMs", triggerAt.get())
                    checkpoint("joining-cohorts-purchased")
                }
                if (env.sqlProfiling) env.primary { connection ->
                    val now = System.currentTimeMillis()
                    val version = connection.query("SELECT max(version) FROM schema_migrations") { it.getInt(1) }.single()
                    check(version in 6..7) { "Update the diagnostic lookup for this schema" }
                    val lookup = if (version == 7) OPEN_COIN_LOBBY_SQL else """SELECT payload FROM rooms WHERE matchable AND phase = 'LOBBY' AND expires_at > ?
                        AND (payload::jsonb->>'startsAt')::bigint > ? AND jsonb_array_length(payload::jsonb->'members') < 8
                        ORDER BY (payload::jsonb->>'startsAt')::bigint, id LIMIT 1 FOR UPDATE"""
                    val plan = connection.query("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) $lookup", now, now) { it.getString(1) }.single()
                    Files.writeString(env.directory.resolve("allocation-query-plan.json"), plan + "\n")
                }
                awaitCondition { actors.all { it.connected.get() == 1 && it.view().round != null } }
                tables.putAll(actors.groupBy { it.view().roomId })
                check(tables.size == playerCount / 8 && tables.values.all { it.size == 8 }) { "Matchmaking did not fill eight-seat tables" }
                check(actors.all { it.view().options.computerPlayers == 0 })
                tables.values.forEach { group ->
                    val pool = CoinPool(group.sumOf { it.quantity })
                    check(group.all { it.view().coins!!.pool == pool.coins && it.view().coins!!.prizes == pool.prizes })
                    check(group.flatMap { it.view().round!!.ownTickets }.map { it.cells }.distinct().size == pool.soldTickets)
                }
                record("tables", tables.size); record("tickets", actors.sumOf { it.quantity }); record("allPrivateHandsVerified", true)
                checkpoint("play-real-five-second-calls")
                var lastProgress = 0
                while (if (probeCalls > 0) actors.any { it.calls.get() < probeCalls } else actors.any { it.view().phase != RoomPhase.FINISHED }) {
                    healthy(); delay(100)
                    val minimum = actors.minOf { it.calls.get() }
                    if (minimum >= lastProgress + 10) {
                        lastProgress = minimum; record("minimumCalls", minimum); record("successfulClaims", successfulClaims.get())
                        checkpoint("minimum-$minimum-calls-claims-${successfulClaims.get()}")
                    }
                }
                if (probeCalls == 0) {
                    awaitCondition { inflight.get() == 0 }
                    checkpoint("verify-ledger-settlement-and-replayed-purchases")
                    val records = env.primary { c -> c.query("SELECT payload FROM rooms ORDER BY id") { WireJson.decodeFromString<RoomRecord>(it.getString(1)) } }
                    var sharedAwards = 0
                    var paid = 0L
                    val joiningWindows = (1..2).mapNotNull(::window)
                    for (record in records) {
                        val group = tables.getValue(record.id)
                        val game = requireNotNull(record.round)
                        val pool = requireNotNull(record.coinPool)
                        check(record.phase == RoomPhase.FINISHED && game.status == RoundStatus.COMPLETED)
                        game.validated()
                        check(game.awards.map { it.prize }.toSet() == pool.prizes.map { it.prize }.toSet())
                        val owners = group.flatMap { it.view().round!!.ownTickets }.associate { it.id to it.playerId }
                        val expected = group.associate { it.credentials.playerId to COIN_STARTER_BALANCE - it.quantity * COIN_TICKET_PRICE }.toMutableMap()
                        for (slot in pool.prizes) {
                            val winners = game.awards.single { it.prize == slot.prize }.ticketIds.sorted()
                            check(winners.isNotEmpty() && winners.distinct().size == winners.size)
                            if (winners.size > 1) sharedAwards++
                            winners.forEachIndexed { index, ticket ->
                                val amount = slot.coins / winners.size + if (index < slot.coins % winners.size) 1 else 0
                                val player = owners.getValue(ticket)
                                expected[player] = expected.getValue(player) + amount
                            }
                        }
                        for (actor in group) {
                            val wallet = actor.api.wallet(actor.credentials.token)
                            check(wallet.balance == expected.getValue(actor.credentials.playerId))
                            actor.saved.updateAndGet { it.acceptWallet(wallet) }
                            val current = actor.view()
                            val oldReceipt = actor.api.match(actor.credentials.token, actor.purchase)
                            actor.accept(oldReceipt); receiptChecks.incrementAndGet()
                            check(actor.view() == current && actor.saved.get().wallet == wallet)
                            check(current.round!!.awards == game.awards && current.round!!.called == game.called)
                        }
                        val events = env.primary { c -> c.query("SELECT payload FROM room_events WHERE room_id = ? ORDER BY revision", record.id) { WireJson.decodeFromString<RoomEvent>(it.getString(1)) } }
                            .filter { it.type == "drawn" }
                        check(events.size in game.called.size..game.called.size + 1)
                        val samples = deliveries.filter { it.roomId == record.id }
                        check(samples.size == game.called.size * group.size) { "Missing call delivery samples" }
                        check(samples.map { it.actor to it.call }.toSet().size == samples.size)
                        check(group.all { actor -> samples.count { it.actor == actor.index } == game.called.size })
                        samples.forEach { sample ->
                            val eventAt = events[sample.call - 1].at
                            val elapsed = sample.receivedAt - eventAt
                            check(elapsed >= 0); deliveryMs += elapsed.toDouble()
                            if (firstCohort > 0 && sample.actor < firstCohort && joiningWindows.any {
                                    eventAt in it.startEpochMs..it.endEpochMs
                                }) overlapDeliveryMs += elapsed.toDouble()
                        }
                        expectedSamples += game.called.size * group.size
                        paid += pool.coins
                    }
                    env.primary { c ->
                        check(c.query("SELECT coalesce(sum(amount), 0) FROM coin_ledger") { it.getLong(1) }.single() == playerCount * COIN_STARTER_BALANCE)
                        check(c.query("SELECT count(*) FROM coin_ledger WHERE amount < 0") { it.getInt(1) }.single() == playerCount)
                        check(c.query("SELECT count(*) FROM match_receipts") { it.getInt(1) }.single() == playerCount)
                        check(c.query("SELECT count(*) FROM finished_rounds") { it.getInt(1) }.single() == tables.size)
                    }
                    check(sharedAwards > 0 && peakClaims.get() > 1 && closedWindows.get() == 0)
                    record("sharedPrizesVerified", sharedAwards); record("settledCoins", paid); record("aggregateBalance", playerCount * COIN_STARTER_BALANCE)
                    record("exactlyOnePurchasePerPlayer", true); record("matchingResultsAndExactShares", true)
                }
                if (env.sqlProfiling) evidence["diagnosticRoomUpdates"] = env.primary { connection ->
                    connection.query("SELECT n_tup_upd, n_tup_hot_upd, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'rooms'") {
                        buildJsonObject { put("updates", it.getLong(1)); put("hotUpdates", it.getLong(2)); put("estimatedDeadTuples", it.getLong(3)) }
                    }.single()
                }
                healthy(); check(serviceRuntimeIdentity(env.root, env.runtimeLib) == env.runtimeIdentity)
                success = true
            }
        } catch (error: Exception) {
            record("failure", safeFailure(error)); record("failedStage", stage); record("clientFailure", failure.get())
        } finally {
            withContext(NonCancellable) {
                scope.cancel(); clients.forEach { runCatching { it.close() } }
                val stopped = withTimeoutOrNull(10_000) { scope.coroutineContext[Job]?.join(); true } == true
                cleanup = runCatching { env.close(); stopped }.getOrDefault(false)
            }
            record("completed", success && probeCalls == 0); record("probePassed", success && probeCalls > 0); record("cleanupComplete", cleanup)
            record("purchasedPlayers", actors.count { it.saved.get().room != null })
            record("connectedPlayers", actors.count { it.connected.get() == 1 })
            record("finishedPlayers", actors.count { it.saved.get().room?.phase == RoomPhase.FINISHED })
            record("minimumCalls", actors.minOfOrNull { it.calls.get() }); record("maximumCalls", actors.maxOfOrNull { it.calls.get() })
            record("databaseWaitPolls", waitPolls.get())
            evidence["databaseActiveWaitSamples"] = buildJsonObject { waitSamples.toSortedMap().forEach { (key, count) -> put(key, count.get()) } }
            record("successfulClaims", successfulClaims.get()); record("closedClaimWindows", closedWindows.get())
            record("peakConcurrentClaims", peakClaims.get()); record("duplicateReceiptsChecked", receiptChecks.get())
            record("delayedAcknowledgementRetries", discardedAcknowledgements.get()); record("expectedDeliverySamples", expectedSamples)
            evidence["purchaseRoundTripMs"] = stats(purchaseMs); evidence["claimRoundTripMs"] = stats(claimMs)
            evidence["duplicateReceiptRoundTripMs"] = stats(receiptMs); evidence["snapshotToClaimReceiptMs"] = stats(claimReactionMs)
            evidence["drawEventToSnapshotMs"] = stats(deliveryMs)
            var mixedTimingPassed = firstCohort == 0
            if (firstCohort > 0) {
                val windows = (1..2).mapNotNull(::window)
                val overlapClaims = existingClaimSpans.filter { claim -> windows.any { claim.startNanos in it.startNanos..it.endNanos } }.map { it.milliseconds }
                val allPurchases = purchaseSpans[0].size == firstCohort && purchaseSpans.drop(1).all { it.size == laterCohortSize }
                val observed = allPurchases && windows.size == 2 && overlapClaims.isNotEmpty() && overlapDeliveryMs.isNotEmpty()
                record("joinTriggerEpochMs", triggerAt.get())
                record("mixedTrafficObserved", observed)
                record("mixedSampleCaution", "Counts are reported explicitly; a small overlapping-claim sample is a smoke measurement, not a stable population p95.")
                evidence["purchaseCohorts"] = JsonArray((0..2).map { group -> buildJsonObject {
                    put("cohort", group); put("players", if (group == 0) firstCohort else laterCohortSize)
                    put("primaryRequestsIssued", issued[group].get()); put("peakPendingPrimaryRequests", peakPending[group].get())
                    put("purchaseRoundTripMs", stats(purchaseSpans[group].map { it.milliseconds }))
                    window(group)?.let { put("startEpochMs", it.startEpochMs); put("endEpochMs", it.endEpochMs) }
                } })
                evidence["existingClaimDuringJoinsMs"] = stats(overlapClaims)
                evidence["existingDrawDuringJoinsMs"] = stats(overlapDeliveryMs)
                mixedTimingPassed = observed && percentile(overlapClaims, .95) < 1000 && percentile(overlapDeliveryMs, .95) < 1000
                record("mixedP95Under1000Ms", mixedTimingPassed)
            }
            record("latencyDefinition", "Same-host wall-clock interval from the persisted drawn event timestamp captured before its transaction commits to each native client's first snapshot containing that call; includes commit and polling. Not scheduled-deadline, mobile or TLS latency.")
            val latencyPassed = mixedTimingPassed && !env.sqlProfiling && !diagnostics && probeCalls == 0 && expectedSamples == deliveryMs.size && expectedSamples > 0 &&
                percentile(deliveryMs, .95) < 1000 && percentile(purchaseMs, .95) < 1000 && percentile(claimMs, .95) < 1000
            record("allP95Under1000Ms", latencyPassed)
            checkpoint(if (success) if (probeCalls > 0) "probe-passed" else "completed" else "failed")
            println("coin-load[${env.runId}]: full=${success && probeCalls == 0} probe=${success && probeCalls > 0} cleanup=$cleanup latency=$latencyPassed")
        }
        return if (success && cleanup && (probeCalls > 0 || evidence["allP95Under1000Ms"] == JsonPrimitive(true))) 0 else 1
    }

    private fun safeFailure(error: Exception) = (if (error is RoomApiFailure) "http_${error.status}_${error.code}" else error.javaClass.simpleName) +
        ":" + (error.stackTrace.firstOrNull { it.className.startsWith("io.github.sbshrey.tambola") }?.let { "${it.fileName}:${it.lineNumber}" } ?: "unknown")
    private fun percentile(values: Collection<Double>, p: Double): Double = if (values.isEmpty()) 0.0 else values.sorted()[(ceil(values.size * p).toInt() - 1).coerceIn(0, values.size - 1)]
    private fun stats(values: Collection<Double>) = buildJsonObject {
        put("count", values.size); put("p50", percentile(values, .5)); put("p95", percentile(values, .95)); put("p99", percentile(values, .99)); put("max", values.maxOrNull() ?: 0.0)
    }
}
