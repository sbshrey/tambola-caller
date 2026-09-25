package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sbshrey.tambola.game.audio.GameAudio
import io.github.sbshrey.tambola.game.audio.SoundPause
import io.github.sbshrey.tambola.game.data.Preferences
import kotlin.math.roundToInt

@Composable
fun SoundNotice() {
    val mixer = GameAudio.get(LocalContext.current)
    val pause by mixer.pause.collectAsStateWithLifecycle()
    pause?.let { reason ->
        GameCard {
            Text(reason.message, style = MaterialTheme.typography.bodyMedium)
            if (reason != SoundPause.WAITING) TextButton(onClick = mixer::resumeSound) { Text("Resume sound") }
        }
    }
}

@Composable
fun SoundSettings(prefs: Preferences, update: (Preferences) -> Unit) {
    GameCard {
        Text("Set the mood", style = MaterialTheme.typography.titleLarge)
        SettingSwitch("Background music", "A Little Game Night · an original, gentle instrumental loop.", prefs.music) { update(prefs.copy(music = it)) }
        SoundVolume("Music volume", prefs.musicVolume, prefs.music) { update(prefs.copy(musicVolume = it)) }
        SettingSwitch("Game sounds", "Soft cues for new tickets, marking, calls and verified wins.", prefs.effects) { update(prefs.copy(effects = it)) }
        SoundVolume("Effects volume", prefs.effectsVolume, prefs.effects) { update(prefs.copy(effectsVolume = it)) }
        Text("Music softens under spoken numbers. Sound stops when you leave the app; after an audio device disconnects, choose Resume sound when you're ready.", color = Muted)
        Text("Music and effects are original synthesized sounds, packaged for offline play.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SoundVolume(label: String, value: Int, enabled: Boolean = true, update: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label · ${draft.roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { update(draft.roundToInt()) },
            valueRange = 0f..100f, steps = 19, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}
