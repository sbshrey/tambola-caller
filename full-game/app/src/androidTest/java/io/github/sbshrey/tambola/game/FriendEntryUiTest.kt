package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import io.github.sbshrey.tambola.game.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class FriendEntryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun englishPortrait() = entry("en", false)
    @Test fun englishLandscape() = entry("en", true)
    @Test fun hindiPortrait() = entry("hi", false)
    @Test fun hindiLandscape() = entry("hi", true)

    private fun entry(language: String, landscape: Boolean) {
        compose.runOnUiThread { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = 2f }
        val context = compose.activity.createConfigurationContext(config)
        val requests = mutableListOf<String?>()
        var closed = false
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, 2f)) {
                TambolaTheme { FriendEntryDialog(6, 600, true, { requests.add(it) }, { closed = true }) }
            }
        }
        fun visible() {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("friend-enter").fetchSemanticsNodes().isNotEmpty() }
            val window = compose.onNodeWithTag("friend-entry-window").getUnclippedBoundsInRoot()
            compose.onNodeWithTag("friend-entry").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.VerticalScrollAxisRange))
            listOf("friend-create-mode", "friend-join-mode", "friend-enter", "friend-entry-cost", "friend-entry-close").forEach {
                val bounds = compose.onNodeWithTag(it).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue("$it fits in the visible dialog", bounds.top >= window.top && bounds.bottom <= window.bottom && bounds.left >= window.left && bounds.right <= window.right)
            }
        }
        visible()
        compose.onNodeWithTag("friend-enter").performClick()
        compose.runOnIdle { assertEquals(listOf<String?>(null), requests) }
        compose.onNodeWithTag("friend-join-mode").performClick()
        visible()
        compose.onNodeWithTag("friend-enter").assertIsNotEnabled()
        compose.onNodeWithTag("friend-code-input").performClick().performTextInput("ab cd2345")
        try {
            compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("friend-code-done").assertIsDisplayed() }.isSuccess }
        } finally {
            captureTestScreen("friend-entry-keyboard-$language-${if (landscape) "landscape" else "portrait"}")
        }
        SystemClock.sleep(600)
        compose.waitForIdle()
        captureTestScreen("friend-entry-keyboard-$language-${if (landscape) "landscape" else "portrait"}")
        compose.onNodeWithTag("friend-code-input").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("friend-code-done").assertIsDisplayed()
        compose.onNodeWithTag("friend-enter").assertDoesNotExist()
        assertEquals(1, requests.size)
        compose.onNodeWithTag("friend-code-input").performImeAction()
        compose.onNodeWithTag("friend-code-input").assertIsNotFocused()
        // System keyboard inset animations run outside Compose's test clock.
        SystemClock.sleep(600)
        compose.waitForIdle()
        captureTestScreen("friend-entry-done-$language-${if (landscape) "landscape" else "portrait"}")
        visible()
        compose.onNodeWithTag("friend-code-input").assertTextContains("ABCD2345")
        captureTestScreen("friend-entry-$language-${if (landscape) "landscape" else "portrait"}")
        compose.onNodeWithTag("friend-enter").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { requests.size == 2 }
        compose.runOnIdle { assertEquals(listOf(null, "ABCD2345"), requests) }
        compose.onNodeWithTag("friend-entry-close").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
