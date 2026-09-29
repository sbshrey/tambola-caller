package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MatchPowersServerTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest(name: String) = service.register(GuestRequest(name), id())
    private fun stored(code: String) = database.transaction { c -> c.query("SELECT payload FROM rooms WHERE code = ?", code) {
        WireJson.decodeFromString<RoomRecord>(it.getString(1))
    }.single() }
    private fun seed(code: String, player: String, powers: MatchPowers) = database.transaction { c ->
        val room = stored(code)
        c.execute("UPDATE rooms SET payload = ? WHERE code = ?", WireJson.encodeToString(room.copy(matchPowers = room.matchPowers + (player to powers))), code)
    }
    private fun command(actor: GuestCredentials, code: String, action: RoomAction) = service.command(actor.token, code,
        CommandRequest(id(), service.read(actor.token, code).snapshot.revision, action)).snapshot
    private fun next(code: String) { now.set(requireNotNull(stored(code).nextDrawAt)); service.tick() }

    @Test fun `friend mode rejection never charges and corrected durable request charges once`() {
        for (power in listOf(false, true)) {
            val host = guest("Host"); val joiner = guest("Joiner")
            val room = service.match(host.token, MatchRequest(id(), 2, true, rulesVersion = 2, powersEnabled = power)).snapshot
            val before = service.wallet(joiner.token)
            val rejected = MatchRequest(id(), 3, true, room.code, rulesVersion = 2, powersEnabled = !power)
            assertEquals("power_room_mismatch", assertThrows(ApiFailure::class.java) { service.match(joiner.token, rejected) }.code)
            assertEquals(before, service.wallet(joiner.token))
            assertEquals(1, service.read(host.token, room.code).snapshot.members.size)
            database.transaction { c ->
                assertEquals(0, c.query("SELECT count(*) FROM match_receipts WHERE actor = ? AND command_id = ?", joiner.playerId, rejected.id) { it.getInt(1) }.single())
            }
            val replacement = rejected.copy(id = id(), powersEnabled = power)
            val accepted = service.match(joiner.token, replacement)
            assertEquals(before.balance - 300, accepted.snapshot.wallet!!.balance)
            service = RoomService(database, now::get)
            assertEquals(accepted, service.match(joiner.token, replacement))
            assertEquals(before.balance - 300, service.wallet(joiner.token).balance)
            assertEquals(2, service.read(host.token, room.code).snapshot.members.size)
        }
    }
    private fun friends(tickets: Int = 2, preview: Boolean = false, summary: Boolean = false): Triple<GuestCredentials, GuestCredentials, String> {
        val a = guest("Mira"); val b = guest("Noor")
        val room = service.match(a.token, MatchRequest(id(), tickets, friendTable = true, rulesVersion = 2, powersEnabled = true, previewPowers = preview, roundSummary = summary)).snapshot
        service.match(b.token, MatchRequest(id(), tickets, friendTable = true, friendCode = room.code, rulesVersion = 2, powersEnabled = true, previewPowers = preview, roundSummary = summary))
        command(a, room.code, RoomAction.Start)
        return Triple(a, b, room.code)
    }

    @Test fun `preview survives restart grants shown power and shield activation replays once`() {
        val (a, b, code) = friends(preview = true)
        val original = service.read(a.token, code).snapshot
        original.validateFor(a.playerId)
        assertEquals(8, original.protocolVersion)
        val shown = original.round!!.powers!!.nextPower
        assertNotNull(shown)
        val peerPowers = service.read(b.token, code).snapshot.round!!.powers
        service = RoomService(database, now::get)
        assertEquals(shown, service.read(a.token, code).snapshot.round!!.powers!!.nextPower)
        val ticket = original.round!!.ownTickets.first()
        while (ticket.numbers.count { it in stored(code).round!!.called } < 5) next(code)
        val called = stored(code).round!!.called
        ticket.numbers.filter { it in called }.take(5).forEach { command(a, code, RoomAction.Mark(original.round!!.id, ticket.id, it)) }
        val earned = service.read(a.token, code).snapshot
        earned.validateFor(a.playerId)
        assertEquals(listOf(shown), earned.round!!.powers!!.inventory)
        assertEquals(peerPowers, service.read(b.token, code).snapshot.round!!.powers)
        seed(code, a.playerId, earned.round!!.powers!!.copy(inventory = listOf(MatchPower.SHIELD)))
        val before = service.read(a.token, code).snapshot
        val request = CommandRequest(id(), before.revision, RoomAction.UsePower(original.round!!.id, ticket.id, MatchPower.SHIELD))
        val active = service.command(a.token, code, request)
        active.snapshot.validateFor(a.playerId)
        assertEquals(setOf(ticket.id), active.snapshot.round!!.powers!!.armedShield)
        service = RoomService(database, now::get)
        assertEquals(active, service.command(a.token, code, request))
        assertEquals("power_unavailable", assertThrows(ApiFailure::class.java) {
            command(a, code, request.action)
        }.code)
    }

    @Test fun `preview matchmaking separates old apps and friend rejection does not charge`() {
        val a = guest("New"); val b = guest("Old")
        val modern = service.match(a.token, MatchRequest(id(), 1, rulesVersion = 2, powersEnabled = true, previewPowers = true)).snapshot
        val legacy = service.match(b.token, MatchRequest(id(), 1, rulesVersion = 2, powersEnabled = true)).snapshot
        assertNotEquals(modern.code, legacy.code)
        assertFalse(WireJson.encodeToString(MatchRequest(id(), 1)).contains("previewPowers"))
        assertFalse(WireJson.encodeToString(MatchPowers()).contains("nextPower"))
        assertFalse(WireJson.encodeToString(MatchPowers()).contains("armedShield"))
        assertEquals("update_required", assertThrows(ApiFailure::class.java) {
            service.match(a.token, MatchRequest(id(), 1, rulesVersion = 2, powersEnabled = true))
        }.code)
        val host = guest("Host"); val joiner = guest("Joiner")
        val friends = service.match(host.token, MatchRequest(id(), 1, friendTable = true, rulesVersion = 2, powersEnabled = true, previewPowers = true)).snapshot
        val balance = service.wallet(joiner.token)
        assertEquals("update_required", assertThrows(ApiFailure::class.java) {
            service.match(joiner.token, MatchRequest(id(), 1, friendTable = true, friendCode = friends.code, rulesVersion = 2, powersEnabled = true))
        }.code)
        assertEquals(balance, service.wallet(joiner.token))
    }

    @Test fun `power lobby has real staggered practice seats and never mixes with classic`() {
        val a = guest("Mira"); val b = guest("Noor")
        val power = service.match(a.token, MatchRequest(id(), 1, rulesVersion = 2, powersEnabled = true)).snapshot
        power.validateFor(a.playerId)
        assertEquals(6, power.protocolVersion); assertEquals(0, power.options.computerPlayers)
        val classic = service.match(b.token, MatchRequest(id(), 1, rulesVersion = 2)).snapshot
        assertNotEquals(classic.code, power.code)
        (1..3).forEach { count ->
            now.addAndGet(3_000); service.tick()
            val view = service.read(a.token, power.code).snapshot
            view.validateFor(a.playerId); assertEquals(count, view.options.computerPlayers)
            assertEquals(1 + count * 3, view.coins!!.tickets)
        }
        now.addAndGet(3_000); service.tick()
        val active = service.read(a.token, power.code).snapshot
        active.validateFor(a.playerId)
        assertEquals(4, active.round!!.players.size)
        assertEquals((1..3).map { practicePersona(power.roomId, it) }, active.round!!.players.filter { it.computer })
        assertEquals("update_required", assertThrows(ApiFailure::class.java) {
            service.match(a.token, MatchRequest(id(), 1, rulesVersion = 2))
        }.code)
    }

    @Test fun `marks and drops are private authoritative durable and idempotent`() {
        val (a,b,code) = friends()
        val ticket = stored(code).round!!.tickets.first { it.playerId == a.playerId }
        while (ticket.numbers.count { it in stored(code).round!!.called } < 5) next(code)
        val view = service.read(a.token, code).snapshot
        val game = view.round!!
        val correct = ticket.numbers.filter { it in game.called }.take(5)
        correct.take(4).forEach { command(a, code, RoomAction.Mark(game.id, ticket.id, it)) }
        val request = CommandRequest(id(), view.revision, RoomAction.Mark(game.id, ticket.id, correct.last()))
        val result = service.command(a.token, code, request)
        assertEquals(5, result.snapshot.round!!.powers!!.correctMarks)
        assertEquals(1, result.snapshot.round!!.powers!!.inventory.size)
        assertEquals(result, service.command(a.token, code, request))
        command(a, code, RoomAction.Mark(game.id, ticket.id, correct.last()))
        service = RoomService(database, now::get)
        val recovered = service.read(a.token, code).snapshot
        recovered.validateFor(a.playerId)
        assertEquals(result.snapshot.round!!.powers, recovered.round!!.powers)
        val peer = service.read(b.token, code).snapshot
        peer.validateFor(b.playerId); assertEquals(MatchPowers(), peer.round!!.powers)
        assertNull(peer.round!!.revealedOrder)
        val uncalled = ticket.numbers.first { it !in game.called }
        assertEquals("number_not_called", assertThrows(ApiFailure::class.java) {
            command(a, code, RoomAction.Mark(game.id, ticket.id, uncalled))
        }.code)
        assertEquals("number_not_called", assertThrows(ApiFailure::class.java) {
            command(b, code, RoomAction.Mark(game.id, ticket.id, correct.first()))
        }.code)
    }

    @Test fun `shield receipt cannot punish twice and stale claims never discard`() {
        val (a,_,code) = friends()
        next(code)
        val game = stored(code).round!!
        val ticket = game.tickets.first { it.playerId == a.playerId }
        seed(code, a.playerId, MatchPowers(inventory = listOf(MatchPower.SHIELD)))
        val action = RoomAction.Claim(game.id, game.called.size, emptySet(), ClaimSelection(ticket.id, Prize.FULL_HOUSE.name))
        val request = CommandRequest(id(), service.read(a.token, code).snapshot.revision, action)
        val saved = service.command(a.token, code, request)
        assertEquals(PowerNotice.SHIELD_SAVED, saved.snapshot.round!!.powers!!.notice)
        assertTrue(saved.snapshot.round!!.powers!!.discarded.isEmpty())
        service = RoomService(database, now::get)
        assertEquals(saved, service.command(a.token, code, request))
        val discarded = command(a, code, action)
        assertEquals(setOf(ticket.id), discarded.round!!.powers!!.discarded)
        discarded.validateFor(a.playerId)
        val other = game.tickets.last { it.playerId == a.playerId }
        next(code)
        assertEquals("claim_window_closed", assertThrows(ApiFailure::class.java) {
            command(a, code, action.copy(selection = ClaimSelection(other.id, Prize.FULL_HOUSE.name)))
        }.code)
        assertEquals(setOf(ticket.id), stored(code).matchPowers.getValue(a.playerId).discarded)
        assertEquals("ticket_discarded", assertThrows(ApiFailure::class.java) {
            command(a, code, action.copy(drawIndex = 2))
        }.code)
    }

    @Test fun `bonus settles after shared pool and restart cannot credit it again`() {
        val (a,b,code) = friends(1, summary = true)
        assertTrue(service.read(a.token, code).snapshot.round!!.winnings.isEmpty())
        repeat(90) { next(code) }
        val game = stored(code).round!!
        val ticket = game.tickets.first { it.playerId == a.playerId }
        val other = game.tickets.first { it.playerId == b.playerId }
        seed(code, a.playerId, MatchPowers(marks = mapOf(ticket.id to ticket.numbers.toSet()), inventory = listOf(MatchPower.PRIZE_BONUS)))
        seed(code, b.playerId, MatchPowers(marks = mapOf(other.id to other.numbers.toSet())))
        command(a, code, RoomAction.UsePower(game.id, ticket.id, MatchPower.PRIZE_BONUS))
        val action = RoomAction.Claim(game.id, 90, emptySet(), ClaimSelection(ticket.id, Prize.FULL_HOUSE.name))
        command(a, code, action)
        command(b, code, action.copy(selection = ClaimSelection(other.id, Prize.FULL_HOUSE.name)))
        assertEquals("claim_window_closed", assertThrows(ApiFailure::class.java) { command(a, code, action) }.code)
        assertTrue(stored(code).matchPowers.getValue(a.playerId).discarded.isEmpty())
        next(code)
        val done = service.read(a.token, code).snapshot
        done.validateFor(a.playerId)
        assertEquals(12L, done.coins!!.bonusCoins)
        assertEquals(mapOf(a.playerId to RoundWinnings(50, 12, 50), b.playerId to RoundWinnings(50, 0, 50)), done.round!!.winnings)
        assertEquals(done.round!!.winnings, service.read(b.token, code).snapshot.round!!.winnings)
        assertThrows(io.github.sbshrey.tambola.client.InvalidRoomResponse::class.java) {
            done.copy(round = done.round!!.copy(winnings = done.round!!.winnings - b.playerId)).validateFor(a.playerId)
        }
        val balances = listOf(service.wallet(a.token), service.wallet(b.token))
        assertEquals(2 * COIN_STARTER_BALANCE + 12, balances.sumOf { it.balance })
        service = RoomService(database, now::get); service.tick()
        assertEquals(balances, listOf(service.wallet(a.token), service.wallet(b.token)))
    }

    @Test fun `deleting a profile removes its private power state and preserves the peer`() {
        val (a,b,code) = friends()
        val powers = MatchPowers(inventory = listOf(MatchPower.SHIELD))
        seed(code, a.playerId, powers); seed(code, b.playerId, powers)
        service.deleteProfile(a.token, DeleteProfileRequest(id()), "power-qa")
        assertFalse(a.playerId in stored(code).matchPowers)
        assertEquals(powers, stored(code).matchPowers[b.playerId])
        service.read(b.token, code).snapshot.validateFor(b.playerId)
    }
}
