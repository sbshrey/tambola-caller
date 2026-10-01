package io.github.sbshrey.tambola.game

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.sbshrey.tambola.game.ads.LocalRewardedAds
import io.github.sbshrey.tambola.game.ads.RewardedAdsController
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.isSystemInDarkTheme
import io.github.sbshrey.tambola.game.data.Appearance
import androidx.lifecycle.Lifecycle
import android.view.WindowManager
import io.github.sbshrey.tambola.domain.RoundStatus
import io.github.sbshrey.tambola.game.online.OnlineViewModel
import io.github.sbshrey.tambola.game.online.RoomInviteViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sbshrey.tambola.game.ui.TambolaApp
import io.github.sbshrey.tambola.game.ui.TambolaTheme
import io.github.sbshrey.tambola.game.audio.GameAudio

class MainActivity : AppCompatActivity() {
    private val updates: io.github.sbshrey.tambola.game.updates.UpdateViewModel by viewModels()
    private val model: GameViewModel by viewModels()
    private val bingo: BingoViewModel by viewModels()
    private val online: OnlineViewModel by viewModels()
    private val invitations: RoomInviteViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the visible game awake through lobbies, results and update prompts as well as live rounds.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val incoming = intent
        // SavedStateHandle defaults include launch extras. Clear them before any ViewModel is created.
        intent = if (incoming.action == Intent.ACTION_VIEW) consumedInviteIntent() else Intent(incoming).replaceExtras(null as Bundle?)
        if (savedInstanceState == null && (incoming.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) receiveInvite(incoming)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(18, 29, 43)))
        setContent {
            val ads = remember { if (BuildConfig.REWARDED_ADS_ENABLED) RewardedAdsController(this) else null }
            LaunchedEffect(ads) {
                try { ads?.updateConsent() }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* The optional watch action retries consent when needed. */ }
            }
            val state by model.state.collectAsStateWithLifecycle()
            val onlineState by online.state.collectAsStateWithLifecycle()
            val bingoState by bingo.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.screen) { if (state.screen != Screen.BINGO) bingo.pause() }
            val inviteState by invitations.state.collectAsStateWithLifecycle()
            LaunchedEffect(inviteState.navigate, inviteState.revision, state.loading, state.saving) {
                if (inviteState.navigate && !state.loading && !state.saving) {
                    model.navigate(Screen.ONLINE)
                    invitations.navigated()
                }
            }
            LaunchedEffect(state.screen) { online.setActive(state.screen in setOf(Screen.HOME, Screen.ONLINE, Screen.BINGO) && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
            val ambient = when (state.screen) {
                Screen.GAME -> state.round?.status == RoundStatus.PLAYING
                Screen.ONLINE -> onlineState.room?.round == null || onlineState.room?.round?.status == RoundStatus.PLAYING
                Screen.RESULTS, Screen.HISTORY, Screen.BADGES -> false
                else -> true
            }
            LaunchedEffect(ambient) { GameAudio.get(application).setMusicEligible(ambient) }
            val dark = when (state.preferences.appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            val gameLobby = state.screen in setOf(Screen.HOME, Screen.BINGO, Screen.SETTINGS) || state.screen == Screen.ONLINE &&
                (onlineState.room == null || onlineState.room?.options?.coinGame == true)
            LaunchedEffect(dark, gameLobby) {
                val transparent = android.graphics.Color.TRANSPARENT
                val lightBar = android.graphics.Color.rgb(255, 249, 240)
                val darkBar = android.graphics.Color.rgb(18, 29, 43)
                enableEdgeToEdge(
                    statusBarStyle = if (dark || gameLobby) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (gameLobby) SystemBarStyle.dark(android.graphics.Color.rgb(25, 22, 47)) else if (dark) SystemBarStyle.dark(darkBar) else SystemBarStyle.light(lightBar, darkBar),
                )
            }
            val updateEligible = !state.loading && !onlineState.loading && !state.saving &&
                state.screen in setOf(Screen.HOME, Screen.ONLINE, Screen.SETTINGS) &&
                state.round?.status != RoundStatus.PLAYING && bingoState.round?.status != RoundStatus.PLAYING &&
                onlineState.room?.phase !in setOf(io.github.sbshrey.tambola.protocol.RoomPhase.LOBBY, io.github.sbshrey.tambola.protocol.RoomPhase.ACTIVE) &&
                onlineState.bingoRoom?.phase !in setOf(io.github.sbshrey.tambola.protocol.RoomPhase.LOBBY, io.github.sbshrey.tambola.protocol.RoomPhase.ACTIVE) &&
                !onlineState.busy && !onlineState.pending && !onlineState.adActive && inviteState.code == null
            CompositionLocalProvider(LocalRewardedAds provides ads,
                io.github.sbshrey.tambola.game.updates.LocalAppUpdates provides io.github.sbshrey.tambola.game.updates.UpdateControls(updates, updateEligible)) {
                TambolaTheme(dark) {
                    TambolaApp(state, model, onlineState, online, bingoState, bingo, inviteState, invitations::dismiss)
                    io.github.sbshrey.tambola.game.updates.UpdateHost(updates, updateEligible, this)
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveInvite(intent)
    }
    private fun receiveInvite(incoming: Intent) {
        if (incoming.action != Intent.ACTION_VIEW) return
        // Do not retain arbitrary external extras or reconsume the original link on recreation.
        intent = consumedInviteIntent()
        invitations.receive(incoming.dataString)
    }
    private fun consumedInviteIntent() = Intent(this, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    override fun onStart() { super.onStart(); model.setForeground(true); online.setActive(model.state.value.screen in setOf(Screen.HOME, Screen.ONLINE, Screen.BINGO)) }
    override fun onResume() { super.onResume(); GameAudio.get(application).setForeground(true) }
    override fun onPause() { GameAudio.get(application).setForeground(false); super.onPause() }
    override fun onStop() {
        if (!isChangingConfigurations) { model.setForeground(false); online.setActive(false); bingo.pause() }
        super.onStop()
    }
}
