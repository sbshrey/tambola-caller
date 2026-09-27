package io.github.sbshrey.tambola.game

import android.content.res.Configuration
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import io.github.sbshrey.tambola.game.online.OnlineUiState
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.util.Locale

class FriendInviteDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun landscape() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }

    @Test fun openingCostsNothingAndOnlyExplicitConfirmationBuysSelectedTickets() = review("en", 1f)
    @Test fun hindiLargeTextRetainsTicketChoiceAndConfirmation() = review("hi", 1.5f)

    private fun review(language: String, scale: Float) {
        var purchases = 0; var quantity = 0
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        val context = compose.activity.createConfigurationContext(config)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { FriendInviteDialog("ABCDEFG2", OnlineUiState(loading = false, available = true),
                    { quantity = it; purchases++ }, {}, {}) }
            }
        }
        compose.waitForIdle(); assertEquals(0, purchases)
        if (scale == 1f) {
            (1..6).forEach { compose.onNodeWithTag("invite-tickets-$it").assertIsDisplayed() }
            compose.onNodeWithTag("friend-invitation-code").assertIsDisplayed()
            compose.onNodeWithTag("friend-invitation-join").assertIsDisplayed()
        }
        compose.onNodeWithTag("invite-tickets-6").performScrollTo().performClick()
        compose.onNodeWithTag("friend-invitation-cost").performScrollTo().assertTextContains("600", substring = true)
        compose.onNodeWithTag("friend-invitation-join").assertIsDisplayed().assertIsEnabled()
        captureTestScreen("friend-invitation-$language")
        compose.onNodeWithTag("friend-invitation-join").performClick()
        assertEquals(1, purchases); assertEquals(6, quantity)
    }

    @Test fun pendingRequestAndInvalidLinkCannotPurchase() {
        var state by mutableStateOf(OnlineUiState(loading = false, available = true, pending = true))
        var code by mutableStateOf("ABCDEFG2")
        compose.setContent { TambolaTheme { FriendInviteDialog(code, state, { error("Unexpected purchase") }, {}, {}) } }
        compose.onNodeWithTag("friend-invitation-join").assertDoesNotExist()
        compose.onNodeWithTag("friend-invitation-resume").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(pending = false); code = "" }
        compose.onNodeWithTag("friend-invitation-join").assertDoesNotExist()
        compose.onNodeWithTag("invite-tickets-1").assertDoesNotExist()
        compose.onNodeWithTag("friend-invitation-dismiss").assertIsDisplayed()
    }

    @Test fun walletLimitsChoicesAndBusyStatePreventsAnotherPurchase() {
        var state by mutableStateOf(OnlineUiState(loading = false, available = true, name = "Mira", wallet = WalletView(200, 1, 0)))
        compose.setContent { TambolaTheme { FriendInviteDialog("ABCDEFG2", state, { error("Unexpected purchase") }, {}, {}) } }
        compose.onNodeWithTag("invite-tickets-2").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("invite-tickets-3").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = true) }
        compose.onNodeWithTag("friend-invitation-join").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = false, wallet = WalletView(0, 2, 0)) }
        compose.onNodeWithTag("friend-invitation-join").assertIsNotEnabled()
    }
}
