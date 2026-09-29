package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class MarkQueueTest {
    private val game = Round.create(listOf(Player("me", "Mira")), RoundSettings(ticketsPerPlayer = 6), Random(88)).start()
    private val ticket = game.tickets.first()
    private fun session(): OnlineSaved {
        val pool = CoinPool(6, 2)
        val settings = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true, prizes = pool.prizes.map { it.prize })
        val round = PublicRound(game.id, RoundStatus.PLAYING, (1..90).toList(), game.tickets, emptyList(), emptyList(), emptyMap(), "0".repeat(64),
            players = game.players, powers = MatchPowers())
        return OnlineSaved("https://fixture.invalid", GuestCredentials("me", "fixture-token", 999999), "Mira", room = RoomView(
            code = "ABCD2345", roomId = "fixture-room", revision = 91, phase = RoomPhase.ACTIVE, hostId = "me", locked = true,
            options = RoomOptions(game = settings, capacity = 50, coinGame = true, coinRulesVersion = 2, powersEnabled = true),
            members = listOf(MemberView("me", "Mira", 0, true, true)), round = round, nextDrawAt = 1000, expiresAt = 999999,
            serverTime = 1, coins = CoinTableView(6, 600, pool.prizes, 6, null)))
    }

    @Test fun `six ticket burst stays ordered through persistence and exact receipt retry`() {
        var saved = session()
        val marks = game.tickets.flatMap { card -> card.numbers.map { RoomAction.Mark(game.id, card.id, it) } }
        marks.forEach { saved = saved.queueMark(it.ticketId, it.number) }
        assertEquals(90, saved.queuedMarks.size)
        assertEquals(saved, saved.queueMark(ticket.id, ticket.numbers.first()))
        assertTrue(saved.marks.isEmpty())
        assertEquals(0, saved.room!!.round!!.powers!!.correctMarks)
        saved = WireJson.decodeFromString(WireJson.encodeToString(saved))
        marks.forEachIndexed { index, mark ->
            saved = saved.promoteMark("receipt-$index")
            val pending = saved.pending
            val restored = WireJson.decodeFromString<OnlineSaved>(WireJson.encodeToString(saved))
            assertEquals(pending, restored.promoteMark("must-not-replace").pending)
            assertEquals(mark, (pending as PendingOperation.Command).request.action)
            saved = restored.copy(pending = null)
        }
        assertTrue(saved.queuedMarks.isEmpty())
    }

    @Test fun `authoritative marks discard and round changes prune intentions but keep in flight receipt`() {
        val mark = ticket.numbers.first()
        var saved = session().queueMark(ticket.id, mark).queueMark(ticket.id, ticket.numbers[1]).promoteMark("durable")
        assertEquals(setOf(mark, ticket.numbers[1]), saved.pendingMarkNumbers()[ticket.id])
        val pending = saved.pending
        val room = saved.room!!
        saved = saved.copy(room = room.copy(round = room.round!!.copy(powers = MatchPowers(marks = mapOf(ticket.id to ticket.numbers.take(2).toSet()))))).pruneQueuedMarks()
        assertTrue(saved.queuedMarks.isEmpty())
        assertTrue(saved.pendingMarkNumbers().isEmpty())
        assertEquals(pending, saved.pending)
        val discarded = session().queueMark(ticket.id, mark).let { it.copy(room = it.room!!.copy(round = it.room!!.round!!.copy(powers = MatchPowers(discarded = setOf(ticket.id))))) }.pruneQueuedMarks()
        assertTrue(discarded.queuedMarks.isEmpty())
        assertEquals(discarded, discarded.queueMark(ticket.id, mark))
        val changed = session().queueMark(ticket.id, mark).let { it.copy(room = it.room!!.copy(round = it.room!!.round!!.copy(id = "another-round"))) }.pruneQueuedMarks()
        assertTrue(changed.queuedMarks.isEmpty())
    }

    @Test fun `uncalled foreign and non power taps do not enter queue or replace another command`() {
        val saved = session()
        assertEquals(saved, saved.queueMark("foreign-ticket", ticket.numbers.first()))
        val uncalled = saved.copy(room = saved.room!!.copy(round = saved.room!!.round!!.copy(called = emptyList())))
        assertEquals(uncalled, uncalled.queueMark(ticket.id, ticket.numbers.first()))
        val leaving = saved.copy(pending = PendingOperation.Command(saved.room!!.code, CommandRequest("leave", 91, RoomAction.Leave)))
        assertEquals(leaving, leaving.queueMark(ticket.id, ticket.numbers.first()))
        val classic = saved.copy(room = saved.room!!.copy(round = saved.room!!.round!!.copy(powers = null)))
        assertEquals(classic, classic.queueMark(ticket.id, ticket.numbers.first()))
    }
}
