package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.HttpRoomApi
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Opt-in loopback integration only: one native player and one authenticated API peer. */
class FriendReactionsOnlineTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun id() = UUID.randomUUID().toString()
    private fun until(predicate: () -> Boolean) = compose.waitUntil(25_000, predicate)

    @Test fun receiveSendExpireAndReconnectWithoutReplayingMessages() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaReactionsLocal") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        var peer: GuestCredentials? = null
        try {
            until { !model.state.value.loading }
            compose.runOnIdle { model.resetLocalData(); ViewModelProvider(compose.activity)[GameViewModel::class.java].navigate(Screen.HOME) }
            compose.useEnglish()
            until { model.state.value.name == null && !model.state.value.loading }
            compose.runOnIdle { model.register("ReviewPlayer") }
            until { model.state.value.name != null && !model.state.value.busy }
            peer = api.guest(GuestRequest("ReviewFriend", 2))
            val lobby = api.match(peer.token, MatchRequest(id(), 2, true, rulesVersion = 2, powersEnabled = true)).snapshot
            compose.runOnIdle { model.play(2, true, lobby.code) }
            until { model.state.value.room?.code == lobby.code && !model.state.value.busy && model.state.value.connection == Connection.LIVE }
            val ready = api.read(peer.token, lobby.code).snapshot
            api.command(peer.token, lobby.code, CommandRequest(id(), ready.revision, RoomAction.Start))
            compose.runOnIdle { ViewModelProvider(compose.activity)[GameViewModel::class.java].navigate(Screen.ONLINE) }
            until { model.state.value.room?.phase == RoomPhase.ACTIVE && model.state.value.reactions != null }
            until { compose.onAllNodesWithTag("owned-hand").fetchSemanticsNodes().isNotEmpty() }
            val handBounds = compose.onNodeWithTag("owned-hand").getUnclippedBoundsInRoot()
            val tickets = model.state.value.room!!.round!!.ownTickets
            val balance = model.state.value.wallet!!.balance

            suspend fun peerReaction(kind: FriendReaction) {
                val limits = requireNotNull(api.reactions(peer.token, lobby.code))
                delay((limits.nextAllowedAt - limits.serverTime).coerceAtLeast(0) + 100)
                val room = api.read(peer.token, lobby.code).snapshot
                api.react(peer.token, lobby.code, CommandRequest(id(), room.revision, RoomAction.React(room.round!!.id, kind)))
            }
            peerReaction(FriendReaction.GOOD_LUCK)
            until { model.state.value.reactions?.reactions?.any { it.playerId == peer.playerId && it.kind == FriendReaction.GOOD_LUCK } == true }
            compose.onNodeWithTag("claim-feedback").assertTextContains("ReviewFriend: Good luck!")
            assertEquals(handBounds, compose.onNodeWithTag("owned-hand").getUnclippedBoundsInRoot())
            captureTestScreen("online-reaction-received")

            delay(1100)
            compose.onNodeWithTag("game-options").performClick()
            compose.onNodeWithTag("friend-react").performClick()
            compose.onNodeWithTag("send-reaction-THANKS").assertIsEnabled().performClick()
            until { !model.state.value.reactionSending && model.state.value.reactions?.reactions?.any { it.playerId == model.state.value.playerId && it.kind == FriendReaction.THANKS } == true }
            val observed = requireNotNull(api.reactions(peer.token, lobby.code))
            assertTrue(observed.reactions.any { it.playerId == model.state.value.playerId && it.kind == FriendReaction.THANKS })
            assertEquals(balance, model.state.value.wallet!!.balance)
            assertEquals(tickets, model.state.value.room!!.round!!.ownTickets)
            captureTestScreen("online-reaction-sent")
            delay(6500)
            compose.onNodeWithTag("claim-feedback").assertTextEquals("")

            // A message accepted while backgrounded must not replay on resuming the room.
            compose.runOnIdle { model.setActive(false) }
            peerReaction(FriendReaction.WELL_PLAYED)
            compose.runOnIdle { model.setActive(true) }
            until { model.state.value.connection == Connection.LIVE && model.state.value.reactions != null }
            assertTrue(model.state.value.reactions!!.reactions.isEmpty())
            compose.onNodeWithTag("claim-feedback").assertTextEquals("")
            peerReaction(FriendReaction.NICE_WIN)
            until { model.state.value.reactions?.reactions?.any { it.kind == FriendReaction.NICE_WIN } == true }
            compose.onNodeWithTag("claim-feedback").assertTextContains("ReviewFriend: Nice win!")
            assertEquals(handBounds, compose.onNodeWithTag("owned-hand").getUnclippedBoundsInRoot())
            assertTrue(model.state.value.room!!.round!!.called.isNotEmpty())
            captureTestScreen("online-reaction-after-reconnect")
        } finally {
            compose.runOnIdle { model.setActive(false) }
            peer?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
            OnlineStore(context).read()?.credentials?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
            compose.runOnIdle { model.resetLocalData() }
            api.close()
        }
    }
}
