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
import org.junit.Assert.assertTrue
import java.util.Locale
import java.util.Random

class BingoOnlineAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun quickRoundShowsDirectClaimAndCardTabsInLandscape() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[OnlineViewModel::class.java] }
        val player = Player("me", "QA")
        val cards = BingoCardGenerator(Random(71)).deal("me", 2)
        val line = cards.first().cells.take(5)
        val game = PublicBingoRound("qa-fast", RoundStatus.PLAYING, line, cards,
            mapOf(cards.first().id to line.toSet()), emptyList(), listOf(player), mapOf("me" to 2),
            5, BingoCoinPool(2, 2).prizes, "0".repeat(64))
        val room = BingoRoomView("qa-room", "B-ABCD2345", 1, RoomPhase.ACTIVE, "me", true,
            listOf(MemberView("me", "QA", 0, true, true)), listOf(player), mapOf("me" to 2),
            null, 6000, 100000, 1000, 200, game, WalletView(50000, 1, 0))
        compose.setContent { TambolaTheme(dark = true) { Surface {
            BingoOnlineScreen(OnlineUiState(loading = false, available = true, playerId = "me",
                bingoRoom = room, wallet = room.wallet, connection = Connection.LIVE), model, true, {})
        } } }
        compose.onNodeWithTag("bingo-card").assertIsDisplayed()
        compose.onNodeWithTag("bingo-quick-goals").assertIsDisplayed()
        compose.onNodeWithTag("bingo-quick-claim-ANY_LINE").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("bingo-quick-claim-FOUR_CORNERS").assertIsDisplayed().assertIsNotEnabled()
        captureTestScreen("bingo-quick-direct-claim")
        compose.onNodeWithTag("bingo-card-tab-2").assertIsDisplayed().performClick()
        compose.onNodeWithTag("bingo-cell-${cards[1].numbers.first()}").assertIsDisplayed()
        captureTestScreen("bingo-quick-card-tabs")
    }
    @Test fun quickPortraitHindiLargeTextKeepsCallAndGoalsVisible() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("hi")); fontScale = 2f }
        val context = compose.activity.createConfigurationContext(config)
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[OnlineViewModel::class.java] }
        val player = Player("me", "QA")
        val cards = BingoCardGenerator(Random(75)).deal("me", 1)
        val game = PublicBingoRound("qa-portrait", RoundStatus.PLAYING, listOf(75), cards,
            emptyMap(), emptyList(), listOf(player), mapOf("me" to 1), 5, BingoCoinPool(1, 2).prizes, "0".repeat(64))
        val room = BingoRoomView("qa-room", "B-ABCD2345", 1, RoomPhase.ACTIVE, "me", true,
            listOf(MemberView("me", "QA", 0, true, true)), listOf(player), mapOf("me" to 1),
            null, 9000, 100000, 1000, 600, game, WalletView(50000, 1, 0))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources,
                LocalConfiguration provides config, LocalDensity provides Density(compose.activity.resources.displayMetrics.density, 2f)) {
                TambolaTheme(dark = true) { Surface {
                    BingoOnlineScreen(OnlineUiState(loading = false, available = true, playerId = "me",
                        bingoRoom = room, wallet = room.wallet, connection = Connection.LIVE), model, true, {})
                } }
            }
        }
        captureTestScreen("bingo-quick-hi-portrait")
        compose.onNodeWithTag("bingo-card").assertIsDisplayed()
        compose.onNodeWithTag("bingo-current-call").assertIsDisplayed()
        compose.onNodeWithTag("bingo-quick-goals").assertIsDisplayed()
        compose.onNodeWithTag("bingo-cell-${cards.first().numbers.last()}").assertIsDisplayed()
    }
    @Test fun liveLandscapeHasSquareCardReadyCellsAndPatternChase() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[OnlineViewModel::class.java] }
        val player = Player("me", "QA")
        val cards = BingoCardGenerator(Random(73)).deal("me", 1)
        val first = cards.first().numbers.first()
        val ready = cards.first().numbers.drop(1).first()
        val game = PublicBingoRound("qa-chase", RoundStatus.PLAYING, listOf(first, ready), cards,
            mapOf(cards.first().id to setOf(first)), emptyList(), listOf(player), mapOf("me" to 1),
            2, BingoCoinPool(1).prizes, "0".repeat(64))
        val room = BingoRoomView("qa-room", "B-ABCD2345", 1, RoomPhase.ACTIVE, "me", true,
            listOf(MemberView("me", "QA", 0, true, true)), listOf(player), mapOf("me" to 1),
            null, 9000, 100000, 1000, 600, game, WalletView(50000, 1, 0))
        compose.setContent { TambolaTheme(dark = true) { Surface {
            BingoOnlineScreen(OnlineUiState(loading = false, available = true, playerId = "me",
                bingoRoom = room, wallet = room.wallet, connection = Connection.LIVE), model, true, {})
        } } }
        compose.onNodeWithTag("bingo-chase").assertIsDisplayed()
        val cardBounds = compose.onNodeWithTag("bingo-card").getUnclippedBoundsInRoot()
        val firstCell = compose.onNodeWithTag("bingo-cell-$first").getUnclippedBoundsInRoot()
        assertTrue("Bingo cells stay square", kotlin.math.abs((firstCell.right - firstCell.left).value - (firstCell.bottom - firstCell.top).value) < 5f)
        assertTrue("Card stays inside its play area", firstCell.left >= cardBounds.left && firstCell.right <= cardBounds.right)
        compose.onNodeWithTag("bingo-cell-$ready").assertIsEnabled()
        compose.onNodeWithTag("bingo-cell-$first").assertIsEnabled()
        captureTestScreen("bingo-square-chase")
    }
    @Test fun quickBingoExplainsComputerFillWhileCountingDown() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("hi")); fontScale = 2f }
        val context = compose.activity.createConfigurationContext(config)
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[OnlineViewModel::class.java] }
        val person = Player("me", "QA")
        val computer = Player("computer-fixture-1", "Mira Rao", computer = true)
        val room = BingoRoomView("qa-room", "B-ABCD2345", 1, RoomPhase.LOBBY, "me", false,
            listOf(MemberView("me", "QA", 0, true, true)), listOf(person, computer),
            mapOf("me" to 1, computer.id to 2), 11_000, null, 100_000, 1_000, 300, null)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources,
                LocalConfiguration provides config, LocalDensity provides Density(compose.activity.resources.displayMetrics.density, 2f)) {
                TambolaTheme(dark = true) { Surface {
                    BingoOnlineScreen(OnlineUiState(loading = false, available = true, bingoRoom = room,
                        playerId = "me", connection = Connection.LIVE), model, true, {})
                } }
            }
        }
        compose.onNodeWithText(context.getString(R.string.bingo_countdown, 10)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.bingo_computer_fill)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.bingo_live_players, 1)).assertIsDisplayed()
        compose.onNodeWithTag("bingo-online-leave").assertIsDisplayed()
        captureTestScreen("quick-fill-bingo-hi-200")
    }
    @Test fun hindiLargePortraitShowsEveryOnlineCardChoiceAndFriendsAction() {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("hi")); fontScale = 2f }
        val context = compose.activity.createConfigurationContext(config)
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[OnlineViewModel::class.java] }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources,
                LocalConfiguration provides config, LocalDensity provides Density(compose.activity.resources.displayMetrics.density, 2f)) {
                TambolaTheme(dark = true) { Surface {
                    BingoOnlineScreen(OnlineUiState(loading = false, available = true, wallet = WalletView(50_000, 1, 0)),
                        model, true, {})
                } }
            }
        }
        (1..6).forEach { compose.onNodeWithTag("bingo-online-cards-$it").assertIsDisplayed() }
        compose.onNodeWithTag("bingo-online-play").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.bingo_friends)).assertIsDisplayed()
    }
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
                        playerId = "me", bingoRoom = room, wallet = room.wallet, connection = Connection.LIVE), model, true, {})
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
