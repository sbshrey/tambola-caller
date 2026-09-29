package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import io.github.sbshrey.tambola.client.validateFor
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class FriendReactionsTest : PostgresTest() {
    @Test fun `HTTP reactions authenticate and reject other actions`() = testApplication {
        application { roomsModule(database, service, runWorker = false) }
        val (a,b,room) = table()
        val path = "/v1/rooms/${room.code}/reactions"
        assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        val forged = client.post(path) { bearerAuth(a.token); contentType(ContentType.Application.Json)
            setBody(WireJson.encodeToString(CommandRequest(id(), room.revision, RoomAction.Start))) }
        assertEquals(HttpStatusCode.BadRequest, forged.status)
        val request = request(room)
        suspend fun send() = client.post(path) { bearerAuth(a.token); contentType(ContentType.Application.Json); setBody(WireJson.encodeToString(request)) }
        val sent = send()
        assertEquals(HttpStatusCode.OK, sent.status)
        assertEquals(sent.bodyAsText(), send().bodyAsText())
        val read = client.get(path) { bearerAuth(b.token) }
        assertEquals(HttpStatusCode.OK, read.status)
        assertEquals("no-store", read.headers[HttpHeaders.CacheControl])
        assertEquals(a.playerId, WireJson.decodeFromString<ReactionSnapshot>(read.bodyAsText()).reactions.single().playerId)
    }
    private fun id() = UUID.randomUUID().toString()
    private fun guest() = service.register(GuestRequest("Player123456"), id())
    private fun table(): Triple<GuestCredentials, GuestCredentials, RoomView> {
        val a = guest(); val b = guest()
        val room = service.match(a.token, MatchRequest(id(), 2, friendTable = true, rulesVersion = 2)).snapshot
        service.match(b.token, MatchRequest(id(), 2, friendTable = true, friendCode = room.code, rulesVersion = 2))
        val current = service.read(a.token, room.code).snapshot
        val active = service.command(a.token, room.code, CommandRequest(id(), current.revision, RoomAction.Start)).snapshot
        return Triple(a,b,active)
    }
    private fun request(room: RoomView, kind: FriendReaction = FriendReaction.GOOD_LUCK) =
        CommandRequest(id(), room.revision, RoomAction.React(room.round!!.id, kind))

    @Test fun `authenticated reaction is shared once without altering tickets coins or calls`() {
        val (a,b,room) = table()
        val command = request(room)
        val result = service.command(a.token, room.code, command)
        assertEquals(room.round, result.snapshot.round)
        assertEquals(room.coins, result.snapshot.coins)
        assertEquals(room.wallet, result.snapshot.wallet)
        assertEquals("reacted", result.events.last().type)
        assertFalse(WireJson.encodeToString(result).contains("reactions"))
        assertEquals(result, service.command(a.token, room.code, command))
        val peer = service.reactions(b.token, room.code)
        peer.validateFor(service.read(b.token, room.code).snapshot)
        assertEquals(listOf(RoomReaction(a.playerId, room.round!!.id, FriendReaction.GOOD_LUCK, now.get())), peer.reactions)
        service = RoomService(database, now::get)
        assertEquals(result, service.command(a.token, room.code, command))
        now.addAndGet(REACTION_LIFETIME_MS)
        assertTrue(service.reactions(b.token, room.code).reactions.isEmpty())
        assertEquals("reaction_wait", assertThrows(ApiFailure::class.java) {
            service.command(a.token, room.code, request(room))
        }.code)
    }

    @Test fun `cooldowns bound both sender and table and tolerate unrelated revisions`() {
        val (a,b,room) = table()
        service.command(a.token, room.code, request(room))
        assertEquals("reaction_wait", assertThrows(ApiFailure::class.java) { service.command(b.token, room.code, request(room)) }.code)
        now.addAndGet(1_000)
        service.command(b.token, room.code, request(room, FriendReaction.NICE_WIN))
        now.addAndGet(9_000)
        service.command(a.token, room.code, request(room, FriendReaction.THANKS))
        assertEquals(FriendReaction.THANKS, service.reactions(b.token, room.code).reactions.single().kind)
        assertEquals(now.get() + REACTION_COOLDOWN_MS, service.reactions(a.token, room.code).nextAllowedAt)
    }

    @Test fun `outsiders wrong rounds and practice rooms cannot react`() {
        val (a,_,room) = table()
        val outsider = guest()
        assertThrows(ApiFailure::class.java) { service.reactions(outsider.token, room.code) }
        assertThrows(ApiFailure::class.java) { service.command(outsider.token, room.code, request(room)) }
        assertEquals("reaction_unavailable", assertThrows(ApiFailure::class.java) {
            service.command(a.token, room.code, CommandRequest(id(), room.revision, RoomAction.React("old-round", FriendReaction.THANKS)))
        }.code)
        val practice = service.match(outsider.token, MatchRequest(id(), 2, rulesVersion = 2)).snapshot
        assertEquals("friends_only", assertThrows(ApiFailure::class.java) { service.reactions(outsider.token, practice.code) }.code)
    }

    @Test fun `profile deletion removes reactions from persisted room and peer reads`() {
        val (a,b,room) = table()
        service.command(a.token, room.code, request(room))
        service.deleteProfile(a.token, DeleteProfileRequest(id()), "reaction-fixture")
        assertTrue(service.reactions(b.token, room.code).reactions.isEmpty())
        database.transaction { c ->
            val stored = c.query("SELECT payload FROM rooms WHERE code = ?", room.code) { WireJson.decodeFromString<RoomRecord>(it.getString(1)) }.single()
            assertFalse(stored.reactions.containsKey(a.playerId))
        }
    }
}
