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
import java.util.UUID

class FriendTableTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun until(predicate: () -> Boolean) = compose.waitUntil(25_000, predicate)
    private fun id() = UUID.randomUUID().toString()
    private fun ready() = until { !model.state.value.busy && !model.state.value.pending && model.state.value.connection == Connection.LIVE }
    private suspend fun clean(api: HttpRoomApi, peer: GuestCredentials?) {
        peer?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
        OnlineStore(context).read()?.credentials?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
        compose.runOnIdle { model.resetLocalData() }
        api.close()
    }
    private fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaOnline") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        until { !model.state.value.loading }
        compose.runOnIdle { model.resetLocalData(); ViewModelProvider(compose.activity)[GameViewModel::class.java].navigate(Screen.HOME) }
        compose.useEnglish()
        until { model.state.value.name == null && !model.state.value.loading }
    }

    @Test fun createWaitRefundThenStartWithActualFriendAndRestoreTickets() = runBlocking<Unit> {
        prepare()
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        var peer: GuestCredentials? = null
        try {
            compose.tapTag("buy-tickets-1"); compose.tapTag("play-friends")
            captureTestScreen("friends-create-dialog")
            compose.tapTag("friend-enter"); ready()
            assertEquals(1400L, model.state.value.wallet!!.balance)
            compose.onNodeWithTag("friend-start").assertIsNotEnabled()
            delay(14_000)
            assertEquals(RoomPhase.LOBBY, model.state.value.room!!.phase)
            compose.tapTag("friend-copy")
            compose.onNodeWithText("Code copied").assertExists()
            captureTestScreen("friends-waiting-alone")
            compose.tapTag("cancel-match")
            until { model.state.value.room == null && !model.state.value.busy }
            assertEquals(1500L, model.state.value.wallet!!.balance)
            compose.tapTag("buy-tickets-6"); compose.tapTag("play-friends"); compose.tapTag("friend-enter"); ready()
            val code = model.state.value.room!!.code
            peer = api.guest(GuestRequest("Bina", 2))
            api.match(peer.token, MatchRequest(id(), 3, true, code))
            until { model.state.value.room?.members?.size == 2 }; ready()
            compose.onNodeWithTag("friend-start").assertIsEnabled()
            captureTestScreen("friends-ready-landscape")
            compose.tapTag("friend-start")
            until { model.state.value.room?.phase == RoomPhase.ACTIVE }
            val tickets = model.state.value.room!!.round!!.ownTickets
            assertEquals(6, tickets.size); assertEquals(900L, model.state.value.wallet!!.balance)
            until { compose.onAllNodesWithTag("play-arena").fetchSemanticsNodes().isNotEmpty() }
            captureTestScreen("friends-playing")
            compose.activityRule.scenario.recreate()
            until { model.state.value.room?.phase == RoomPhase.ACTIVE }; ready()
            assertEquals(tickets, model.state.value.room!!.round!!.ownTickets)
            assertEquals(900L, model.state.value.wallet!!.balance)
        } finally { clean(api, peer) }
    }

    @Test fun joinCodeFlowUsesChosenTicketsAndAllowsRefund() = runBlocking<Unit> {
        prepare()
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        var peer: GuestCredentials? = null
        try {
            peer = api.guest(GuestRequest("Rahul", 3))
            val room = api.match(peer.token, MatchRequest(id(), 2, true)).snapshot
            compose.tapTag("buy-tickets-3"); compose.tapTag("play-friends"); compose.tapTag("friend-join-mode")
            compose.onNodeWithTag("friend-enter").assertIsNotEnabled()
            compose.onNodeWithTag("friend-code-input").performTextInput(room.code.lowercase())
            compose.onNodeWithTag("friend-enter").assertIsEnabled()
            captureTestScreen("friends-join-dialog")
            compose.tapTag("friend-enter"); ready()
            assertEquals(room.code, model.state.value.room!!.code)
            assertEquals(3, model.state.value.room!!.coins!!.ownTickets)
            assertEquals(1200L, model.state.value.wallet!!.balance)
            compose.onNodeWithTag("friend-start").assertDoesNotExist()
            captureTestScreen("friends-guest-waiting")
            compose.tapTag("cancel-match")
            until { model.state.value.room == null && !model.state.value.busy }
            assertEquals(1500L, model.state.value.wallet!!.balance)
        } finally { clean(api, peer) }
    }
}
