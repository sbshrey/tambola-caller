package io.github.sbshrey.tambola.game.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.github.sbshrey.tambola.game.R

@Composable
fun InterfaceLanguagePicker() {
    val words = gameText()
    val selected = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    GameCard {
        Text(words(R.string.interface_language), style = MaterialTheme.typography.titleLarge)
        Text(words(R.string.interface_language_detail), color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to R.string.language_system, "en" to R.string.language_english, "hi" to R.string.language_hindi).forEach { (tag, label) ->
                FilterChip(selected = selected == tag, onClick = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                }, label = { Text(words(label)) }, modifier = Modifier.testTag("interface-language-${tag.ifEmpty { "system" }}"))
            }
        }
    }
}
