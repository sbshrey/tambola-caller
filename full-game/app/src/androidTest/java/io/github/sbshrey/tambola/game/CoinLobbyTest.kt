package io.github.sbshrey.tambola.game

import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.client.ServerTime
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.util.Locale
import java.util.Random

/** Render real lobby screens with local fixtures; no guest, purchase or persisted profile. */
class CoinLobbyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun landscape() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }

    @Test fun firstPlayAndPersonalizationKeepSixReadableChoices() = landing("en", 1f)
    @Test fun hindiLandingKeepsAllChoicesAndPlayVisibleAtLargeText() = landing("hi", 1.5f)

    private fun landing(language: String, scale: Float) {
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        var state by mutableStateOf(OnlineUiState(loading = false, available = true))
        var selected = 0; var purchases = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { CoinLobby(state, model, { selected = it; purchases++ }, {}, {}, reducedMotion = true) }
            }
        }
        compose.onNodeWithTag("lobby-welcome-heading").assertIsDisplayed()
        compose.onNodeWithTag("lobby-player-name").assertDoesNotExist()
        (1..6).forEach {
            val button = compose.onNodeWithTag("buy-tickets-$it").assertIsDisplayed().assertIsEnabled()
            val bounds = button.getUnclippedBoundsInRoot()
            assertTrue("Ticket choice $it must retain a 48dp target", (bounds.right - bounds.left).value >= 48f && (bounds.bottom - bounds.top).value >= 48f)
        }
        compose.onNodeWithTag("coin-play").assertIsDisplayed().assertIsEnabled()
        captureTestScreen("game-night-welcome-$language")
        compose.onNodeWithTag("buy-tickets-6").performClick().assertIsSelected()
        compose.onNodeWithTag("lobby-ticket-cost").assertTextEquals(words(R.string.coin_balance, 600L))
        compose.onNodeWithTag("coin-wallet").performClick()
        compose.onNodeWithTag("lobby-player-name").performTextInput("Mira")
        compose.onNodeWithText(words(R.string.lobby_save_player)).assertIsEnabled()
        captureTestScreen("game-night-player-$language")
        val input = compose.onNodeWithTag("lobby-player-name").fetchSemanticsNode().boundsInRoot
        assertTrue("The keyboard must leave the whole name field visible", input.height / compose.activity.resources.displayMetrics.density >= 50f)
        compose.onNodeWithText(words(R.string.lobby_save_player)).assertIsDisplayed()
        compose.onNodeWithText(words(R.string.ui_keep_playing)).assertIsDisplayed()
        compose.onNodeWithText(words(R.string.ui_keep_playing)).performClick()
        // A saved first profile must not reset the hand selected immediately before personalization.
        compose.runOnIdle { state = state.copy(name = "Mira", playerId = "local-review", wallet = WalletView(1500, 1, 0)) }
        compose.onNodeWithTag("lobby-welcome-heading").assertDoesNotExist()
        compose.onNodeWithTag("lobby-heading").assertIsDisplayed()
        compose.onNodeWithTag("buy-tickets-6").assertIsSelected()
        compose.onNodeWithTag("coin-play").assertIsDisplayed().performClick()
        assertEquals(6, selected); assertEquals(1, purchases)
        captureTestScreen("game-night-lobby-$language")
        // Unknown purchase acknowledgement gets one recovery action, never another purchase.
        compose.runOnIdle { state = state.copy(pending = true) }
        compose.onNodeWithTag("coin-play").assertDoesNotExist()
        compose.onNodeWithTag("buy-tickets-6").assertDoesNotExist()
        compose.onNodeWithTag("coin-retry").assertIsDisplayed().assertIsEnabled()
        captureTestScreen("game-night-pending-$language")
    }

    private fun finished(): RoomView {
        val plan = CoinPool(4)
        var game = Round.create(listOf(Player("a", "You"), Player("b", "Asha"), Player("c", "Ben")),
            RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true,
                playAllNumbers = true, prizes = plan.prizes.map { it.prize }), Random(92),
            ticketCounts = mapOf("a" to 2, "b" to 1, "c" to 1)).start()
        repeat(90) { game = game.draw() }
        for (ticket in game.tickets.filter { it.playerId != "c" })
            game = game.claim(ticket.playerId, ticket.numbers.toSet(), ClaimSelection(ticket.id, Prize.EARLY_FIVE.name))
        val other = game.tickets.last()
        game = game.claim(other.playerId, other.numbers.toSet(), ClaimSelection(other.id, Prize.TOP_LINE.name)).draw()
        val own = game.tickets.filter { it.playerId == "a" }
        val paid = plan.allocations(game).filter { it.playerId == "a" }
        return RoomView(code = "LOCAL1", roomId = "local-result", revision = 100, phase = RoomPhase.FINISHED,
            hostId = "a", locked = true, options = RoomOptions(game = game.settings, intervalSeconds = 5, coinGame = true),
            members = game.players.map { MemberView(it.id, it.name, it.avatar, true, true) },
            round = PublicRound(game.id, game.status, game.called, own, game.awards, emptyList(), game.players.associate { it.id to game.score(it.id) }, "fixture", players = game.players),
            nextDrawAt = null, expiresAt = 100_000, serverTime = 10_000,
            coins = CoinTableView(4, 400, plan.prizes, 2, null,
                settledWinnings = paid.filter { it.prize != null }.sumOf { it.coins },
                returnedCoins = paid.filter { it.prize == null }.sumOf { it.coins }))
    }

    @Test fun sharedResultsShowOnlyOwnShareAndPlayAgainRemainsOneTap() = results("en", 1f)
    @Test fun sharedResultsStayReadableInHindiAtLargerTextSize() = results("hi", 1.3f)

    @Test fun friendsResultsOfferExplicitSameGroupPurchase() = friendsResults("en", 1f)
    @Test fun friendsResultsKeepReplayAndAlternativesReachableInLargeHindi() = friendsResults("hi", 1.5f)

    private fun friendsResults(language: String, scale: Float) {
        val result = finished().let { it.copy(coins = it.coins!!.copy(friendTable = true)) }
        val config = Configuration(compose.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language)); fontScale = scale
        }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        var state by mutableStateOf(OnlineUiState(loading = false, available = true, name = "You", playerId = "a",
            room = result, preferredTickets = 6, wallet = WalletView(350, 1, 0)))
        var replayTickets = 0; var quickTickets = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { CoinLobby(state, model, { quickTickets = it }, {}, {}, replayFriends = { replayTickets = it }) }
            }
        }
        compose.onNodeWithTag("buy-tickets-3").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("buy-tickets-4").assertIsNotEnabled()
        compose.onNodeWithTag("friend-replay").performScrollTo().assertIsDisplayed()
            .assertTextEquals(words(R.string.friend_replay, 300L)).performClick()
        assertEquals(3, replayTickets); assertEquals(0, quickTickets)
        captureTestScreen("friends-results-$language")
        compose.onNodeWithTag("coin-play").performScrollTo().assertIsEnabled().performClick()
        assertEquals(3, quickTickets)
        compose.onNodeWithTag("play-friends").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .assertTextEquals(words(R.string.friend_other_table))
        compose.runOnIdle { state = state.copy(pending = true) }
        compose.onNodeWithTag("friend-replay").assertDoesNotExist()
        compose.onNodeWithTag("coin-play").assertDoesNotExist()
        compose.onNodeWithTag("coin-retry").performScrollTo().assertIsDisplayed()
    }

    private fun results(language: String, scale: Float) {
        val room = finished()
        val config = Configuration(compose.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language)); fontScale = scale
        }
        val context = compose.activity.createConfigurationContext(config)
        val words = GameText(context.resources)
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        var selected = 0
        var state by mutableStateOf(OnlineUiState(loading = false, name = "You", playerId = "a",
            room = room, wallet = WalletView(1487, 1, 0), connection = Connection.LIVE))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(compose.activity.resources.displayMetrics.density, scale)) {
                TambolaTheme { CoinLobby(state, model, { selected = it }, {}, {}) }
            }
        }
        compose.onNodeWithTag("coin-winnings").assertTextEquals(words(R.string.coin_won, 27L))
        compose.onNodeWithTag("coin-prize-value-EARLY_FIVE").assertIsDisplayed().assertTextEquals("27")
        compose.onNodeWithTag("coin-prize-share-EARLY_FIVE").assertTextEquals(words(R.string.coin_shared))
        compose.onNodeWithTag("coin-prize-TOP_LINE").assertDoesNotExist()
        compose.onNodeWithTag("coin-prize-FULL_HOUSE").assertDoesNotExist()
        compose.onNodeWithTag("coin-play").performScrollTo().assertIsEnabled()
        captureTestScreen("coin-shared-results-$language")
        compose.onNodeWithTag("coin-play").performClick()
        assertEquals(3, selected)

        // Changing to a finished table where only another player won must remove the old win.
        compose.runOnIdle {
            val finishedRound = room.round!!
            state = state.copy(room = room.copy(round = finishedRound.copy(awards = finishedRound.awards.filter { it.prize != Prize.EARLY_FIVE }),
                coins = room.coins!!.copy(settledWinnings = 0)))
        }
        compose.onNodeWithTag("coin-winnings").performScrollTo().assertTextEquals(words(R.string.coin_won, 0L))
        compose.onNodeWithTag("coin-prize-EARLY_FIVE").assertDoesNotExist()
        compose.onNodeWithTag("coin-play").performScrollTo().assertIsEnabled()
    }

    @Test fun queueCountdownEndsAndNewServerSnapshotStartsTheNextCountdown() {
        var room by mutableStateOf(finished().copy(phase = RoomPhase.LOBBY, round = null,
            coins = finished().coins!!.copy(startsAt = 12_000)))
        val words = GameText(compose.activity.resources)
        compose.setContent { TambolaTheme { MatchCountdown(room) } }
        compose.onNodeWithTag("match-countdown").assertTextEquals(words(R.string.coin_starts, 2L))
        SystemClock.sleep(2100)
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("match-countdown").assertTextEquals(words(R.string.coin_starting))
        compose.runOnIdle { room = room.copy(serverTime = 20_000, coins = room.coins!!.copy(startsAt = 23_000)) }
        compose.onNodeWithTag("match-countdown").assertTextEquals(words(R.string.coin_starts, 3L))
    }

    @Test fun replayRetainsChoiceAcrossRemountAndOffersOnlyAffordableTickets() {
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        var shown by mutableStateOf(true)
        var state by mutableStateOf(OnlineUiState(loading = false, available = true, name = "You", playerId = "a",
            room = finished(), preferredTickets = 6, wallet = WalletView(1500, 1, 0)))
        var purchased = 0
        compose.setContent { TambolaTheme { if (shown) CoinLobby(state, model, { purchased = it }, {}, {}) } }
        compose.onNodeWithTag("buy-tickets-6").performScrollTo().assertIsSelected()
        compose.runOnIdle { shown = false }
        compose.runOnIdle { shown = true }
        compose.onNodeWithTag("buy-tickets-6").performScrollTo().assertIsSelected()
        compose.runOnIdle { state = state.copy(wallet = WalletView(230, 2, 0)) }
        compose.onNodeWithTag("buy-tickets-2").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("buy-tickets-3").assertIsNotEnabled()
        compose.onNodeWithTag("buy-tickets-6").assertIsNotEnabled()
        val words = GameText(compose.activity.resources)
        compose.onNodeWithTag("coin-play").performScrollTo().assertTextEquals(words(R.string.coin_play_again, 200L)).performClick()
        assertEquals(2, purchased)
        captureTestScreen("coin-affordable-replay")
        compose.onNodeWithTag("buy-tickets-1").performScrollTo().performClick()
        compose.runOnIdle { state = state.copy(wallet = WalletView(500, 3, 0)) }
        compose.onNodeWithTag("buy-tickets-1").assertIsSelected()
        compose.onNodeWithTag("buy-tickets-5").assertIsEnabled()
        compose.onNodeWithTag("buy-tickets-6").assertIsNotEnabled()
    }

    @Test fun refillUsesFreshServerClockAndRemountDoesNotRestartCooldown() {
        var shown by mutableStateOf(true)
        // Deliberately nowhere near the device wall clock or the old finished-table timestamp.
        val clock = ServerTime(500_000, System.nanoTime())
        var collected = 0
        compose.setContent { TambolaTheme { if (shown) CoinRefill(clock, 502_000, true) { collected++ } } }
        compose.onNodeWithTag("coin-refill").assertIsNotEnabled()
        compose.runOnIdle { shown = false }
        SystemClock.sleep(2100)
        compose.runOnIdle { shown = true }
        compose.onNodeWithTag("coin-refill").assertIsEnabled().performClick()
        assertEquals(1, collected)
    }

    @Test fun brokeWalletShowsFreeCoinsInsteadOfAnyPurchase() {
        val model = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
        val state = OnlineUiState(loading = false, available = true, name = "You", preferredTickets = 6,
            room = finished(), wallet = WalletView(99, 2, 0), serverTime = ServerTime(500_000, System.nanoTime()))
        compose.setContent { TambolaTheme { CoinLobby(state, model, { fail("Must collect coins before buying") }, {}, {}) } }
        compose.onNodeWithTag("coin-play").assertDoesNotExist()
        compose.onNodeWithTag("coin-refill").performScrollTo().assertIsEnabled()
        (1..6).forEach { compose.onNodeWithTag("buy-tickets-$it").assertIsNotEnabled().assertIsNotSelected() }
        captureTestScreen("coin-free-refill")
    }
}
