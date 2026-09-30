package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.ui.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.Random

class BingoAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun hindiLargeTextKeepsHomeCardsAndPatternsUsable() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("hi")); fontScale = 2f }
        val context = compose.activity.createConfigurationContext(config)
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[BingoViewModel::class.java] }
        val fixture = BingoRound.practice(Player("me", "QA"), 6, 1, Random(9)).start().next().pause()
        var bingo by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources,
                LocalConfiguration provides config, LocalDensity provides Density(compose.activity.resources.displayMetrics.density, 2f)) {
                TambolaTheme(dark = true) { Surface(color = MaterialTheme.colorScheme.background) {
                    if (bingo) BingoScreen(BingoUiState(loading = false, round = fixture), model, "QA", true) { bingo = false }
                    else GameHub("QA", 50500, 0, Screen.BINGO, {}, {}, { bingo = true }, {})
                } }
            }
        }
        compose.onNodeWithTag("choose-tambola").assertIsDisplayed()
        compose.onNodeWithTag("choose-bingo").assertIsDisplayed()
        captureTestScreen("multi-game-home-hi-large")
        compose.onNodeWithTag("choose-bingo").performClick()
        compose.onNodeWithTag("bingo-card").assertIsDisplayed()
        compose.onNodeWithTag("bingo-next").assertIsDisplayed().performClick()
        compose.onNodeWithTag("bingo-toggle").assertIsDisplayed()
        compose.onNodeWithTag("bingo-prizes").assertIsDisplayed()
        captureTestScreen("bingo-card-hi-large")
        compose.onNodeWithTag("bingo-prizes").performClick()
        BingoPattern.entries.forEach { compose.onNodeWithTag("bingo-claim-${it.name}").assertIsDisplayed() }
        captureTestScreen("bingo-patterns-hi-large")
        compose.onNodeWithTag("bingo-prizes-close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("bingo-home").performClick()
        compose.onNodeWithTag("choose-tambola").assertIsDisplayed()
    }
}
