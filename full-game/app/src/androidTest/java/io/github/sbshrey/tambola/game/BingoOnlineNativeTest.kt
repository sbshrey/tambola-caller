package io.github.sbshrey.tambola.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class BingoOnlineNativeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun sixCardPurchaseMarkAndFreshViewModelRestore() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bingoFixture") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        val app = compose.activity.application
        check(runBlocking { OnlineStore(app).read() } == null) { "Fixture requires an empty debug profile; existing profiles must be preserved" }
        val stores = mutableListOf<ViewModelStore>()
        var model by mutableStateOf<OnlineViewModel?>(null)
        fun fresh(): OnlineViewModel = compose.runOnIdle {
            val store = ViewModelStore().also(stores::add)
            ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[OnlineViewModel::class.java]
        }
        model = fresh()
        compose.setContent { TambolaTheme {
            val current = requireNotNull(model)
            val state by current.state.collectAsState()
            BingoOnlineScreen(state, current, reducedMotion = true, practice = {}, home = {})
        } }
        fun until(predicate: () -> Boolean) = compose.waitUntil(30_000, predicate)
        try {
            until { !model!!.state.value.loading }
            compose.runOnIdle { model!!.setActive(true) }
            compose.onNodeWithTag("bingo-online-cards-6").performClick()
            compose.onNodeWithTag("bingo-online-play").performClick()
            until { model!!.state.value.bingoRoom?.phase == RoomPhase.ACTIVE && !model!!.state.value.busy }
            val roomId = model!!.state.value.bingoRoom!!.roomId
            assertEquals(6, model!!.state.value.bingoRoom!!.round!!.ownCards.size)
            repeat(5) { compose.onNodeWithTag("bingo-online-next").performClick() }
            compose.onNodeWithTag("bingo-online-next").assertIsNotEnabled()
            captureTestScreen("bingo-online-six-cards")
            repeat(5) { compose.onNodeWithTag("bingo-online-previous").performClick() }
            until {
                model!!.state.value.bingoRoom!!.round!!.let { game -> game.ownCards.any { card -> card.numbers.any { it in game.called } } }
            }
            val game = model!!.state.value.bingoRoom!!.round!!
            val index = game.ownCards.indexOfFirst { card -> card.numbers.any { it in game.called } }
            repeat(index) { compose.onNodeWithTag("bingo-online-next").performClick() }
            val card = game.ownCards[index]
            val number = card.numbers.first { it in game.called }
            compose.onNodeWithTag("bingo-cell-$number").performClick()
            until { number in model!!.state.value.bingoRoom?.round?.ownMarks?.get(card.id).orEmpty() && !model!!.state.value.busy }
            compose.runOnIdle { model!!.setActive(false); stores.first().clear() }
            model = fresh()
            until { !model!!.state.value.loading }
            assertEquals(roomId, model!!.state.value.bingoRoom!!.roomId)
            assertEquals(6, model!!.state.value.preferredBingoCards)
            assertTrue(number in model!!.state.value.bingoRoom!!.round!!.ownMarks.getValue(card.id))
            compose.runOnIdle { model!!.setActive(true) }
            until { model!!.state.value.connection == Connection.LIVE }
            compose.runOnIdle { model!!.deleteProfile() }
            until { model!!.state.value.name == null && !model!!.state.value.busy }
            assertNull(runBlocking { OnlineStore(app).read() })
        } finally {
            compose.runOnIdle { stores.forEach { it.clear() } }
            runBlocking {
                OnlineStore(app).read()?.let { saved ->
                    HttpRoomApi(BuildConfig.ROOM_API_URL, true).use { api ->
                        api.deleteProfile(saved.credentials.token, DeleteProfileRequest(UUID.randomUUID().toString()))
                    }
                    OnlineStore(app).write(null)
                }
            }
        }
    }
}
