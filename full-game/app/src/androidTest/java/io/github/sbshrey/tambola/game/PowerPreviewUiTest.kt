package io.github.sbshrey.tambola.game

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Random

class PowerPreviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun previewChargesIntoOneTapShieldAndThenShowsTheNextPower() {
        check(isAndroidEmulator())
        val round = Round.create(listOf(Player("me", "Mira")), RoundSettings(), Random(13)).start()
        val ticket = round.tickets.single()
        var powers by mutableStateOf(MatchPowers(nextPower = MatchPower.SHIELD))
        var activations = 0
        val words = GameText(compose.activity.resources)
        compose.setContent { TambolaTheme { MaterialTheme(colorScheme = GameNightPalette.colors) {
            Box(Modifier.width(200.dp).height(90.dp)) {
                TopPowerControl(round.toTable().copy(powers = powers), listOf(ticket.id), ticket.id, true, true) { id, power ->
                    assertEquals(ticket.id, id)
                    activations++
                    powers = powers.activate(ticket, power, ticket.numbers, 1)
                }
            }
        } } }
        val button = compose.onNodeWithTag("top-power-control")
        button.assertIsNotEnabled().assertContentDescriptionEquals(words.matchPowerName(MatchPower.SHIELD) + ". " + words(R.string.power_progress, 0))
        compose.runOnIdle {
            ticket.numbers.take(4).forEach { powers = powers.mark(ticket, it, ticket.numbers) { error("Too soon") } }
        }
        button.assertIsNotEnabled().assertContentDescriptionEquals(words.matchPowerName(MatchPower.SHIELD) + ". " + words(R.string.power_progress, 4))
        compose.runOnIdle { powers = powers.mark(ticket, ticket.numbers[4], ticket.numbers) { MatchPower.AUTO_DAB } }
        button.assertIsEnabled().assertContentDescriptionEquals(words.matchPowerName(MatchPower.SHIELD) + ". " + words(R.string.power_use_ticket, 1)).performClick()
        compose.runOnIdle { assertEquals(1, activations); assertEquals(setOf(ticket.id), powers.armedShield) }
        button.assertIsNotEnabled().assertContentDescriptionEquals(words.matchPowerName(MatchPower.AUTO_DAB) + ". " + words(R.string.power_progress, 0))
    }
}
