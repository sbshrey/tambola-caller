package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LargeMatchTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest() = service.register(GuestRequest("Player"), id())
    private fun request(large: Boolean = true) = MatchRequest(id(), 6, rulesVersion = 2, powersEnabled = true, largeMatch = large)
    private fun stored(code: String) = database.transaction { c -> c.query("SELECT payload FROM rooms WHERE code = ?", code) {
        WireJson.decodeFromString<RoomRecord>(it.getString(1))
    }.single() }

    @Test fun `large match fills progressively survives restart and plays legitimate claims`() {
        val owner = guest()
        val purchase = request()
        val initial = service.match(owner.token, purchase)
        val code = initial.snapshot.code
        val target = stored(code).matchPopulation()
        assertTrue(target in 30..50)
        assertEquals(0, initial.snapshot.options.computerPlayers)
        var previous = 0
        repeat(10) {
            now.addAndGet(1_000); service.tick()
            val view = service.read(owner.token, code).snapshot
            view.validateFor(owner.playerId)
            assertTrue(view.options.computerPlayers > previous)
            previous = view.options.computerPlayers
            if (it == 4) service = RoomService(database, now::get)
        }
        assertEquals(target-1, previous)
        assertEquals(initial, service.match(owner.token, purchase)) // Durable receipt remains unchanged.
        now.addAndGet(2_000); service.tick()
        val active = service.read(owner.token, code).snapshot
        active.validateFor(owner.playerId)
        assertEquals(7, active.protocolVersion)
        assertEquals(target, active.round!!.players.size)
        assertEquals(target-1, active.round!!.players.count { it.computer })
        assertEquals(6 + (target-1)*3, active.coins!!.tickets)
        assertEquals((target+9)/10, active.options.game.winnersPerPrize)
        assertEquals(6, active.round!!.ownTickets.size)
        assertNull(active.round!!.revealedOrder)
        assertEquals("update_required", assertThrows(ApiFailure::class.java) { service.match(owner.token, request(false)) }.code)
        // Run a whole round with real domain eligibility, no invented wins or future-number claims.
        repeat(90) {
            val next = stored(code).nextDrawAt
            if (next != null) {
                now.set(next); service.tick()
                now.addAndGet(3_000); service.tick()
            }
        }
        val done = stored(code).round!!
        assertTrue(done.finished)
        assertTrue(done.awards.isNotEmpty())
        done.awards.forEach { award ->
            award.ticketIds.forEach { ticketId ->
                assertTrue(award.prize.matches(done.tickets.first { it.id == ticketId }, done.called.take(award.drawIndex).toSet()))
            }
        }
        service.read(owner.token, code).snapshot.validateFor(owner.playerId)
    }

    @Test fun `real players replace filler and legacy clients cannot join larger lobbies`() {
        val owner = guest()
        val room = service.match(owner.token, request()).snapshot
        val legacy = guest()
        assertNotEquals(room.code, service.match(legacy.token, request(false)).snapshot.code)
        now.addAndGet(10_000); service.tick()
        val target = stored(room.code).matchPopulation()
        repeat(49) {
            val human = guest()
            val joined = service.match(human.token, request()).snapshot
            assertEquals(room.code, joined.code)
            joined.validateFor(human.playerId)
            assertEquals((target-joined.members.size).coerceAtLeast(0), joined.options.computerPlayers)
            assertTrue(joined.members.size + joined.options.computerPlayers <= 50)
        }
        val overflow = guest()
        assertNotEquals(room.code, service.match(overflow.token, request()).snapshot.code)
        now.addAndGet(2_000); service.tick()
        val active = service.read(owner.token, room.code).snapshot
        active.validateFor(owner.playerId)
        assertEquals(50, active.round!!.players.size)
        assertEquals(0, active.options.computerPlayers)
    }

    @Test fun `large mode is opt in and excluded from friends and legacy formats`() {
        assertFalse(WireJson.encodeToString(request(false)).contains("largeMatch"))
        assertThrows(IllegalArgumentException::class.java) { request().copy(friendTable = true) }
        assertThrows(IllegalArgumentException::class.java) { request().copy(powersEnabled = false, rulesVersion = 1) }
        assertThrows(IllegalArgumentException::class.java) { coinOptions(rulesVersion = 2).copy(computerPlayers = 49) }
        (1..49).map { practicePersona("large", it) }.also { players ->
            assertEquals(49, players.map { it.id }.distinct().size)
            assertTrue(players.all { it.computer })
        }
    }

    @Test fun `classic quick play also fills and never mixes with power mode`() {
        val classic = guest(); val power = guest()
        val initial = service.match(classic.token, request().copy(tickets = 1, powersEnabled = false)).snapshot
        initial.validateFor(classic.playerId)
        assertEquals(0, initial.options.computerPlayers)
        assertNotEquals(initial.code, service.match(power.token, request()).snapshot.code)
        now.addAndGet(12_000); service.tick()
        val active = service.read(classic.token, initial.code).snapshot
        active.validateFor(classic.playerId)
        assertTrue(active.round!!.players.size in 30..50)
        assertNull(active.round!!.powers)
    }
}
