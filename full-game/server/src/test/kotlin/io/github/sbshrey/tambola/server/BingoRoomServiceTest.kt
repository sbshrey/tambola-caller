package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import io.github.sbshrey.tambola.domain.BingoPattern
import io.github.sbshrey.tambola.client.validateFor
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BingoRoomServiceTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest(name: String = "Bingo player") = service.register(GuestRequest(name), id())
    private fun failure(code: String, action: () -> Unit) = assertEquals(code, assertThrows(ApiFailure::class.java, action).code)
    private fun stored(code: String) = database.transaction { c ->
        c.query("SELECT payload FROM bingo_rooms WHERE code = ?", code) { WireJson.decodeFromString<BingoRoomRecord>(it.getString(1)) }.single()
    }

    @Test fun `quick Bingo starts after ten seconds with clearly flagged computer seats`() {
        val actor = guest("Early player")
        val room = service.bingo.match(actor.token, BingoMatchRequest(id(), 2, realPlayersOnly = false))
        assertEquals(10_000L, room.startsAt!! - now.get())
        now.set(room.startsAt!! - 1); service.tick()
        assertEquals(RoomPhase.LOBBY, service.bingo.read(actor.token, room.code).phase)
        now.incrementAndGet(); service.tick()
        val active = service.bingo.read(actor.token, room.code)
        assertEquals(RoomPhase.ACTIVE, active.phase)
        assertEquals(1, active.players.count { !it.computer })
        assertTrue(active.players.count { it.computer } in 29..49)
        assertEquals(1300L, service.wallet(actor.token).balance)
    }

    @Test fun `real player queue waits for a peer and starts without computer seats`() {
        val legacy = service.bingo.match(guest("Legacy").token, BingoMatchRequest(id(), 1))
        val first = guest("First")
        val second = guest("Second")
        val request = BingoMatchRequest(id(), 2, realPlayersOnly = true)
        val room = service.bingo.match(first.token, request)
        assertNotEquals(legacy.code, room.code)
        assertEquals(1, room.players.size)
        now.set(room.startsAt!!); service.tick()
        val waiting = service.bingo.read(first.token, room.code)
        assertEquals(RoomPhase.LOBBY, waiting.phase)
        assertEquals(1, waiting.players.size)
        assertTrue(waiting.startsAt!! > now.get())
        val joined = service.bingo.match(second.token, BingoMatchRequest(id(), 1, realPlayersOnly = true))
        assertEquals(room.code, joined.code)
        now.set(joined.startsAt!!); service.tick()
        val active = service.bingo.read(first.token, room.code)
        assertEquals(RoomPhase.ACTIVE, active.phase)
        assertEquals(2, active.players.size)
        assertTrue(active.players.none { it.computer })
        assertEquals(3, active.round!!.cardCounts.values.sum())
    }

    @Test fun `unmatched real player Bingo queue refunds at its wait limit`() {
        val actor = guest()
        val room = service.bingo.match(actor.token, BingoMatchRequest(id(), 3, realPlayersOnly = true))
        assertEquals(1200L, service.wallet(actor.token).balance)
        now.set(room.startsAt!!); service.tick()
        assertEquals(RoomPhase.LOBBY, stored(room.code).phase)
        now.set(stored(room.code).createdAt + MATCH_WAIT_LIMIT); service.tick()
        assertEquals(RoomPhase.CLOSED, stored(room.code).phase)
        assertEquals(1500L, service.wallet(actor.token).balance)
    }

    @Test fun `concurrent purchase retries and leave refund each apply once`() {
        val actor = guest()
        val request = BingoMatchRequest(id(), 6)
        val executor = Executors.newFixedThreadPool(4)
        val room = try {
            val responses = executor.invokeAll(List(4) { Callable { service.bingo.match(actor.token, request) } })
                .map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, responses.distinct().size)
            responses.first()
        } finally { executor.shutdownNow() }
        assertEquals(900L, service.wallet(actor.token).balance)
        failure("id_reused") { service.bingo.match(actor.token, request.copy(cards = 1)) }
        val leave = BingoCommandRequest(id(), room.revision, BingoAction.Leave)
        val left = service.bingo.command(actor.token, room.code, leave)
        assertEquals(RoomPhase.CLOSED, left.phase)
        assertEquals(1500L, left.wallet!!.balance)
        assertEquals(left, service.bingo.command(actor.token, room.code, leave))
        assertEquals(1500L, service.wallet(actor.token).balance)
    }

    @Test fun `game queues remain isolated and leaving releases shared profile`() {
        val actor = guest()
        val room = service.bingo.match(actor.token, BingoMatchRequest(id(), 1))
        failure("other_game_active") { service.match(actor.token, MatchRequest(id(), 1)) }
        service.bingo.command(actor.token, room.code, BingoCommandRequest(id(), room.revision, BingoAction.Leave))
        service.match(actor.token, MatchRequest(id(), 1))
        failure("other_game_active") { service.bingo.match(actor.token, BingoMatchRequest(id(), 1)) }
        assertEquals(1400L, service.wallet(actor.token).balance)
    }

    @Test fun `receipt write failure rolls back room seat and debit before retry`() {
        val actor = guest()
        val request = BingoMatchRequest(id(), 6)
        database.transaction { it.execute("ALTER TABLE bingo_receipts ADD CONSTRAINT reject_bingo_receipt CHECK (false) NOT VALID") }
        assertEquals("23514", assertThrows(java.sql.SQLException::class.java) { service.bingo.match(actor.token, request) }.sqlState)
        assertEquals(1500L, service.wallet(actor.token).balance)
        database.transaction { connection ->
            listOf("bingo_rooms", "bingo_participants", "bingo_receipts").forEach { table ->
                assertEquals(0, connection.query("SELECT count(*) FROM $table") { it.getInt(1) }.single())
            }
            connection.execute("ALTER TABLE bingo_receipts DROP CONSTRAINT reject_bingo_receipt")
        }
        val accepted = service.bingo.match(actor.token, request)
        assertEquals(900L, accepted.wallet!!.balance)
        assertEquals(accepted, service.bingo.match(actor.token, request))
    }

    @Test fun `progressive population private cards and reconnect survive service restart`() {
        val actor = guest()
        val room = service.bingo.match(actor.token, BingoMatchRequest(id(), 6))
        assertEquals(1, room.players.size)
        now.addAndGet(5000); service.tick()
        val halfway = service.bingo.read(actor.token, room.code)
        assertTrue(halfway.players.size in 2..29)
        now.set(room.startsAt!!); service.tick()
        val active = service.bingo.read(actor.token, room.code)
        assertEquals(RoomPhase.ACTIVE, active.phase)
        assertTrue(active.players.size in 30..50)
        assertTrue(active.cardCounts.values.all { it in 1..6 })
        assertEquals(6, active.round!!.ownCards.size)
        assertTrue(active.round!!.ownCards.all { it.playerId == actor.playerId })
        assertNull(active.round!!.revealedOrder); assertNull(active.round!!.revealedNonce)
        failure("not_member") { service.bingo.read(guest("Other").token, room.code) }
        service = RoomService(database, now::get)
        assertEquals(active, service.bingo.read(actor.token, room.code))
        assertEquals(active, service.bingo.match(actor.token, BingoMatchRequest(id(), 1)))
        assertEquals(900L, service.wallet(actor.token).balance)
    }

    @Test fun `late host recovery refunds a queued purchase without starting a missed game`() {
        val actor = guest()
        val room = service.bingo.match(actor.token, BingoMatchRequest(id(), 4))
        now.set(room.startsAt!! + 30_001); service.tick(); service.tick()
        assertEquals(RoomPhase.CLOSED, stored(room.code).phase)
        assertEquals(1500L, service.wallet(actor.token).balance)
    }

    @Test fun `deleting queued profile redacts peer receipts and removes its purchase`() {
        val actor = guest("Name to erase"); val peer = guest("Peer")
        val room = service.bingo.match(actor.token, BingoMatchRequest(id(), 6, friendTable = true))
        val request = BingoMatchRequest(id(), 1, friendTable = true, friendCode = room.code)
        service.bingo.match(peer.token, request)
        service.deleteProfile(actor.token, DeleteProfileRequest(id()), "bingo-delete")
        val remaining = service.bingo.read(peer.token, room.code)
        assertEquals(listOf(peer.playerId), remaining.members.map { it.playerId })
        assertEquals(mapOf(peer.playerId to 1), remaining.cardCounts)
        assertFalse(WireJson.encodeToString(service.bingo.match(peer.token, request)).contains("Name to erase"))
        assertEquals(1400L, service.wallet(peer.token).balance)
    }

    @Test fun `full friends round validates ownership preserves final claim window and settles once`() {
        val actors = listOf(guest("Host"), guest("Friend"))
        val room = service.bingo.match(actors[0].token, BingoMatchRequest(id(), 1, friendTable = true))
        service.bingo.match(actors[1].token, BingoMatchRequest(id(), 1, friendTable = true, friendCode = room.code))
        fun command(index: Int, action: BingoAction): BingoRoomView {
            val actor = actors[index]
            val view = service.bingo.read(actor.token, room.code)
            return service.bingo.command(actor.token, room.code, BingoCommandRequest(id(), view.revision, action))
        }
        command(0, BingoAction.Start)
        val initial = service.bingo.read(actors[0].token, room.code).round!!
        val peerCard = service.bingo.read(actors[1].token, room.code).round!!.ownCards.single()
        failure("not_card_owner") { command(0, BingoAction.Mark(initial.id, peerCard.id, peerCard.numbers.first())) }
        failure("bingo_not_called") { command(0, BingoAction.Mark(initial.id, initial.ownCards.single().id, initial.ownCards.single().numbers.first())) }
        repeat(75) {
            now.set(stored(room.code).nextDrawAt!!); service.tick()
        }
        assertEquals(RoomPhase.ACTIVE, stored(room.code).phase)
        actors.forEachIndexed { index, actor ->
            val game = service.bingo.read(actor.token, room.code).round!!
            val card = game.ownCards.single()
            card.numbers.forEach { command(index, BingoAction.Mark(game.id, card.id, it)) }
            BingoPattern.entries.forEach { pattern -> command(index, BingoAction.Claim(game.id, card.id, pattern)) }
            assertEquals(1400L, service.wallet(actor.token).balance)
        }
        now.set(stored(room.code).nextDrawAt!!); service.tick()
        val finished = service.bingo.read(actors[0].token, room.code)
        finished.validateFor(actors[0].playerId)
        assertEquals(RoomPhase.FINISHED, finished.phase)
        assertEquals(75, finished.round!!.revealedOrder!!.size)
        assertEquals(initial.drawCommitment, bingoCommitment(stored(room.code).round!!, finished.round!!.revealedNonce!!))
        assertEquals(8, finished.round!!.claims.size)
        actors.forEach { assertEquals(1500L, service.wallet(it.token).balance) }
        service = RoomService(database, now::get)
        service.tick(); service.tick()
        actors.forEach { assertEquals(1500L, service.wallet(it.token).balance) }
    }
}
