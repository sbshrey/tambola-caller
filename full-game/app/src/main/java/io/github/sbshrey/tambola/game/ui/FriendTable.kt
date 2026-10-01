package io.github.sbshrey.tambola.game.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import io.github.sbshrey.tambola.game.online.Connection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.protocol.RoomView
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun FriendEntryDialog(tickets: Int, cost: Long, enabled: Boolean, enter: (String?) -> Unit, close: () -> Unit) {
    val words = gameText()
    var join by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      val focus = LocalFocusManager.current
      val keyboard = LocalSoftwareKeyboardController.current
      fun finishTyping() { focus.clearFocus(); keyboard?.hide() }
      BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().testTag("friend-entry-window"), contentAlignment = Alignment.Center) {
        val typingOnly = join && (WindowInsets.ime.getBottom(LocalDensity.current) > 0 || maxHeight < 260.dp)
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        val choices: @Composable (Boolean) -> Unit = { editing ->
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!editing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !join, onClick = { join = false }, label = { Text(words(R.string.friend_create), fontSize = 13.sp) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("friend-create-mode"))
                FilterChip(selected = join, onClick = { join = true }, label = { Text(words(R.string.friend_join), fontSize = 13.sp) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("friend-join-mode"))
            }
            if (join) OutlinedTextField(value = code, onValueChange = {
                code = it.uppercase(Locale.ROOT).filter { c -> c.isLetterOrDigit() }.take(8)
            }, label = { Text(words(R.string.friend_code_label)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finishTyping() }),
                modifier = Modifier.fillMaxWidth().testTag("friend-code-input"))
          }
        }
        val purchase: @Composable (Boolean) -> Unit = { editing ->
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) TextButton(onClick = { finishTyping() }, modifier = Modifier.align(Alignment.End).testTag("friend-code-done")) { Text(words(R.string.ui_done)) }
            else {
            Text(words(R.string.friend_entry_cost, pluralStringResource(R.plurals.ticket_count, tickets, tickets), cost), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("friend-entry-cost"))
            Text(words(R.string.friend_refund_short), fontSize = 12.sp)
            Button(onClick = { finishTyping(); enter(code.takeIf { join }) }, enabled = enabled && (!join || Regex("[A-HJ-NP-Z2-9]{8}").matches(code)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("friend-enter")) { Text(words(if (join) R.string.friend_join else R.string.friend_create), fontSize = 14.sp) }
            }
          }
        }
        Surface(Modifier.widthIn(max = 740.dp).fillMaxWidth(.94f).testTag("friend-entry"), shape = RoundedCornerShape(24.dp)) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!typingOnly) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(words(R.string.lobby_friends_short), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = close, modifier = Modifier.size(48.dp).testTag("friend-entry-close").semantics { contentDescription = words(R.string.ui_keep_playing) }) { Text("×", fontSize = 24.sp) }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.1f)) { choices(typingOnly) }
                Box(Modifier.weight(1f)) { purchase(typingOnly) }
            } else { choices(typingOnly); purchase(typingOnly) }
          }
        }
      }
    }
}

/** Only actual members appear here. The host controls the start; seats do not fill with computers. */
@Composable
internal fun FriendWaitingRoom(room: RoomView, playerId: String?, enabled: Boolean, start: () -> Unit, leave: () -> Unit,
    invitationLink: suspend (String) -> String? = { null }, connection: Connection = Connection.LIVE, reconnect: () -> Unit = {}) {
    val words = gameText()
    val context = LocalContext.current
    var copied by remember(room.code) { mutableStateOf(false) }
    var sharing by remember(room.code) { mutableStateOf(false) }
    var showPlayers by remember(room.code) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val host = room.hostId == playerId
    val remaining = remainingCoinTime(room.expiresAt, room.serverTime, room.roomId)
    val seconds by countdownSeconds(remaining)
    val ready = room.members.size >= 2 && room.members.all { it.connected && it.ready }
    Column(Modifier.fillMaxWidth().testTag("friend-waiting"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (connection != Connection.LIVE) WaitingConnectionNotice(connection, enabled, reconnect)
        val invite: @Composable () -> Unit = { Surface(color = GameNightPalette.panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, GameNightPalette.raised)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(words(if (room.options.powersEnabled) R.string.power_room else R.string.power_classic_room) + " · " + words(R.string.friend_code_label),
                    color = GameNightPalette.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("friend-mode"))
                Text(room.code.chunked(4).joinToString(" "), color = GameNightPalette.gold, fontWeight = FontWeight.Black, fontSize = 24.sp,
                    letterSpacing = 3.sp, modifier = Modifier.testTag("friend-code"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        sharing = true
                        scope.launch {
                            try {
                                val link = invitationLink(room.code)
                                val text = (link?.let { "$it\n\n" } ?: "") + words(R.string.friend_share_text, room.code)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null))
                            } finally { sharing = false }
                        }
                    }, enabled = !sharing, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("friend-share")) {
                        Text(words(if (sharing) R.string.friend_preparing_link else R.string.friend_invite), fontSize = 13.sp, lineHeight = 16.sp)
                    }
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(words(R.string.friend_code_label), room.code))
                        copied = true
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("friend-copy")) { Text(words(if (copied) R.string.friend_copied else R.string.friend_copy), fontSize = 13.sp, lineHeight = 16.sp) }
                }
            }
        } }
        val roster: @Composable () -> Unit = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = { showPlayers = true }, modifier = Modifier.testTag("friend-players")) {
            Text(words(R.string.friend_people, room.members.size, room.options.capacity), fontWeight = FontWeight.Bold)
        }
        if (showPlayers) ArenaDialog(words(R.string.friend_people, room.members.size, room.options.capacity), { showPlayers = false }) {
        room.members.chunked(2).forEach { members ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                members.forEach { member ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarBadge(member.avatar, size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Text(words(if (!member.connected) R.string.friend_reconnecting else if (member.playerId == room.hostId) R.string.friend_host else R.string.friend_ready),
                                color = if (member.connected) GameNightPalette.mint else GameNightPalette.gold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        }
        if (host) Button(onClick = start, enabled = enabled && connection == Connection.LIVE && ready && seconds > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("friend-start"),
            colors = ButtonDefaults.buttonColors(containerColor = GameNightPalette.coral, contentColor = GameNightPalette.background)) {
            Text(words(if (connection != Connection.LIVE) R.string.friend_wait_connection else if (ready) R.string.friend_start else R.string.friend_wait), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = leave, enabled = enabled && connection == Connection.LIVE,
            modifier = Modifier.testTag("cancel-match")) { Text(words(R.string.coin_cancel)) }
        } }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 580.dp) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { invite() }
                Box(Modifier.weight(1f)) { roster() }
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { invite(); roster() }
        }
        Text(words(R.string.friend_expires, (seconds + 59) / 60), color = GameNightPalette.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("friend-expiry"))
    }
}
