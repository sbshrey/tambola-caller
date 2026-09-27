package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sbshrey.tambola.game.R

/** A landscape keyboard leaves too little height for AlertDialog's fixed title/action slots. */
@Composable
internal fun LobbyPlayerDialog(name: String, avatar: Int, enabled: Boolean, changeName: (String) -> Unit,
    chooseAvatar: (Int) -> Unit, save: () -> Unit, close: () -> Unit) {
    val words = gameText()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun finish(action: () -> Unit) { focus.clearFocus(); keyboard?.hide(); action() }
    Dialog(onDismissRequest = { finish(close) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish(close) }
            .clearAndSetSemantics {})
        Surface(Modifier.fillMaxWidth(.9f).widthIn(max = 680.dp), shape = RoundedCornerShape(24.dp), color = GameNightPalette.panel) {
            BoxWithConstraints {
                val short = maxHeight < 280.dp
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!short) Text(words(R.string.lobby_personalize), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, changeName, singleLine = true, enabled = enabled,
                        label = { Text(words(R.string.ui_online_display_name)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finish {} }),
                        modifier = Modifier.fillMaxWidth().testTag("lobby-player-name"))
                    if (!short) AvatarChoice(words(R.string.ui_your_profile), avatar, enabled) { finish { chooseAvatar(it) } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { finish(close) }) { Text(words(R.string.ui_keep_playing)) }
                        Button(onClick = { finish(save) }, enabled = enabled && name.isNotBlank()) { Text(words(R.string.lobby_save_player)) }
                    }
                }
            }
        }
      }
    }
}
