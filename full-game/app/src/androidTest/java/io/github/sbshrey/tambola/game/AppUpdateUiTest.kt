package io.github.sbshrey.tambola.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.sbshrey.tambola.game.ui.TambolaTheme
import io.github.sbshrey.tambola.game.updates.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppUpdateUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun downloadAndInstallRequireSeparateExplicitActions() {
        var state by mutableStateOf(UpdateState(UpdateStage.AVAILABLE, visible = true))
        var downloads = 0; var installs = 0
        compose.setContent { TambolaTheme { UpdateDialog(state, {}, {}, { downloads++ }, { installs++ }, {}) } }
        assertEquals(0, downloads); assertEquals(0, installs)
        compose.onNodeWithTag("download-update").performClick()
        assertEquals(1, downloads); assertEquals(0, installs)
        compose.runOnIdle { state = state.copy(stage = UpdateStage.DOWNLOADING, percent = 42) }
        compose.onNodeWithTag("install-update").assertDoesNotExist()
        compose.onNodeWithText("42%").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(stage = UpdateStage.READY) }
        compose.onNodeWithTag("install-update").performClick()
        assertEquals(1, installs)
    }
    @Test fun installedBetaChecksLiveReleaseFeedFromSettings() {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("tambolaUpdateSmoke") == "true")
        val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am start -n io.github.sbshrey.tambola.game.beta/io.github.sbshrey.tambola.game.MainActivity")
        val settings = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.desc("Settings")), 15000)
        assertNotNull("Beta lobby settings", settings); settings.click()
        val check = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Check for updates")), 10000)
        assertNotNull("Manual update check", check); check.click()
        assertNotNull("Live release check succeeds", device.wait(androidx.test.uiautomator.Until.findObject(
            androidx.test.uiautomator.By.text("You have the latest available update.")), 30000))
        device.pressBack()
    }
    @Test fun laterDismissesWithoutDownloading() {
        var dismissed = 0
        compose.setContent { TambolaTheme { UpdateDialog(UpdateState(UpdateStage.AVAILABLE, visible = true),
            { dismissed++ }, {}, { error("Unexpected download") }, { error("Unexpected install") }, {}) } }
        compose.onNodeWithText("Later").performClick(); assertEquals(1, dismissed)
    }
}
