package io.github.sbshrey.tambola.game

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Disposable emulator + isolated real HTTP/WSS service; does not alter the installed PC host. */
class CoinGameTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private fun until(predicate: () -> Boolean) = compose.waitUntil(25_000, predicate)
    private fun saved() = runBlocking { requireNotNull(OnlineStore(context).read()) }
    private fun tap(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }

    @Test fun lostPurchaseResponseKeepsIdentityAndRestoresTheSamePaidTable() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaFaultProxy") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        suspend fun control(path: String) = withContext(Dispatchers.IO) {
            val connection = java.net.URL("http://127.0.0.1:8082/$path").openConnection() as java.net.HttpURLConnection
            try { connection.requestMethod = "POST"; connection.connectTimeout = 3000; connection.readTimeout = 3000; check(connection.responseCode == 200) }
            finally { connection.disconnect() }
        }
        until { !model.state.value.loading }
        compose.runOnIdle { model.resetLocalData(); ViewModelProvider(compose.activity)[GameViewModel::class.java].navigate(Screen.HOME) }
        until { model.state.value.name == null }
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        try {
            control("arm-match-drop")
            compose.runOnIdle { model.play(6) }
            until { model.state.value.pending && !model.state.value.busy && model.state.value.error != null }
            val uncertain = saved()
            assertTrue(uncertain.pending is PendingOperation.Match)
            assertEquals(900L, api.wallet(uncertain.credentials.token).balance)
            compose.activityRule.scenario.recreate()
            until { !model.state.value.loading && model.state.value.pending }
            assertEquals(uncertain.pending, saved().pending)
            control("allow-matches")
            compose.runOnIdle { model.retry() }
            until { model.state.value.room != null && !model.state.value.pending && !model.state.value.busy }
            assertEquals(900L, model.state.value.wallet!!.balance)
            val recovered = saved()
            val duplicate = api.match(recovered.credentials.token, (uncertain.pending as PendingOperation.Match).request)
            assertEquals(recovered.room!!.roomId, duplicate.snapshot.roomId)
            assertEquals(900L, api.wallet(recovered.credentials.token).balance)
        } finally {
            control("allow-matches")
            runCatching { saved().credentials }.getOrNull()?.let { api.deleteProfile(it.token, DeleteProfileRequest(UUID.randomUUID().toString())) }
            compose.runOnIdle { model.resetLocalData() }
            api.close()
        }
    }

    @Test fun buyCancelRefundThenJoinWithPeerAndPlayOwnPagedTickets() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaOnline") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        until { !model.state.value.loading && compose.activity.resources.configuration.locales[0].language == "en" }
        PreferenceStore(context).update(Preferences(voice = false, reducedMotion = true))
        compose.runOnIdle { model.resetLocalData(); ViewModelProvider(compose.activity)[GameViewModel::class.java].navigate(Screen.HOME) }
        until { model.state.value.name == null && !model.state.value.loading }
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        var peer: GuestCredentials? = null
        try {
            until { compose.onAllNodesWithTag("coin-lobby").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            captureTestScreen("coin-ticket-selection")
            tap("buy-tickets-1"); tap("coin-play")
            until { model.state.value.room?.phase == RoomPhase.LOBBY && model.state.value.connection == Connection.LIVE && !model.state.value.busy }
            assertEquals(1400L, model.state.value.wallet!!.balance)
            tap("cancel-match")
            until { model.state.value.room == null && !model.state.value.busy }
            assertEquals(1500L, model.state.value.wallet!!.balance)
            tap("buy-tickets-6"); tap("coin-play")
            until { model.state.value.room?.phase == RoomPhase.LOBBY && !model.state.value.busy }
            peer = api.guest(GuestRequest("Bina", 2))
            val other = api.match(peer.token, MatchRequest(UUID.randomUUID().toString(), 2))
            until { model.state.value.room!!.members.size == 2 }
            assertEquals(other.snapshot.roomId, model.state.value.room!!.roomId)
            assertEquals(900L, model.state.value.wallet!!.balance)
            assertEquals(1400L, model.state.value.room!!.coins!!.pool)
            compose.waitForIdle()
            captureTestScreen("coin-countdown")
            until { model.state.value.room?.phase == RoomPhase.ACTIVE && model.state.value.connection == Connection.LIVE }
            until { compose.onAllNodesWithTag("play-arena").fetchSemanticsNodes().isNotEmpty() }
            val mine = model.state.value.room!!.round!!.ownTickets
            val theirs = api.read(peer.token, other.snapshot.code).snapshot.round!!.ownTickets
            assertEquals(6, mine.size); assertEquals(2, theirs.size)
            assertEquals(90, mine.flatMap { it.numbers }.distinct().size)
            assertTrue(mine.none { card -> theirs.any { it.id == card.id } })
            compose.onNodeWithTag("online-pause").assertDoesNotExist()
            compose.onNodeWithTag("online-next").assertDoesNotExist()
            compose.onNodeWithTag("hand-ticket-3").assertDoesNotExist()
            until { model.state.value.room!!.round!!.called.isNotEmpty() }
            val number = model.state.value.room!!.round!!.called.first()
            val ticket = mine.first { number in it.numbers }
            val ordinal = mine.indexOf(ticket) + 1
            repeat(5) {
                if (compose.onAllNodesWithTag("hand-ticket-$ordinal").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("tickets-down").performClick()
            }
            compose.onNodeWithTag("dab-$number").performClick()
            until { number in model.state.value.marks[ticket.id].orEmpty() }
            compose.waitForIdle()
            captureTestScreen("coin-live-table")
            compose.onNodeWithTag("claim-ticket-$ordinal").performClick()
            compose.onNodeWithTag("claim-prize-HOUSE_TWO").assertIsDisplayed()
            compose.onNodeWithTag("claim-prize-HOUSE_TWO").assertIsNotEnabled()
            captureTestScreen("coin-prize-picker")
            compose.onNodeWithTag("claim-prize-TOP_LINE").performClick()
            until { model.state.value.claimMessage != null && !model.state.value.busy }
            assertEquals(R.string.play_claim_none, model.state.value.claimMessage!!.resource)
            val credentials = saved().credentials
            assertEquals(900L, api.wallet(credentials.token).balance)
        } finally {
            val me = runCatching { saved().credentials }.getOrNull()
            me?.let { api.deleteProfile(it.token, DeleteProfileRequest(UUID.randomUUID().toString())) }
            peer?.let { api.deleteProfile(it.token, DeleteProfileRequest(UUID.randomUUID().toString())) }
            compose.runOnIdle { model.resetLocalData() }
            api.close()
        }
    }
}
