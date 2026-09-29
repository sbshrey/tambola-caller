package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RoundSummaryTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun stored(code: String) = database.transaction { c -> c.query("SELECT payload FROM rooms WHERE code = ?", code) {
        WireJson.decodeFromString<RoomRecord>(it.getString(1))
    }.single() }

    @Test fun `completed large classic round publishes every settled total and hides summary before completion`() {
        val owner = service.register(GuestRequest("Mira"), id())
        val old = service.register(GuestRequest("Noor"), id())
        val request = MatchRequest(id(), 6, rulesVersion = 2, largeMatch = true, roundSummary = true)
        val room = service.match(owner.token, request).snapshot
        val legacy = service.match(old.token, request.copy(id = id(), roundSummary = false)).snapshot
        assertNotEquals(room.code, legacy.code)
        assertEquals(9, room.protocolVersion)
        assertEquals(7, legacy.protocolVersion)
        assertFalse(WireJson.encodeToString(legacy).contains("roundSummary"))
        assertEquals("update_required", assertThrows(ApiFailure::class.java) {
            service.match(owner.token, request.copy(id = id(), roundSummary = false))
        }.code)
        now.addAndGet(12000); service.tick()
        val active = service.read(owner.token, room.code).snapshot
        active.validateFor(owner.playerId)
        assertTrue(active.round!!.winnings.isEmpty())
        assertFalse(WireJson.encodeToString(active.round!!).contains("winnings"))
        repeat(90) {
            stored(room.code).nextDrawAt?.let { now.set(it); service.tick(); now.addAndGet(3000); service.tick() }
        }
        val done = service.read(owner.token, room.code).snapshot
        done.validateFor(owner.playerId)
        assertEquals(RoomPhase.FINISHED, done.phase)
        val winners = done.round!!.winnings
        assertEquals(done.round!!.players.map { it.id }.toSet(), winners.keys)
        assertEquals(done.coins!!.pool, winners.values.sumOf { it.prizes + it.returned })
        assertEquals(done.coins!!.settledWinnings, winners.getValue(owner.playerId).prizes)
        assertEquals(0L, winners.values.sumOf { it.bonus })
        service = RoomService(database, now::get)
        assertEquals(winners, service.read(owner.token, room.code).snapshot.round!!.winnings)
    }
}
