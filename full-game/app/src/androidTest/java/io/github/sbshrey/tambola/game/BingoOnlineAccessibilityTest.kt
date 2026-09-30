package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.Random

class BingoOnlineAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun hindiLargeLandscapeKeepsSixCardsAndAllClaimChoicesVisible() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("hi")); fontScale = 2f }
        val context = compose.activity.createConfigurationContext(config)
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[OnlineViewModel::class.java] }
        val player = Player("me", "QA")
        val cards = BingoCardGenerator(Random(42)).deal("me", 6)
        val counts = mapOf("me" to 6)
        val game = PublicBingoRound("qa-game", RoundStatus.PLAYING, (1..75).toList(), cards,
            cards.associate { it.id to it.numbers.toSet() }, emptyList(), listOf(player), counts, 2, BingoCoinPool(6).prizes, "0".repeat(64))
        val room = BingoRoomView("qa-room", "B-ABCD2345", 1, RoomPhase.ACTIVE, "me", true,
            listOf(MemberView("me", "QA", 0, true, true)), listOf(player), counts, null, 9000, 100000, 1000, 600, game, WalletView(50000, 1, 0))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources,
                LocalConfiguration provides config, LocalDensity provides Density(compose.activity.resources.displayMetrics.density, 2f)) {
                TambolaTheme(dark = true) { Surface(color = MaterialTheme.colorScheme.background) {
                    BingoOnlineScreen(OnlineUiState(loading = false, available = true,
                        playerId = "me", bingoRoom = room, wallet = room.wallet, connection = Connection.LIVE), model, true, {}, {})
                } }
            }
        }
        compose.onNodeWithTag("bingo-card").assertIsDisplayed()
        compose.onNodeWithTag("bingo-cell-${cards.first().numbers.first()}").assertHeightIsAtLeast(40.dp)
        repeat(5) { compose.onNodeWithTag("bingo-online-next").assertIsDisplayed().performClick() }
        compose.onNodeWithTag("bingo-online-next").assertIsNotEnabled()
        compose.onNodeWithTag("bingo-online-home").assertIsDisplayed()
        compose.onNodeWithTag("bingo-online-prizes").assertIsDisplayed()
        captureTestScreen("bingo-online-hi-landscape")
        compose.onNodeWithTag("bingo-online-prizes").performClick()
        BingoPattern.entries.forEach { compose.onNodeWithTag("bingo-online-claim-${it.name}").assertIsDisplayed() }
        captureTestScreen("bingo-online-hi-prizes")
    }
}
