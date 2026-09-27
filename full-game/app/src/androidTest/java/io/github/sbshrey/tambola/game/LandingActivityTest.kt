package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit isolated-review identity: never replace the installed Wi-Fi app for a design check. */
class LandingActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchSettingsAndReturnStayLandscape() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaUiReview") == "true")
        assertEquals("io.github.sbshrey.tambola.game.uireview", BuildConfig.APPLICATION_ID)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lobby-welcome-heading").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, compose.activity.resources.configuration.orientation)
        compose.onNodeWithTag("coin-play").assertIsDisplayed()
        captureTestScreen("game-night-activity-welcome")
        compose.onNodeWithTag("lobby-settings").performClick()
        compose.onNodeWithTag("home").assertIsDisplayed()
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, compose.activity.resources.configuration.orientation)
        captureTestScreen("game-night-activity-settings")
        compose.onNodeWithTag("home").performClick()
        compose.onNodeWithTag("lobby-welcome-heading").assertIsDisplayed()
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, compose.activity.resources.configuration.orientation)
    }
}
