package io.github.sbshrey.tambola.game

import android.content.res.Configuration
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsProperties
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
    @Test fun hindiLargeTextRetainsTicketChoiceAndConfirmation() = review("hi", 2f)
    @Test fun englishLargeTextRetainsTicketChoiceAndConfirmation() = review("en", 2f)
    @Test fun englishPortraitDoesNotScroll() = review("en", 2f, false)
    @Test fun hindiPortraitDoesNotScroll() = review("hi", 2f, false)

    private fun review(language: String, scale: Float, landscape: Boolean = true) {
        if (!landscape) {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        }
        var purchases = 0; var quantity = 0
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        val context = compose.activity.createConfigurationContext(config)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { FriendInviteDialog("ABCDEFG2", OnlineUiState(loading = false, available = true),
                    { quantity = it; purchases++ }, {}, {}) }
            }
        }
        compose.waitForIdle(); assertEquals(0, purchases)
        compose.onNodeWithTag("friend-invitation-content").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.VerticalScrollAxisRange))
        val window = compose.onNodeWithTag("friend-invitation-window").getUnclippedBoundsInRoot()
        ((1..6).map { "invite-tickets-$it" } + listOf("friend-invitation-code", "friend-invitation-cost", "friend-invitation-join", "friend-invitation-dismiss")).forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$tag fits the invitation", bounds.top >= window.top && bounds.bottom <= window.bottom && bounds.left >= window.left && bounds.right <= window.right)
        }
        compose.onNodeWithTag("invite-tickets-6").performClick()
        compose.onNodeWithTag("friend-invitation-cost").assertTextContains("600", substring = true)
        compose.onNodeWithTag("friend-invitation-join").assertIsDisplayed().assertIsEnabled()
        captureTestScreen("friend-invitation-$language-${(scale * 100).toInt()}-${if (landscape) "landscape" else "portrait"}")
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
