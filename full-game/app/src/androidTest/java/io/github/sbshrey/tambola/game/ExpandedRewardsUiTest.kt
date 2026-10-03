package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class ExpandedRewardsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun englishDailyAndPowerUpChoices() = checkRewards("en", 1f)
    @Test fun hindiDailyAndPowerUpChoicesAtLargeText() = checkRewards("hi", 2f)

    private fun checkRewards(language: String, scale: Float) {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        val wallet = WalletView(50_500, 3, 0)
        val state = OnlineUiState(loading = false, available = true, name = "Mira", playerId = "fixture",
            wallet = wallet, loginRewards = LoginRewards(wallet, 1, 500, 86_400_000, 48_500, true))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { CoinLobby(state, model, {}, {}, {}, reducedMotion = true) }
            }
        }
        compose.onNodeWithText(words(R.string.quick_tambola_panel)).assertIsDisplayed()
        if (scale == 1f) {
            compose.onNodeWithText(words(R.string.quick_tambola_heading)).assertIsDisplayed()
            compose.onNodeWithText(words(R.string.quick_tambola_pace)).assertIsDisplayed()
        }
        compose.onNodeWithTag("coin-wallet").performClick()
        compose.onNodeWithText(words(R.string.daily_coins_collected, 1, 500L)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(words(R.string.daily_coins_day, 7, 5000L)).performScrollTo().assertIsDisplayed()
        captureTestScreen("rewards-daily-$language")
        compose.onNodeWithText(words(R.string.ui_got_it)).performClick()
        compose.onNodeWithTag("choose-powerup").assertIsDisplayed().performClick()
        compose.onNodeWithText(words(R.string.quick_tambola_lobby_rules)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(words(R.string.quick_tambola_end)).performScrollTo().assertIsDisplayed()
        captureTestScreen("rewards-quick-help-$language")
        compose.onNodeWithText(words(R.string.ui_back_to_game)).performClick()
        compose.onNodeWithTag("coin-play").assertIsDisplayed()
    }
}
