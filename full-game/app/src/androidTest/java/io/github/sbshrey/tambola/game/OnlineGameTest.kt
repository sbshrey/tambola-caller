package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in local integration: start the isolated service and adb reverse tcp:8080 first. */
class OnlineGameTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private fun tap(text: String) = compose.tapText(text)
    private fun type(tag: String, value: String) {
        val input = compose.onNodeWithTag(tag)
        input.performScrollTo().performTextReplacement(value)
        input.performImeAction()
        compose.waitForIdle()
    }
    private fun until(predicate: () -> Boolean) {
        try { compose.waitUntil(20_000, predicate) }
        catch (error: ComposeTimeoutException) {
            captureTestScreen("online-failure")
            val state = model.state.value
            throw AssertionError("Online wait failed: loading=${state.loading}, busy=${state.busy}, profile=${state.name != null}, pending=${state.pending}, connection=${state.connection}, error=${state.error}", error)
        }
    }
    private fun connected() = until { model.state.value.connection == Connection.LIVE }
    private fun saved(): OnlineSaved = runBlocking { checkNotNull(OnlineStore(context).read()) }

    @Before fun prepare() {
        assumeTrue("Opt-in local room integration", InstrumentationRegistry.getArguments().getString("tambolaOnline") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080")
        check(isAndroidEmulator())
        // This fixture uses English labels even when a preceding process saved Hindi.
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        until { compose.activity.resources.configuration.locales[0].language == "en" }
        runBlocking { PreferenceStore(context).update(Preferences(voice = false, reducedMotion = true)) }
        until { !model.state.value.loading }
        compose.runOnIdle { model.resetLocalData() }
        until { model.state.value.name == null && !model.state.value.loading }
        until { compose.hasTextNow("Play online") }
        tap("Play online")
    }

    @Test fun profileDeletionConfirmsAfterLostResponseAndKeepsOfflineGame() = runBlocking<Unit> {
        assumeTrue("Needs the isolated drop-response proxy", InstrumentationRegistry.getArguments().getString("tambolaFaultProxy") == "true")
        suspend fun control(path: String) = withContext(Dispatchers.IO) {
            val connection = java.net.URL("http://127.0.0.1:8082/$path").openConnection() as java.net.HttpURLConnection
            try { connection.requestMethod = "POST"; connection.connectTimeout = 3_000; connection.readTimeout = 3_000; check(connection.responseCode == 200) }
            finally { connection.disconnect() }
        }
        tap("‹ Home"); tap("Play solo"); tap("Just me"); tap("Deal the tickets")
        if (compose.hasTextNow("Start new round")) tap("Start new round")
        until { compose.hasTextNow("Call next number") }
        tap("Call next number"); until { compose.hasTextNow("Call 1 of 90") }
        val offlineBefore = ViewModelProvider(compose.activity)[GameViewModel::class.java].state.value.round!!
        tap("‹ Home"); tap("Play online")
        type("online-name", "Delete fixture Asha"); tap("Continue online")
        until { compose.hasTextNow("Create private room") }
        tap("Create private room"); until { model.state.value.room != null }; connected()
        val original = saved()
        val peerApi = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        val peer = peerApi.guest(GuestRequest("Remaining Bina"))
        val code = original.room!!.code
        try {
            tap("Edit room rules"); tap("Automatic online calling"); tap("Save room rules")
            until { !model.state.value.busy && model.state.value.room?.options?.automaticCalling == false }
            val joined = peerApi.join(peer.token, code).snapshot
            peerApi.command(peer.token, code, CommandRequest(UUID.randomUUID().toString(), joined.revision, RoomAction.Ready(true)))
            until { model.state.value.room!!.members.size == 2 }
            until { model.state.value.room!!.members.last().ready }
            tap("I'm ready"); until { !model.state.value.busy && model.state.value.room!!.members.all { it.ready } }
            tap("Start online round"); until { model.state.value.room?.phase == RoomPhase.ACTIVE }
            tap("Call next online number"); until { model.state.value.room?.round?.called?.size == 1 && !model.state.value.busy }
            tap("Delete online profile")
            compose.onNodeWithText("Delete your online profile?").assertExists()
            tap("Keep profile")
            assertEquals(original.credentials.playerId, model.state.value.playerId)
            control("arm-delete-drop")
            tap("Delete online profile"); captureTestScreen("delete-confirmation")
            compose.onNodeWithText("Delete profile permanently").assertIsDisplayed()
            compose.onNodeWithText("Keep profile").assertIsDisplayed()
            compose.scrollTextToEnd(context.getString(R.string.ui_this_permanently_removes_your_service_profile_and_access))
            captureTestScreen("delete-confirmation-end")
            compose.onNodeWithText("Delete profile permanently").assertIsDisplayed()
            compose.onNodeWithText("Keep profile").assertIsDisplayed()
            tap("Delete profile permanently")
            compose.waitUntil(35_000) { model.state.value.deletingProfile && !model.state.value.busy && model.state.value.error != null }
            val pending = saved().pending as PendingOperation.DeleteProfile
            assertEquals(original.credentials, saved().credentials)
            assertEquals(Connection.SUSPENDED, model.state.value.connection)
            assertEquals(RoomPhase.ACTIVE, saved().room!!.phase)
            compose.onNodeWithText("Call next online number").assertDoesNotExist()
            tap("Got it")
            compose.onNodeWithText("Profile deletion is waiting for confirmation").performScrollTo().assertIsDisplayed()
            captureTestScreen("delete-awaiting-confirmation")
            compose.onNodeWithText("Retry pending action").performScrollTo().assertIsDisplayed()
            captureTestScreen("delete-retry")
            compose.activityRule.scenario.recreate()
            until { !model.state.value.loading && model.state.value.deletingProfile }
            assertEquals(pending, saved().pending)
            control("allow-deletes")
            tap("Retry pending action")
            until { model.state.value.name == null && !model.state.value.busy }
            assertNull(OnlineStore(context).read())
            assertEquals(R.string.notice_profile_deleted, model.state.value.notice!!.resource)
            val remaining = peerApi.read(peer.token, code).snapshot
            assertEquals(peer.playerId, remaining.hostId)
            assertTrue(remaining.members.none { it.playerId == original.credentials.playerId })
            assertEquals("Deleted player", remaining.round!!.players.first { it.id == original.credentials.playerId }.name)
            assertEquals(2, peerApi.command(peer.token, code, CommandRequest(UUID.randomUUID().toString(), remaining.revision, RoomAction.Draw)).snapshot.round!!.called.size)
            val receipt = peerApi.deleteProfile(original.credentials.token, pending.request)
            assertEquals(pending.request.id, receipt.id)
            tap("‹ Home"); tap("Resume round")
            compose.onNodeWithText("Call 1 of 90").assertExists()
            val offlineAfter = ViewModelProvider(compose.activity)[GameViewModel::class.java].state.value.round!!
            assertEquals(offlineBefore.id, offlineAfter.id); assertEquals(offlineBefore.tickets, offlineAfter.tickets)
            assertEquals(offlineBefore.called, offlineAfter.called); assertEquals(offlineBefore.marks, offlineAfter.marks)
        } finally { control("allow-deletes"); peerApi.close() }
    }

    @Test fun nativeHostPlaysNinetyCallsWithPrivatePeerAndRestoresEncryptedSession() = runBlocking<Unit> {
        fun pickAvatar(label: String) = compose.onNode(hasContentDescription("$label avatar") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        type("online-name", "Online Asha")
        tap("Your profile · Sun\nChoose avatar"); pickAvatar("Moon"); tap("Continue online")
        until { compose.hasTextNow("Create private room") || model.state.value.error != null }
        assertNull("Registration error", model.state.value.error)
        tap("Create private room"); until { model.state.value.room != null }; connected()
        assertEquals(7, model.state.value.avatar)
        tap("Your profile · Moon\nChoose avatar"); pickAvatar("Mango")
        until { !model.state.value.busy && model.state.value.avatar == 1 }
        assertEquals(1, saved().avatar)
        val roomCode = model.state.value.room!!.code
        tap("Edit room rules"); compose.tapTag("online-tickets-2"); tap("Automatic online calling"); tap("Three houses"); tap("Call all 90 numbers")
        tap("Create custom prize"); type("prize-title", "Online five pair"); compose.tapTag("minimum-tickets-2"); tap("Save prize")
        tap("Save room rules"); until { !model.state.value.busy && model.state.value.room?.options?.game?.customPrizes?.size == 1 }
        val peerApi = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        val peer = peerApi.guest(GuestRequest("Online Bina", avatar = 3))
        var peerRoom = peerApi.join(peer.token, roomCode).snapshot
        val peerUpdates = MutableStateFlow(peerRoom)
        val peerJob = launch(Dispatchers.IO) { peerApi.events(peer.token, roomCode, null).collect { peerUpdates.value = it.snapshot } }
        try {
            peerRoom = peerApi.read(peer.token, roomCode).snapshot
            peerApi.command(peer.token, roomCode, CommandRequest(UUID.randomUUID().toString(), peerRoom.revision, RoomAction.Ready(true)))
            until { model.state.value.room!!.members.size == 2 && model.state.value.room!!.members.last().ready }
            tap("I'm ready"); until { !model.state.value.busy && model.state.value.room!!.members.all { it.ready } }
            compose.onNodeWithTag("room-code").performScrollTo(); compose.waitForIdle(); captureTestScreen("online-lobby")
            tap("Start online round"); until { model.state.value.room?.phase == RoomPhase.ACTIVE }
            val original = saved()
            assertEquals(listOf(1, 3), original.room!!.round!!.players.map { it.avatar })
            assertEquals(2, original.room!!.round!!.ownTickets.size)
            assertTrue(original.room!!.round!!.ownTickets.all { it.playerId == original.credentials.playerId })
            assertNull(original.room!!.round!!.revealedOrder)
            val encrypted = File(context.noBackupFilesDir, "private-rooms.enc").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(encrypted.contains(original.credentials.token)); assertFalse(encrypted.contains("Online Asha"))
            until { !model.state.value.busy }
            repeat(5) { index -> tap("Call next online number"); until { model.state.value.room?.round?.called?.size == index + 1 && !model.state.value.busy } }
            tap("Pause online calling"); until { model.state.value.room?.round?.status == RoundStatus.PAUSED && !model.state.value.busy }
            compose.activityRule.scenario.recreate(); connected()
            assertNull(model.state.value.winMoment)
            assertEquals(original.credentials.playerId, saved().credentials.playerId)
            assertTrue("Session token restored", original.credentials.token == saved().credentials.token)
            assertEquals(original.room!!.round!!.ownTickets, saved().room!!.round!!.ownTickets)
            assertEquals(original.room!!.round!!.players, saved().room!!.round!!.players)
            assertEquals(5, saved().room!!.round!!.called.size)
            compose.onNodeWithText("Mark ticket").performScrollTo(); compose.waitForIdle(); captureTestScreen("online-paused-table")
            tap("Resume online calling"); until { model.state.value.room?.round?.status == RoundStatus.PLAYING && !model.state.value.busy }
            // Simulates leaving the screen. Reconnect must restore the same round and calls.
            tap("‹ Home"); until { model.state.value.connection == Connection.SUSPENDED }
            tap("Play online"); connected()
            assertNull(model.state.value.winMoment)
            var inspectedWin = false
            repeat(85) { index ->
                tap("Call next online number"); until { model.state.value.room?.round?.called?.size == index + 6 && !model.state.value.busy }
                val current = model.state.value.room!!.round!!
                if (current.awards.any { it.drawIndex == index + 6 } || current.customAwards.any { it.drawIndex == index + 6 }) {
                    until { model.state.value.winMoment?.drawIndex == index + 6 }
                    assertTrue(model.state.value.winMoment!!.players.all { winner -> current.players.any { it == winner } })
                    if (!inspectedWin && model.state.value.room!!.phase == RoomPhase.ACTIVE) {
                        compose.onNodeWithTag("verified-win").performScrollTo().assertIsDisplayed()
                        compose.onNodeWithText("Call next online number").assertIsDisplayed()
                        captureTestScreen("online-verified-win")
                        tap("Dismiss celebration"); assertNull(model.state.value.winMoment)
                        tap("‹ Home"); until { model.state.value.connection == Connection.SUSPENDED }
                        tap("Play online"); connected(); assertNull(model.state.value.winMoment)
                        inspectedWin = true
                    }
                }
            }
            assertTrue("A live verified award was inspected", inspectedWin)
            until { model.state.value.room?.phase == RoomPhase.FINISHED }
            withTimeout(10_000) { peerUpdates.first { it.phase == RoomPhase.FINISHED } }
            val final = saved()
            val hostGame = final.room!!.round!!
            val peerGame = peerUpdates.value.round!!
            assertEquals((1..90).toSet(), hostGame.called.toSet())
            assertEquals(hostGame.called, peerGame.called); assertEquals(hostGame.scores, peerGame.scores)
            assertEquals(1, hostGame.customAwards.size)
            assertTrue(peerGame.ownTickets.all { it.playerId == peer.playerId })
            assertTrue(peerGame.ownTickets.none { ticket -> hostGame.ownTickets.any { it.id == ticket.id } })
            assertEquals(1, final.history.size)
            assertTrue(final.badges.earned(Badge.FIRST_ROUND))
            assertEquals(hostGame.awards.hasHouseFor(setOf(final.credentials.playerId)), final.badges.earned(Badge.FIRST_HOUSE))
            tap("Check claims · ${hostGame.awards.size + hostGame.customAwards.size} verified")
            tap("Inspect Online five pair"); compose.onNodeWithText("Matching owned tickets: 2 / 2 needed").assertExists()
            captureTestScreen("online-custom-winner"); tap("Back to prizes"); tap("Back to game")
            tap("Share online results"); compose.onNodeWithTag("share-preview").assertTextContains("Player 1", substring = true)
            assertFalse(compose.onNodeWithTag("share-preview").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString().contains("Online Asha"))
            captureTestScreen("online-private-share"); tap("Keep private")
            tap("Set up rematch"); until { model.state.value.room?.phase == RoomPhase.LOBBY && !model.state.value.busy }
            assertEquals(1, model.state.value.room!!.options.game.customPrizes.size)
            assertTrue(model.state.value.room!!.members.none { it.ready })
            assertNull(model.state.value.room!!.round)
            tap("‹ Home"); tap("Your badges"); tap("Online profile")
            compose.onNodeWithTag("badge-FIRST_ROUND").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Earned"))
        } finally { peerJob.cancelAndJoin(); peerApi.close() }
    }

    @Test fun nativeGuestJoinsMarksAndCatchesUpWithoutHostControls() = runBlocking<Unit> {
        val hostApi = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        val host = hostApi.guest(GuestRequest("Guest test host"))
        var room = hostApi.create(host.token, CreateRoomRequest(UUID.randomUUID().toString(),
            RoomOptions(game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 2, playAllNumbers = true), automaticCalling = false))).snapshot
        suspend fun command(action: RoomAction): RoomView {
            val current = hostApi.read(host.token, room.code).snapshot
            return hostApi.command(host.token, room.code, CommandRequest(UUID.randomUUID().toString(), current.revision, action)).snapshot
        }
        try {
            type("online-name", "Guest Bina"); tap("Continue online")
            until { compose.hasTextNow("Join room") }
            type("room-code-input", room.code.lowercase()); tap("Join room")
            until { model.state.value.room != null }; connected()
            tap("I'm ready"); until { !model.state.value.busy && model.state.value.room!!.members.last().ready }
            command(RoomAction.Ready(true)); room = command(RoomAction.Start)
            until { model.state.value.room?.phase == RoomPhase.ACTIVE }
            compose.onNodeWithText("Call next online number").assertDoesNotExist()
            val ticket = saved().room!!.round!!.ownTickets.first()
            do { room = command(RoomAction.Draw) } while (room.round!!.called.none { it in ticket.numbers })
            val number = room.round!!.called.first { it in ticket.numbers }
            until { number in model.state.value.room!!.round!!.called }
            tap("Mark ticket"); compose.onNodeWithContentDescription("Number $number").performScrollTo().performClick()
            until { number in model.state.value.marks[ticket.id].orEmpty() }
            tap("Done"); compose.waitForIdle(); captureTestScreen("online-guest-mark")
            tap("‹ Home"); until { model.state.value.connection == Connection.SUSPENDED }
            repeat(5) { room = command(RoomAction.Draw) }
            tap("Play online"); connected()
            until { model.state.value.room!!.round!!.called == room.round!!.called }
            assertNull(model.state.value.winMoment)
            assertEquals(setOf(number), saved().marks[ticket.id])
            assertEquals(ticket, saved().room!!.round!!.ownTickets.first())
            assertTrue(saved().room!!.round!!.ownTickets.none { it.playerId == host.playerId })
            room = command(RoomAction.End)
            until { model.state.value.room?.phase == RoomPhase.FINISHED }
            compose.onNodeWithText("Set up rematch").assertDoesNotExist()
            tap("Leave room"); compose.onNode(hasText("Leave room") and hasAnyAncestor(isDialog())).performClick()
            until { model.state.value.room == null && !model.state.value.busy }
            assertEquals(1, saved().history.size)
            assertFalse(saved().badges.earned(Badge.FIRST_ROUND))
            tap("Online history · 1")
            compose.onNodeWithText("${room.code} · ${room.round!!.called.size} calls · cancelled").assertExists()
        } finally { hostApi.close() }
    }
}
