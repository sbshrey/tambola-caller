package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import io.github.sbshrey.tambola.client.ServerTime
import io.github.sbshrey.tambola.domain.RoundStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class FriendReactionsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun englishLandscapeReactions() = choices("en", true)
    @Test fun hindiLandscapeReactionsAtLargeText() = choices("hi", true)
    @Test fun hindiPortraitReactionsAtLargeText() = choices("hi", false)

    private fun choices(language: String, landscape: Boolean) {
        compose.runOnUiThread { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        val scale = if (language == "hi") 2f else 1f
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        val context = compose.activity.createConfigurationContext(config)
        var snapshot by mutableStateOf(ReactionSnapshot("room", "round", 1, 100_000, 101_000, emptyList()))
        var sent: FriendReaction? = null
        var closed = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                val clock = remember(snapshot.serverTime) { ServerTime(snapshot.serverTime, System.nanoTime()) }
                TambolaTheme { FriendReactionMenu(snapshot, true, false, clock, { closed++ }) { sent = it } }
            }
        }
        compose.onNodeWithTag("friend-react").performClick()
        FriendReaction.entries.forEach { compose.onNodeWithTag("send-reaction-${it.name}").assertIsDisplayed().assertIsNotEnabled() }
        compose.runOnIdle { snapshot = snapshot.copy(serverTime = 102_000) }
        FriendReaction.entries.forEach { compose.onNodeWithTag("send-reaction-${it.name}").assertIsDisplayed().assertIsEnabled() }
        captureTestScreen("friend-reactions-$language")
        compose.onNodeWithTag("send-reaction-NICE_WIN").performClick()
        assertEquals(FriendReaction.NICE_WIN, sent)
        assertEquals(1, closed)
    }

    @Test fun expiredReactionDisappearsWithoutNewRoomTraffic() {
        val room = RoomView(code = "ABCD2345", roomId = "room", revision = 1, phase = RoomPhase.ACTIVE, hostId = "friend",
            locked = true, options = RoomOptions(), members = listOf(MemberView("friend", "Mira", 0, true, true)),
            round = PublicRound("round", RoundStatus.PLAYING, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), "fixture"),
            nextDrawAt = null, expiresAt = 200_000, serverTime = 100_000)
        val snapshot = ReactionSnapshot("room", "round", 1, 100_000, 100_000, listOf(RoomReaction("friend", "round", FriendReaction.THANKS, 95_000)))
        val clock = ServerTime(snapshot.serverTime, System.nanoTime())
        compose.setContent { TambolaTheme { Text(friendReactionCaption(snapshot, room, clock) ?: "Quiet") } }
        compose.onNodeWithText("Mira: Thanks!").assertIsDisplayed()
        SystemClock.sleep(1300)
        compose.mainClock.advanceTimeBy(1300)
        compose.onNodeWithText("Quiet").assertIsDisplayed()
    }

    @Test fun expiredCooldownDoesNotRestartWhenMenuOpens() {
        val snapshot = ReactionSnapshot("room", "round", 1, 100_000, 101_000, emptyList())
        val received = ServerTime(100_000, System.nanoTime() - 2_000_000_000)
        compose.setContent { TambolaTheme { FriendReactionMenu(snapshot, true, false, received, {}) {} } }
        compose.onNodeWithTag("friend-react").performClick()
        FriendReaction.entries.forEach { compose.onNodeWithTag("send-reaction-${it.name}").assertIsEnabled() }
        compose.onNodeWithTag("reaction-cooldown").assertDoesNotExist()
    }
}
