package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.Random

class TableExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun landscape() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }

    @Test fun minimalSettingsChangeOnlyPersonalPreferences() = settings("en", 1f)
    @Test fun minimalSettingsRemainUsableInHindiAtLargeText() = settings("hi", 1.5f)

    private fun settings(language: String, scale: Float) {
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        val context = compose.activity.createConfigurationContext(config)
        var preferences by mutableStateOf(Preferences(voice = false, voiceVolume = 0, musicVolume = 0))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { GameSettings(preferences, { preferences = it }, {}) }
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(5)
        val root = compose.onNodeWithTag("game-settings").getUnclippedBoundsInRoot()
        listOf("voice", "music", "effects", "haptics", "motion").forEach { key ->
            val control = compose.onNodeWithTag("setting-$key").assertIsDisplayed()
            val bounds = control.getUnclippedBoundsInRoot()
            assertTrue("$key must be a full touch target", (bounds.bottom - bounds.top).value >= 48)
            assertTrue("$key is clipped", bounds.top >= root.top && bounds.bottom <= root.bottom)
            control.performClick()
        }
        assertTrue(preferences.voice); assertEquals(100, preferences.voiceVolume)
        assertTrue(preferences.music); assertEquals(45, preferences.musicVolume)
        assertFalse(preferences.effects); assertFalse(preferences.haptics); assertTrue(preferences.reducedMotion)
        assertEquals(5, preferences.interval)
        compose.onNodeWithTag("open-game-data").assertIsDisplayed()
        captureTestScreen("table-settings-$language")
    }

    @Test fun countdownAddsOnlyRosterMembersWithoutInventedOpponents() {
        val pool = CoinPool(12)
        var room by mutableStateOf(RoomView(code = "DESIGN", roomId = "arrivals", revision = 1,
            phase = RoomPhase.LOBBY, hostId = "me", locked = false,
            options = RoomOptions(game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true,
                prizes = pool.prizes.map { it.prize }), capacity = 8, intervalSeconds = 5, computerPlayers = 3, coinGame = true),
            members = listOf(MemberView("me", "You", 0, true, true)), round = null, nextDrawAt = null,
            expiresAt = 90_000, serverTime = 10_000, coins = CoinTableView(12, 1200, pool.prizes, 3, 22_000)))
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        compose.setContent { TambolaTheme { CoinLobby(OnlineUiState(loading = false, available = true, name = "You", playerId = "me",
            room = room, wallet = WalletView(1200, 1, 0), connection = Connection.LIVE), model, {}, {}, {}, reducedMotion = true) } }
        val words = GameText(compose.activity.resources)
        compose.onAllNodesWithText(words(R.string.table_joining)).assertCountEquals(3)
        compose.onNodeWithText("ChaiChamp").assertDoesNotExist()
        captureTestScreen("table-arrivals-empty")
        compose.runOnIdle { room = room.copy(revision = 2, members = room.members + MemberView("real-peer", "ChaiChamp", 2, true, true)) }
        compose.onNodeWithText("ChaiChamp").assertIsDisplayed()
        compose.onAllNodesWithText(words(R.string.table_joining)).assertCountEquals(2)
        compose.onNodeWithText(words(R.string.table_joined, "ChaiChamp")).assertIsDisplayed()
        compose.onNodeWithTag("cancel-match").assertIsDisplayed()
        captureTestScreen("table-arrivals-joined")
        compose.runOnIdle { room = room.copy(serverTime = 30_000) }
        compose.onNodeWithTag("table-start-seconds").assertTextEquals("…")
    }

    @Test fun fiveSecondRingDrainsWithoutRestartingOnOtherUiChanges() {
        var redraw by mutableIntStateOf(0)
        val pool = CoinPool(12)
        val table = Round.create(listOf(Player("me", "You")), RoundSettings(ticketsPerPlayer = 3, manualClaims = true), Random(7))
            .start().draw().toTable().copy(coins = CoinTableView(12, 1200, pool.prizes, 3, null), nextDrawAt = 55_000, serverTime = 50_000)
        compose.setContent { TambolaTheme { PlayArena(table, "me", Preferences(), "Live $redraw", {}, {}, {}, null, {},
            markNumber = { _, _ -> }, claim = {}, footer = {}) } }
        val ring = compose.onNodeWithTag("deadline-ring-${table.id}")
        fun coloredPixels(): Int {
            val pixels = ring.captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width)
                if ((pixels[x, y].toArgb() and 0xffffff) == 0xa8e3cb) count++
            return count
        }
        val full = coloredPixels(); assertTrue(full > 100)
        SystemClock.sleep(1600)
        val partial = coloredPixels(); assertTrue(partial in 1 until full)
        compose.runOnIdle { redraw++ }
        val afterRecompose = coloredPixels(); assertTrue(afterRecompose <= partial)
        SystemClock.sleep(3700)
        assertEquals(0, coloredPixels())
        captureTestScreen("table-ring-finished")
    }
}
