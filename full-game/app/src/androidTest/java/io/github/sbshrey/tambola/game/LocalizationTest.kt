package io.github.sbshrey.tambola.game

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.client.PendingOperation
import io.github.sbshrey.tambola.game.presentation.UiMessage
import io.github.sbshrey.tambola.game.setup.*
import io.github.sbshrey.tambola.game.ui.GameText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class LocalizationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private val online get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private fun until(predicate: () -> Boolean) = compose.waitUntil(15_000, predicate)
    private fun words() = GameText(compose.activity.resources)
    private fun tap(id: Int) = compose.tapText(words()(id))
    private fun selectLanguage(tag: String) {
        compose.tapTag("interface-language-$tag")
        until { compose.activity.resources.configuration.locales[0].language == tag }
        compose.waitForIdle()
    }
    @After fun resetLanguage() {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        compose.waitForIdle()
    }
    private suspend fun prepare() {
        PreferenceStore(context).update(Preferences(language = "hinglish", voice = false, effects = false,
            reducedMotion = true, tutorialCompleted = true))
        until { !model.state.value.loading && model.state.value.preferences.language == "hinglish" }
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        until { compose.activity.resources.configuration.locales[0].language == "en" }
    }

    @Test fun languageSwitchPreservesUnsavedSetupAndRuleEditor() = runBlocking<Unit> {
        prepare()
        compose.runOnIdle {
            model.setup(GameMode.FAMILY)
            model.updateSetup(model.state.value.setupDraft.copy(names = "आशा\nBina", tickets = 3, avatars = listOf(7, 3)))
            model.editRule()
            model.updateRule(model.state.value.ruleDraft!!.copy(title = "हमारा इनाम", points = "31", minimumTickets = 2))
            model.navigate(Screen.SETTINGS)
        }
        val draft = model.state.value.setupDraft
        val editor = model.state.value.ruleDraft
        selectLanguage("hi")
        assertEquals(draft, model.state.value.setupDraft)
        assertEquals(editor, model.state.value.ruleDraft)
        assertEquals("hinglish", model.state.value.preferences.language)
        compose.runOnIdle { model.navigate(Screen.SETUP) }
        compose.onNodeWithText("हमारा इनाम").assertExists()
        compose.onNodeWithText(words()(R.string.ui_save_prize)).assertIsDisplayed()
        captureTestScreen("hindi-rule-editor")
        compose.activityRule.scenario.recreate()
        assertEquals("hi", compose.activity.resources.configuration.locales[0].language)
        assertEquals(editor, model.state.value.ruleDraft)
        compose.runOnIdle { model.cancelRule(); model.updateSetup(draft.copy(names = "आशा\nआशा")) }
        compose.onNodeWithText(words()(R.string.error_unique_names)).performScrollTo().assertIsDisplayed()
        captureTestScreen("hindi-setup-validation")
        compose.runOnIdle { model.navigate(Screen.SETTINGS) }
        selectLanguage("en")
        assertEquals("हमारा इनाम", editor!!.title)
        assertEquals("hinglish", model.state.value.preferences.language)
    }

    @Test fun hindiRoundKeepsNamesTicketsMarksClaimsAndVoiceAcrossRecreation() = runBlocking<Unit> {
        prepare()
        val custom = CustomRuleDraft(title = "पहला नंबर", groups = listOf(listOf(ConditionDraft(minimum = "1")))).prize(1)
        compose.runOnIdle {
            model.setup(GameMode.FAMILY)
            model.updateSetup(model.state.value.setupDraft.copy(names = "आशा\nBina", avatars = listOf(7, 3),
                playAllNumbers = true, customPrizes = listOf(custom)))
            model.create()
        }
        until { !model.state.value.saving && model.state.value.screen == Screen.GAME }
        do {
            val count = model.state.value.round!!.called.size
            if (count > 0) android.os.SystemClock.sleep(550)
            tap(R.string.ui_call_next_number)
            until { model.state.value.round!!.called.size == count + 1 }
        } while (model.state.value.round!!.customAwards.isEmpty())
        val round = model.state.value.round!!
        val winner = round.tickets.first { it.id in round.customAwards.single().ticketIds }
        val number = round.called.first { it in winner.numbers }
        compose.runOnIdle { model.toggleMark(winner.id, number) }
        until { number in model.state.value.round!!.marks[winner.id].orEmpty() }
        val original = model.state.value.round!!
        tap(R.string.ui_settings)
        selectLanguage("hi")
        captureTestScreen("hindi-settings")
        assertEquals("hinglish", model.state.value.preferences.language)
        val retained = model.state.value.round!!
        assertEquals(original.id, retained.id); assertEquals(original.tickets, retained.tickets)
        assertEquals(original.called, retained.called); assertEquals(original.marks, retained.marks)
        assertEquals(original.players, retained.players); assertEquals(original.customAwards, retained.customAwards)
        assertNull(model.state.value.winMoment)
        tap(R.string.ui_home)
        captureTestScreen("hindi-home")
        tap(R.string.ui_resume_round)
        captureTestScreen("hindi-table")
        val claimText = words()(R.string.ui_check_claims_verified, retained.awards.size + retained.customAwards.size)
        compose.tapText(claimText)
        compose.tapText(words()(R.string.ui_inspect, "पहला नंबर"))
        compose.onNodeWithText(words()(R.string.ui_back_to_prizes)).assertIsDisplayed()
        captureTestScreen("hindi-claim-details")
        tap(R.string.ui_back_to_prizes); tap(R.string.ui_back_to_game)
        tap(R.string.ui_end_round)
        compose.onNode(hasText(words()(R.string.ui_end_round)) and hasAnyAncestor(isDialog())).performClick()
        until { model.state.value.round!!.finished }
        tap(R.string.ui_see_round_results)
        captureTestScreen("hindi-results")
        tap(R.string.ui_share_these_results)
        val preview = compose.onNodeWithTag("share-preview").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assertTrue(preview.contains("खिलाड़ी 1")); assertFalse(preview.contains("आशा")); assertFalse(preview.contains("Bina"))
        captureTestScreen("hindi-share")
        tap(R.string.ui_keep_private)
        tap(R.string.ui_settings); selectLanguage("en")
        assertEquals(original.tickets, model.state.value.round!!.tickets)
        assertEquals(original.marks, model.state.value.round!!.marks)
    }

    @Test fun ruleAndRetainedMessageFormattingUsesRequestedLocaleWithoutChangingRules() {
        fun text(tag: String): GameText {
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            return GameText(context.createConfigurationContext(config).resources)
        }
        val en = text("en"); val hi = text("hi")
        Prize.entries.forEach {
            assertEquals(it.title, en.prizeTitle(it)); assertEquals(it.explanation, en.prizeExplanation(it))
            assertNotEquals(en.prizeTitle(it), hi.prizeTitle(it))
        }
        val selectors = listOf(NumberSelection.All, NumberSelection.Row(1), NumberSelection.Column(8),
            NumberSelection.Range(20, 45), NumberSelection.Positions(listOf(0, 4, 10, 14)), NumberSelection.Positions(listOf(1, 8, 14)))
        selectors.forEach { assertEquals(it.describe(), en.selection(it)); assertNotEquals(en.selection(it), hi.selection(it)) }
        val prize = CustomPrize("custom_sample", "User title stays", 25, TicketPattern(listOf(
            listOf(RuleCondition(NumberSelection.Row(0)), RuleCondition(NumberSelection.All, 8)),
            listOf(RuleCondition(NumberSelection.All, 10)))), minimumTickets = 2, ticketOrdinals = listOf(2, 3))
        assertEquals(prize.describe(), en.customPrize(prize))
        assertTrue(hi.customPrize(prize).contains(" या ")); assertTrue(hi.customPrize(prize).contains(" और "))
        val issue = UiMessage(R.string.error_prize_tickets, listOf("हमारा Prize"))
        assertTrue(hi.message(issue).contains("हमारा Prize")); assertNotEquals(en.message(issue), hi.message(issue))
        assertEquals("User title stays", prize.title)
    }

    @Test fun pendingOnlineDeletionSurvivesLanguageChangeAndConfirmsSameRequest() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaFaultProxy") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080")
        prepare()
        suspend fun control(path: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val connection = java.net.URL("http://127.0.0.1:8082/$path").openConnection() as java.net.HttpURLConnection
            try { connection.requestMethod = "POST"; connection.connectTimeout = 3_000; connection.readTimeout = 3_000; check(connection.responseCode == 200) }
            finally { connection.disconnect() }
        }
        until { !online.state.value.loading }
        compose.runOnIdle { online.resetLocalData(); model.navigate(Screen.ONLINE) }
        until { online.state.value.name == null }
        compose.onNodeWithTag("online-name").performScrollTo().performTextReplacement("सीमा")
        compose.onNodeWithTag("online-name").performImeAction()
        tap(R.string.ui_continue_online)
        until { online.state.value.name != null && !online.state.value.busy }
        tap(R.string.ui_create_private_room)
        until { online.state.value.room != null && !online.state.value.busy && online.state.value.connection == Connection.LIVE }
        val original = checkNotNull(OnlineStore(context).read())
        tap(R.string.ui_settings); selectLanguage("hi"); tap(R.string.ui_home); tap(R.string.ui_play_online)
        until { online.state.value.connection == Connection.LIVE }
        assertEquals(original.room!!.code, online.state.value.room!!.code)
        assertEquals("सीमा", online.state.value.name)
        captureTestScreen("hindi-online-lobby")
        try {
            control("arm-delete-drop")
            tap(R.string.ui_delete_online_profile); tap(R.string.ui_delete_profile_permanently)
            compose.waitUntil(35_000) { online.state.value.deletingProfile && !online.state.value.busy && online.state.value.error != null }
            val pending = checkNotNull(OnlineStore(context).read()).pending as PendingOperation.DeleteProfile
            compose.onNodeWithText(words()(R.string.error_action_unconfirmed)).assertIsDisplayed()
            captureTestScreen("hindi-online-pending-error")
            tap(R.string.ui_got_it)
            tap(R.string.ui_settings); selectLanguage("en"); tap(R.string.ui_home); tap(R.string.ui_play_online)
            assertEquals(pending, checkNotNull(OnlineStore(context).read()).pending)
            assertEquals(original.credentials, checkNotNull(OnlineStore(context).read()).credentials)
            compose.onNodeWithText(words()(R.string.ui_profile_deletion_is_waiting_for_confirmation)).performScrollTo().assertIsDisplayed()
            assertEquals("hinglish", model.state.value.preferences.language)
            control("allow-deletes")
            tap(R.string.ui_retry_pending_action)
            until { online.state.value.name == null && !online.state.value.busy }
            assertEquals(R.string.notice_profile_deleted, online.state.value.notice!!.resource)
            assertNull(OnlineStore(context).read())
        } finally { control("allow-deletes") }
    }
}
