package io.github.sbshrey.tambola.game

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Random

class ExpandedArenaUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun landscapeKeepsTwoTicketsAndOpenClaimAcrossCalls() = play(true)
    @Test fun portraitKeepsTwoTicketsAndOpenClaimAcrossCalls() = play(false)

    private fun play(landscape: Boolean) {
        check(isAndroidEmulator())
        compose.runOnUiThread { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        var round by mutableStateOf(Round.create(listOf(Player("me", "Me"), Player("friend", "Friend")),
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true, winnersPerPrize = 2,
                prizes = CoinPool(12, 2).prizes.map { it.prize }), Random(17)).start().draw())
        var claimEnabled by mutableStateOf(true)
        var submitted: ClaimSelection? = null
        var submittedAt = 0
        var repeated = 0
        compose.setContent { TambolaTheme {
            ClaimArena(round.toTable(), "me", Preferences(reducedMotion = true), "Playing",
                markNumber = { ticket, number -> round = round.toggleMark(ticket, number) },
                claim = { submitted = it; submittedAt = round.called.size }, claimMessage = null,
                repeatCall = { repeated++ }, back = {}, win = null, dismissWin = {}, markEnabled = true,
                claimEnabled = claimEnabled, expandedFooter = false, extraMenu = {}, footer = {})
        } }
        compose.onNodeWithTag("hand-ticket-1").assertIsDisplayed()
        compose.onNodeWithTag("hand-ticket-2").assertIsDisplayed()
        compose.onNodeWithTag("hand-ticket-3").assertDoesNotExist()
        val ticket = round.tickets.first { it.playerId == "me" }
        val number = ticket.numbers.first { it !in round.called }
        compose.onNodeWithTag("dab-$number").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(number in round.marks.getValue(ticket.id)) }
        compose.onNodeWithTag("current-call").performClick()
        compose.runOnIdle { assertEquals(1, repeated); assertEquals(setOf(number), round.marks.getValue(ticket.id)) }
        compose.onNodeWithTag("claim-ticket-1").performClick()
        compose.onNodeWithTag("ticket-prize-picker").assertIsDisplayed()
        compose.runOnIdle { round = round.draw() }
        compose.onNodeWithTag("ticket-prize-picker").assertIsDisplayed()
        compose.onNodeWithTag("claim-prize-${Prize.TOP_LINE.name}").performClick()
        compose.runOnIdle {
            assertEquals(ClaimSelection(ticket.id, Prize.TOP_LINE.name), submitted)
            assertEquals(2, submittedAt)
        }
        compose.onNodeWithTag("ticket-prize-picker").assertDoesNotExist()
        compose.onNodeWithTag("claim-ticket-1").performClick()
        compose.runOnIdle { claimEnabled = false }
        compose.onNodeWithTag("ticket-prize-picker").assertDoesNotExist()
        captureTestScreen("expanded-${if (landscape) "landscape" else "portrait"}")
    }
}
