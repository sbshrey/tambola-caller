package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AvatarWinTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val model get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private fun tap(text: String) = compose.tapText(text)
    private fun until(condition: () -> Boolean) = compose.waitUntil(12_000, condition)
    private fun pick(label: String) = compose.onNode(hasContentDescription("$label avatar") and hasAnyAncestor(isDialog())).performScrollTo().performClick()

    @Test fun avatarsAndVerifiedWinsSurviveRoundSavesWithoutReplayingOnRestore() = runBlocking<Unit> {
        PreferenceStore(context).update(Preferences(voice = false, voiceVolume = 0, effects = false, reducedMotion = true,
            tutorialCompleted = true, appearance = Appearance.DARK))
        until { !model.state.value.loading && model.state.value.preferences.appearance == Appearance.DARK }
        tap("Play on one device")
        tap("Asha · Sun\nChoose avatar")
        compose.onNodeWithText("Avatar for Asha").assertIsDisplayed()
        compose.onNode(hasContentDescription("Sun avatar") and hasAnyAncestor(isDialog())).assertIsSelected()
        captureTestScreen("avatars-dark-picker")
        PreferenceStore(context).update(model.state.value.preferences.copy(appearance = Appearance.LIGHT))
        until { model.state.value.preferences.appearance == Appearance.LIGHT }
        compose.waitForIdle()
        captureTestScreen("avatars-light-picker")
        pick("Moon"); until { model.state.value.setupDraft.avatar(0) == 7 }
        tap("Bina · Sun\nChoose avatar"); pick("Peacock")
        until { model.state.value.setupDraft.avatar(1) == 3 }
        compose.activityRule.scenario.recreate()
        assertEquals(listOf(7, 3), model.state.value.setupDraft.avatars)
        val firstNumber = CustomPrize("custom_first", "First called number", 10,
            TicketPattern(listOf(listOf(RuleCondition(NumberSelection.All, 1)))))
        compose.runOnIdle { model.updateSetup(model.state.value.setupDraft.copy(
            playAllNumbers = true, customPrizes = listOf(firstNumber))) }
        tap("Deal the tickets"); if (compose.hasTextNow("Start new round")) tap("Start new round")
        until { !model.state.value.saving && model.state.value.screen == Screen.GAME }
        assertEquals(listOf(7, 3), model.state.value.round!!.players.map { it.avatar })
        do {
            val count = model.state.value.round!!.called.size
            if (count > 0) android.os.SystemClock.sleep(550)
            tap("Call next number"); until { model.state.value.round!!.called.size == count + 1 }
        } while (model.state.value.winMoment == null)
        val moment = model.state.value.winMoment!!
        val round = model.state.value.round!!
        assertEquals(round.called.size, moment.drawIndex)
        assertEquals(round.customAwards.single().playerIds.toSet(), moment.players.map { it.id }.toSet())
        compose.onNodeWithTag("verified-win").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Call next number").assertIsDisplayed()
        captureTestScreen("win-family-table")
        tap("See winning tickets"); compose.onNodeWithText("Back to game").assertIsDisplayed(); tap("Back to game")
        tap("Dismiss celebration"); until { model.state.value.winMoment == null }
        tap("Hear again"); assertNull(model.state.value.winMoment)
        val ticket = round.tickets.first { it.id in round.customAwards.single().ticketIds }
        val owner = round.players.first { it.id == ticket.playerId }
        tap("${owner.name} · ticket 1")
        val number = round.called.first { it in ticket.numbers }
        tap("Mark ticket"); compose.onNodeWithContentDescription("Number $number").performScrollTo().performClick()
        until { number in model.state.value.round!!.marks[ticket.id].orEmpty() }; tap("Done")
        val before = model.state.value.round!!
        compose.activityRule.scenario.recreate()
        until { model.state.value.round?.status == RoundStatus.PAUSED }
        val restored = model.state.value.round!!
        assertEquals(before.id, restored.id); assertEquals(before.tickets, restored.tickets)
        assertEquals(before.called, restored.called); assertEquals(before.marks, restored.marks)
        assertEquals(before.players, restored.players); assertNull(model.state.value.winMoment)
        tap("End round"); compose.onNode(hasText("End round") and hasAnyAncestor(isDialog())).performClick()
        until { model.state.value.round!!.finished }
        tap("See round results")
        compose.onNodeWithContentDescription("Moon avatar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Peacock avatar").assertExists()
        captureTestScreen("avatars-family-results")
        tap("Play another round")
        assertEquals(listOf(7, 3), model.state.value.setupDraft.avatars)
        compose.onNodeWithText("Asha · Moon\nChoose avatar").performScrollTo().assertIsDisplayed()
        PreferenceStore(context).update(model.state.value.preferences.copy(appearance = Appearance.SYSTEM))
    }
}
