package io.github.sbshrey.tambola.game

import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.game.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private fun tap(text: String) = compose.tapText(text)
    private fun until(predicate: () -> Boolean) = compose.waitUntil(10_000, predicate)
    private fun assertPalette(dark: Boolean) {
        compose.waitForIdle()
        val pixel = compose.onNodeWithTag("app-background").captureToImage().toPixelMap()[0, 0]
        val expected = if (model.state.value.screen == Screen.GAME) {
            if (dark) Color(0xFF092F2B) else Color(0xFFECF1E7)
        } else if (dark) Color(0xFF121D2B) else Color(0xFFFFF9F0)
        assertEquals(expected.red, pixel.red, .01f); assertEquals(expected.green, pixel.green, .01f); assertEquals(expected.blue, pixel.blue, .01f)
        compose.runOnIdle { assertEquals(!dark, WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars) }
    }

    @Test fun savedAppearanceChangesEveryScreenAndPreservesActiveRound() = runBlocking<Unit> {
        compose.useEnglish()
        val store = PreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.update(Preferences(voice = false, reducedMotion = true, tutorialCompleted = true, appearance = Appearance.DARK))
        until { !model.state.value.loading && model.state.value.preferences.appearance == Appearance.DARK }
        tap("Custom game"); tap("Just me"); tap("Help with marking"); tap("Deal the tickets")
        if (compose.hasTextNow("Start new round")) tap("Start new round")
        until { compose.hasTextNow("Next") && !model.state.value.saving }
        val own = model.state.value.round!!.tickets.first()
        do {
            val count = model.state.value.round!!.called.size
            if (count > 0) android.os.SystemClock.sleep(550)
            tap("Next"); until { model.state.value.round!!.called.size == count + 1 && !model.state.value.saving }
        } while (model.state.value.round!!.called.none { it >= 10 && it in own.numbers })
        val markedNumber = model.state.value.round!!.called.first { it >= 10 && it in own.numbers }
        compose.tapTag("dab-called")
        until { markedNumber in model.state.value.round!!.marks[own.id].orEmpty() && !model.state.value.saving }
        val original = model.state.value.round!!
        compose.openArenaOption("Settings"); tap("Light"); until { model.state.value.preferences.appearance == Appearance.LIGHT }
        compose.onNodeWithText("Light").assertIsSelected(); assertPalette(false)
        captureTestScreen("appearance-light-settings")
        compose.goHome(); assertPalette(false); captureTestScreen("appearance-light-home")
        tap("Resume round"); assertPalette(false); captureTestScreen("appearance-light-table")
        compose.onNodeWithTag("owned-hand").assertIsDisplayed()
        captureTestScreen("appearance-light-ticket")
        compose.openArenaOption("Number board"); captureTestScreen("appearance-light-board")
        compose.onNode(hasText("90") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed(); tap("Back to game")
        compose.activityRule.scenario.recreate()
        until { !model.state.value.loading && model.state.value.round != null }
        assertEquals(Appearance.LIGHT, model.state.value.preferences.appearance); assertPalette(false)
        assertEquals(original.id, model.state.value.round!!.id)
        assertEquals(original.tickets, model.state.value.round!!.tickets); assertEquals(original.called, model.state.value.round!!.called)
        assertEquals(original.marks, model.state.value.round!!.marks)
        compose.openArenaOption("Settings"); tap("Dark"); until { model.state.value.preferences.appearance == Appearance.DARK }
        assertPalette(true); captureTestScreen("appearance-dark-settings")
        compose.goHome(); assertPalette(true); captureTestScreen("appearance-dark-home")
        tap("Resume round"); assertPalette(true); captureTestScreen("appearance-dark-table")
        compose.onNodeWithTag("owned-hand").assertIsDisplayed(); captureTestScreen("appearance-dark-ticket")
        compose.openArenaOption("Number board"); captureTestScreen("appearance-dark-board")
        compose.onNode(hasText("90") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed(); tap("Back to game")
        compose.openArenaOption("Settings"); tap("System"); until { model.state.value.preferences.appearance == Appearance.SYSTEM }
        val systemDark = compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        assertPalette(systemDark)
        assertEquals(original.id, model.state.value.round!!.id); assertEquals(original.called, model.state.value.round!!.called)
    }
}
