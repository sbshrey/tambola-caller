package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import java.security.MessageDigest
import java.util.Random
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString

class BingoOnlineStateTest {
    @Test fun `called Bingo mark rebases after a draw advances the table`() {
        val first = cards.first().numbers.first()
        val pending = PendingOperation.BingoCommand("B-ABCD2345",
            BingoCommandRequest("old", 2, BingoAction.Mark("game", cards.first().id, first)))
        val fresh = view().copy(revision = 3, round = view().round!!.copy(called = listOf(1, first).distinct()))
        val rebased = pending.rebaseMark(fresh, "new")
        assertEquals("new", rebased?.request?.id)
        assertEquals(3L, rebased?.request?.expectedRevision)
        assertNull(pending.rebaseMark(fresh.copy(revision = 2), "new"))
        assertNull(pending.rebaseMark(fresh.copy(phase = RoomPhase.FINISHED), "new"))
        assertNull(pending.rebaseMark(fresh.copy(round = fresh.round!!.copy(ownMarks = mapOf(cards.first().id to setOf(first)))), "new"))
        assertNull(pending.rebaseMark(fresh.copy(round = fresh.round!!.copy(called = emptyList())), "new"))
    }

    @Test fun `Bingo purchase and command survive save reload without changing Tambola preference`() {
        val purchase = PendingOperation.BingoMatch(BingoMatchRequest("purchase", 6))
        val pending = saved().copy(preferredTickets = 3).withPending(purchase)
        val restored = WireJson.decodeFromString<OnlineSaved>(WireJson.encodeToString(pending))
        assertEquals(purchase, restored.pending)
        assertEquals(6, restored.preferredBingoCards)
        assertEquals(3, restored.ticketPreference())
        val command = PendingOperation.BingoCommand("B-ABCD2345", BingoCommandRequest("mark", 2, BingoAction.Mark("game", "me-card", 1)))
        val marked = restored.copy(bingoRoom = view()).withPending(command)
        val retry = WireJson.decodeFromString<OnlineSaved>(WireJson.encodeToString(marked))
        assertEquals(command, retry.pending)
        assertEquals(view(), retry.bingoRoom)
    }
    private val players = listOf(Player("me", "Me"), Player("peer", "Peer"))
    private val cards = players.map { BingoCardGenerator(Random(it.id.hashCode().toLong())).generate(it.id + "-card", it.id) }
    private val order = (1..75).toList()
    private val hash = MessageDigest.getInstance("SHA-256").digest("bingo-75-draw-v1\ngame\nnonce\n${order.joinToString(",")}".toByteArray()).joinToString("") { "%02x".format(it) }
    private fun view() = BingoRoomView("room", "B-ABCD2345", 2, RoomPhase.ACTIVE, "me", true,
        players.map { MemberView(it.id, it.name, 0, true, true) }, players, mapOf("me" to 1, "peer" to 1),
        null, 9000, 100000, 1000, 200,
        PublicBingoRound("game", RoundStatus.PLAYING, listOf(1), cards.take(1), emptyMap(), emptyList(), players,
            mapOf("me" to 1, "peer" to 1), 2, BingoCoinPool(2).prizes, hash), WalletView(1400, 2, 0))
    private fun saved() = OnlineSaved("https://example.test", GuestCredentials("me", "test-token", 100000), "Me", wallet = WalletView(1500, 1, 0))
    private fun invalid(room: BingoRoomView) = assertThrows(InvalidRoomResponse::class.java) { room.validateFor("me") }

    @Test fun `private projection rejects foreign cards future calls invalid marks and changed economics`() {
        val room = view(); room.validateFor("me")
        val round = room.round!!
        invalid(room.copy(round = round.copy(ownCards = cards.takeLast(1))))
        invalid(room.copy(round = round.copy(revealedOrder = order)))
        invalid(room.copy(round = round.copy(called = listOf(1, 1))))
        invalid(room.copy(round = round.copy(ownMarks = mapOf("peer-card" to setOf(1)))))
        invalid(room.copy(round = round.copy(ownMarks = mapOf("me-card" to setOf(75)))))
        invalid(room.copy(pool = 201))
        invalid(room.copy(round = round.copy(prizes = round.prizes.map { it.copy(coins = it.coins + 1) })))
    }

    @Test fun `stale receipts cannot rewind wallet or cards and same revision cannot mutate game`() {
        val old = view()
        val newer = old.copy(revision = 4, round = old.round!!.copy(called = listOf(1, 2)), wallet = WalletView(1600, 4, 0))
        val accepted = saved().acceptBingo(newer, old)
        val replay = accepted.first.acceptBingo(old, accepted.second)
        assertEquals(newer, replay.second)
        assertEquals(1600L, replay.first.wallet!!.balance)
        assertThrows(InvalidRoomResponse::class.java) { accepted.first.acceptBingo(newer.copy(round = newer.round!!.copy(called = listOf(1, 3))), newer) }
        assertThrows(InvalidRoomResponse::class.java) { accepted.first.acceptBingo(old.copy(revision = 5), newer) }
        assertThrows(InvalidRoomResponse::class.java) { accepted.first.acceptBingo(newer.copy(roomId = "different"), newer) }
    }

    @Test fun `completed projection verifies committed draw and conserved payouts`() {
        val room = view()
        val finished = room.copy(phase = RoomPhase.FINISHED, nextDrawAt = null, round = room.round!!.copy(status = RoundStatus.COMPLETED,
            called = order, revealedOrder = order, revealedNonce = "nonce",
            winnings = players.associate { it.id to RoundWinnings(0, 0, 100) }))
        finished.validateFor("me")
        invalid(finished.copy(round = finished.round!!.copy(revealedNonce = "different")))
        invalid(finished.copy(round = finished.round!!.copy(winnings = mapOf("me" to RoundWinnings(200, 0, 0), "peer" to RoundWinnings(0, 0, 0)))))
        invalid(finished.copy(round = finished.round!!.copy(winnings = players.associate { it.id to RoundWinnings(0, 0, 99) })))
    }
}
