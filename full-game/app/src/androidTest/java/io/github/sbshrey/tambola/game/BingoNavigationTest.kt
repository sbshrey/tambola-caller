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
import org.junit.Rule
import org.junit.Test

class BingoNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
