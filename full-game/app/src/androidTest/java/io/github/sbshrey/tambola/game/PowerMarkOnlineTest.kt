package io.github.sbshrey.tambola.game

import android.content.ContextWrapper
import java.io.File

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

/** Real local service + fault proxy; never enabled in a general device test run. */
class PowerMarkOnlineTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val peerStore get() = OnlineStore(object : ContextWrapper(context) {
        override fun getNoBackupFilesDir() = File(context.noBackupFilesDir, "mark-recovery-peer").apply { mkdirs() }
    })
    private fun id() = UUID.randomUUID().toString()
    private fun until(predicate: () -> Boolean) = compose.waitUntil(90000, predicate)
    private suspend fun control(path: String) = withContext(Dispatchers.IO) {
        val connection = URL("http://127.0.0.1:8082/$path").openConnection() as HttpURLConnection
        try { connection.requestMethod = "POST"; connection.connectTimeout = 3000; connection.readTimeout = 3000; check(connection.responseCode == 200) }
        finally { connection.disconnect() }
    }

    @Test fun burstSurvivesDelayedCommittedResponseLossWithoutDuplicatePowerProgress() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaMarkFaults") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        var peer: GuestCredentials? = null
        var preserve = false
        try {
            control("allow-commands"); control("undelay-commands")
            until { !model.state.value.loading }
            compose.runOnIdle { model.resetLocalData() }
            until { model.state.value.name == null && !model.state.value.busy }
            compose.runOnIdle { model.register("Mark queue fixture") }
            until { model.state.value.name != null && !model.state.value.busy }
            compose.runOnIdle {
                ViewModelProvider(compose.activity)[GameViewModel::class.java].navigate(Screen.ONLINE)
                model.choosePowerRoom(true); model.play(6, friendTable = true)
            }
            until { model.state.value.room != null && !model.state.value.busy && model.state.value.connection == Connection.LIVE }
            val code = model.state.value.room!!.code
            peer = api.guest(GuestRequest("Mark queue peer"))
            api.match(peer.token, MatchRequest(id(), 1, true, code, rulesVersion = 2, powersEnabled = true, previewPowers = true))
            until { model.state.value.room!!.members.size == 2 }
            compose.runOnIdle { model.command(RoomAction.Start) }
            until { (model.state.value.room?.round?.called?.size ?: 0) >= 5 && !model.state.value.busy }
            val game = model.state.value.room!!.round!!
            val shownPower = game.powers!!.nextPower
            val taps = game.called.take(5).map { number -> game.ownTickets.first { number in it.numbers }.id to number }
            val balance = model.state.value.wallet!!.balance
            control("delay-commands"); control("arm-command-drop")
            compose.runOnIdle { taps.forEach { (ticket, number) -> model.mark(ticket, number) } }
            until { model.state.value.pending && model.state.value.markSending }
            compose.onNodeWithTag("round-recovery").assertDoesNotExist()
            compose.onNodeWithTag("deadline-ring-${game.id}").assertExists()
            until { !model.state.value.busy && model.state.value.pending && model.state.value.error != null }
            val saved = checkNotNull(OnlineStore(context).read())
            val pending = saved.pending as PendingOperation.Command
            assertTrue(pending.request.action is RoomAction.Mark)
            assertEquals(4, saved.queuedMarks.size)
            assertEquals(1, api.read(saved.credentials.token, code).snapshot.round!!.powers!!.correctMarks)
            if (InstrumentationRegistry.getArguments().getString("tambolaMarkStage") == "seed") {
                peerStore.write(OnlineSaved(BuildConfig.ROOM_API_URL, checkNotNull(peer), "Mark queue peer"))
                preserve = true
                return@runBlocking
            }
            control("allow-commands")
            compose.onNodeWithTag("round-retry").assertIsEnabled().performClick()
            until { !model.state.value.pending && !model.state.value.busy }
            assertNull(model.state.value.error)
            val current = model.state.value.room!!.round!!.powers!!
            assertEquals(5, current.correctMarks)
            assertEquals(listOf(shownPower), current.inventory)
            taps.forEach { (ticket, number) -> assertTrue(number in current.marks[ticket].orEmpty()) }
            assertEquals(balance, model.state.value.wallet!!.balance)
            // Replaying the uncertain first receipt still contains only its original mark.
            assertEquals(1, api.command(saved.credentials.token, code, pending.request).snapshot.round!!.powers!!.correctMarks)
            assertEquals(5, api.read(saved.credentials.token, code).snapshot.round!!.powers!!.correctMarks)
        } finally {
            compose.runOnIdle { model.setActive(false) }
            if (!preserve) {
                control("allow-commands"); control("undelay-commands")
                peer?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
                OnlineStore(context).read()?.credentials?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
                compose.runOnIdle { model.resetLocalData() }
            }
            api.close()
        }
    }

    @Test fun recoverEncryptedQueueInNewProcess() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaMarkFaults") == "true" &&
            InstrumentationRegistry.getArguments().getString("tambolaMarkStage") == "recover")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        try {
            until { !model.state.value.loading && model.state.value.connection == Connection.LIVE }
            val saved = checkNotNull(OnlineStore(context).read())
            val pending = saved.pending as PendingOperation.Command
            assertTrue(pending.request.action is RoomAction.Mark)
            assertEquals(4, saved.queuedMarks.size)
            val code = saved.room!!.code
            val powers = api.read(saved.credentials.token, code).snapshot.round!!.powers!!
            assertEquals(1, powers.correctMarks)
            val balance = model.state.value.wallet!!.balance
            control("allow-commands"); control("delay-commands")
            compose.runOnIdle { model.retry() }
            until { !model.state.value.pending && !model.state.value.busy }
            assertNull(model.state.value.error)
            val confirmed = model.state.value.room!!.round!!.powers!!
            assertEquals(5, confirmed.correctMarks)
            assertEquals(listOf(powers.nextPower), confirmed.inventory)
            assertEquals(balance, model.state.value.wallet!!.balance)
            assertEquals(1, api.command(saved.credentials.token, code, pending.request).snapshot.round!!.powers!!.correctMarks)
            assertEquals(5, api.read(saved.credentials.token, code).snapshot.round!!.powers!!.correctMarks)
            val restored = checkNotNull(OnlineStore(context).read())
            assertNull(restored.pending)
            assertTrue(restored.queuedMarks.isEmpty())
        } finally {
            control("allow-commands"); control("undelay-commands")
            compose.runOnIdle { model.setActive(false) }
            peerStore.read()?.credentials?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
            OnlineStore(context).read()?.credentials?.let { api.deleteProfile(it.token, DeleteProfileRequest(id())) }
            peerStore.write(null)
            compose.runOnIdle { model.resetLocalData() }
            api.close()
        }
    }
}
