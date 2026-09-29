package io.github.sbshrey.tambola.game.updates

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sbshrey.tambola.game.R

internal data class UpdateControls(val model: UpdateViewModel, val eligible: Boolean)
internal val LocalAppUpdates = staticCompositionLocalOf<UpdateControls?> { null }

@Composable internal fun UpdateSettingsButton() {
    val controls = LocalAppUpdates.current ?: return
    if (!controls.model.enabled) return
    TextButton(onClick = { controls.model.check() }, enabled = controls.eligible,
        modifier = Modifier.heightIn(min = 48.dp).testTag("check-update")) {
        Text(stringResource(if (controls.eligible) R.string.update_check else R.string.update_after_game))
    }
}

@Composable internal fun UpdateHost(model: UpdateViewModel, eligible: Boolean, activity: Activity) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(eligible) { model.setEligible(eligible) }
    if (!eligible || !state.visible) return
    UpdateDialog(state, model::dismiss, model::cancel, model::download, { model.install(activity) }, { model.check() })
}

@Composable internal fun UpdateDialog(state: UpdateState, dismiss: () -> Unit, cancel: () -> Unit,
    download: () -> Unit, install: () -> Unit, retry: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, modifier = Modifier.testTag("app-update-dialog"),
        title = { Text(stringResource(R.string.update_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(when (state.stage) {
                UpdateStage.CHECKING -> R.string.update_checking
                UpdateStage.AVAILABLE -> R.string.update_available
                UpdateStage.DOWNLOADING -> R.string.update_downloading
                UpdateStage.READY -> R.string.update_ready
                UpdateStage.PERMISSION -> R.string.update_permission
                UpdateStage.CURRENT -> R.string.update_current
                else -> R.string.update_error
            }))
            if (state.stage == UpdateStage.DOWNLOADING) {
                LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${state.percent}%")
            }
        } },
        confirmButton = { when (state.stage) {
            UpdateStage.AVAILABLE -> TextButton(onClick = download, modifier = Modifier.testTag("download-update")) { Text(stringResource(R.string.update_download)) }
            UpdateStage.READY, UpdateStage.PERMISSION -> TextButton(onClick = install, modifier = Modifier.testTag("install-update")) { Text(stringResource(R.string.update_install)) }
            UpdateStage.ERROR -> TextButton(onClick = retry) { Text(stringResource(R.string.update_retry)) }
            else -> TextButton(onClick = dismiss) { Text(stringResource(R.string.update_close)) }
        } },
        dismissButton = { if (state.stage in setOf(UpdateStage.AVAILABLE, UpdateStage.READY, UpdateStage.PERMISSION, UpdateStage.ERROR)) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.update_later)) }
        } else if (state.stage == UpdateStage.DOWNLOADING) {
            TextButton(onClick = cancel) { Text(stringResource(R.string.update_cancel)) }
        } })
}
