package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.online.OnlineUiState

/** The existing exact retry or reconnect, presented without moving the hand. */
@Composable
internal fun RoundRecoveryControls(state: OnlineUiState, retry: () -> Unit, reconnect: () -> Unit) {
    val words = gameText()
    val enabled = !state.busy && !state.storageFailure && !state.sessionExpired && !state.deletingProfile
    val action = if (state.pending) retry else reconnect
    val label = when {
        state.busy -> words(R.string.ui_checking)
        state.pending -> words(R.string.ui_retry_pending_action)
        else -> words(R.string.ui_reconnect_now)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp).testTag("round-recovery")) {
        val explain = maxWidth > 400.dp
        val visibleLabel = if (maxWidth < 160.dp && state.pending && !state.busy) words(R.string.round_retry_short) else label
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (explain) Text(words(if (state.pending) R.string.play_waiting_result else R.string.play_saved_calls),
                modifier = Modifier.weight(1f).testTag("round-recovery-copy").semantics { liveRegion = LiveRegionMode.Polite },
                fontSize = 11.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = action, enabled = enabled,
                modifier = (if (explain) Modifier.width(164.dp) else Modifier.fillMaxWidth()).height(56.dp)
                    .testTag(if (state.pending) "round-retry" else "round-reconnect").semantics { contentDescription = label },
                contentPadding = PaddingValues(horizontal = 8.dp), shape = RoundedCornerShape(16.dp)) {
                Text(visibleLabel, fontSize = 11.sp, lineHeight = 13.sp)
            }
        }
    }
}
