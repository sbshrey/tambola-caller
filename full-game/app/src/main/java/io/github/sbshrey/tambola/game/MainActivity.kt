package io.github.sbshrey.tambola.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import io.github.sbshrey.tambola.game.data.Appearance
import androidx.lifecycle.Lifecycle
import android.view.WindowManager
import io.github.sbshrey.tambola.domain.RoundStatus
import io.github.sbshrey.tambola.game.online.OnlineViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sbshrey.tambola.game.ui.TambolaApp
import io.github.sbshrey.tambola.game.ui.TambolaTheme

class MainActivity : ComponentActivity() {
    private val model: GameViewModel by viewModels()
    private val online: OnlineViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(18, 29, 43)))
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val onlineState by online.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.screen) { online.setActive(state.screen == Screen.ONLINE && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
            val playing = (state.screen == Screen.GAME && state.round?.status == RoundStatus.PLAYING) ||
                (state.screen == Screen.ONLINE && onlineState.room?.round?.status == RoundStatus.PLAYING)
            LaunchedEffect(playing) {
                if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val dark = when (state.preferences.appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val lightBar = android.graphics.Color.rgb(255, 249, 240)
                val darkBar = android.graphics.Color.rgb(18, 29, 43)
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(darkBar) else SystemBarStyle.light(lightBar, darkBar),
                )
            }
            TambolaTheme(dark) { TambolaApp(state, model, onlineState, online) }
        }
    }
    override fun onStart() { super.onStart(); model.setForeground(true); online.setActive(model.state.value.screen == Screen.ONLINE) }
    override fun onStop() { model.setForeground(false); online.setActive(false); super.onStop() }
}
