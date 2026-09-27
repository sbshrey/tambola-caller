package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.online.Connection

/** Keep connection recovery beside the saved table without replacing or narrowing its contents. */
@Composable
internal fun WaitingConnectionNotice(connection: Connection, enabled: Boolean, reconnect: () -> Unit) {
    val words = gameText()
    val message = when (connection) {
        Connection.CONNECTING -> R.string.lobby_table_connecting
        Connection.RECONNECTING -> R.string.lobby_table_reconnecting
        else -> R.string.lobby_table_disconnected
    }
    val status: @Composable (Modifier) -> Unit = { modifier ->
        Text(words(message), color = GameNightPalette.gold, fontSize = 13.sp,
            modifier = modifier.testTag("waiting-connection-copy").semantics { liveRegion = LiveRegionMode.Polite })
    }
    val retry: @Composable () -> Unit = {
        TextButton(onClick = reconnect, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("waiting-reconnect")) {
            Text(words(R.string.ui_reconnect_now))
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("waiting-connection")) {
        if (maxWidth >= 480.dp) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            status(Modifier.weight(1f)); retry()
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            status(Modifier.fillMaxWidth())
            Box(Modifier.align(Alignment.End)) { retry() }
        }
    }
}
