package io.github.sbshrey.tambola.game

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.GameMode
import io.github.sbshrey.tambola.game.data.PreferenceStore
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.online.OnlineStore
import io.github.sbshrey.tambola.game.online.OnlineViewModel
import io.github.sbshrey.tambola.game.ui.GameText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GameDataTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private val online get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private fun until(predicate: () -> Boolean) = compose.waitUntil(15_000, predicate)
    private fun words() = GameText(compose.activity.resources)

    @Before fun prepare() = runBlocking<Unit> {
        check(isAndroidEmulator())
        PreferenceStore(context).update(Preferences(voice = false, effects = false,
            reducedMotion = true, tutorialCompleted = true))
        until { !model.state.value.loading && !online.state.value.loading }
        language("en")
    }

    @After fun restoreLanguage() {
        compose.activityRule.scenario.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
    }

    private fun language(tag: String) {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
        until { compose.activity.resources.configuration.locales[0].language == tag }
        compose.waitForIdle()
    }

    @Test fun settingsDisclosureRemainsReadableAcrossRecreationWithoutChangingRound() {
        compose.runOnIdle {
            model.setup(GameMode.FAMILY)
            model.updateSetup(model.state.value.setupDraft.copy(names = "Data Asha\nData Bina", playAllNumbers = true))
            model.create()
        }
        until { !model.state.value.saving && model.state.value.screen == Screen.GAME }
        val original = model.state.value.round!!
        compose.runOnIdle { model.navigate(Screen.SETTINGS) }
        for (tag in listOf("en", "hi")) {
            language(tag)
            compose.tapTag("open-game-data")
            compose.onNodeWithText(words()(R.string.privacy_title)).assertIsDisplayed()
            compose.onNodeWithTag("close-game-data").assertIsDisplayed()
            captureTestScreen("game-data-$tag-top")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(words()(R.string.privacy_title)).assertIsDisplayed()
            for (title in listOf(R.string.privacy_device_title, R.string.privacy_online_title,
                R.string.privacy_retention_title, R.string.privacy_deletion_title, R.string.privacy_recovery_title,
                R.string.privacy_controls_title, R.string.privacy_monitoring_title)) {
                compose.onNodeWithText(words()(title)).performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("close-game-data").assertIsDisplayed()
                if (title == R.string.privacy_recovery_title) {
                    compose.onNodeWithText(words()(R.string.privacy_recovery_body)).performScrollTo().assertIsDisplayed()
                    captureTestScreen("game-data-$tag-recovery")
                }
            }
            compose.onNode(hasText(words()(R.string.ui_build_status, BuildConfig.VERSION_NAME)) and hasAnyAncestor(isDialog()))
                .performScrollTo().assertIsDisplayed()
            captureTestScreen("game-data-$tag-end")
            compose.tapTag("close-game-data")
            compose.onNodeWithTag("game-data-content").assertDoesNotExist()
            assertEquals(original.id, model.state.value.round!!.id)
            assertEquals(original.tickets, model.state.value.round!!.tickets)
            assertEquals(original.called, model.state.value.round!!.called)
            assertEquals(original.marks, model.state.value.round!!.marks)
        }
    }

    @Test fun disclosureBeforeRegistrationPreservesNicknameWithoutCreatingProfile() = runBlocking<Unit> {
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080")
        compose.runOnIdle { online.resetLocalData(); model.navigate(Screen.ONLINE) }
        until { !online.state.value.busy && online.state.value.name == null }
        compose.onNodeWithTag("coin-wallet").performClick()
        for (tag in listOf("en", "hi")) {
            language(tag)
            val nickname = if (tag == "hi") "आशा" else "Asha"
            compose.onNodeWithTag("lobby-player-name").performScrollTo().performTextReplacement(nickname)
            compose.onNodeWithTag("lobby-player-name").performImeAction()
            compose.tapTag("open-game-data")
            compose.onNodeWithText(words()(R.string.privacy_title)).assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(words()(R.string.privacy_title)).assertIsDisplayed()
            compose.tapTag("close-game-data")
            compose.onNodeWithTag("lobby-player-name").performScrollTo().assertTextContains(nickname)
            assertNull(online.state.value.name)
            assertFalse(online.state.value.busy)
            assertNull(OnlineStore(context).read())
        }
    }
}
