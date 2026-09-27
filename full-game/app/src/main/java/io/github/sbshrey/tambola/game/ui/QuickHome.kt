package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.domain.GameMode
import io.github.sbshrey.tambola.game.*
import io.github.sbshrey.tambola.game.R

@Composable
fun QuickHome(state: GameUiState, model: GameViewModel) {
    val words = gameText()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF174E41), Color(0xFF092F2B)))).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(words(R.string.play_tagline), color = Ivory, style = MaterialTheme.typography.headlineMedium)
        // Original vector art stays offline and costs no image download at startup.
        GameNightArtwork(Modifier.fillMaxWidth().height(136.dp), state.preferences.reducedMotion)
        PrimaryAction(words(R.string.ui_online_play), modifier = Modifier.testTag("quick-play"), enabled = !state.saving) { model.navigate(Screen.ONLINE) }
    }
}
