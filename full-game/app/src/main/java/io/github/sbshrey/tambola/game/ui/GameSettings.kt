package io.github.sbshrey.tambola.game.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.data.Preferences

/** Online rounds own their pace. Only personal controls belong on this screen. */
@Composable
internal fun GameSettings(prefs: Preferences, update: (Preferences) -> Unit, back: () -> Unit) {
    val words = gameText()
    var privacy by rememberSaveable { mutableStateOf(false) }
    MaterialTheme(colorScheme = GameNightPalette.colors) {
      Surface(Modifier.fillMaxSize().testTag("game-settings"), color = GameNightPalette.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(words(R.string.ui_settings), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = back, modifier = Modifier.heightIn(min = 48.dp).testTag("home")) { Text(words(R.string.ui_back_to_game)) }
            }
            Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsPanel(words(R.string.settings_sound), Modifier.weight(1f)) {
                    PersonalToggle(words(R.string.ui_number_voice), "setting-voice", prefs.voice) {
                        update(prefs.copy(voice = it, voiceVolume = if (it && prefs.voiceVolume == 0) 100 else prefs.voiceVolume))
                    }
                    PersonalToggle(words(R.string.settings_music), "setting-music", prefs.music) {
                        update(prefs.copy(music = it, musicVolume = if (it && prefs.musicVolume == 0) 45 else prefs.musicVolume))
                    }
                    PersonalToggle(words(R.string.ui_game_sounds), "setting-effects", prefs.effects) {
                        update(prefs.copy(effects = it, effectsVolume = if (it && prefs.effectsVolume == 0) 60 else prefs.effectsVolume))
                    }
                }
                SettingsPanel(words(R.string.settings_play), Modifier.weight(1f)) {
                    PersonalToggle(words(R.string.settings_vibration), "setting-haptics", prefs.haptics) { update(prefs.copy(haptics = it)) }
                    PersonalToggle(words(R.string.ui_reduced_motion), "setting-motion", prefs.reducedMotion) { update(prefs.copy(reducedMotion = it)) }
                    Text(words(R.string.settings_language), fontSize = 12.sp, color = GameNightPalette.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("en" to "English", "hi" to "हिन्दी", "hinglish" to "Hinglish").forEach { (id, label) ->
                            FilterChip(selected = prefs.language == id, onClick = {
                                update(prefs.copy(language = id))
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (id == "hi") "hi" else "en"))
                            }, label = { Text(label, fontSize = 12.sp) }, modifier = Modifier.heightIn(min = 48.dp).testTag("setting-language-$id"))
                        }
                    }
                }
            }
            TextButton(onClick = { privacy = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("open-game-data")) { Text(words(R.string.privacy_open)) }
        }
      }
      if (privacy) GameDataDialog { privacy = false }
    }
}

@Composable
private fun SettingsPanel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = GameNightPalette.panel) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = GameNightPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun PersonalToggle(label: String, tag: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(value, role = Role.Switch, onValueChange = change).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(value, onCheckedChange = null)
    }
}
