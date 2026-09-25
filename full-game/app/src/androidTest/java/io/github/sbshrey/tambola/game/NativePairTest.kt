package io.github.sbshrey.tambola.game

import android.os.Process
import android.util.AtomicFile
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.RoundStatus
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import java.io.File
import java.util.UUID

/** Each role runs in a separate emulator. All room mutations go through its own native UI. */
class NativePairTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private val runId get() = checkNotNull(arguments.getString("tambolaRunId"))
    private val role get() = checkNotNull(arguments.getString("tambolaPairRole"))
    private fun until(timeout: Long = 90_000, predicate: () -> Boolean) = compose.waitUntil(timeout, predicate)
    private fun tap(text: String) = compose.tapText(text)
    private fun type(tag: String, text: String) {
        val input = compose.onNodeWithTag(tag)
        input.performScrollTo().performTextReplacement(text); input.performImeAction(); compose.waitForIdle()
    }
    private fun publish(phase: String) {
        val state = model.state.value
        val room = checkNotNull(state.room)
        room.round?.let { game ->
            assertEquals(2, game.ownTickets.size)
            assertTrue(game.ownTickets.all { it.playerId == state.playerId })
        }
        val value = PairWitness(runId, role, Process.myPid(), phase, state.playerId!!, room, state.history.size)
        val file = AtomicFile(File(context.filesDir, "native-pair.json"))
        val output = file.startWrite()
        try { output.write(WireJson.encodeToString(value).toByteArray()); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }

    @Test fun completeRoundAndRematchOnIndependentNativeClients() = runBlocking<Unit> {
        assumeTrue("Use the two-emulator driver", arguments.getString("tambolaNativePair") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        check(UUID.fromString(runId).toString() == runId && role in setOf("host", "guest"))
        PreferenceStore(context).update(Preferences(voice = false, reducedMotion = true))
        until { !model.state.value.loading }
        compose.runOnIdle { model.resetLocalData() }
        until { model.state.value.name == null && !model.state.value.loading }
        until { compose.hasTextNow("Play online") }; tap("Play online")
        type("online-name", if (role == "host") "Native pair Asha" else "Native pair Bina")
        tap("Continue online"); until { model.state.value.name != null && !model.state.value.busy }
        if (role == "host") {
            tap("Create private room"); until { model.state.value.room != null && !model.state.value.busy }
            tap("Edit room rules"); compose.tapTag("online-tickets-2"); tap("Automatic online calling")
            tap("Three houses"); tap("Call all 90 numbers"); tap("Save room rules")
            until { !model.state.value.busy && model.state.value.room!!.options.game.ticketsPerPlayer == 2 }
            publish("lobby")
            until { model.state.value.room!!.members.size == 2 }
        } else {
            type("room-code-input", checkNotNull(arguments.getString("tambolaRoomCode")))
            tap("Join room"); until { model.state.value.room != null && !model.state.value.busy }
        }
        until { model.state.value.connection == Connection.LIVE }
        tap("I'm ready"); until { !model.state.value.busy && model.state.value.room!!.members.first { it.playerId == model.state.value.playerId }.ready }
        if (role == "host") {
            until { model.state.value.room!!.members.size == 2 && model.state.value.room!!.members.all { it.ready && it.connected } }
            tap("Start online round")
        }
        until { model.state.value.room?.phase == RoomPhase.ACTIVE && !model.state.value.busy }
        val first = model.state.value.room!!
        val firstRound = first.round!!
        compose.onNodeWithText("Mark ticket").performScrollTo().assertIsDisplayed()
        captureTestScreen("native-pair-$role-playing")
        if (role == "guest") compose.onNodeWithText("Call next online number").assertDoesNotExist()
        if (role == "host") repeat(90) { index ->
            tap("Call next online number")
            until { !model.state.value.busy && model.state.value.room!!.round!!.called.size == index + 1 }
        }
        until(300_000) { model.state.value.room?.phase == RoomPhase.FINISHED && !model.state.value.busy }
        val finished = model.state.value.room!!.round!!
        assertEquals(RoundStatus.COMPLETED, finished.status)
        assertEquals((1..90).toSet(), finished.called.toSet())
        assertEquals(firstRound.ownTickets, finished.ownTickets)
        assertEquals(1, model.state.value.history.size)
        compose.onNodeWithText("90 calls ·", substring = true).performScrollTo().assertIsDisplayed()
        captureTestScreen("native-pair-$role-finished")
        publish("finished")
        // Driver compares both independently observed calls, scores, cards and public audit before releasing this gate.
        val gate = File(context.filesDir, "native-pair-$runId-rematch")
        until { gate.exists() }
        if (role == "host") tap("Set up rematch")
        until { model.state.value.room?.phase == RoomPhase.LOBBY && !model.state.value.busy }
        assertNull(model.state.value.room!!.round)
        assertEquals(first.options, model.state.value.room!!.options)
        assertFalse(model.state.value.room!!.members.first { it.playerId == model.state.value.playerId }.ready)
        tap("I'm ready")
        until { !model.state.value.busy && model.state.value.room!!.members.first { it.playerId == model.state.value.playerId }.ready }
        if (role == "host") {
            until { model.state.value.room!!.members.all { it.ready && it.connected } }
            tap("Start online round")
        }
        until { model.state.value.room?.phase == RoomPhase.ACTIVE && !model.state.value.busy }
        val second = model.state.value.room!!.round!!
        assertNotEquals(firstRound.id, second.id)
        // Ticket IDs denote player/ordinal slots inside a round; a rematch changes the round and deal.
        assertTrue("The observed rematch must deal new number grids", second.ownTickets.zip(firstRound.ownTickets).any { (fresh, old) -> fresh.cells != old.cells })
        assertTrue(second.called.isEmpty())
        publish("rematched")
        val endGate = File(context.filesDir, "native-pair-$runId-end")
        until { endGate.exists() }
        if (role == "host") {
            tap("End online round")
            compose.onNode(hasText("End online round") and hasAnyAncestor(isDialog())).performClick()
        }
        until { model.state.value.room?.phase == RoomPhase.FINISHED && !model.state.value.busy }
        assertEquals(RoundStatus.CANCELLED, model.state.value.room!!.round!!.status)
        assertEquals(2, model.state.value.history.size)
        assertEquals(2, OnlineStore(context).read()!!.history.size)
        compose.waitForIdle(); captureTestScreen("native-pair-$role-rematch-result")
        publish("complete")
        gate.delete(); endGate.delete()
    }
}

@Serializable private data class PairWitness(
    val runId: String, val role: String, val pid: Int, val phase: String,
    val playerId: String, val room: RoomView, val historySize: Int,
)
