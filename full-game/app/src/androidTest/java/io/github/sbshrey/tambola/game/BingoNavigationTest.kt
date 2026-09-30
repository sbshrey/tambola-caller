package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.sbshrey.tambola.domain.RoundStatus
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class BingoNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val bingo get() = ViewModelProvider(compose.activity)[BingoViewModel::class.java]

    @Test fun sixCardPagingHomeAndActivityRecreationPreservePracticeRound() {
        assumeTrue(BuildConfig.DEBUG && android.os.Build.FINGERPRINT.contains("generic"))
        compose.waitUntil(20000) { !bingo.state.value.loading }
        compose.onNodeWithTag("hub-profile").performClick()
        compose.onNodeWithTag("lobby-player-name").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_keep_playing)).performClick()
        compose.onNodeWithTag("lobby-player-name").assertDoesNotExist()
        compose.waitForIdle()
        captureTestScreen("multi-game-home")
        val previousId = bingo.state.value.round?.id
        compose.runOnIdle { bingo.chooseCards(6); bingo.newRound("Bingo navigation QA") }
        compose.waitUntil(20000) { bingo.state.value.round?.id != previousId && bingo.state.value.round?.cards?.count { it.playerId == "me" } == 6 }
        val id = bingo.state.value.round!!.id
        compose.onNodeWithTag("choose-bingo").performClick()
        compose.onNodeWithText("Card 1 / 6").assertIsDisplayed()
        compose.onNodeWithTag("bingo-previous").assertIsNotEnabled()
        repeat(5) { compose.onNodeWithTag("bingo-next").performClick() }
        compose.onNodeWithText("Card 6 / 6").assertIsDisplayed()
        captureTestScreen("bingo-six-card-play")
        compose.onNodeWithTag("bingo-next").assertIsNotEnabled()
        compose.onNodeWithTag("bingo-toggle").performClick()
        compose.waitUntil(10000) { bingo.state.value.round?.status == RoundStatus.PLAYING }
        compose.onNodeWithTag("bingo-toggle").performClick()
        compose.waitUntil(10000) { bingo.state.value.round?.status == RoundStatus.PAUSED }
        compose.onNodeWithTag("bingo-home").performClick()
        compose.onNodeWithTag("choose-tambola").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20000) { !bingo.state.value.loading }
        assertEquals(id, bingo.state.value.round?.id)
        assertEquals(6, bingo.state.value.cards)
        assertEquals(RoundStatus.PAUSED, bingo.state.value.round?.status)
        compose.onNodeWithTag("choose-bingo").performClick()
        compose.onNodeWithTag("bingo-card").assertIsDisplayed()
        val store = ViewModelStore()
        val restored = compose.runOnIdle {
            ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(compose.activity.application))[BingoViewModel::class.java]
        }
        try {
            compose.waitUntil(20000) { !restored.state.value.loading }
            assertFalse(restored.state.value.error)
            assertEquals(id, restored.state.value.round?.id)
            assertEquals(bingo.state.value.round?.cards, restored.state.value.round?.cards)
            assertEquals(bingo.state.value.round?.draw, restored.state.value.round?.draw)
            assertEquals(RoundStatus.PAUSED, restored.state.value.round?.status)
            assertEquals(6, restored.state.value.cards)
        } finally { compose.runOnIdle { store.clear() } }
    }
}
