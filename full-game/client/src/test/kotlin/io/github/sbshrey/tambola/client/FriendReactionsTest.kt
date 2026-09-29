package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import io.github.sbshrey.tambola.domain.*

class FriendReactionsTest {
    @Test fun `snapshot boundary rejects foreign actors rounds duplicates and expired messages`() {
        val room = RoomView(code = "ABCD2345", roomId = "room", revision = 1, phase = RoomPhase.ACTIVE,
            hostId = "a", locked = true, options = RoomOptions(), members = listOf(MemberView("a", "Player1", 0, true, true)),
            round = PublicRound("round", RoundStatus.PLAYING, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), "fixture"),
            nextDrawAt = null, expiresAt = 100_000, serverTime = 10_000,
            coins = CoinTableView(2, 200, CoinPool(2, 2).prizes, 1, null, friendTable = true))
        val reaction = RoomReaction("a", "round", FriendReaction.THANKS, 9_000)
        val valid = ReactionSnapshot("room", "round", 1, 10_000, 19_000, listOf(reaction))
        valid.validateFor(room)
        listOf(valid.copy(roomId = "other"), valid.copy(roundId = "other"),
            valid.copy(reactions = listOf(reaction.copy(playerId = "outsider"))),
            valid.copy(reactions = listOf(reaction.copy(at = 10_001))),
            valid.copy(reactions = listOf(reaction.copy(at = 4_000))),
            valid.copy(reactions = listOf(reaction, reaction)), valid.copy(nextAllowedAt = 20_001)).forEach { invalid ->
            assertThrows(InvalidRoomResponse::class.java) { invalid.validateFor(room) }
        }
        assertThrows(InvalidRoomResponse::class.java) { valid.validateFor(room.copy(coins = room.coins!!.copy(friendTable = false))) }
    }
    @Test fun `old host missing optional route is supported but other failures are not swallowed`() = runBlocking {
        var status = HttpStatusCode.NotFound
        val api = HttpRoomApi("https://rooms.example", client = HttpClient(MockEngine { request ->
            assertEquals("/v1/rooms/ABCD2345/reactions", request.url.encodedPath)
            assertEquals("Bearer fixture-token", request.headers[HttpHeaders.Authorization])
            respond("", status)
        }))
        assertNull(api.reactions("fixture-token", "ABCD2345"))
        status = HttpStatusCode.Unauthorized
        try { api.reactions("fixture-token", "ABCD2345"); fail() } catch (error: RoomApiFailure) { assertEquals(401, error.status) }
        api.close()
    }
}
