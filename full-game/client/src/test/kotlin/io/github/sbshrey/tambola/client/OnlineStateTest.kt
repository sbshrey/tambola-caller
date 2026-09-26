package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Random
import java.util.UUID

class OnlineStateTest {
    @Test fun `claim freezes selected ticket prize marks and call through pending persistence`() {
        var game = Round.create(players, round.settings.copy(manualClaims = true), Random(44)).start()
        repeat(90) { game = game.draw() }
        val ticket = game.tickets.first { it.playerId == "asha" }
        val choice = ClaimSelection(ticket.id, Prize.TOP_LINE.name)
        val session = saved(game).copy(marks = mapOf(ticket.id to (ticket.row(0).toSet() + 999)))
        val action = session.claimAction(choice)!!
        assertEquals(ticket.row(0).toSet(), action.markedNumbers)
        assertEquals(90, action.drawIndex); assertEquals(choice, action.selection)
        val pending = PendingOperation.Command("ABCD2345", CommandRequest(UUID.randomUUID().toString(), session.room!!.revision, action))
        val restored = WireJson.decodeFromString<OnlineSaved>(WireJson.encodeToString(session.copy(pending = pending)))
        assertEquals(pending, restored.pending)
        assertNull(session.claimAction(choice.copy(ticketId = "foreign")))
        assertNull(session.claimAction(choice.copy(prizeId = "unknown")))
    }

    @Test fun `between-call claims animate only for live updates never receipts or resync`() {
        var game = Round.create(players, round.settings.copy(manualClaims = true), Random(44)).start()
        repeat(90) { game = game.draw() }
        val ticket = game.tickets.first { it.playerId == "asha" }
        val next = game.claim("asha", ticket.numbers.toSet(), ClaimSelection(ticket.id, Prize.TOP_LINE.name))
        val session = saved(game)
        val update = RoomUpdate(view(next, session.room!!.revision + 1), emptyList(), false)
        assertTrue(session.accept(update, live = true).liveAwards)
        assertFalse(session.accept(update, live = false).liveAwards)
        assertFalse(session.accept(update.copy(resyncRequired = true), live = true).liveAwards)
        assertFalse(session.accept(update, true).saved.accept(RoomUpdate(session.room!!, emptyList(), false), true).liveAwards)
    }
    @Test fun `legacy snapshots default round avatars and accepted profile changes cannot be rolled back by receipts`() {
        val original = view()
        val legacy = WireJson.encodeToString(original).replace("\"protocolVersion\":$PROTOCOL_VERSION", "\"protocolVersion\":1")
            .replace("\"computer\":false,\"avatar\":0", "\"computer\":false")
        val restored = WireJson.decodeFromString<RoomView>(legacy)
        restored.validateFor("asha")
        assertTrue(restored.round!!.players.all { it.avatar == 0 })
        val changed = original.copy(revision = 10, members = original.members.map { if (it.playerId == "asha") it.copy(avatar = 7) else it })
        val accepted = saved().accept(RoomUpdate(changed, emptyList(), false), live = true)
        assertEquals(7, accepted.saved.avatar); assertNull(accepted.announcement)
        assertEquals(7, accepted.saved.accept(RoomUpdate(original, emptyList(), false), true).saved.avatar)
        assertThrows(InvalidRoomResponse::class.java) { changed.copy(members = changed.members.map { it.copy(avatar = 8) }).validateFor("asha") }
        assertThrows(InvalidRoomResponse::class.java) { changed.copy(protocolVersion = 999).validateFor("asha") }
    }
    private val players = listOf(Player("asha", "Asha"), Player("bina", "Bina"))
    private val round = Round.create(players, RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 2, playAllNumbers = true), random = Random(33)).start()
    private val credentials = GuestCredentials("asha", "never-print-this-token", 99_999)
    private fun view(game: Round = round, revision: Long = game.called.size.toLong() + 1): RoomView {
        val nonce = "fixture-nonce"
        val commitment = MessageDigest.getInstance("SHA-256").digest("tambola-draw-v1\n${game.id}\n$nonce\n${game.drawOrder.joinToString(",")}".toByteArray()).joinToString("") { "%02x".format(it) }
        val winners = (game.awards.flatMap { it.ticketIds } + game.customAwards.flatMap { it.ticketIds }).toSet()
        val labels = game.players.flatMap { player -> game.tickets.filter { it.playerId == player.id }.mapIndexedNotNull { i, ticket -> if (ticket.id in winners) WinningTicket(ticket.id, player.id, i + 1) else null } }
        return RoomView(code = "ABCD2345", roomId = "room", revision = revision, phase = if (game.finished) RoomPhase.FINISHED else RoomPhase.ACTIVE,
            hostId = "asha", locked = true, options = RoomOptions(game.settings), members = players.map { MemberView(it.id, it.name, 0, true, true) },
            round = PublicRound(game.id, game.status, game.called, game.tickets.filter { it.playerId == "asha" }, game.awards, game.customAwards,
                players.associate { it.id to game.score(it.id) }, commitment, game.drawOrder.takeIf { game.finished }, nonce.takeIf { game.finished }, players, labels),
            nextDrawAt = null, expiresAt = 100_000, serverTime = 1_000)
    }
    private fun saved(game: Round = round) = OnlineSaved("https://rooms.example", credentials, "Asha", room = view(game))
    private fun update(game: Round, resync: Boolean = false) = RoomUpdate(view(game), emptyList(), resync)

    @Test fun `older receipt never rolls back calls marks or scores`() {
        val first = round.draw()
        val fifth = (1..4).fold(first) { game, _ -> game.draw() }
        val session = saved(fifth)
        val receipt = session.accept(update(first), live = true)
        assertEquals(session, receipt.saved)
        assertNull(receipt.announcement)
        val pending = PendingOperation.Command("ABCD2345", CommandRequest(UUID.randomUUID().toString(), 1, RoomAction.Draw))
        val encoded = WireJson.encodeToString(session.copy(pending = pending))
        val restored = WireJson.decodeFromString<OnlineSaved>(encoded)
        assertEquals(pending, restored.pending)
        assertFalse(restored.toString().contains(credentials.token))
    }

    @Test fun `only a new single live call announces and reconnect is silent`() {
        val session = saved()
        val first = round.draw()
        assertEquals(first.latest, session.accept(update(first), live = true).announcement)
        assertNull(session.accept(update(first), live = false).announcement)
        assertNull(session.accept(update(first, resync = true), live = true).announcement)
        assertNull(session.accept(update(first.draw()), live = true).announcement)
        val updated = session.accept(update(first), live = true).saved
        assertNull(updated.accept(update(first), live = true).announcement)
        assertThrows(InvalidRoomResponse::class.java) { updated.accept(update(first.copy(called = listOf(first.drawOrder[1]))), true) }
    }

    @Test fun `only called numbers on owned tickets can be marked and marks survive catchup`() {
        val ticket = round.tickets.first { it.playerId == "asha" }
        val index = round.drawOrder.indexOf(ticket.numbers.first()) + 1
        val game = (1..index).fold(round) { current, _ -> current.draw() }
        val session = saved(game).mark(ticket.id, ticket.numbers.first())
        assertEquals(setOf(ticket.numbers.first()), session.marks[ticket.id])
        assertEquals(session, session.mark("somebody-elses-ticket", game.called.first()))
        assertEquals(session, session.mark(ticket.id, 0))
        assertEquals(session.marks, session.accept(update(game.draw(), true), false).saved.marks)
        assertTrue(session.mark(ticket.id, ticket.numbers.first()).marks[ticket.id]!!.isEmpty())
        val rematch = Round.create(players, round.settings, random = Random(44)).start()
        assertTrue(session.accept(RoomUpdate(view(rematch, 999), emptyList(), false), false).saved.marks.isEmpty())
    }

    @Test fun `snapshot privacy score and draw commitment are checked before rendering`() {
        val good = view()
        good.validateFor("asha")
        val game = good.round!!
        assertThrows(InvalidRoomResponse::class.java) { good.copy(round = game.copy(revealedOrder = round.drawOrder)).validateFor("asha") }
        assertThrows(InvalidRoomResponse::class.java) { good.copy(round = game.copy(ownTickets = round.tickets.takeLast(2))).validateFor("asha") }
        assertThrows(InvalidRoomResponse::class.java) { good.copy(round = game.copy(scores = mapOf("asha" to 1000, "bina" to 0))).validateFor("asha") }
        val finished = (1..90).fold(round) { current, _ -> current.draw() }
        val result = view(finished)
        result.validateFor("asha")
        assertThrows(InvalidRoomResponse::class.java) { result.copy(round = result.round!!.copy(revealedNonce = "tampered")).validateFor("asha") }
        assertThrows(InvalidRoomResponse::class.java) { result.copy(round = result.round!!.copy(winningTickets = emptyList())).validateFor("asha") }
        val history = saved().accept(RoomUpdate(result, emptyList(), true), false).saved.history
        assertEquals(1, history.size)
        // The historical roster must survive another member leaving the finished room.
        result.copy(members = result.members.take(1)).validateFor("asha")
        assertEquals(2, result.round!!.players.size)
    }

    @Test fun `credential destination accepts HTTPS and only explicit debug loopback HTTP`() {
        assertEquals("https://rooms.example", checkedEndpoint("https://rooms.example/"))
        assertEquals("http://127.0.0.1:8080", checkedEndpoint("http://127.0.0.1:8080", true))
        listOf("http://rooms.example", "https://token@rooms.example", "https://rooms.example?token=x", "https://rooms.example/v1", "https://rooms.example#secret", "https://rooms.example:99999").forEach {
            assertThrows(IllegalArgumentException::class.java) { checkedEndpoint(it, true) }
        }
        assertThrows(IllegalArgumentException::class.java) { checkedEndpoint("http://127.0.0.1:8080") }
    }

    @Test fun `online badges survive replay serialization and history eviction without counting opponents`() {
        val ticket = round.tickets.first { it.playerId == "asha" }
        val ordered = round.copy(drawOrder = ticket.numbers + round.drawOrder.filterNot { it in ticket.numbers },
            settings = round.settings.copy(playAllNumbers = false))
        val finished = (1..15).fold(ordered) { game, _ -> game.draw() }
        var session = saved().accept(update(finished), false).saved
        assertTrue(session.badges.earned(Badge.FIRST_HOUSE))
        session = session.accept(update(finished), false).saved
        assertEquals(1, session.badges.completedRoundIds.size)
        // Later cancelled results evict the original winning snapshot, but cannot revoke its badge.
        repeat(55) { n ->
            val next = round.copy(id = "cancel-$n").cancel()
            session = session.accept(RoomUpdate(view(next, 100L + n), emptyList(), true), false).saved
        }
        assertEquals(50, session.history.size)
        assertTrue(session.history.none { it.round?.id == finished.id })
        val restored = WireJson.decodeFromString<OnlineSaved>(WireJson.encodeToString(session))
        assertEquals(session.badges, restored.badgeProgress())
        assertEquals(1, restored.badges.completedRoundIds.size)
        val opponent = finished.copy(awards = listOf(Award(Prize.FULL_HOUSE, 15, listOf("other"), listOf("bina"))))
        val legacy = saved().copy(history = listOf(view(opponent)))
        assertFalse(legacy.badgeProgress().earned(Badge.FIRST_HOUSE))
    }

    @Test fun `old encrypted snapshots without badge field recover milestones from completed history`() {
        val finished = (1..90).fold(round) { game, _ -> game.draw() }
        val older = saved().copy(history = listOf(view(finished), view(finished)))
        val json = WireJson.encodeToString(older)
        val objectValue = WireJson.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
        val withoutBadges = kotlinx.serialization.json.JsonObject(objectValue.filterKeys { it != "badges" }).toString()
        val restored = WireJson.decodeFromString<OnlineSaved>(withoutBadges)
        assertEquals(1, restored.badgeProgress().completedRoundIds.size)
        assertFalse(restored.badgeProgress().earned(Badge.FIVE_ROUNDS))
    }
}
