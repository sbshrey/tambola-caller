package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.HttpRoomApi
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FriendModeJoinOnlineTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun id() = UUID.randomUUID().toString()
    private fun until(predicate: () -> Boolean) = compose.waitUntil(25_000, predicate)
    @Test fun classicSelectionJoinsPowerHost() = joinHost(true)
    @Test fun powerSelectionJoinsClassicHost() = joinHost(false)

    private fun joinHost(power: Boolean) = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaFriendsLocal") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        var peer: GuestCredentials? = null
        try {
            until { !model.state.value.loading }
            compose.runOnIdle { model.resetLocalData(); ViewModelProvider(compose.activity)[GameViewModel::class.java].navigate(Screen.HOME) }
            compose.useEnglish()
            until { model.state.value.name == null && !model.state.value.loading }
            compose.runOnIdle { model.register("ReviewJoiner") }
            until { model.state.value.name != null && !model.state.value.busy }
            val balance = model.state.value.wallet!!.balance
            peer = api.guest(GuestRequest("ReviewHost", 2))
            val lobby = api.match(peer.token, MatchRequest(id(), 2, true, rulesVersion = 2, powersEnabled = power)).snapshot
            compose.runOnIdle { model.choosePowerRoom(!power) }
            compose.onNodeWithTag("buy-tickets-3").performClick()
            compose.onNodeWithTag("play-friends").performClick()
            compose.onNodeWithTag("friend-join-mode").performClick()
            compose.onNodeWithTag("friend-code-input").performTextInput(lobby.code)
            compose.onNodeWithTag("friend-code-input").performImeAction()
            android.os.SystemClock.sleep(800)
            compose.onNodeWithTag("friend-enter").performClick()
            until {
                check(model.state.value.error == null) { "Join failed: resource ${model.state.value.error?.resource}" }
                model.state.value.room?.code == lobby.code && !model.state.value.busy && model.state.value.connection == Connection.LIVE
            }
            assertEquals(power, model.state.value.room!!.options.powersEnabled)
            assertEquals(3, model.state.value.room!!.coins!!.ownTickets)
            assertEquals(balance - 300, model.state.value.wallet!!.balance)
            compose.onNodeWithTag("friend-mode").assertTextContains(if (power) "Power room" else "Classic", substring = true)
            val window = compose.onNodeWithTag("coin-lobby").getUnclippedBoundsInRoot()
            listOf("friend-mode", "friend-share", "friend-copy", "friend-expiry", "waiting-pool", "cancel-match").forEach { tag ->
                val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue("$tag fits the lobby", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            captureTestScreen("friend-host-mode-${if (power) "power" else "classic"}")
            compose.activityRule.scenario.recreate()
            until { !model.state.value.loading && model.state.value.connection == Connection.LIVE }
            assertEquals(lobby.code, model.state.value.room!!.code)
            assertEquals(balance - 300, model.state.value.wallet!!.balance)
            assertEquals(2, api.read(peer.token, lobby.code).snapshot.members.size)
            compose.onNodeWithTag("cancel-match").performClick()
            until { model.state.value.room == null && !model.state.value.busy }
            assertEquals(balance, model.state.value.wallet!!.balance)
        } finally {
            compose.runOnIdle { model.setActive(false) }
            peer?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
            OnlineStore(context).read()?.credentials?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
            compose.runOnIdle { model.resetLocalData() }
            api.close()
        }
    }
}
