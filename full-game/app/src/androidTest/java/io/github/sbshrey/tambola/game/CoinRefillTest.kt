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

/** Real native UI/HTTP/ledger with a disposable test-schema wallet; never funds the installed host. */
class CoinRefillTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private fun until(predicate: () -> Boolean) = compose.waitUntil(25_000, predicate)
    private fun saved() = runBlocking { requireNotNull(OnlineStore(context).read()) }
    private fun tap(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private suspend fun post(port: Int, path: String) = withContext(Dispatchers.IO) {
        val connection = URL("http://127.0.0.1:$port/$path").openConnection() as HttpURLConnection
        try { connection.requestMethod = "POST"; connection.connectTimeout = 3000; connection.readTimeout = 3000; check(connection.responseCode in 200..299) }
        finally { connection.disconnect() }
    }

    @Test fun lostRefillResponseRetriesOnceAndAffordableReplaySurvivesRecreation() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaFaultProxy") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        until { !model.state.value.loading }
        check(OnlineStore(context).read() == null) { "Fixture requires an empty profile; never reset an existing wallet" }
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        var actor: GuestCredentials? = null
        try {
            compose.runOnIdle { model.register("Refill QA") }
            until { model.state.value.wallet != null && !model.state.value.busy }
            actor = saved().credentials
            tap("buy-tickets-6"); tap("coin-play")
            until { model.state.value.room?.phase == RoomPhase.LOBBY && !model.state.value.busy && model.state.value.connection == Connection.LIVE }
            tap("cancel-match")
            until { model.state.value.room == null && !model.state.value.busy }
            assertEquals(1500L, model.state.value.wallet!!.balance)
            compose.activityRule.scenario.recreate()
            until { !model.state.value.loading }
            compose.onNodeWithTag("buy-tickets-6").performScrollTo().assertIsSelected()
            post(8080, "qa/spend/${actor.playerId}")
            compose.runOnIdle { model.refreshWallet() }
            until { model.state.value.wallet?.balance == 0L }
            compose.onNodeWithTag("coin-refill").performScrollTo().assertIsEnabled()
            post(8082, "arm-refill-drop")
            tap("coin-refill")
            until { model.state.value.pending && !model.state.value.busy && model.state.value.error != null }
            val pending = saved().pending as PendingOperation.Refill
            assertEquals(500L, api.wallet(actor.token).balance)
            compose.activityRule.scenario.recreate()
            until { !model.state.value.loading && model.state.value.pending }
            // Rotation retains the ViewModel; explicitly model the independent wallet refresh.
            compose.runOnIdle { model.refreshWallet() }
            until { !model.state.value.loading && model.state.value.pending && model.state.value.wallet?.balance == 500L }
            assertEquals(pending, saved().pending)
            post(8082, "allow-refills")
            compose.runOnIdle { model.clearError() }
            tap("coin-retry")
            until { !model.state.value.pending && !model.state.value.busy }
            assertEquals(500L, api.wallet(actor.token).balance)
            assertEquals(6, saved().ticketPreference())
            compose.onNodeWithTag("buy-tickets-5").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("buy-tickets-6").assertIsNotEnabled()
            compose.onNodeWithTag("coin-play").performScrollTo().assertIsEnabled()
            captureTestScreen("coin-refilled-replay")
            post(8080, "qa/spend/${actor.playerId}")
            compose.runOnIdle { model.refreshWallet() }
            until { model.state.value.wallet?.balance == 0L }
            assertNotNull(model.state.value.serverTime)
            compose.onNodeWithTag("coin-refill").performScrollTo().assertIsNotEnabled()
            val remaining = model.state.value.wallet!!.refillAfter - model.state.value.serverTime!!.currentTimeMillis()
            assertTrue(remaining in 1..300_999)
            try { api.refill(actor.token, RefillRequest(UUID.randomUUID().toString())); fail("Server must enforce cooldown") }
            catch (error: RoomApiFailure) { assertEquals("refill_wait", error.code) }
            compose.activityRule.scenario.recreate()
            until { !model.state.value.loading && model.state.value.serverTime != null }
            compose.onNodeWithTag("coin-refill").performScrollTo().assertIsNotEnabled()
            captureTestScreen("coin-refill-cooldown")
        } finally {
            post(8082, "allow-refills")
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
