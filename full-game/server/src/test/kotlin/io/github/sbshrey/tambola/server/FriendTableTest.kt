package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FriendTableTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest() = service.register(GuestRequest("Friend ${id().take(5)}"), id())
    private fun enter(actor: GuestCredentials, code: String? = null, tickets: Int = 1) =
        service.match(actor.token, MatchRequest(id(), tickets, friendTable = true, friendCode = code)).snapshot.also { it.validateFor(actor.playerId) }
    private fun read(actor: GuestCredentials, code: String) = service.read(actor.token, code).snapshot
    private fun command(actor: GuestCredentials, code: String, action: RoomAction) =
        service.command(actor.token, code, CommandRequest(id(), read(actor, code).revision, action)).snapshot
    private fun failure(code: String, block: () -> Unit) = assertEquals(code, assertThrows(ApiFailure::class.java, block).code)

    @Test fun `one ticket host waits for friends and quick play cannot enter the private table`() {
        val host = guest(); val lobby = enter(host)
        assertEquals(1400L, lobby.wallet!!.balance); assertEquals(100L, lobby.coins!!.pool)
        assertTrue(lobby.coins!!.prizes.isEmpty()); assertNull(lobby.coins!!.startsAt)
        assertEquals(0, lobby.options.computerPlayers)
        now.addAndGet(20_000); service.tick()
        assertEquals(RoomPhase.LOBBY, read(host, lobby.code).phase)
        failure("not_ready") { command(host, lobby.code, RoomAction.Start) }
        val publicGuest = guest()
        val quick = service.match(publicGuest.token, MatchRequest(id(), 1)).snapshot
        assertNotEquals(lobby.code, quick.code)
        assertFalse(quick.coins!!.friendTable)
        assertFalse(WireJson.encodeToString(quick).contains("friendTable"))
        assertEquals("{\"id\":\"test\",\"tickets\":1}", WireJson.encodeToString(MatchRequest("test", 1)))
        failure("friend_table_closed") { enter(guest(), quick.code) }
        failure("use_matchmaking") { service.join(guest().token, lobby.code) }
        failure("not_member") { service.read(guest().token, lobby.code) }
    }

    @Test fun `host starts purchased tickets with no computers and late purchases cannot enter`() {
        val host = guest(); val friend = guest(); val lobby = enter(host, tickets = 6)
        val joined = enter(friend, lobby.code, 3)
        assertEquals(900L, joined.coins!!.pool); assertEquals(1200L, joined.wallet!!.balance)
        failure("host_only") { command(friend, lobby.code, RoomAction.Start) }
        val active = command(host, lobby.code, RoomAction.Start)
        active.validateFor(host.playerId)
        assertEquals(RoomPhase.ACTIVE, active.phase)
        assertEquals(6, active.round!!.ownTickets.size)
        assertEquals(90, active.round!!.ownTickets.flatMap { it.numbers }.distinct().size)
        assertEquals(mapOf(host.playerId to 6, friend.playerId to 3), active.round!!.ticketCounts)
        assertTrue(active.round!!.players.none { it.computer }); assertNull(active.round!!.revealedOrder)
        val late = guest()
        failure("friend_table_closed") { enter(late, lobby.code) }
        assertEquals(1500L, service.wallet(late.token).balance)
        failure("not_lobby") { command(host, lobby.code, RoomAction.BuyTickets(1)) }
        failure("automatic_coin_round") { command(host, lobby.code, RoomAction.End) }
        val resumed = enter(host, tickets = 1)
        assertEquals(active.roomId, resumed.roomId); assertEquals(900L, resumed.wallet!!.balance)
        service = RoomService(database, now::get)
        assertEquals(6, read(host, lobby.code).round!!.ownTickets.size)
        now.addAndGet(5_000); service.tick()
        assertEquals(1, read(friend, lobby.code).round!!.called.size)
    }

    @Test fun `duplicate friend entry receipts preserve purchase once and reject reused ids`() {
        val host = guest(); val friend = guest(); val lobby = enter(host)
        val request = MatchRequest(id(), 3, true, lobby.code)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val responses = executor.invokeAll(List(4) { Callable { service.match(friend.token, request) } }).map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, responses.distinct().size); assertEquals(1200L, service.wallet(friend.token).balance)
            assertEquals(2, read(host, lobby.code).members.size)
            failure("id_reused") { service.match(friend.token, request.copy(friendCode = null)) }
        } finally { executor.shutdownNow() }
    }

    @Test fun `leaving transfers host and refunds exactly once while expiry refunds remaining friends`() {
        val host = guest(); val friend = guest(); val lobby = enter(host, tickets = 6)
        enter(friend, lobby.code, 2)
        val current = read(host, lobby.code)
        val leave = CommandRequest(id(), current.revision, RoomAction.Leave)
        val receipt = service.command(host.token, lobby.code, leave)
        assertEquals(receipt, service.command(host.token, lobby.code, leave))
        assertEquals(1500L, service.wallet(host.token).balance)
        assertEquals(friend.playerId, read(friend, lobby.code).hostId)
        now.set(lobby.expiresAt); service.tick(); service.tick()
        assertEquals(1500L, service.wallet(friend.token).balance)
        failure("room_closed") { service.read(friend.token, lobby.code) }
    }

    @Test fun `offline host transfers to a connected friend and all members must reconnect before start`() {
        val host = guest(); val friend = guest(); val lobby = enter(host)
        now.addAndGet(30_000); enter(friend, lobby.code)
        now.addAndGet(20_000); service.tick()
        assertEquals(friend.playerId, read(friend, lobby.code).hostId)
        failure("not_ready") { command(friend, lobby.code, RoomAction.Start) }
        read(host, lobby.code)
        assertEquals(RoomPhase.ACTIVE, command(friend, lobby.code, RoomAction.Start).phase)
    }

    @Test fun `eight friends fill the table and a ninth is not charged`() {
        val host = guest(); val lobby = enter(host)
        repeat(7) { enter(guest(), lobby.code) }
        val extra = guest()
        failure("room_full") { enter(extra, lobby.code) }
        assertEquals(1500L, service.wallet(extra.token).balance)
        assertEquals(8, read(host, lobby.code).members.size)
    }

    @Test fun `join racing start either enters the frozen pool or receives a refund free rejection`() {
        val host = guest(); val lobby = enter(host); enter(guest(), lobby.code)
        val late = guest(); val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = listOf(Callable { runCatching { enter(late, lobby.code) } }, Callable { runCatching { command(host, lobby.code, RoomAction.Start) } })
            val results = executor.invokeAll(tasks).map { it.get(15, TimeUnit.SECONDS) }
            val after = read(host, lobby.code)
            val active = if (after.phase == RoomPhase.LOBBY) command(host, lobby.code, RoomAction.Start) else after
            val admitted = active.members.any { it.playerId == late.playerId }
            assertEquals(if (admitted) 1400L else 1500L, service.wallet(late.token).balance)
            assertEquals(active.members.size * 100L, active.coins!!.pool)
            assertEquals(admitted, results[0].isSuccess)
        } finally { executor.shutdownNow() }
    }
}
