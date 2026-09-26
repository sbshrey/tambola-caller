package io.github.sbshrey.tambola.game

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.security.NetworkSecurityPolicy
import android.util.AtomicFile
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URI
import java.util.UUID

/** Real five-second game on the installed TLS host. Three passive QA peers fund a
 * 24-ticket pool so all eight native selected claims/payouts are deterministic.
 * No server clock, draw order, wallet or result is modified by this fixture.
 */
class LanCoinGameTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private fun until(timeout: Long = 25_000, condition: () -> Boolean) = compose.waitUntil(timeout, condition)
    private fun saved() = runBlocking { requireNotNull(OnlineStore(context).read()) }
    private fun showTicket(ordinal: Int) {
        repeat(6) {
            if (compose.onAllNodesWithTag("hand-ticket-$ordinal").fetchSemanticsNodes().isNotEmpty()) return
            val visible = (1..6).first { n -> compose.onAllNodesWithTag("hand-ticket-$n").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(if (ordinal < visible) "tickets-up" else "tickets-down").performClick()
        }
        error("Owned ticket page unavailable")
    }

    @Test fun realTimeCoinGamePaysClaimsThenBuysAndRefundsANewRound() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaLan") == "true")
        check(isAndroidEmulator())
        val config = context.classLoader.loadClass("io.github.sbshrey.tambola.game.BuildConfig")
        assertEquals("lan", config.getField("BUILD_TYPE").get(null))
        val origin = config.getField("ROOM_API_URL").get(null) as String
        check(URI(origin).scheme == "https" && URI(origin).host.startsWith("192.168."))
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(URI(origin).host))
        val api = HttpRoomApi(origin)
        val peers = mutableListOf<GuestCredentials>()
        val prefs = PreferenceStore(context)
        val originalPrefs = prefs.values.first()
        val frames = SessionFrames()
        val frameThread = HandlerThread("coin-game-frames").apply { start() }
        val report = JSONObject().put("completed", false).put("origin", origin).put("scope", "Android emulator UI over verified LAN HTTPS/WSS; three HTTP QA peers; no physical phone or mobile-network claim")
        val started = SystemClock.elapsedRealtime()
        var stage = "prepare"
        var mainGuest: GuestCredentials? = null
        var activeWindow: android.view.Window? = null
        fun checkpoint() {
            report.put("stage", stage).put("elapsedMs", SystemClock.elapsedRealtime() - started)
                .put("calls", model.state.value.room?.round?.called?.size ?: 0)
                .put("claims", model.state.value.room?.round?.awards?.size ?: 0)
                .put("frames", frames.snapshot())
            val file = AtomicFile(File(context.filesDir, "coin-lan-game.json"))
            val stream = file.startWrite()
            try { stream.write((report.toString(2) + "\n").toByteArray()); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
        }
        try {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
            until { !model.state.value.loading && compose.activity.resources.configuration.locales[0].language == "en" }
            // Previous debug endpoint is intentionally different; only this disposable emulator is reset.
            compose.runOnIdle { model.resetLocalData(); ViewModelProvider(compose.activity)[GameViewModel::class.java].navigate(Screen.HOME) }
            until { !model.state.value.storageFailure && model.state.value.name == null }
            prefs.update(Preferences(voice = false, music = false, effects = true, haptics = false, reducedMotion = false))
            compose.onNodeWithTag("buy-tickets-6").performScrollTo().performClick()
            compose.onNodeWithTag("coin-play").performScrollTo().performClick()
            until { model.state.value.room?.phase == RoomPhase.LOBBY && !model.state.value.busy }
            val actor = saved().credentials
            mainGuest = actor
            val originalRoom = model.state.value.room!!
            assertEquals(900L, model.state.value.wallet!!.balance)
            for (index in 1..3) {
                val peer = api.guest(GuestRequest("LAN QA Peer $index", index)); peers += peer
                assertEquals(originalRoom.roomId, api.match(peer.token, MatchRequest(UUID.randomUUID().toString(), 6)).snapshot.roomId)
            }
            until { model.state.value.room!!.members.size == 4 }
            assertEquals(2400L, model.state.value.room!!.coins!!.pool)
            assertEquals(8, model.state.value.room!!.coins!!.prizes.size)
            until { model.state.value.room?.phase == RoomPhase.ACTIVE && model.state.value.connection == Connection.LIVE }
            until { compose.onAllNodesWithTag("play-arena").fetchSemanticsNodes().isNotEmpty() }
            val cards = model.state.value.room!!.round!!.ownTickets
            assertEquals(6, cards.size); assertEquals(90, cards.flatMap { it.numbers }.distinct().size)
            assertEquals(0, model.state.value.room!!.options.computerPlayers)
            compose.runOnUiThread {
                activeWindow = compose.activity.window
                activeWindow!!.addOnFrameMetricsAvailableListener(frames, Handler(frameThread.looper))
            }
            frames.active.set(true)
            stage = "playing"; checkpoint()
            var seen = 0
            var photographed = false
            while (model.state.value.room?.phase == RoomPhase.ACTIVE) {
                check(SystemClock.elapsedRealtime() - started < 520_000) { "Native round exceeded deadline" }
                until(45_000) { model.state.value.room?.phase == RoomPhase.FINISHED ||
                    (model.state.value.room!!.round!!.called.size > seen && model.state.value.connection == Connection.LIVE) }
                if (model.state.value.room?.phase == RoomPhase.FINISHED) break
                val call = model.state.value.room!!.round!!
                for (number in call.called.drop(seen)) {
                    val card = cards.first { number in it.numbers }
                    showTicket(cards.indexOf(card) + 1)
                    if (number !in model.state.value.marks[card.id].orEmpty()) compose.onNodeWithTag("dab-$number").performClick()
                    until { number in model.state.value.marks[card.id].orEmpty() }
                }
                seen = call.called.size
                for (prize in model.state.value.room!!.options.game.prizes) {
                    val state = model.state.value
                    val snapshot = requireNotNull(state.room)
                    val round = requireNotNull(snapshot.round)
                    if (snapshot.phase != RoomPhase.ACTIVE || state.connection != Connection.LIVE || state.busy || state.pending || round.awards.any { it.prize == prize }) continue
                    val closedHouses = round.awards.filter { it.prize.isRankedHouse && it.drawIndex < round.called.size }
                    val nextHouse = snapshot.options.game.prizes.filter { it.isRankedHouse && closedHouses.none { award -> award.prize == it } }.minByOrNull { it.ordinal }
                    if (prize.isRankedHouse && prize != nextHouse) continue
                    val card = cards.firstOrNull { ticket -> prize.matches(ticket, state.marks[ticket.id].orEmpty()) &&
                        (!prize.isRankedHouse || closedHouses.none { ticket.id in it.ticketIds }) } ?: continue
                    val beforeCall = round.called.size
                    showTicket(cards.indexOf(card) + 1)
                    compose.onNodeWithTag("claim-ticket-${cards.indexOf(card) + 1}").performClick()
                    try { compose.onNodeWithTag("claim-prize-${prize.name}").performScrollTo().performClick() }
                    catch (error: AssertionError) { if (model.state.value.room!!.round!!.called.size == beforeCall) throw error else continue }
                    until { !model.state.value.busy && !model.state.value.pending }
                    if (!photographed && model.state.value.room!!.round!!.awards.isNotEmpty()) {
                        compose.waitForIdle(); captureTestScreen("coin-lan-first-win"); photographed = true
                    }
                }
                assertNull(model.state.value.error)
                checkpoint()
                if (seen % 10 == 0) println("Coin LAN game: $seen calls; ${model.state.value.room!!.round!!.awards.size} verified prizes")
            }
            frames.active.set(false)
            val result = model.state.value.room!!
            assertEquals(8, result.round!!.awards.size)
            assertTrue(result.round!!.awards.all { it.playerIds == listOf(actor.playerId) })
            assertEquals(2400L, result.coins!!.settledWinnings)
            assertEquals(0L, result.coins!!.returnedCoins)
            assertEquals(3300L, model.state.value.wallet!!.balance)
            assertEquals(6000L, api.wallet(actor.token).balance + peers.sumOf { api.wallet(it.token).balance })
            until { compose.onAllNodesWithTag("coin-winnings").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle(); captureTestScreen("coin-lan-results")
            report.put("finishedCalls", result.round!!.called.size).put("settledWinnings", result.coins!!.settledWinnings).put("finalBalance", 3300)
            stage = "play-again"; checkpoint()
            compose.onNodeWithTag("buy-tickets-3").performScrollTo().performClick()
            compose.onNodeWithTag("coin-play").performScrollTo().performClick()
            until { model.state.value.room?.phase == RoomPhase.LOBBY && !model.state.value.busy && model.state.value.connection == Connection.LIVE }
            assertNotEquals(originalRoom.roomId, model.state.value.room!!.roomId)
            assertEquals(3000L, model.state.value.wallet!!.balance)
            compose.onNodeWithTag("cancel-match").performScrollTo().performClick()
            until { model.state.value.room == null && !model.state.value.busy }
            assertEquals(3300L, model.state.value.wallet!!.balance)
            report.put("newRoundPurchasedAndRefunded", true).put("completed", true)
            stage = "passed"; checkpoint()
        } catch (error: Throwable) {
            report.put("failureType", error.javaClass.simpleName); checkpoint(); captureTestScreen("coin-lan-failure")
            throw error
        } finally {
            frames.active.set(false)
            activeWindow?.let { window -> compose.runOnUiThread { window.removeOnFrameMetricsAvailableListener(frames) } }
            frameThread.quitSafely(); frameThread.join(5000)
            try {
                (peers + listOfNotNull(mainGuest)).forEach { api.deleteProfile(it.token, DeleteProfileRequest(UUID.randomUUID().toString())) }
                compose.runOnIdle { model.resetLocalData() }
            } finally { prefs.update(originalPrefs); api.close() }
        }
    }
}
