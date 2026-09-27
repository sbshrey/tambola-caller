package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FriendReplayTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest() = service.register(GuestRequest("Replay QA"), id())
    private fun read(actor: GuestCredentials, code: String) = service.read(actor.token, code).snapshot
    private fun command(actor: GuestCredentials, code: String, action: RoomAction) =
        service.command(actor.token, code, CommandRequest(id(), read(actor, code).revision, action)).snapshot
    private fun finish(vararg actors: GuestCredentials): RoomView {
        val room = service.match(actors[0].token, MatchRequest(id(), 1, true)).snapshot
        actors.drop(1).forEach { service.match(it.token, MatchRequest(id(), 1, true, room.code)) }
        command(actors[0], room.code, RoomAction.Start)
        repeat(91) { now.addAndGet(5_000); service.tick() }
        return read(actors[0], room.code).also { assertEquals(RoomPhase.FINISHED, it.phase) }
    }
    private fun request(room: RoomView, tickets: Int = 3) = MatchRequest(id(), tickets, true, room.code, room.round!!.id)
    private fun failure(code: String, block: () -> Unit) = assertEquals(code, assertThrows(ApiFailure::class.java, block).code)

    @Test fun `simultaneous replay clicks buy one shared successor and preserve old results and exact receipts`() {
        val host = guest(); val friend = guest(); val previous = finish(host, friend)
        val requests = listOf(request(previous, 2), request(previous, 3))
        val executor = Executors.newFixedThreadPool(2)
        val updates = try {
            executor.invokeAll(listOf(host, friend).mapIndexed { index, actor -> Callable { service.match(actor.token, requests[index]) } })
                .map { it.get(15, TimeUnit.SECONDS) }
        } finally { executor.shutdownNow() }
        val next = read(host, updates[0].snapshot.code)
        assertEquals(next.roomId, updates[1].snapshot.roomId)
        assertNotEquals(previous.roomId, next.roomId); assertNotEquals(previous.code, next.code)
        assertEquals(2, next.members.size); assertEquals(500L, next.coins!!.pool)
        assertEquals(1300L, service.wallet(host.token).balance); assertEquals(1200L, service.wallet(friend.token).balance)
        assertEquals(updates[0], service.match(host.token, requests[0]))
        failure("id_reused") { service.match(host.token, requests[0].copy(previousFriendRound = id())) }
        // Another click and service restart must reconnect to the existing purchase.
        service = RoomService(database, now::get)
        assertEquals(next.roomId, service.match(friend.token, request(previous, 6)).snapshot.roomId)
        assertEquals(1200L, service.wallet(friend.token).balance)
        assertEquals(previous.round, read(host, previous.code).round)
        assertEquals(previous.coins, read(host, previous.code).coins)
        val starter = if (next.hostId == host.playerId) host else friend
        val active = command(starter, next.code, RoomAction.Start)
        active.validateFor(starter.playerId)
        assertNotEquals(previous.round!!.id, active.round!!.id)
        assertEquals(mapOf(host.playerId to 2, friend.playerId to 3), active.round!!.ticketCounts)
        assertTrue(active.round!!.players.none { it.computer })
    }

    @Test fun `replay needs the original membership and completed round and never charges an unconsenting friend`() {
        val host = guest(); val friend = guest(); val outsider = guest(); val previous = finish(host, friend)
        failure("not_member") { service.match(outsider.token, request(previous)) }
        failure("friend_round_changed") { service.match(host.token, request(previous).copy(previousFriendRound = id())) }
        failure("friend_table_closed") { service.match(friend.token, MatchRequest(id(), 3, true, previous.code)) }
        val next = service.match(host.token, request(previous, 6)).snapshot
        assertEquals(listOf(host.playerId), next.members.map { it.playerId })
        assertEquals(1500L, service.wallet(friend.token).balance)
        assertEquals(1500L, service.wallet(outsider.token).balance)
        failure("not_ready") { command(host, next.code, RoomAction.Start) }
        assertEquals(previous.round!!.id, read(friend, previous.code).round!!.id)
    }

    @Test fun `failed first replay purchase rolls back its link and another friend can host`() {
        val host = guest(); val friend = guest(); val previous = finish(host, friend)
        database.transaction { CoinLedger.change(it, host.playerId, "qa-spend", -1500, now.get()) }
        failure("coins_low") { service.match(host.token, request(previous)) }
        val next = service.match(friend.token, request(previous, 2)).snapshot
        assertEquals(friend.playerId, next.hostId)
        assertEquals(1300L, service.wallet(friend.token).balance)
        database.transaction { connection ->
            assertEquals(2, connection.query("SELECT count(*) FROM rooms") { it.getInt(1) }.single())
        }
        service.refill(host.token, RefillRequest(id()))
        assertEquals(next.roomId, service.match(host.token, request(previous, 1)).snapshot.roomId)
        assertEquals(400L, service.wallet(host.token).balance)
    }

    @Test fun `expired successor refunds and stale replay cannot silently open a different round`() {
        val host = guest(); val friend = guest(); val previous = finish(host, friend)
        val next = service.match(host.token, request(previous, 6)).snapshot
        now.set(next.expiresAt); service.tick()
        failure("room_closed") { service.match(friend.token, request(previous)) }
        assertEquals(1500L, service.wallet(host.token).balance)
        assertEquals(1500L, service.wallet(friend.token).balance)
        database.transaction { connection -> assertEquals(2, connection.query("SELECT count(*) FROM rooms") { it.getInt(1) }.single()) }
    }

    @Test fun `late replay cannot buy into a successor that has already started`() {
        val host = guest(); val friend = guest(); val late = guest(); val previous = finish(host, friend, late)
        val next = service.match(host.token, request(previous, 2)).snapshot
        service.match(friend.token, request(previous, 3))
        command(host, next.code, RoomAction.Start)
        failure("friend_table_closed") { service.match(late.token, request(previous)) }
        assertEquals(1500L, service.wallet(late.token).balance)
        assertEquals(500L, read(host, next.code).coins!!.pool)
    }
}
