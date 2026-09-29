package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class FriendModeRecoveryTest {
    @Test fun `preview capability follows power mode without leaking into classic`() {
        val newPower = requireNotNull(join(false).followFriendMode(mismatch, "new-power", supportsPreview = true))
        assertTrue(newPower.request.previewPowers)
        assertTrue(newPower.request.powersEnabled)
        val classic = requireNotNull(newPower.followFriendMode(mismatch, "classic", supportsPreview = true))
        assertFalse(classic.request.previewPowers)
        assertFalse(classic.request.powersEnabled)
    }
    private val mismatch = RoomApiFailure(409, "power_room_mismatch", "fixture")
    private fun join(power: Boolean) = PendingOperation.Match(MatchRequest("original", 6, true, "ABCD2345", rulesVersion = 2, powersEnabled = power))

    @Test fun `confirmed mismatch follows host mode and remains exactly retryable after restore`() {
        for (power in listOf(false, true)) {
            val pending = join(power)
            val replacement = requireNotNull(pending.followFriendMode(mismatch, "replacement"))
            assertEquals(pending.request.copy(id = "replacement", powersEnabled = !power), replacement.request)
            val restored = WireJson.decodeFromString<PendingOperation>(WireJson.encodeToString<PendingOperation>(replacement))
            assertEquals(replacement, restored)
            assertNotEquals(pending.request.id, replacement.request.id)
        }
    }

    @Test fun `ambiguous failures other rejections creation and replay never change the request`() {
        for (status in listOf(400, 401, 408, 429, 500, 503)) assertNull(join(true).followFriendMode(RoomApiFailure(status, "power_room_mismatch", "fixture"), "next"))
        for (code in listOf("coins_low", "id_reused", "room_full", "friend_table_closed", "update_required"))
            assertNull(join(true).followFriendMode(RoomApiFailure(409, code, "fixture"), "next"))
        val request = join(false).request
        for (other in listOf(request.copy(friendCode = null), request.copy(friendTable = false, friendCode = null),
            request.copy(previousFriendRound = "previous"), request.copy(rulesVersion = 1)))
            assertNull(PendingOperation.Match(other).followFriendMode(mismatch, "next"))
        assertThrows(IllegalArgumentException::class.java) { join(true).followFriendMode(mismatch, "original") }
    }
}
