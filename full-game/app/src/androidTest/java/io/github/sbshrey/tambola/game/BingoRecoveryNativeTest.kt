package io.github.sbshrey.tambola.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.domain.BingoPattern
import io.github.sbshrey.tambola.domain.isComplete
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class BingoRecoveryNativeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun post(port: Int, path: String) = runBlocking {
        withContext(Dispatchers.IO) {
            val connection = URL("http://127.0.0.1:$port/$path").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.connectTimeout = 3000; connection.readTimeout = 20000
                check(connection.responseCode in 200..299)
            } finally { connection.disconnect() }
        }
    }
    @Test fun lostPurchaseAndMarkRecoverWithoutDoubleChargeOrToggleThenClaimAndReplay() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bingoFixture") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        val app = compose.activity.application
        fun saved() = runBlocking { OnlineStore(app).read() }
        check(saved() == null) { "Preserve existing debug profiles" }
        val stores = mutableListOf<ViewModelStore>()
        var model by mutableStateOf<OnlineViewModel?>(null)
        fun fresh() = compose.runOnIdle {
            val store = ViewModelStore().also(stores::add)
            ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[OnlineViewModel::class.java]
        }
        fun until(predicate: () -> Boolean) = compose.waitUntil(30000, predicate)
        model = fresh()
        compose.setContent { TambolaTheme {
            val current = requireNotNull(model)
            val state by current.state.collectAsState()
            BingoOnlineScreen(state, current, true, {})
        } }
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        var peer: GuestCredentials? = null
        try {
            until { !model!!.state.value.loading }
            compose.runOnIdle { model!!.setActive(true) }
            post(8082, "arm-match-drop")
            compose.runOnIdle { model!!.playBingo(6, friendTable = true) }
            until { !model!!.state.value.busy && model!!.state.value.pending && model!!.state.value.error != null }
            val original = saved()!!
            val purchase = original.pending as PendingOperation.BingoMatch
            val charged = runBlocking { api.wallet(original.credentials.token) }.balance
            compose.runOnIdle { stores.last().clear() }
            model = fresh(); until { !model!!.state.value.loading }
            assertEquals(purchase, saved()!!.pending)
            post(8082, "allow-matches")
            compose.runOnIdle { model!!.setActive(true) }
            compose.onNodeWithTag("bingo-online-retry").performClick()
            until { !model!!.state.value.pending && !model!!.state.value.busy && model!!.state.value.bingoRoom != null }
            assertEquals(charged, model!!.state.value.wallet!!.balance)
            val code = model!!.state.value.bingoRoom!!.code
            peer = runBlocking { api.guest(GuestRequest("Bingo recovery peer")) }
            runBlocking { api.bingoMatch(peer!!.token, BingoMatchRequest(UUID.randomUUID().toString(), 1, true, code, quickPlay = true)) }
            until { model!!.state.value.bingoRoom!!.members.size == 2 }
            compose.runOnIdle { model!!.bingoCommand(BingoAction.Start) }
            until { !model!!.state.value.busy && model!!.state.value.bingoRoom?.phase == RoomPhase.ACTIVE }
            post(8080, "qa/bingo/tick/45")
            assertEquals(45, runBlocking { api.bingoRead(saved()!!.credentials.token, code) }.round!!.called.size)
            until { model!!.state.value.bingoRoom!!.round!!.called.size == 45 }
            val game = model!!.state.value.bingoRoom!!.round!!
            val card = game.ownCards.firstOrNull { own -> game.prizes.any { it.pattern.isComplete(own, game.called.toSet()) } }
                ?: error("Six cards should contain a quick goal by call 45")
            compose.onNodeWithTag("bingo-card-tab-${game.ownCards.indexOf(card) + 1}").performClick()
            val number = card.numbers.first { it in game.called }
            post(8082, "arm-command-drop")
            compose.onNodeWithTag("bingo-cell-$number").performClick()
            until { !model!!.state.value.busy && model!!.state.value.pending && model!!.state.value.error != null }
            val command = saved()!!.pending as PendingOperation.BingoCommand
            compose.runOnIdle { stores.last().clear() }
            model = fresh(); until { !model!!.state.value.loading }
            assertEquals(command, saved()!!.pending)
            post(8082, "allow-commands")
            compose.runOnIdle { model!!.setActive(true) }
            compose.onNodeWithTag("bingo-online-retry").performClick()
            until { !model!!.state.value.pending && !model!!.state.value.busy }
            assertTrue(number in model!!.state.value.bingoRoom!!.round!!.ownMarks.getValue(card.id))
            until { model!!.state.value.connection == Connection.LIVE }
            card.numbers.filter { it != number && it in game.called }.forEach { mark ->
                compose.onNodeWithTag("bingo-cell-$mark").performClick()
                until { !model!!.state.value.busy && mark in model!!.state.value.bingoRoom!!.round!!.ownMarks[card.id].orEmpty() }
            }
            val readyGoals = game.prizes.map { it.pattern }.filter { it.isComplete(card, game.called.toSet()) }
            readyGoals.forEach { pattern ->
                compose.onNodeWithTag("bingo-quick-claim-${pattern.name}").assertIsEnabled().performClick()
                until { !model!!.state.value.busy && model!!.state.value.bingoRoom!!.round!!.claims.any { it.playerId == model!!.state.value.playerId && it.pattern == pattern } }
            }
            assertEquals(charged, model!!.state.value.wallet!!.balance)
            post(8080, "qa/bingo/tick/1")
            until { model!!.state.value.bingoRoom?.phase == RoomPhase.FINISHED }
            compose.onNodeWithTag("bingo-online-ranking").assertIsDisplayed()
            compose.onNodeWithTag("bingo-results-summary").assertIsDisplayed()
            val settlement = model!!.state.value.bingoRoom!!.round!!.winnings.getValue(model!!.state.value.playerId!!).total
            assertEquals(charged + settlement, model!!.state.value.wallet!!.balance)
            captureTestScreen("bingo-online-results")
            compose.onNodeWithTag("bingo-online-again").performClick()
            compose.onNodeWithTag("bingo-online-cards-6").assertIsSelected()
        } finally {
            runCatching { post(8082, "allow-matches"); post(8082, "allow-commands") }
            compose.runOnIdle { stores.forEach { it.clear() } }
            runBlocking {
                peer?.let { api.deleteProfile(it.token, DeleteProfileRequest(UUID.randomUUID().toString())) }
                saved()?.let { api.deleteProfile(it.credentials.token, DeleteProfileRequest(UUID.randomUUID().toString())); OnlineStore(app).write(null) }
            }
            api.close()
        }
    }
}
