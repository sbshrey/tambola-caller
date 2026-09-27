package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ExpandedMatchTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest(n: Int) = service.register(GuestRequest("Player $n"), id())
    private fun stored(code: String) = database.transaction { c -> c.query("SELECT payload FROM rooms WHERE code = ?", code) {
        WireJson.decodeFromString<RoomRecord>(it.getString(1))
    }.single() }
    private fun command(actor: GuestCredentials, code: String, action: RoomAction) = service.command(actor.token, code,
        CommandRequest(id(), service.read(actor.token, code).snapshot.revision, action)).snapshot

    @Test fun `legacy and expanded matchmaking never mix and old clients cannot buy into new friends tables`() {
        val legacy = guest(1); val expanded = guest(2)
        val old = service.match(legacy.token, MatchRequest(id(), 3)).snapshot
        val fresh = service.match(expanded.token, MatchRequest(id(), 3, rulesVersion = 2)).snapshot
        assertNotEquals(old.code, fresh.code)
        assertEquals(4, old.protocolVersion); assertEquals(5, fresh.protocolVersion)
        assertEquals(5, old.options.intervalSeconds); assertEquals(10, fresh.options.intervalSeconds)
        old.validateFor(legacy.playerId); fresh.validateFor(expanded.playerId)
        val friend = guest(3)
        val table = service.match(friend.token, MatchRequest(id(), 3, friendTable = true, rulesVersion = 2)).snapshot
        val outsider = guest(4)
        val error = assertThrows(ApiFailure::class.java) {
            service.match(outsider.token, MatchRequest(id(), 3, friendTable = true, friendCode = table.code))
        }
        assertEquals("update_required", error.code)
        assertEquals(COIN_STARTER_BALANCE, service.wallet(outsider.token).balance)
    }

    @Test fun `fifty friends settle all six categories once with fixed pools and ten second calls`() {
        val actors = (1..50).map(::guest)
        actors.forEach { service.loginRewards(it.token) }
        val first = service.match(actors.first().token, MatchRequest(id(), 6, friendTable = true, rulesVersion = 2, powerUp = PowerUp.PRIZE_BOOST)).snapshot
        actors.drop(1).forEachIndexed { index, actor -> service.match(actor.token, MatchRequest(id(), 6, friendTable = true, friendCode = first.code,
            rulesVersion = 2, powerUp = if (index % 2 == 0) PowerUp.TICKET_INSURANCE else PowerUp.PRIZE_BOOST)) }
        val extra = guest(51)
        assertEquals("room_full", assertThrows(ApiFailure::class.java) {
            service.match(extra.token, MatchRequest(id(), 1, friendTable = true, friendCode = first.code, rulesVersion = 2))
        }.code)
        val active = command(actors.first(), first.code, RoomAction.Start)
        assertEquals(50, active.round!!.players.size)
        assertEquals(5, active.options.game.winnersPerPrize)
        assertEquals(30_000L, active.coins!!.pool)
        actors.forEach { service.read(it.token, first.code).snapshot.validateFor(it.playerId) }
        var steps = 0
        while (stored(first.code).phase == RoomPhase.ACTIVE && steps++ < 100) {
            val record = stored(first.code)
            assertEquals(10_000L, requireNotNull(record.nextDrawAt) - now.get())
            now.set(requireNotNull(record.nextDrawAt)); service.tick()
            var game = requireNotNull(stored(first.code).round)
            if (game.finished) break
            actors.forEach { actor ->
                val hand = game.tickets.filter { it.playerId == actor.playerId }
                val marked = hand.flatMap { it.numbers }.intersect(game.called.toSet())
                hand.forEach { ticket -> game.settings.prizes.forEach { prize ->
                    val selection = ClaimSelection(ticket.id, prize.name)
                    val claimed = game.claim(actor.playerId, marked, selection)
                    if (claimed != game) {
                        command(actor, first.code, RoomAction.Claim(game.id, game.called.size, marked, selection))
                        game = claimed
                    }
                } }
            }
        }
        val final = stored(first.code)
        assertEquals(RoomPhase.FINISHED, final.phase)
        assertEquals(6, final.round!!.awards.size)
        assertTrue(final.round!!.awards.all { it.playerIds.size >= 5 })
        assertEquals(30_000L, final.coinPool!!.allocations(final.round!!).sumOf { it.coins })
        val before = actors.map { service.wallet(it.token) }
        val bonuses = actors.sumOf { final.coinPool!!.powerUpBonus(final.round!!, it.playerId, final.powerUps.getValue(it.playerId)) }
        assertTrue(bonuses > 0)
        assertEquals(50 * 50_500L + bonuses, before.sumOf { it.balance })
        service = RoomService(database, now::get); service.tick()
        assertEquals(before, actors.map { service.wallet(it.token) })
        actors.forEach { service.read(it.token, first.code).snapshot.validateFor(it.playerId) }
    }
}
