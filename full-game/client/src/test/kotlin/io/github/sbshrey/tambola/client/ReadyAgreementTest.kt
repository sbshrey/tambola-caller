package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ReadyAgreementTest {
    private val room = RoomView(code = "ABCD2345", roomId = "room", revision = 5, phase = RoomPhase.LOBBY,
        hostId = "asha", locked = false, options = RoomOptions(),
        members = listOf(MemberView("asha", "Asha", 0, false, true), MemberView("bina", "Bina", 1, false, true)),
        round = null, nextDrawAt = null, expiresAt = 100_000, serverTime = 1_000)
    private val pending = PendingOperation.Command(room.code, CommandRequest("original", room.revision, RoomAction.Ready(true)), room.readyAgreement())
    private val changed = room.copy(revision = 6, members = room.members.map { if (it.playerId == "bina") it.copy(ready = true, connected = false) else it })

    @Test fun `peer readiness and presence can rebase without changing the agreed game`() {
        val rebased = pending.rebaseReady(changed, "replacement")!!
        assertEquals(CommandRequest("replacement", 6, RoomAction.Ready(true)), rebased.request)
        assertEquals(pending.readyAgreement, rebased.readyAgreement)
        assertEquals("original", pending.request.id)
        assertNull(pending.rebaseReady(room, "unused"))
        assertNull(pending.rebaseReady(room.copy(revision = 4), "unused"))
    }

    @Test fun `changed rules roster avatar host lock or lifecycle require another review`() {
        val cases = listOf(
            changed.copy(options = room.options.copy(intervalSeconds = 5)),
            changed.copy(options = room.options.copy(game = room.options.game.copy(ticketsPerPlayer = 6))),
            changed.copy(members = room.members.dropLast(1)),
            changed.copy(members = room.members.map { it.copy(avatar = 3) }),
            changed.copy(hostId = "bina"), changed.copy(locked = true),
            changed.copy(phase = RoomPhase.ACTIVE), changed.copy(roomId = "another-room"), changed.copy(code = "EFGH2345"))
        cases.forEach { assertNull(pending.rebaseReady(it, "unused")) }
    }

    @Test fun `persisted agreement survives process death and legacy commands never rebase`() {
        val restored = WireJson.decodeFromString<PendingOperation.Command>(WireJson.encodeToString(pending))
        assertEquals(pending, restored)
        assertNotNull(restored.rebaseReady(changed, "new"))
        val legacyJson = """{"type":"command","code":"ABCD2345","request":{"id":"old","expectedRevision":5,"action":{"type":"ready","value":true}}}"""
        val legacy = WireJson.decodeFromString<PendingOperation>(legacyJson) as PendingOperation.Command
        assertNull(legacy.rebaseReady(changed, "unused"))
        assertNull(pending.copy(request = pending.request.copy(action = RoomAction.Draw)).rebaseReady(changed, "unused"))
    }
}
