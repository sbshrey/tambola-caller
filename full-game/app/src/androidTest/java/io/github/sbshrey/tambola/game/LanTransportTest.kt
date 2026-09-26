package io.github.sbshrey.tambola.game

import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.HttpRoomApi
import io.github.sbshrey.tambola.client.validateFor
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.util.UUID

/** Real Android trust manager + OkHttp HTTPS/WSS against the installed private host.
 * No adb reverse, custom trust manager, insecure socket or stored app profile is used.
 */
class LanTransportTest {
    @Test fun encryptedRoomTransportUsesOnlyTheBundledLanAuthority() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaLan") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Read the installed variant, not the debug constants in the instrumentation APK.
        val config = context.classLoader.loadClass("io.github.sbshrey.tambola.game.BuildConfig")
        assertEquals("lan", config.getField("BUILD_TYPE").get(null))
        val origin = config.getField("ROOM_API_URL").get(null) as String
        val host = URI(origin).host
        check(host.startsWith("192.168.") && URI(origin).scheme == "https")
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host))
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1"))
        val api = HttpRoomApi(origin)
        val guests = mutableListOf<GuestCredentials>()
        try {
            guests += api.guest(GuestRequest("Android WiFi QA"))
            guests += api.guest(GuestRequest("Android WiFi Peer"))
            val owner = guests[0]; val peer = guests[1]
            var room = api.create(owner.token, CreateRoomRequest(UUID.randomUUID().toString(), RoomOptions(
                game = RoundSettings(mode = GameMode.ONLINE, manualClaims = true, ticketsPerPlayer = 3),
                intervalSeconds = 5, computerPlayers = 1,
            ))).snapshot
            room = api.join(peer.token, room.code).snapshot
            suspend fun command(actor: GuestCredentials, action: RoomAction) {
                room = api.command(actor.token, room.code, CommandRequest(UUID.randomUUID().toString(), room.revision, action)).snapshot
            }
            command(peer, RoomAction.Ready(true)); command(owner, RoomAction.Ready(true)); command(owner, RoomAction.Start)
            val live = withTimeout(15_000) { api.events(owner.token, room.code, null).first() }.snapshot
            live.validateFor(owner.playerId)
            assertEquals(3, live.round!!.ownTickets.size)
            assertEquals(1, live.round!!.players.count { it.computer })
            val other = api.read(peer.token, room.code).snapshot
            other.validateFor(peer.playerId)
            assertTrue(other.round!!.ownTickets.all { it.playerId == peer.playerId })
            assertNull(live.round!!.revealedOrder)
            File(context.filesDir, "lan-transport-validation.json").writeText(
                """{"https":true,"wss":true,"cleartextRejected":true,"ownTicketPrivacy":true,"computerLabels":true,"scope":"Android emulator to LAN host; not physical phone Wi-Fi"}"""
            )
        } finally {
            try { guests.forEach { api.deleteProfile(it.token, DeleteProfileRequest(UUID.randomUUID().toString())) } }
            finally { api.close() }
        }
    }
}
