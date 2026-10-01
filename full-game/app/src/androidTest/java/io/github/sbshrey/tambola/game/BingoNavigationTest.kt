package io.github.sbshrey.tambola.game

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.game.online.OnlineUiState
import io.github.sbshrey.tambola.game.online.OnlineViewModel
import io.github.sbshrey.tambola.game.ui.BingoOnlineScreen
import io.github.sbshrey.tambola.game.ui.GameHub
import io.github.sbshrey.tambola.game.ui.TambolaTheme
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BingoNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shortLandscapeHomeKeepsBothJoinActionsInsideCards() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        compose.setContent {
            TambolaTheme(dark = true) { GameHub("QA", 50_000, 0, null, {}, {}, {}, {}) }
        }
        val join = compose.activity.getString(R.string.hub_join_table)
        val actions = compose.onAllNodesWithText(join, useUnmergedTree = true)
        actions.assertCountEquals(2)
        val tambolaAction = actions[0].getUnclippedBoundsInRoot()
        val tambolaCard = compose.onNodeWithTag("choose-tambola").getUnclippedBoundsInRoot()
        val bingoAction = actions[1].getUnclippedBoundsInRoot()
        val bingoCard = compose.onNodeWithTag("choose-bingo").getUnclippedBoundsInRoot()
        assertTrue("Tambola Join action must remain within its card",
            tambolaAction.top >= tambolaCard.top && tambolaAction.bottom <= tambolaCard.bottom)
        assertTrue("Bingo Join action must remain within its card",
            bingoAction.top >= bingoCard.top && bingoAction.bottom <= bingoCard.bottom)
        captureTestScreen("jalsa-short-landscape-home")
    }

    @Test fun bothGameChoicesRouteToOnlinePlayWithoutPracticeEntry() {
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[OnlineViewModel::class.java] }
        var screen by mutableStateOf(Screen.HOME)
        compose.setContent {
            TambolaTheme(dark = true) { Surface {
                if (screen == Screen.HOME) GameHub("QA", 50_000, 0, null, {}, { screen = Screen.ONLINE },
                    { screen = Screen.BINGO }, {})
                else if (screen == Screen.BINGO) BingoOnlineScreen(OnlineUiState(loading = false, available = false),
                    model, reducedMotion = true) { screen = Screen.HOME }
            } }
        }
        compose.onNodeWithTag("choose-bingo").performClick()
        compose.onNodeWithTag("bingo-online-home").assertIsDisplayed()
        compose.onNodeWithTag("bingo-practice-mode").assertDoesNotExist()
        compose.onNodeWithTag("bingo-online-home").performClick()
        compose.onNodeWithTag("choose-tambola").assertIsDisplayed()
        compose.onNodeWithTag("choose-tambola").performClick()
        compose.onNodeWithTag("choose-bingo").assertDoesNotExist()
    }
}
