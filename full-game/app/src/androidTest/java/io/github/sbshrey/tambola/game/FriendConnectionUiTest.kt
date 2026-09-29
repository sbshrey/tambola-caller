package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.domain.CoinPool
import io.github.sbshrey.tambola.domain.GameMode
import io.github.sbshrey.tambola.domain.RoundSettings
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Render saved friends-table states without creating a profile, purchase or network connection. */
class FriendConnectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun englishLandscapeRetainsTableDuringReconnect() = checkConnection("en", 1f, true)
    @Test fun hindiLandscapeRetainsTableAtLargeText() = checkConnection("hi", 1.5f, true)
    @Test fun englishPortraitExplainsSavedTableAtLargeText() = checkConnection("en", 1.5f, false)
    @Test fun hindiPortraitExplainsSavedTableAtLargeText() = checkConnection("hi", 1.5f, false)

    private fun checkConnection(language: String, scale: Float, landscape: Boolean) {
        compose.runOnUiThread {
            compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        val room = RoomView(code = "ABCD2345", roomId = "connection-fixture", revision = 5, phase = RoomPhase.LOBBY,
            hostId = "me", locked = false, options = RoomOptions(game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6,
                manualClaims = true, assistedMarking = false), capacity = 8, coinGame = true, computerPlayers = 0, intervalSeconds = 5),
            members = listOf(MemberView("me", "Mira", 1, true, true), MemberView("peer", "Noor", 2, true, true)),
            round = null, nextDrawAt = null, expiresAt = 910_000, serverTime = 10_000,
            coins = CoinTableView(5, 500, CoinPool(5).prizes, 3, null, friendTable = true), wallet = WalletView(1200, 1, 0))
        var state by mutableStateOf(OnlineUiState(loading = false, available = true, name = "Mira", playerId = "me",
            room = room, wallet = room.wallet, connection = Connection.LIVE))
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { CoinLobby(state, model, {}, {}, {}, reducedMotion = true, reconnect = { retries++ }) }
            }
        }
        compose.onNodeWithTag("friend-start").assertIsDisplayed().assertIsEnabled()
        val before = compose.onNodeWithTag("friend-waiting").getUnclippedBoundsInRoot()
        val label = "$language-${if (landscape) "landscape" else "portrait"}"
        captureTestScreen("friend-connection-live-$label")
        listOf(Connection.CONNECTING, Connection.RECONNECTING, Connection.SUSPENDED).forEachIndexed { index, connection ->
            compose.runOnIdle { state = state.copy(connection = connection) }
            compose.onNodeWithTag("friend-start").assertIsDisplayed().assertIsNotEnabled()
                .assertTextEquals(words(R.string.friend_wait_connection))
            val after = compose.onNodeWithTag("friend-waiting").getUnclippedBoundsInRoot()
            assertEquals("A disconnected table must keep its width", (before.right - before.left).value, (after.right - after.left).value, .5f)
            compose.onNodeWithTag("lobby-ticket-panel").assertDoesNotExist()
            val copy = compose.onNodeWithTag("waiting-connection-copy").assertIsDisplayed()
            val expected = when (connection) {
                Connection.CONNECTING -> R.string.lobby_table_connecting
                Connection.RECONNECTING -> R.string.lobby_table_reconnecting
                else -> R.string.lobby_table_disconnected
            }
            copy.assertTextEquals(words(expected)).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            val layouts = mutableListOf<TextLayoutResult>()
            copy.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertFalse("Connection message must fit without clipping", layouts.single().hasVisualOverflow)
            val button = compose.onNodeWithTag("waiting-reconnect").assertIsDisplayed().assertIsEnabled()
            val target = button.fetchSemanticsNode().boundsInRoot
            val requiredHeight = kotlin.math.round(48f * compose.activity.resources.displayMetrics.density)
            assertTrue("Reconnect target ${target.height}px must meet ${requiredHeight}px", target.height >= requiredHeight)
            captureTestScreen("friend-connection-${connection.name.lowercase()}-$label")
            button.performClick()
            compose.runOnIdle { assertEquals(index + 1, retries) }
        }
        // A pending command still exposes its exact-retry flow; connection retry cannot bypass it.
        compose.runOnIdle { state = state.copy(pending = true) }
        compose.onNodeWithTag("coin-retry").assertIsDisplayed()
        compose.onNodeWithTag("waiting-reconnect").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(pending = false, storageFailure = true) }
        compose.onNodeWithText(words(R.string.ui_reset_online_data)).assertIsDisplayed()
        compose.onNodeWithTag("waiting-reconnect").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(storageFailure = false, sessionExpired = true) }
        compose.onNodeWithText(words(R.string.ui_reset_online_data)).assertIsDisplayed()
        compose.onNodeWithTag("waiting-reconnect").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(connection = Connection.LIVE, sessionExpired = false) }
        compose.onNodeWithTag("friend-start").assertIsEnabled()
        compose.onNodeWithTag("waiting-connection").assertDoesNotExist()
        compose.onNodeWithTag("friend-code").assertTextEquals("ABCD 2345")
        assertEquals(1200L, state.wallet!!.balance)
        assertEquals(3, state.room!!.coins!!.ownTickets)
    }
}
