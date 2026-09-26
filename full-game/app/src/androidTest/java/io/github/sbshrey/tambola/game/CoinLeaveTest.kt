package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** A committed leave loses its receipt while the real stream observes removed membership. */
class CoinLeaveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private fun until(predicate: () -> Boolean) = compose.waitUntil(25_000, predicate)
    private fun saved() = runBlocking { requireNotNull(OnlineStore(context).read()) }
    private fun tap(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private suspend fun control(path: String) = withContext(Dispatchers.IO) {
        val connection = URL("http://127.0.0.1:8082/$path").openConnection() as HttpURLConnection
        try { connection.requestMethod = "POST"; connection.connectTimeout = 3000; connection.readTimeout = 3000; check(connection.responseCode == 200) }
        finally { connection.disconnect() }
    }

    @Test fun committedLeaveRetainsExactRetryAndDoesNotShowRoomLoss() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaFaultProxy") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        until { !model.state.value.loading }
        check(OnlineStore(context).read() == null) { "Use an empty fixture profile; never reset an existing wallet" }
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        var actor: GuestCredentials? = null
        suspend fun buy() {
            tap("buy-tickets-3"); tap("coin-play")
            until { model.state.value.room?.phase == RoomPhase.LOBBY && !model.state.value.busy && model.state.value.connection == Connection.LIVE }
            assertEquals(1200L, model.state.value.wallet!!.balance)
        }
        try {
            compose.runOnIdle { model.register("Leave receipt QA") }
            until { model.state.value.wallet != null && !model.state.value.busy }
            actor = saved().credentials
            buy()
            control("arm-command-drop")
            tap("cancel-match")
            until { !model.state.value.busy && model.state.value.error != null }
            assertEquals(1500L, api.wallet(actor.token).balance)
            // Let the old server stream reject removed membership, including lifecycle reconnect.
            compose.runOnIdle { model.setActive(false); model.setActive(true) }
            delay(1_500)
            val pending = saved().pending as? PendingOperation.Command
            assertNotNull("A committed leave still needs its exact HTTP receipt", pending)
            assertEquals(RoomAction.Leave, pending!!.request.action)
            assertNotNull(saved().room)
            assertNotEquals(R.string.error_room_unavailable, model.state.value.error?.resource)
            control("allow-commands")
            compose.runOnIdle { model.clearError(); model.retry() }
            until { !model.state.value.pending && !model.state.value.busy && model.state.value.room == null }
            assertEquals(1500L, model.state.value.wallet!!.balance)
            assertNull(model.state.value.error)
            assertEquals(1500L, api.command(actor.token, pending.code, pending.request).snapshot.wallet!!.balance)
            assertEquals(1500L, api.wallet(actor.token).balance)
            repeat(3) {
                buy(); tap("cancel-match")
                until { !model.state.value.busy && model.state.value.room == null }
                delay(650)
                assertNull(model.state.value.error)
                assertNull(saved().pending)
                assertEquals(1500L, model.state.value.wallet!!.balance)
                compose.onNodeWithTag("coin-play").performScrollTo().assertIsEnabled()
            }
            // A real removal outside the app must still surface the unavailable-room error.
            buy()
            val room = saved().room!!
            api.command(actor.token, room.code, CommandRequest(UUID.randomUUID().toString(), room.revision, RoomAction.Leave))
            until { model.state.value.room == null && model.state.value.error?.resource == R.string.error_room_unavailable }
            until { model.state.value.wallet?.balance == 1500L }
            compose.runOnIdle { model.clearError() }
            captureTestScreen("leave-receipt-recovered")
        } finally {
            control("allow-commands")
            actor?.let {
                api.deleteProfile(it.token, DeleteProfileRequest(UUID.randomUUID().toString()))
                check(saved().credentials.playerId == it.playerId)
                compose.runOnIdle { model.resetLocalData() }
                until { model.state.value.name == null && !model.state.value.busy }
            }
            api.close()
        }
    }
}
