package io.github.sbshrey.tambola.game

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.AtomicFile
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Random

/** Owned emulator fixture: verifies a real persisted final-call window and native claim/replay flow. */
class BingoRoundUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun finalCallClaimResultsAndReplayWithDifferentCardCount() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val app = compose.activity.application
        val save = AtomicFile(File(app.filesDir, "bingo-practice.json"))
        val previous = if (save.baseFile.exists()) save.openRead().use { it.readBytes() } else null
        val prefs = app.getSharedPreferences("bingo", 0)
        val previousCards = prefs.getInt("cards", 1)
        val store = ViewModelStore()
        fun write(bytes: ByteArray) { val stream = save.startWrite(); stream.write(bytes); save.finishWrite(stream) }
        try {
            val cards = BingoCardGenerator(Random(42)).deal("me", 6)
            val card = cards.first()
            val missing = card.numbers.last()
            val fixture = BingoRound(id = "native-final-call", createdAt = 1,
                players = listOf(Player("me", "Bingo QA")), cards = cards,
                draw = BingoDraw.shuffled(Random(43)).copy(count = 75), status = RoundStatus.PAUSED,
                marks = mapOf(card.id to (card.numbers.toSet() - missing)))
            write(BingoRoundCodec.encode(fixture).toByteArray())
            val model = compose.runOnIdle { ViewModelProvider(store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app))[BingoViewModel::class.java] }
            compose.setContent { TambolaTheme {
                val state by model.state.collectAsState()
                BingoScreen(state, model, "Bingo QA", reducedMotion = true, home = {})
            } }
            compose.waitUntil(15000) { !model.state.value.loading }
            assertFalse(model.state.value.error)
            compose.onNodeWithTag("bingo-prizes").performClick()
            compose.onNodeWithTag("bingo-claim-BLACKOUT").assertIsNotEnabled()
            compose.onNodeWithTag("bingo-prizes-close").performClick()
            compose.onNodeWithTag("bingo-toggle").performClick()
            compose.waitUntil(10000) { model.state.value.round?.status == RoundStatus.PLAYING }
            compose.onNodeWithTag("bingo-cell-$missing").performClick()
            compose.waitUntil(5000) { missing in model.state.value.round!!.marks[card.id].orEmpty() }
            compose.onNodeWithTag("bingo-prizes").performClick()
            compose.onNodeWithTag("bingo-claim-BLACKOUT").assertIsEnabled().performClick()
            compose.waitUntil(5000) { model.state.value.round!!.claims.any { it.pattern == BingoPattern.BLACKOUT } }
            assertEquals(40, model.state.value.round!!.points("me"))
            compose.waitUntil(15000) { model.state.value.round!!.finished }
            compose.onNodeWithTag("bingo-result-me").assertIsDisplayed()
            captureTestScreen("bingo-round-results")
            compose.onNodeWithTag("bingo-replay").performClick()
            compose.onNodeWithTag("bingo-replay-cards-3").performClick()
            compose.onNodeWithTag("bingo-replay-deal").performClick()
            compose.waitUntil(15000) { model.state.value.round?.id != fixture.id }
            assertEquals(3, model.state.value.round!!.cards.count { it.playerId == "me" })
            assertEquals(RoundStatus.READY, model.state.value.round!!.status)
            compose.onNodeWithTag("bingo-card").assertIsDisplayed()
        } finally {
            compose.runOnIdle { store.clear() }
            if (previous == null) save.delete() else write(previous)
            prefs.edit().putInt("cards", previousCards).commit()
        }
    }
    @Test fun corruptPracticeSaveRequiresConfirmedReset() {
        check(isAndroidEmulator())
        val app = compose.activity.application
        val save = AtomicFile(File(app.filesDir, "bingo-practice.json"))
        val previous = if (save.baseFile.exists()) save.openRead().use { it.readBytes() } else null
        val store = ViewModelStore()
        fun write(bytes: ByteArray) { val stream = save.startWrite(); stream.write(bytes); save.finishWrite(stream) }
        try {
            write("invalid fixture".toByteArray())
            val model = compose.runOnIdle { ViewModelProvider(store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app))[BingoViewModel::class.java] }
            compose.setContent { TambolaTheme {
                val state by model.state.collectAsState()
                BingoScreen(state, model, "QA", true, {})
            } }
            compose.waitUntil(10000) { !model.state.value.loading }
            assertTrue(model.state.value.error)
            assertTrue(save.baseFile.exists())
            compose.onNodeWithTag("bingo-reset").performClick()
            assertTrue(save.baseFile.exists())
            compose.onNodeWithTag("bingo-reset-confirm").performClick()
            compose.waitUntil(10000) { !model.state.value.error }
            assertNull(model.state.value.round)
            compose.onNodeWithTag("bingo-deal").assertIsDisplayed()
        } finally {
            compose.runOnIdle { store.clear() }
            if (previous == null) save.delete() else write(previous)
        }
    }

}
