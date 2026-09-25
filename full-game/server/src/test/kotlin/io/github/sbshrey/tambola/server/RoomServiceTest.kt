package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.sql.SQLException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RoomServiceTest : PostgresTest() {
    private fun guest(name: String) = service.register(GuestRequest(name), UUID.randomUUID().toString())
    private fun id() = UUID.randomUUID().toString()
    private fun create(host: GuestCredentials, automatic: Boolean = false, options: RoomOptions? = null) =
        service.create(host.token, CreateRoomRequest(id(), options ?: RoomOptions(automaticCalling = automatic))).snapshot
    private fun command(actor: GuestCredentials, code: String, action: RoomAction): RoomView {
        val revision = service.read(actor.token, code).snapshot.revision
        return service.command(actor.token, code, CommandRequest(id(), revision, action)).snapshot
    }
    private fun start(host: GuestCredentials, other: GuestCredentials, automatic: Boolean = false, options: RoomOptions? = null): RoomView {
        val room = create(host, automatic, options)
        service.join(other.token, room.code)
        command(host, room.code, RoomAction.Ready(true))
        command(other, room.code, RoomAction.Ready(true))
        return command(host, room.code, RoomAction.Start)
    }
    private fun failure(status: Int, code: String, block: () -> Unit) {
        val error = assertThrows(ApiFailure::class.java, block)
        assertEquals(status, error.status); assertEquals(code, error.code)
    }

    @Test fun `authentication stores only token hash and supports revocation and expiry`() {
        val host = guest("Asha")
        val hashes = database.transaction { it.query("SELECT token_hash FROM guests") { row -> row.getString(1) } }
        assertEquals(listOf(digest(host.token)), hashes)
        assertFalse(hashes.contains(host.token))
        failure(401, "unauthorized") { service.create("invalid", CreateRoomRequest(id())) }
        service.revoke(host.token)
        failure(401, "unauthorized") { service.create(host.token, CreateRoomRequest(id())) }
        val next = guest("Bina")
        now.set(next.expiresAt)
        failure(401, "unauthorized") { service.create(next.token, CreateRoomRequest(id())) }
    }

    @Test fun `room creation is idempotent and changing reused request is rejected`() {
        val host = guest("Asha")
        val request = CreateRoomRequest(id())
        val first = service.create(host.token, request)
        assertEquals(first, service.create(host.token, request))
        failure(409, "id_reused") { service.create(host.token, request.copy(options = RoomOptions(capacity = 4))) }
        assertEquals(1, database.transaction { it.query("SELECT count(*) FROM rooms") { row -> row.getInt(1) }.single() })
    }

    @Test fun `lobby enforces membership capacity readiness host controls and configuration freeze`() {
        val host = guest("Asha"); val other = guest("Bina"); val stranger = guest("Chirag")
        val room = create(host, options = RoomOptions(capacity = 2))
        failure(403, "not_member") { service.read(stranger.token, room.code) }
        command(host, room.code, RoomAction.Lock(true))
        failure(409, "room_locked") { service.join(other.token, room.code) }
        command(host, room.code, RoomAction.Lock(false))
        service.join(other.token, room.code)
        failure(409, "room_full") { service.join(stranger.token, room.code) }
        failure(403, "host_only") { command(other, room.code, RoomAction.Start) }
        failure(409, "not_ready") { command(host, room.code, RoomAction.Start) }
        command(host, room.code, RoomAction.Ready(true)); command(other, room.code, RoomAction.Ready(true))
        val reset = command(host, room.code, RoomAction.Configure(RoomOptions(capacity = 2)))
        assertTrue(reset.members.none { it.ready })
        command(host, room.code, RoomAction.Ready(true)); command(other, room.code, RoomAction.Ready(true))
        command(host, room.code, RoomAction.Start)
        failure(409, "not_lobby") { command(host, room.code, RoomAction.Configure(RoomOptions())) }
        failure(409, "room_locked") { service.join(stranger.token, room.code) }
        failure(409, "round_in_progress") { command(other, room.code, RoomAction.Leave) }
    }

    @Test fun `snapshots never disclose future draws nonce or another player's ticket`() {
        val host = guest("Asha"); val other = guest("Bina")
        val started = start(host, other)
        val privateRoom = database.transaction { it.query("SELECT payload FROM rooms") { row -> WireJson.decodeFromString<RoomRecord>(row.getString(1)) }.single() }
        val views = listOf(started, service.read(other.token, started.code).snapshot)
        views.zip(listOf(host, other)).forEach { (view, player) ->
            assertTrue(view.round!!.ownTickets.all { it.playerId == player.playerId })
            assertNull(view.round!!.revealedOrder); assertNull(view.round!!.revealedNonce)
            val encoded = WireJson.encodeToString(view)
            assertFalse(encoded.contains(privateRoom.nonce!!))
            assertFalse(encoded.contains("drawOrder"))
            privateRoom.round!!.tickets.filterNot { it.playerId == player.playerId }.forEach { assertFalse(encoded.contains(it.id)) }
        }
        val ended = command(host, started.code, RoomAction.End)
        assertEquals(privateRoom.round!!.drawOrder, ended.round!!.revealedOrder)
        assertEquals(started.round!!.drawCommitment, commitment(privateRoom.round!!, ended.round!!.revealedNonce!!))
    }

    @Test fun `concurrent retries draw exactly once and different commands reject a stale revision`() {
        val host = guest("Asha"); val other = guest("Bina")
        val room = start(host, other)
        val request = CommandRequest(id(), room.revision, RoomAction.Draw)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll(List(4) { Callable { service.command(host.token, room.code, request) } }).map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(results.all { it == results.first() })
            assertEquals(1, results.first().snapshot.round!!.called.size)
            failure(409, "id_reused") { service.command(host.token, room.code, request.copy(action = RoomAction.Pause)) }
            val revision = results.first().snapshot.revision
            val races = executor.invokeAll(List(2) { Callable { runCatching { service.command(host.token, room.code, CommandRequest(id(), revision, RoomAction.Draw)) } } }).map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, races.count { it.isSuccess })
            assertEquals("stale_revision", (races.first { it.isFailure }.exceptionOrNull() as ApiFailure).code)
            assertEquals(2, service.read(host.token, room.code).snapshot.round!!.called.size)
        } finally { executor.shutdownNow() }
    }

    @Test fun `event write failure rolls back room state and receipt before retry`() {
        val host = guest("Asha"); val other = guest("Bina")
        val room = start(host, other)
        val request = CommandRequest(id(), room.revision, RoomAction.Draw)
        database.transaction { it.execute("INSERT INTO room_events VALUES (?, ?, ?)", room.roomId, room.revision + 1,
            WireJson.encodeToString(RoomEvent(room.revision + 1, "injected-conflict", now.get()))) }
        assertThrows(SQLException::class.java) { service.command(host.token, room.code, request) }
        val unchanged = service.read(host.token, room.code).snapshot
        assertEquals(room.revision, unchanged.revision); assertTrue(unchanged.round!!.called.isEmpty())
        val receipts = database.transaction { connection ->
            val count = connection.query("SELECT count(*) FROM command_receipts WHERE command_id = ?", request.id) { it.getInt(1) }.single()
            connection.execute("DELETE FROM room_events WHERE room_id = ? AND revision = ?", room.roomId, room.revision + 1)
            count
        }
        assertEquals(0, receipts)
        assertEquals(1, service.command(host.token, room.code, request).snapshot.round!!.called.size)
    }

    @Test fun `two schedulers and a replacement service preserve one durable draw per interval`() {
        val host = guest("Asha"); val other = guest("Bina")
        val room = start(host, other, automatic = true)
        failure(409, "automatic_calling") { command(host, room.code, RoomAction.Draw) }
        now.addAndGet(10_000)
        val replacement = RoomService(database, now::get)
        val executor = Executors.newFixedThreadPool(2)
        try {
            executor.invokeAll(listOf(Callable { service.tick() }, Callable { replacement.tick() })).forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, replacement.read(host.token, room.code).snapshot.round!!.called.size)
            now.addAndGet(60_000) // delayed worker draws once; it does not burst six calls
            replacement.tick()
            val recovered = replacement.read(host.token, room.code)
            assertEquals(2, recovered.snapshot.round!!.called.size)
            assertEquals(now.get() + 10_000, recovered.snapshot.nextDrawAt)
            assertTrue(recovered.events.isEmpty())
            assertTrue(recovered.resyncRequired)
        } finally { executor.shutdownNow() }
    }

    @Test fun `disconnected host transfers controls while a connected guest keeps the round alive`() {
        val host = guest("Asha"); val other = guest("Bina")
        val room = start(host, other, automatic = true)
        repeat(3) { now.addAndGet(16_000); service.read(other.token, room.code) }
        service.tick()
        val changed = service.read(other.token, room.code).snapshot
        assertEquals(other.playerId, changed.hostId)
        assertFalse(changed.members.first { it.playerId == host.playerId }.connected)
        failure(403, "host_only") { command(host, room.code, RoomAction.Pause) }
        val paused = command(other, room.code, RoomAction.Pause)
        assertNull(paused.nextDrawAt)
        val calls = paused.round!!.called.size
        now.addAndGet(20_000); service.tick()
        assertEquals(calls, service.read(other.token, room.code).snapshot.round!!.called.size)
        command(other, room.code, RoomAction.Resume)
        now.addAndGet(10_000); service.tick()
        assertEquals(calls + 1, service.read(other.token, room.code).snapshot.round!!.called.size)
    }

    @Test fun `complete online round custom prizes audit and rematch share domain rules`() {
        val host = guest("Asha"); val other = guest("Bina")
        val custom = CustomPrize("custom_two", "First two", 7, TicketPattern(listOf(listOf(RuleCondition(NumberSelection.All, 2)))))
        val options = RoomOptions(game = RoundSettings(mode = GameMode.ONLINE, playAllNumbers = true, customPrizes = listOf(custom)), automaticCalling = false)
        var room = start(host, other, options = options)
        repeat(90) { room = command(host, room.code, RoomAction.Draw) }
        assertEquals(RoomPhase.FINISHED, room.phase)
        assertEquals((1..90).toSet(), room.round!!.called.toSet())
        assertEquals(1, room.round!!.customAwards.size)
        assertEquals(90, room.round!!.revealedOrder!!.size)
        val saved = database.transaction { it.query("SELECT payload FROM finished_rounds") { row -> WireJson.decodeFromString<RoomRecord>(row.getString(1)) }.single() }
        saved.round!!.validated()
        assertEquals(saved.round!!.players.associate { it.id to saved.round!!.score(it.id) }, room.round!!.scores)
        val lobby = command(host, room.code, RoomAction.Rematch)
        assertEquals(RoomPhase.LOBBY, lobby.phase); assertNull(lobby.round); assertTrue(lobby.members.none { it.ready })
        assertEquals(1, database.transaction { it.query("SELECT count(*) FROM finished_rounds") { row -> row.getInt(1) }.single() })
    }

    @Test fun `durable event replay detects stale and future cursors`() {
        val host = guest("Asha"); val other = guest("Bina")
        val room = start(host, other)
        val drawn = command(host, room.code, RoomAction.Draw)
        val recovered = RoomService(database, now::get).read(other.token, room.code, room.revision)
        assertEquals(listOf("drawn"), recovered.events.map { it.type })
        assertEquals(drawn.revision, recovered.events.single().revision)
        assertFalse(recovered.resyncRequired)
        assertTrue(service.read(host.token, room.code, drawn.revision + 1).resyncRequired)
        // Simulate a long lobby history without retaining every old event.
        database.transaction { connection ->
            val stored = connection.query("SELECT payload FROM rooms") { WireJson.decodeFromString<RoomRecord>(it.getString(1)) }.single()
            connection.execute("UPDATE rooms SET payload = ?", WireJson.encodeToString(stored.copy(revision = EVENT_LIMIT + 5)))
        }
        assertTrue(service.read(host.token, room.code, 0).resyncRequired)
    }

    @Test fun `guest rate limits are durable across service instances and room expiry is enforced`() {
        repeat(60) { service.register(GuestRequest("Player $it"), "same-source") }
        failure(429, "rate_limited") { RoomService(database, now::get).register(GuestRequest("Another"), "same-source") }
        now.addAndGet(60_000)
        val host = service.register(GuestRequest("Asha"), "same-source")
        val room = create(host)
        now.addAndGet(ROOM_LIFETIME)
        failure(410, "room_closed") { service.read(host.token, room.code) }
        service.tick()
        assertEquals("CLOSED", database.transaction { it.query("SELECT phase FROM rooms") { row -> row.getString(1) }.single() })
        now.addAndGet(30 * ROOM_LIFETIME + 1); service.cleanup()
        assertEquals(0, database.transaction { it.query("SELECT count(*) FROM rooms") { row -> row.getInt(1) }.single() })
    }

    @Test fun `leaving and removing lobby members revoke access and select another host`() {
        val host = guest("Asha"); val other = guest("Bina"); val third = guest("Chirag")
        val room = create(host); service.join(other.token, room.code); service.join(third.token, room.code)
        command(host, room.code, RoomAction.Remove(third.playerId))
        failure(403, "not_member") { service.read(third.token, room.code) }
        val revision = service.read(host.token, room.code).snapshot.revision
        val leave = CommandRequest(id(), revision, RoomAction.Leave)
        val left = service.command(host.token, room.code, leave)
        assertEquals(other.playerId, left.snapshot.hostId)
        assertEquals(left, service.command(host.token, room.code, leave))
        failure(403, "not_member") { service.read(host.token, room.code) }
    }

    @Test fun `32 players with six tickets complete 90 automatic draws with 192 unique tickets`() {
        val players = (1..32).map { guest("Player $it") }
        val host = players.first()
        val room = create(host, options = RoomOptions(game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, playAllNumbers = true), intervalSeconds = 5))
        players.drop(1).forEach { service.join(it.token, room.code) }
        players.forEach { command(it, room.code, RoomAction.Ready(true)) }
        command(host, room.code, RoomAction.Start)
        val tickets = players.flatMap { service.read(it.token, room.code).snapshot.round!!.ownTickets }
        assertEquals(192, tickets.size); assertEquals(192, tickets.map { it.fingerprint }.distinct().size)
        repeat(90) { now.addAndGet(5_000); service.tick() }
        val result = service.read(host.token, room.code).snapshot
        assertEquals(RoomPhase.FINISHED, result.phase); assertEquals(90, result.round!!.called.size)
        assertEquals(32, result.round!!.scores.size)
        assertTrue(result.round!!.awards.any { it.prize == Prize.FULL_HOUSE })
    }
}
