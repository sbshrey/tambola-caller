package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CoinMatchTest : PostgresTest() {
    @Test fun `quick Tambola starts after ten seconds with clearly flagged computer seats`() {
        val actor = guest("Early player")
        val room = service.match(actor.token, MatchRequest(UUID.randomUUID().toString(), 2,
            rulesVersion = 2, largeMatch = true, roundSummary = true, realPlayersOnly = false)).snapshot
        assertEquals(10_000L, room.coins!!.startsAt!! - now.get())
        now.set(room.coins!!.startsAt!! - 1); service.tick()
        assertEquals(RoomPhase.LOBBY, service.read(actor.token, room.code).snapshot.phase)
        now.incrementAndGet(); service.tick()
        val active = service.read(actor.token, room.code).snapshot
        assertEquals(RoomPhase.ACTIVE, active.phase)
        assertEquals(1, active.round!!.players.count { !it.computer })
        assertTrue(active.round!!.players.count { it.computer } in 29..49)
        assertEquals(1300L, service.wallet(actor.token).balance)
    }

    @Test fun `real player Tambola queue waits for a peer and never fills computer seats`() {
        val legacy = service.match(guest("Legacy").token, MatchRequest(UUID.randomUUID().toString(), 1,
            rulesVersion = 2, largeMatch = true, roundSummary = true)).snapshot
        val first = guest("First")
        val second = guest("Second")
        fun request() = MatchRequest(UUID.randomUUID().toString(), 2, rulesVersion = 2,
            largeMatch = true, roundSummary = true, realPlayersOnly = true)
        val room = service.match(first.token, request()).snapshot
        assertNotEquals(legacy.code, room.code)
        now.set(room.coins!!.startsAt!!); service.tick()
        val waiting = service.read(first.token, room.code).snapshot
        assertEquals(RoomPhase.LOBBY, waiting.phase)
        assertEquals(0, waiting.options.computerPlayers)
        assertTrue(waiting.coins!!.startsAt!! > now.get())
        assertEquals(room.code, service.match(second.token, request()).snapshot.code)
        now.set(service.read(first.token, room.code).snapshot.coins!!.startsAt!!); service.tick()
        val active = service.read(first.token, room.code).snapshot
        assertEquals(RoomPhase.ACTIVE, active.phase)
        assertEquals(2, active.round!!.players.size)
        assertTrue(active.round!!.players.none { it.computer })
        assertEquals(4, active.coins!!.tickets)
    }

    @Test fun `unmatched real player Tambola queue refunds at its wait limit`() {
        val actor = guest("Waiting")
        val room = service.match(actor.token, MatchRequest(UUID.randomUUID().toString(), 3,
            rulesVersion = 2, largeMatch = true, roundSummary = true, realPlayersOnly = true)).snapshot
        assertEquals(1200L, service.wallet(actor.token).balance)
        now.set(room.coins!!.startsAt!!); service.tick()
        assertEquals(RoomPhase.LOBBY, stored(room.code).phase)
        now.set(stored(room.code).expiresAt - ROOM_LIFETIME + MATCH_WAIT_LIMIT); service.tick()
        assertEquals(RoomPhase.CLOSED, stored(room.code).phase)
        assertEquals(1500L, service.wallet(actor.token).balance)
    }
    @Test fun `a three house game settles every slot exactly once and conserves all human coins`() {
        val actors = (1..4).map { guest("House player $it") }
        val active = start(actors, List(4) { 6 })
        assertEquals(24, active.coins!!.tickets); assertEquals(8, active.coins!!.prizes.size)
        var steps = 0
        while (stored(active.code).phase == RoomPhase.ACTIVE && steps++ < 100) {
            now.set(requireNotNull(stored(active.code).nextDrawAt)); service.tick()
            var game = requireNotNull(stored(active.code).round)
            if (game.finished) break
            actors.forEach { actor ->
                val own = game.tickets.filter { it.playerId == actor.playerId }
                val marks = own.flatMap { it.numbers }.intersect(game.called.toSet())
                own.forEach { ticket -> game.settings.prizes.forEach { prize ->
                    val selection = ClaimSelection(ticket.id, prize.name)
                    val claimed = game.claim(actor.playerId, marks, selection)
                    if (claimed != game) {
                        command(actor, active.code, RoomAction.Claim(game.id, game.called.size, marks, selection))
                        game = claimed
                    }
                } }
            }
        }
        val record = stored(active.code)
        assertEquals(RoomPhase.FINISHED, record.phase)
        assertEquals(8, record.round!!.awards.size)
        assertEquals(2400L, record.coinPool!!.allocations(record.round!!).sumOf { it.coins })
        assertEquals(6000L, actors.sumOf { service.wallet(it.token).balance })
        assertEquals(2400L, actors.sumOf { read(it, active.code).coins!!.settledWinnings })
        service = RoomService(database, now::get)
        val entries = count("coin_ledger"); service.tick(); service.tick()
        assertEquals(entries, count("coin_ledger"))
    }

    @Test fun `computer payouts consume only their allocations and active expiry returns the unawarded pool`() {
        val actor = guest(); val active = start(listOf(actor), listOf(6))
        var turns = 0
        while (stored(active.code).round!!.awards.isEmpty() && turns++ < 400) {
            val record = stored(active.code)
            now.set(listOfNotNull(record.nextDrawAt, record.computerClaimsAt.values.minOrNull()).min()); service.tick()
        }
        val before = stored(active.code)
        now.set(before.expiresAt); service.tick()
        val record = stored(active.code)
        assertEquals(RoomPhase.CLOSED, record.phase)
        val allocations = record.coinPool!!.allocations(record.round!!)
        assertEquals(1500L, allocations.sumOf { it.coins })
        assertEquals(900L + allocations.filter { it.playerId == actor.playerId }.sumOf { it.coins }, service.wallet(actor.token).balance)
        assertTrue(allocations.any { it.playerId != actor.playerId && it.prize != null })
        val entries = count("coin_ledger"); service.tick(); assertEquals(entries, count("coin_ledger"))
    }

    @Test fun `simultaneous retries of one match have one receipt one seat and one charge`() {
        val actor = guest(); val request = MatchRequest(id(), 6)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val responses = executor.invokeAll(List(4) { Callable { service.match(actor.token, request) } }).map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, responses.distinct().size)
            assertEquals(900L, service.wallet(actor.token).balance)
            assertEquals(1, count("rooms")); assertEquals(1, count("match_receipts")); assertEquals(2, count("coin_ledger"))
        } finally { executor.shutdownNow() }
    }

    @Test fun `returning to an owned table does not wait for unrelated lobby allocation`() {
        val actor = guest()
        val first = match(actor, 3)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holder = executor.submit(Callable {
                database.transaction { connection ->
                    connection.query("SELECT pg_advisory_xact_lock(749023809)") { true }
                    held.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
            })
            assertTrue(held.await(5, TimeUnit.SECONDS))
            val resumed = executor.submit(Callable { match(actor, 6) }).get(3, TimeUnit.SECONDS)
            assertEquals(first.roomId, resumed.roomId)
            assertEquals(3, resumed.coins!!.ownTickets)
            assertEquals(1200L, resumed.wallet!!.balance)
            assertFalse(holder.isDone)
            release.countDown(); holder.get(5, TimeUnit.SECONDS)
        } finally { release.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS) }
    }
    private fun id() = UUID.randomUUID().toString()
    private fun guest(name: String = "Coin player") = service.register(GuestRequest(name), id())
    private fun match(actor: GuestCredentials, tickets: Int = 3) = service.match(actor.token, MatchRequest(id(), tickets)).snapshot
    private fun read(actor: GuestCredentials, code: String) = service.read(actor.token, code).snapshot
    private fun command(actor: GuestCredentials, code: String, action: RoomAction): RoomView =
        service.command(actor.token, code, CommandRequest(id(), read(actor, code).revision, action)).snapshot
    private fun stored(code: String) = database.transaction { connection ->
        connection.query("SELECT payload FROM rooms WHERE code = ?", code) { WireJson.decodeFromString<RoomRecord>(it.getString(1)) }.single()
    }
    private fun count(table: String): Int {
        require(table in setOf("rooms", "coin_ledger", "match_receipts"))
        return database.transaction { it.query("SELECT count(*) FROM $table") { row -> row.getInt(1) }.single() }
    }
    private fun failure(code: String, action: () -> Unit) = assertEquals(code, assertThrows(ApiFailure::class.java, action).code)
    private fun start(actors: List<GuestCredentials>, quantities: List<Int> = List(actors.size) { 1 }): RoomView {
        val views = actors.mapIndexed { index, actor -> match(actor, quantities[index]) }
        assertEquals(1, views.map { it.code }.distinct().size)
        now.set(views.first().coins!!.startsAt!!); service.tick()
        return read(actors.first(), views.first().code)
    }

    @Test fun `purchase quantity changes and leave commit once with exact retry receipts`() {
        val actor = guest(); val request = MatchRequest(id(), 3)
        val first = service.match(actor.token, request)
        val room = first.snapshot
        room.validateFor(actor.playerId)
        assertEquals(1200L, room.wallet!!.balance)
        assertEquals(12, room.coins!!.tickets); assertEquals(7, room.coins!!.prizes.size)
        assertEquals(first, service.match(actor.token, request))
        failure("id_reused") { service.match(actor.token, request.copy(tickets = 6)) }
        val resumed = match(actor, 6)
        assertEquals(room.roomId, resumed.roomId); assertEquals(3, resumed.coins!!.ownTickets)
        assertEquals(1200L, resumed.wallet!!.balance)
        val larger = command(actor, room.code, RoomAction.BuyTickets(6))
        assertEquals(900L, larger.wallet!!.balance)
        val smaller = command(actor, room.code, RoomAction.BuyTickets(1))
        assertEquals(1400L, smaller.wallet!!.balance)
        val leave = CommandRequest(id(), smaller.revision, RoomAction.Leave)
        val cancelled = service.command(actor.token, room.code, leave)
        assertEquals(RoomPhase.CLOSED, cancelled.snapshot.phase)
        assertEquals(1500L, cancelled.snapshot.wallet!!.balance)
        assertEquals(cancelled, service.command(actor.token, room.code, leave))
        assertEquals(1500L, service.wallet(actor.token).balance)
    }

    @Test fun `simultaneous real players fill one lobby without duplicate debits then spill at eight seats`() {
        val actors = (1..10).map { guest("Player $it") }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val requests = actors.map { MatchRequest(id(), 1) }
            val views = executor.invokeAll(actors.mapIndexed { index, actor -> Callable { service.match(actor.token, requests[index]).snapshot } })
                .map { it.get(15, TimeUnit.SECONDS) }
            val groups = views.groupBy { it.roomId }
            assertEquals(listOf(2, 8), groups.values.map { it.size }.sorted())
            actors.forEach { assertEquals(1400L, service.wallet(it.token).balance) }
            assertEquals(20, count("coin_ledger")); assertEquals(10, count("match_receipts"))
        } finally { executor.shutdownNow() }
    }

    @Test fun `six independent quantities freeze the exact pool and only owned disjoint tickets are exposed`() {
        val actors = (1..6).map { guest("Player $it") }
        val active = start(actors, (1..6).toList())
        assertEquals(RoomPhase.ACTIVE, active.phase)
        assertEquals(21, active.coins!!.tickets); assertEquals(2100L, active.coins!!.pool)
        assertEquals(7, active.coins!!.prizes.size)
        assertEquals(0, active.options.computerPlayers)
        actors.forEachIndexed { index, actor ->
            val view = read(actor, active.code); view.validateFor(actor.playerId)
            val cards = view.round!!.ownTickets
            assertEquals(index + 1, cards.size)
            assertEquals(15 * cards.size, cards.flatMap { it.numbers }.distinct().size)
            assertTrue(cards.all { it.playerId == actor.playerId })
            assertNull(view.round!!.revealedOrder)
        }
        assertEquals(5, stored(active.code).round!!.version)
        stored(active.code).round!!.validated()
        failure("automatic_coin_round") { command(actors[0], active.code, RoomAction.Pause) }
        failure("automatic_coin_round") { command(actors[0], active.code, RoomAction.Draw) }
        failure("automatic_coin_round") { command(actors[0], active.code, RoomAction.End) }
        failure("not_lobby") { command(actors[0], active.code, RoomAction.BuyTickets(1)) }
        val later = match(guest(), 1)
        assertNotEquals(active.roomId, later.roomId)
    }

    @Test fun `same-call claims stay provisional then split once and return the remaining pool`() {
        val actors = (1..4).map { guest("Player $it") }
        val active = start(actors)
        repeat(90) { now.addAndGet(5_000); service.tick() }
        val initial = read(actors[0], active.code)
        assertEquals(90, initial.round!!.called.size)
        fun claim(actor: GuestCredentials): RoomAction.Claim {
            val game = read(actor, active.code).round!!; val ticket = game.ownTickets.single()
            return RoomAction.Claim(game.id, 90, ticket.numbers.toSet(), ClaimSelection(ticket.id, Prize.TOP_LINE.name))
        }
        val request = CommandRequest(id(), initial.revision, claim(actors[0]))
        val receipt = service.command(actors[0].token, active.code, request)
        command(actors[1], active.code, claim(actors[1]))
        assertEquals(1400L, service.wallet(actors[0].token).balance)
        assertEquals(0L, read(actors[0], active.code).coins!!.settledWinnings)
        service = RoomService(database, now::get)
        now.addAndGet(5_000); service.tick()
        actors.forEachIndexed { index, actor ->
            val finished = read(actor, active.code); finished.validateFor(actor.playerId)
            assertEquals(RoomPhase.FINISHED, finished.phase)
            assertEquals(if (index < 2) 20L else 0L, finished.coins!!.settledWinnings)
            assertEquals(90L, finished.coins!!.returnedCoins)
            assertEquals(if (index < 2) 1510L else 1490L, service.wallet(actor.token).balance)
        }
        assertEquals(6000L, actors.sumOf { service.wallet(it.token).balance })
        val entries = count("coin_ledger")
        service.tick(); service.tick()
        assertEquals(entries, count("coin_ledger"))
        assertEquals(receipt, service.command(actors[0].token, active.code, request))
        assertEquals(1510L, service.wallet(actors[0].token).balance)
    }

    @Test fun `an interrupted countdown refunds reservations once and leaves no spent ghost room`() {
        val actors = listOf(guest(), guest())
        val first = match(actors[0], 6); match(actors[1], 6)
        now.set(first.coins!!.startsAt!! + 30_001); service.tick(); service.tick()
        assertEquals(RoomPhase.CLOSED, stored(first.code).phase)
        actors.forEach { assertEquals(1500L, service.wallet(it.token).balance) }
        assertNotEquals(first.roomId, match(actors[0], 1).roomId)
    }

    @Test fun `leaving an old lobby cannot hide an already purchased newer game`() {
        val actor = guest(); val peer = guest()
        val first = match(actor, 2); match(peer, 2)
        command(actor, first.code, RoomAction.Leave)
        now.set(first.coins!!.startsAt!!)
        val second = match(actor, 3)
        assertNotEquals(first.roomId, second.roomId)
        val again = match(actor, 6)
        assertEquals(second.roomId, again.roomId)
        assertEquals(3, again.coins!!.ownTickets)
        assertEquals(1200L, service.wallet(actor.token).balance)
    }

    @Test fun `insufficient balance rolls back the room membership purchase and receipt together`() {
        val host = guest(); val actor = guest()
        val before = match(host, 3)
        database.transaction { CoinLedger.change(it, actor.playerId, "spent:fixture", -1500, now.get()) }
        val request = MatchRequest(id(), 3)
        failure("coins_low") { service.match(actor.token, request) }
        assertEquals(before.revision, read(host, before.code).revision)
        assertEquals(listOf(host.playerId), read(host, before.code).members.map { it.playerId })
        assertEquals(1, count("rooms")); assertEquals(1, count("match_receipts"))
        service.refill(actor.token, RefillRequest(id()))
        val accepted = service.match(actor.token, request)
        assertEquals(before.roomId, accepted.snapshot.roomId)
        assertEquals(200L, accepted.snapshot.wallet!!.balance)
        assertEquals(accepted, service.match(actor.token, request))
    }

    @Test fun `event write failure rolls back the room participants debit and receipt before an exact retry`() {
        val host = guest(); val actor = guest()
        val before = match(host, 3)
        val record = stored(before.code)
        val request = MatchRequest(id(), 2)
        database.transaction {
            it.execute("""ALTER TABLE room_events ADD CONSTRAINT reject_purchase_event
                CHECK (payload::jsonb->>'type' <> 'tickets_bought') NOT VALID""")
        }
        assertEquals("23514", assertThrows(java.sql.SQLException::class.java) { service.match(actor.token, request) }.sqlState)
        assertEquals(record, stored(before.code))
        assertEquals(1500L, service.wallet(actor.token).balance)
        assertEquals(1, count("match_receipts"))
        database.transaction {
            assertTrue(it.query("SELECT 1 FROM room_participants WHERE player_id = ?", actor.playerId) { true }.isEmpty())
            assertEquals(1, it.query("SELECT count(*) FROM room_events WHERE room_id = ?", before.roomId) { row -> row.getInt(1) }.single())
            it.execute("ALTER TABLE room_events DROP CONSTRAINT reject_purchase_event")
        }
        val accepted = service.match(actor.token, request)
        assertEquals(before.roomId, accepted.snapshot.roomId)
        assertEquals(1300L, accepted.snapshot.wallet!!.balance)
        assertEquals(accepted, service.match(actor.token, request))
        assertEquals(2, count("match_receipts"))
    }

    @Test fun `deleting a queued profile removes its purchase and redacts others stored match receipts`() {
        val actor = guest("Delete this name"); val peer = guest("Peer")
        val first = match(actor, 6)
        val request = MatchRequest(id(), 1)
        service.match(peer.token, request)
        service.deleteProfile(actor.token, DeleteProfileRequest(id()), "coin-delete")
        val remaining = read(peer, first.code); remaining.validateFor(peer.playerId)
        assertEquals(listOf(peer.playerId), remaining.members.map { it.playerId })
        assertEquals(10, remaining.coins!!.tickets)
        val replay = service.match(peer.token, request)
        assertFalse(WireJson.encodeToString(replay).contains("Delete this name"))
        assertEquals(1, count("match_receipts"))
        assertEquals(1400L, service.wallet(peer.token).balance)
        assertFalse(stored(first.code).purchases.containsKey(actor.playerId))
    }

    @Test fun `computer fill is explicit and its quantities and prize pool remain fixed after start`() {
        val actor = guest(); val active = start(listOf(actor), listOf(1))
        active.validateFor(actor.playerId)
        assertEquals(3, active.round!!.players.count { it.computer })
        assertEquals(10, active.coins!!.tickets); assertEquals(1000L, active.coins!!.pool)
        assertEquals(6, active.coins!!.prizes.size)
        assertEquals(1, active.round!!.ownTickets.size)
        now.addAndGet(5_000); service.tick()
        assertEquals(active.coins!!.prizes, read(actor, active.code).coins!!.prizes)
        failure("use_matchmaking") { service.join(guest().token, active.code) }
        failure("use_matchmaking") { service.create(actor.token, CreateRoomRequest(id(), coinOptions())) }
    }
}
