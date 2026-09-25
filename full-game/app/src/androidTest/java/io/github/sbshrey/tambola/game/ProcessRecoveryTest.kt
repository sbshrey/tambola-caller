package io.github.sbshrey.tambola.game

import android.content.ContextWrapper
import android.os.Process
import android.util.AtomicFile
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Two-stage acceptance driven by android-process-recovery.mjs; seed is deliberately killed. */
class ProcessRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val online get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private val offline get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private val directory get() = File(context.noBackupFilesDir, "process-recovery-fixture").apply { mkdirs() }
    private val peerStore get() = OnlineStore(object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory })
    private val runId get() = checkNotNull(arguments.getString("tambolaRunId"))
    private val kind get() = checkNotNull(arguments.getString("tambolaRecoveryKind"))
    private fun until(predicate: () -> Boolean) = compose.waitUntil(30_000, predicate)
    private fun tap(text: String) = compose.tapText(text)
    private suspend fun saved() = checkNotNull(OnlineStore(context).read())
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun writeFixture(name: String, text: String) {
        val file = AtomicFile(File(directory, name))
        val stream = file.startWrite()
        try { stream.write(text.toByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    private suspend fun control(path: String) = withContext(Dispatchers.IO) {
        val connection = java.net.URL("http://127.0.0.1:8082/$path").openConnection() as java.net.HttpURLConnection
        try { connection.requestMethod = "POST"; connection.connectTimeout = 3_000; connection.readTimeout = 3_000; check(connection.responseCode == 200) }
        finally { connection.disconnect() }
    }
    @Before fun guard() {
        assumeTrue("Only the external process-recovery driver may run these cases", arguments.getString("tambolaProcessRecovery") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080")
        check(isAndroidEmulator())
        check(UUID.fromString(runId).toString() == runId && kind in setOf("draw", "delete"))
        until { !online.state.value.loading && !offline.state.value.loading }
    }

    @Test fun seedPendingAndWaitForProcessKill() = runBlocking<Unit> {
        PreferenceStore(context).update(Preferences(voice = false, reducedMotion = true))
        compose.runOnIdle { online.resetLocalData() }
        until { online.state.value.name == null && !online.state.value.loading }
        tap("Play solo"); tap("Just me"); tap("Deal the tickets")
        if (compose.hasTextNow("Start new round")) tap("Start new round")
        until { offline.state.value.round != null && !offline.state.value.saving && compose.hasTextNow("Call next number") }
        val ticket = offline.state.value.round!!.tickets.first()
        do {
            val count = offline.state.value.round!!.called.size
            if (count > 0) delay(550) // Respect the app's physical double-tap guard.
            tap("Call next number")
            until { offline.state.value.round!!.called.size == count + 1 && !offline.state.value.saving }
        } while (offline.state.value.round!!.called.none { it in ticket.numbers })
        val number = offline.state.value.round!!.called.first { it in ticket.numbers }
        tap("Mark ticket"); compose.onNodeWithContentDescription("Number $number").performScrollTo().performClick()
        until { number in offline.state.value.round!!.marks[ticket.id].orEmpty() }
        tap("Done"); tap("‹ Home"); tap("Play online")
        until { !offline.state.value.saving && offline.state.value.round!!.status == RoundStatus.PAUSED }
        val offlineRound = offline.state.value.round!!
        val field = compose.onNodeWithTag("online-name")
        field.performScrollTo().performTextReplacement("Recovery fixture Asha"); field.performImeAction(); compose.waitForIdle()
        tap("Continue online"); until { online.state.value.name != null && !online.state.value.busy }
        // Room setup uses the real service; the uncertain action and retry use native controls.
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        try {
            compose.runOnIdle { online.create(RoomOptions(automaticCalling = false)) }
            until { online.state.value.room != null && !online.state.value.busy && online.state.value.connection == Connection.LIVE }
            val host = saved()
            val peer = api.guest(GuestRequest("Recovery fixture Bina"))
            val joined = api.join(peer.token, host.room!!.code).snapshot
            peerStore.write(OnlineSaved(BuildConfig.ROOM_API_URL, peer, "Recovery fixture Bina", room = joined))
            api.command(peer.token, joined.code, CommandRequest(UUID.randomUUID().toString(), joined.revision, RoomAction.Ready(true)))
            until { online.state.value.room!!.members.size == 2 && online.state.value.room!!.members.last().ready }
            tap("I'm ready"); until { !online.state.value.busy && online.state.value.room!!.members.all { it.ready } }
            tap("Start online round"); until { online.state.value.room?.phase == RoomPhase.ACTIVE && !online.state.value.busy }
            val initial = saved()
            control(if (kind == "delete") "arm-delete-drop" else "arm-command-drop")
            if (kind == "delete") { tap("Delete online profile"); tap("Delete profile permanently") }
            else tap("Call next online number")
            until { online.state.value.pending && !online.state.value.busy && online.state.value.error != null }
            val pending = saved().pending!!
            val committed = api.read(peer.token, joined.code).snapshot
            if (kind == "delete") {
                assertTrue(pending is PendingOperation.DeleteProfile)
                assertEquals(peer.playerId, committed.hostId)
                assertFalse(committed.members.any { it.playerId == host.credentials.playerId })
            } else {
                assertTrue(pending is PendingOperation.Command)
                assertEquals(RoomAction.Draw, (pending as PendingOperation.Command).request.action)
                assertEquals(1, committed.round!!.called.size)
            }
            val witness = RecoveryWitness(runId, kind, Process.myPid(), host.credentials.playerId,
                digest(WireJson.encodeToString(host.credentials)), initial.room!!.code, initial.room!!.round!!.id,
                initial.room!!.round!!.ownTickets, pending, offlineRound)
            writeFixture("witness.json", WireJson.encodeToString(witness))
            tap("Got it"); compose.onNodeWithText("Retry pending action").performScrollTo().assertIsDisplayed()
            captureTestScreen("process-$kind-pending")
            writeFixture("ready.json", "{\"runId\":\"$runId\",\"kind\":\"$kind\",\"pid\":${Process.myPid()}}")
            // The external driver verifies this live PID, kills it, and launches verify in a new process.
            awaitCancellation()
        } finally { api.close() }
    }

    @Test fun verifyAfterColdProcessStart() = runBlocking<Unit> {
        val witness = WireJson.decodeFromString<RecoveryWitness>(File(directory, "witness.json").readText())
        assertEquals(runId, witness.runId); assertEquals(kind, witness.kind)
        assertNotEquals("Must use a new Android process", witness.pid, Process.myPid())
        val restored = saved()
        assertTrue("Original bearer credential restored", witness.credentialDigest == digest(WireJson.encodeToString(restored.credentials)))
        assertEquals(witness.playerId, restored.credentials.playerId)
        assertEquals(witness.pending, restored.pending)
        assertEquals(witness.roundId, restored.room!!.round!!.id)
        assertEquals(witness.tickets, restored.room!!.round!!.ownTickets)
        assertEquals(witness.offline, offline.state.value.round)
        val peer = checkNotNull(peerStore.read())
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        try {
            if (kind == "draw") {
                // Advance authoritative state beyond the old pending receipt before reconnect/retry.
                val server = api.read(restored.credentials.token, witness.code).snapshot
                assertEquals(1, server.round!!.called.size)
                api.command(restored.credentials.token, witness.code, CommandRequest(UUID.randomUUID().toString(), server.revision, RoomAction.Draw))
            }
            tap("Play online"); until { online.state.value.pending }
            if (kind == "draw") until { online.state.value.connection == Connection.LIVE && online.state.value.room!!.round!!.called.size == 2 }
            tap("Retry pending action")
            until { !online.state.value.pending && !online.state.value.busy }
            val server = api.read(peer.credentials.token, witness.code).snapshot
            if (kind == "delete") {
                assertNull(OnlineStore(context).read()); assertNull(online.state.value.name)
                assertEquals(R.string.notice_profile_deleted, online.state.value.notice!!.resource)
                assertEquals(peer.credentials.playerId, server.hostId)
                assertEquals("Deleted player", server.round!!.players.first { it.id == witness.playerId }.name)
                val next = api.command(peer.credentials.token, witness.code, CommandRequest(UUID.randomUUID().toString(), server.revision, RoomAction.Draw))
                assertEquals(1, next.snapshot.round!!.called.size)
                compose.onNodeWithText("Online profile deleted.", substring = true).performScrollTo().assertIsDisplayed()
            } else {
                val current = saved()
                assertEquals(2, current.room!!.round!!.called.size)
                assertEquals(server.round!!.called, current.room!!.round!!.called)
                assertEquals(witness.tickets, current.room!!.round!!.ownTickets)
                val original = witness.pending as PendingOperation.Command
                val receipt = api.command(restored.credentials.token, witness.code, original.request)
                assertEquals(1, receipt.snapshot.round!!.called.size)
                assertEquals(2, api.read(restored.credentials.token, witness.code).snapshot.round!!.called.size)
                compose.onNodeWithText("Latest number: ${current.room!!.round!!.called.last()} · 2 called").assertIsDisplayed()
            }
            compose.waitForIdle()
            captureTestScreen("process-$kind-recovered")
            tap("‹ Home"); tap("Resume round")
            assertEquals(witness.offline.id, offline.state.value.round!!.id)
            assertEquals(witness.offline.tickets, offline.state.value.round!!.tickets)
            assertEquals(witness.offline.called, offline.state.value.round!!.called)
            assertEquals(witness.offline.marks, offline.state.value.round!!.marks)
            peerStore.write(null)
            File(directory, "ready.json").delete(); File(directory, "witness.json").delete()
        } finally { api.close() }
    }
}

@Serializable private data class RecoveryWitness(
    val runId: String, val kind: String, val pid: Int, val playerId: String, val credentialDigest: String,
    val code: String, val roundId: String, val tickets: List<Ticket>, val pending: PendingOperation, val offline: Round,
)
