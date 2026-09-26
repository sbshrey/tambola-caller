package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import io.github.sbshrey.tambola.client.validateFor
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ManualOnlinePlayTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest(name: String) = service.register(GuestRequest(name), id())
    private fun command(actor: GuestCredentials, code: String, action: RoomAction): RoomView =
        service.command(actor.token, code, CommandRequest(id(), service.read(actor.token, code).snapshot.revision, action)).snapshot
    private fun options(bots: Int = 0, capacity: Int = 32, automatic: Boolean = false) = RoomOptions(
        game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 2, manualClaims = true, playAllNumbers = true),
        capacity = capacity, intervalSeconds = 5, automaticCalling = automatic, computerPlayers = bots)
    private fun start(host: GuestCredentials, peer: GuestCredentials? = null, opts: RoomOptions = options()): RoomView {
        val room = service.create(host.token, CreateRoomRequest(id(), opts)).snapshot
        peer?.let { service.join(it.token, room.code); command(it, room.code, RoomAction.Ready(true)) }
        command(host, room.code, RoomAction.Ready(true))
        return command(host, room.code, RoomAction.Start)
    }
    private fun stored(code: String) = database.transaction { connection ->
        connection.query("SELECT payload FROM rooms WHERE code = ?", code) { WireJson.decodeFromString<RoomRecord>(it.getString(1)) }.single()
    }
    private fun proof(room: RoomView) = RoomAction.Claim(room.round!!.id, room.round!!.called.size,
        room.round!!.ownTickets.flatMap { it.numbers }.filter { it in room.round!!.called }.toSet(),
        ClaimSelection(room.round!!.ownTickets.first().id, Prize.EARLY_FIVE.name))
    private fun failure(status: Int, code: String, action: () -> Unit) {
        val error = assertThrows(ApiFailure::class.java, action)
        assertEquals(status, error.status); assertEquals(code, error.code)
    }

    @Test fun `one command claims only chosen ticket and scheme and same-call claimant shares it despite old revision`() {
        val host = guest("Asha"); val peer = guest("Bina")
        var room = start(host, peer)
        repeat(90) { room = command(host, room.code, RoomAction.Draw) }
        assertTrue(room.round!!.awards.isEmpty()); assertEquals(RoomPhase.ACTIVE, room.phase)
        val beforePeer = service.read(peer.token, room.code).snapshot
        val first = command(host, room.code, proof(room))
        assertEquals(1, first.round!!.awards.size)
        assertEquals(listOf(room.round!!.ownTickets.first().id), first.round!!.awards.single().ticketIds)
        val stale = CommandRequest(id(), beforePeer.revision, proof(beforePeer))
        val tied = service.command(peer.token, room.code, stale).snapshot
        assertTrue(tied.round!!.awards.all { it.playerIds.toSet() == setOf(host.playerId, peer.playerId) })
        assertEquals(Prize.EARLY_FIVE.points, tied.round!!.scores[peer.playerId])
        tied.validateFor(peer.playerId)
        assertTrue(tied.round!!.ownTickets.all { it.playerId == peer.playerId })
        assertFalse(WireJson.encodeToString(tied).contains("submissions"))
        val finished = command(host, room.code, RoomAction.Draw)
        assertEquals(RoomPhase.FINISHED, finished.phase); assertEquals(90, finished.round!!.called.size)
        stored(room.code).round!!.validated()
        // A response lost before the final window closed retains its exact receipt.
        assertEquals(tied, service.command(peer.token, room.code, stale).snapshot)
    }

    @Test fun `claim identity and marks cannot target another call round or player`() {
        val host = guest("Asha"); val peer = guest("Bina"); val outsider = guest("Chirag")
        val room = command(host, start(host, peer).code, RoomAction.Draw)
        val goodIdentity = proof(room)
        failure(403, "not_member") { service.command(outsider.token, room.code, CommandRequest(id(), room.revision, goodIdentity)) }
        failure(409, "claim_round_changed") { command(host, room.code, goodIdentity.copy(roundId = id())) }
        failure(400, "invalid_claim_selection") { command(host, room.code, goodIdentity.copy(selection = goodIdentity.selection.copy(ticketId = "someone-elses-ticket"))) }
        failure(400, "invalid_claim_selection") { command(host, room.code, goodIdentity.copy(selection = goodIdentity.selection.copy(prizeId = "not-enabled"))) }
        failure(409, "stale_revision") { service.command(host.token, room.code, CommandRequest(id(), room.revision + 5, goodIdentity)) }
        val uncalled = room.round!!.ownTickets.flatMap { it.numbers }.first { it !in room.round!!.called }
        failure(400, "invalid_claim_marks") { command(host, room.code, goodIdentity.copy(markedNumbers = setOf(uncalled))) }
        failure(422, "no_valid_claim") { command(host, room.code, goodIdentity.copy(markedNumbers = emptySet())) }
        command(host, room.code, RoomAction.Draw)
        failure(409, "claim_window_closed") { service.command(host.token, room.code, CommandRequest(id(), room.revision, goodIdentity)) }
        assertTrue(service.read(host.token, room.code).snapshot.round!!.awards.isEmpty())
    }

    @Test fun `concurrent retries produce one claim proof and one receipt`() {
        val host = guest("Asha"); val peer = guest("Bina")
        var room = start(host, peer)
        repeat(90) { room = command(host, room.code, RoomAction.Draw) }
        val request = CommandRequest(id(), room.revision, proof(room))
        val executor = Executors.newFixedThreadPool(4)
        try {
            val replies = executor.invokeAll(List(4) { Callable { service.command(host.token, room.code, request) } }).map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(replies.all { it == replies.first() })
            assertEquals(1, stored(room.code).round!!.claims.size)
            failure(409, "id_reused") { service.command(host.token, room.code, request.copy(action = proof(room).copy(markedNumbers = emptySet()))) }
        } finally { executor.shutdownNow() }
    }

    @Test fun `computer seats are explicit do not register guests and count toward capacity`() {
        val host = guest("Asha"); val peer = guest("Bina")
        val lobby = service.create(host.token, CreateRoomRequest(id(), options(bots = 2, capacity = 3))).snapshot
        failure(409, "room_full") { service.join(peer.token, lobby.code) }
        command(host, lobby.code, RoomAction.Ready(true))
        val active = command(host, lobby.code, RoomAction.Start)
        assertEquals(1, active.members.size)
        assertEquals(2, active.round!!.players.count { it.computer })
        assertEquals(3, active.round!!.players.size)
        assertTrue(active.round!!.ownTickets.all { it.playerId == host.playerId })
        assertEquals(2, database.transaction { it.query("SELECT count(*) FROM guests") { row -> row.getInt(1) }.single() })
        active.validateFor(host.playerId)
        stored(active.code).round!!.validated()
    }

    @Test fun `computer claim reactions survive service restart and never reveal private cards`() {
        val host = guest("Asha")
        var room = start(host, opts = options(bots = 1))
        while (stored(room.code).computerClaimsAt.isEmpty()) room = command(host, room.code, RoomAction.Draw)
        val scheduled = stored(room.code)
        val deadline = scheduled.computerClaimsAt.values.min()
        assertTrue(deadline - now.get() in 1_200..2_800)
        assertTrue(room.round!!.awards.isEmpty())
        now.set(deadline - 1); service.tick()
        assertTrue(service.read(host.token, room.code).snapshot.round!!.awards.isEmpty())
        service = RoomService(database, now::get)
        now.set(deadline); service.tick()
        val after = service.read(host.token, room.code).snapshot
        assertTrue(after.round!!.awards.isNotEmpty())
        assertEquals(room.round!!.called, after.round!!.called)
        assertTrue(after.round!!.ownTickets.all { it.playerId == host.playerId })
        assertTrue(stored(room.code).computerClaimsAt.isEmpty())
        stored(room.code).round!!.validated()
        after.validateFor(host.playerId)
    }

    @Test fun `pause cancels reactions and resume reschedules without duplicate awards`() {
        val host = guest("Asha")
        var room = start(host, opts = options(bots = 1))
        while (stored(room.code).computerClaimsAt.isEmpty()) room = command(host, room.code, RoomAction.Draw)
        val paused = command(host, room.code, RoomAction.Pause)
        assertTrue(stored(room.code).computerClaimsAt.isEmpty())
        now.addAndGet(3_000); service.tick()
        assertEquals(paused.round!!.awards, service.read(host.token, room.code).snapshot.round!!.awards)
        command(host, room.code, RoomAction.Resume)
        assertTrue(stored(room.code).computerClaimsAt.isNotEmpty())
        now.addAndGet(3_000); service.tick()
        val claimed = service.read(host.token, room.code).snapshot
        service.tick()
        assertEquals(claimed.round!!.awards, service.read(host.token, room.code).snapshot.round!!.awards)
        stored(room.code).round!!.validated()
    }

    @Test fun `mixed rematch creates fresh private tickets and preserves agreed bot seats`() {
        val host = guest("Asha"); val peer = guest("Bina")
        val first = start(host, peer, options(bots = 2, capacity = 4))
        assertEquals(4, first.round!!.players.size)
        command(host, first.code, RoomAction.End)
        command(host, first.code, RoomAction.Rematch)
        command(host, first.code, RoomAction.Ready(true)); command(peer, first.code, RoomAction.Ready(true))
        val next = command(host, first.code, RoomAction.Start)
        assertNotEquals(first.round!!.id, next.round!!.id)
        assertNotEquals(first.round!!.ownTickets, next.round!!.ownTickets)
        assertEquals(2, next.round!!.players.count { it.computer })
        assertTrue(next.round!!.awards.isEmpty()); assertTrue(stored(first.code).computerClaimsAt.isEmpty())
        next.validateFor(host.playerId)
        service.read(peer.token, first.code).snapshot.validateFor(peer.playerId)
    }

    @Test fun `last human leaving by profile deletion closes computer room and cancels pending reactions`() {
        val host = guest("Asha")
        var room = start(host, opts = options(bots = 1))
        while (stored(room.code).computerClaimsAt.isEmpty()) room = command(host, room.code, RoomAction.Draw)
        service.deleteProfile(host.token, DeleteProfileRequest(id()), "fixture")
        val closed = stored(room.code)
        assertEquals(RoomPhase.CLOSED, closed.phase)
        assertTrue(closed.computerClaimsAt.isEmpty()); assertNull(closed.nextDrawAt)
        assertEquals(RoundStatus.CANCELLED, closed.round!!.status)
        assertFalse(closed.round!!.players.any { it.name == "Asha" })
        closed.round!!.validated()
    }
}
