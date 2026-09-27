package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.game.data.PreferenceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit isolated-review identity: never replace the installed Wi-Fi app for a design check. */
class LandingActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun personalSettingPersistsAcrossActivityRecreation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaUiReview") == "true")
        assertEquals("io.github.sbshrey.tambola.game.uireview", BuildConfig.APPLICATION_ID)
        val store = PreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val before = runBlocking { store.values.first() }
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("lobby-settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("lobby-settings").performClick()
            compose.onNodeWithTag("setting-motion").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { ViewModelProvider(compose.activity)[GameViewModel::class.java].state.value.preferences.reducedMotion != before.reducedMotion }
            assertEquals(!before.reducedMotion, runBlocking { store.values.first() }.reducedMotion)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("setting-motion").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("lobby-settings").fetchSemanticsNodes().isNotEmpty() }
            if (compose.onAllNodesWithTag("lobby-settings").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("lobby-settings").performClick()
            if (before.reducedMotion) compose.onNodeWithTag("setting-motion").assertIsOff() else compose.onNodeWithTag("setting-motion").assertIsOn()
        } finally { runBlocking { store.update(before) } }
    }

    @Test fun launchSettingsAndReturnStayLandscape() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaUiReview") == "true")
        assertEquals("io.github.sbshrey.tambola.game.uireview", BuildConfig.APPLICATION_ID)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lobby-welcome-heading").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)
        // Font-scale changes can launch against the previous window configuration for a frame.
        compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
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
