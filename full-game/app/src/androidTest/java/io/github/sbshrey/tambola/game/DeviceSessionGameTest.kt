package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Isolated HTTP/WSS service + loopback fault proxy. The first wallet 401 is injected;
 * subsequent token rotation, rejection, receipts and deletion are real server behavior. */
class DeviceSessionGameTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun saved() = runBlocking { requireNotNull(OnlineStore(context).read()) }
    private suspend fun control(path: String) = withContext(Dispatchers.IO) {
        val connection = java.net.URL("http://127.0.0.1:8082/$path").openConnection() as java.net.HttpURLConnection
        try { connection.requestMethod = "POST"; connection.connectTimeout = 3000; connection.readTimeout = 3000; check(connection.responseCode == 200) }
        finally { connection.disconnect() }
    }

    @Test fun paidGameRenewsAcrossResponseLossAndActivityRecreationThenConfirmsDeletion() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaFaultProxy") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        val preferences = PreferenceStore(context)
        val originalPreferences = preferences.values.first()
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        until { !model.state.value.loading }
        compose.runOnIdle { model.resetLocalData(); ViewModelProvider(compose.activity)[GameViewModel::class.java].navigate(Screen.HOME) }
        until { model.state.value.name == null }
        preferences.update(Preferences(voice = false, music = false, effects = false, reducedMotion = true))
        try {
            compose.runOnIdle { model.play(6) }
            until { model.state.value.room?.phase == RoomPhase.ACTIVE && model.state.value.connection == Connection.LIVE && !model.state.value.busy }
            until { model.state.value.room!!.round!!.called.isNotEmpty() }
            val initial = saved()
            assertEquals(0L, initial.deviceIdentity!!.revision)
            assertEquals(900L, initial.wallet!!.balance)
            val number = initial.room!!.round!!.called.first()
            val ticket = initial.room!!.round!!.ownTickets.first { number in it.numbers }
            compose.runOnIdle { model.mark(ticket.id, number) }
            until { number in model.state.value.marks[ticket.id].orEmpty() }
            control("arm-session-drop"); control("reject-next-wallet")
            compose.runOnIdle { model.refreshWallet() }
            until { runCatching { saved().deviceIdentity?.pending != null }.getOrDefault(false) && model.state.value.error != null }
            val uncertain = saved()
            assertFalse(model.state.value.sessionExpired)
            val pending = requireNotNull(uncertain.deviceIdentity!!.pending)
            try { api.wallet(initial.credentials.token); fail("Old token must be rejected after committed rotation") }
            catch (error: RoomApiFailure) { assertEquals(401, error.status) }
            compose.activityRule.scenario.recreate()
            until { !model.state.value.loading }
            assertEquals(pending, saved().deviceIdentity!!.pending)
            control("allow-sessions")
            compose.runOnIdle { model.clearError(); model.reconnect(); model.refreshWallet() }
            until { model.state.value.connection == Connection.LIVE && saved().deviceIdentity!!.pending == null }
            val recovered = saved()
            assertEquals(initial.credentials.playerId, recovered.credentials.playerId)
            assertEquals(initial.deviceIdentity!!.key, recovered.deviceIdentity!!.key)
            assertEquals(1L, recovered.deviceIdentity!!.revision)
            assertEquals(pending.token, recovered.credentials.token)
            assertEquals(initial.room!!.roomId, recovered.room!!.roomId)
            assertEquals(6, recovered.room!!.round!!.ownTickets.size)
            assertTrue(number in recovered.marks[ticket.id].orEmpty())
            assertEquals(900L, api.wallet(recovered.credentials.token).balance)
            assertEquals(recovered.credentials, api.renewSession(recovered.deviceIdentity!!.key, pending).credentials)
            assertFalse(model.state.value.sessionExpired)

            control("arm-delete-drop")
            compose.runOnIdle { model.deleteProfile() }
            until { model.state.value.pending && !model.state.value.busy && model.state.value.error != null }
            val deleting = saved()
            assertTrue(deleting.pending is PendingOperation.DeleteProfile)
            compose.activityRule.scenario.recreate()
            until { !model.state.value.loading && model.state.value.deletingProfile }
            assertEquals(deleting.credentials, saved().credentials)
            assertEquals(deleting.pending, saved().pending)
            control("allow-deletes")
            compose.runOnIdle { model.retry() }
            until { model.state.value.name == null && !model.state.value.busy && !model.state.value.pending }
            assertEquals(R.string.notice_profile_deleted, model.state.value.notice!!.resource)
            assertNull(OnlineStore(context).read())
            val report = JSONObject().put("passed", true).put("wallet", 900).put("tickets", 6)
                .put("singleSessionRevision", 1).put("sameIdentityAndMarks", true)
                .put("lostRenewalResponseAndActivityRecreation", true).put("deletionRetryAfterRotation", true)
                .put("scope", "Dedicated emulator, isolated real service; injected initial wallet 401; actual committed renewal response dropped. Activity recreation, not process death.")
            File(context.filesDir, "device-session-native.json").writeText(report.toString(2) + "\n")
        } finally {
            control("allow-sessions"); control("allow-deletes")
            runCatching { OnlineStore(context).read() }.getOrNull()?.let { value ->
                val proof = value.deviceIdentity?.pending
                val credentials = if (proof != null) api.renewSession(value.deviceIdentity!!.key, proof).credentials else value.credentials
                api.deleteProfile(credentials.token, (value.pending as? PendingOperation.DeleteProfile)?.request ?: DeleteProfileRequest(UUID.randomUUID().toString()))
            }
            compose.runOnIdle { model.resetLocalData() }
            preferences.update(originalPreferences); api.close()
        }
    }
}
