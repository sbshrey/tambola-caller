package io.github.sbshrey.tambola.game.ui

import io.github.sbshrey.tambola.game.R

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
    val words = gameText()
    val mixer = GameAudio.get(LocalContext.current)
    val pause by mixer.pause.collectAsStateWithLifecycle()
    pause?.let { reason ->
        GameCard {
            Text(words.soundPause(reason), style = MaterialTheme.typography.bodyMedium)
            if (reason != SoundPause.WAITING) TextButton(onClick = mixer::resumeSound) { Text(words(R.string.ui_resume_sound)) }
        }
    }
}

@Composable
fun SoundSettings(prefs: Preferences, update: (Preferences) -> Unit) {
    val words = gameText()
    GameCard {
        Text(words(R.string.ui_set_the_mood), style = MaterialTheme.typography.titleLarge)
        SettingSwitch(words(R.string.ui_background_music), words(R.string.ui_a_little_game_night_an_original_gentle_instrumental), prefs.music) { update(prefs.copy(music = it)) }
        SoundVolume(words(R.string.ui_music_volume), prefs.musicVolume, prefs.music) { update(prefs.copy(musicVolume = it)) }
        SettingSwitch(words(R.string.ui_game_sounds), words(R.string.ui_soft_cues_for_new_tickets_marking_calls_and), prefs.effects) { update(prefs.copy(effects = it)) }
        SoundVolume(words(R.string.ui_effects_volume), prefs.effectsVolume, prefs.effects) { update(prefs.copy(effectsVolume = it)) }
        Text(words(R.string.ui_music_softens_under_spoken_numbers_sound_stops_when), color = Muted)
        Text(words(R.string.ui_music_and_effects_are_original_synthesized_sounds_packaged), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SoundVolume(label: String, value: Int, enabled: Boolean = true, update: (Int) -> Unit) {
    val words = gameText()
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label · ${draft.roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { update(draft.roundToInt()) },
            valueRange = 0f..100f, steps = 19, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}
