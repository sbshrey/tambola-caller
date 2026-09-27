package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.game.BuildConfig
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.data.PreferenceStore
import io.github.sbshrey.tambola.game.data.Preferences
import kotlinx.coroutines.launch

/** Available before creating a profile and from settings, without a network request. */
@Composable
fun GameDataDialog(dismiss: () -> Unit) {
    val words = gameText()
    val context = LocalContext.current
    val store = remember(context) { PreferenceStore(context.applicationContext) }
    val preferences by store.values.collectAsState(initial = Preferences())
    val scope = rememberCoroutineScope()
    val sections = listOf(
        R.string.privacy_device_title to R.string.privacy_device_body,
        R.string.privacy_online_title to R.string.privacy_online_body,
        R.string.privacy_retention_title to R.string.privacy_retention_body,
        R.string.privacy_deletion_title to R.string.privacy_deletion_body,
        R.string.privacy_recovery_title to R.string.privacy_recovery_body,
        R.string.privacy_controls_title to R.string.privacy_controls_body,
        R.string.privacy_monitoring_title to R.string.privacy_monitoring_body,
    )
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(words(R.string.privacy_title)) },
        text = {
            Column(Modifier.testTag("game-data-content").verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (BuildConfig.TELEMETRY_CONFIGURED) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(words(R.string.diagnostics_title), modifier = Modifier.weight(1f))
                        Switch(checked = preferences.diagnostics, onCheckedChange = { enabled -> scope.launch { store.diagnostics(enabled) } },
                            modifier = Modifier.testTag("diagnostics-consent"))
                    }
                    Text(words(R.string.diagnostics_detail))
                }
                sections.forEach { (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(words(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(words(body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(words(R.string.ui_build_status, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = dismiss, modifier = Modifier.testTag("close-game-data")) { Text(words(R.string.privacy_close)) } },
    )
}
