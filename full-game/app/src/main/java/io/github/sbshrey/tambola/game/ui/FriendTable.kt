package io.github.sbshrey.tambola.game.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.protocol.RoomView
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun FriendEntryDialog(tickets: Int, cost: Long, enabled: Boolean, enter: (String?) -> Unit, close: () -> Unit) {
    val words = gameText()
    var join by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text(words(R.string.friend_play)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(words(R.string.friend_entry_detail))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !join, onClick = { join = false }, label = { Text(words(R.string.friend_create)) }, modifier = Modifier.testTag("friend-create-mode"))
                FilterChip(selected = join, onClick = { join = true }, label = { Text(words(R.string.friend_join)) }, modifier = Modifier.testTag("friend-join-mode"))
            }
            if (join) OutlinedTextField(value = code, onValueChange = {
                code = it.uppercase(Locale.ROOT).filter { c -> c.isLetterOrDigit() }.take(8)
            }, label = { Text(words(R.string.friend_code_label)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().testTag("friend-code-input"))
            Text(words(R.string.friend_entry_cost, tickets, cost), color = GameNightPalette.gold, fontWeight = FontWeight.Bold)
            Text(words(R.string.friend_refund), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        Button(onClick = { enter(code.takeIf { join }) }, enabled = enabled && (!join || Regex("[A-HJ-NP-Z2-9]{8}").matches(code)),
            modifier = Modifier.heightIn(min = 48.dp).testTag("friend-enter")) { Text(words(if (join) R.string.friend_join else R.string.friend_create)) }
    }, dismissButton = { TextButton(onClick = close) { Text(words(R.string.ui_keep_playing)) } })
}

/** Only actual members appear here. The host controls the start; seats do not fill with computers. */
@Composable
internal fun FriendWaitingRoom(room: RoomView, playerId: String?, enabled: Boolean, start: () -> Unit,
    invitationLink: suspend (String) -> String? = { null }) {
    val words = gameText()
    val context = LocalContext.current
    var copied by remember(room.code) { mutableStateOf(false) }
    var sharing by remember(room.code) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val host = room.hostId == playerId
    val remaining = remainingCoinTime(room.expiresAt, room.serverTime, room.roomId)
    val seconds by countdownSeconds(remaining)
    val ready = room.members.size >= 2 && room.members.all { it.connected && it.ready }
    Column(Modifier.fillMaxWidth().testTag("friend-waiting"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(words(R.string.friend_title), fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(words(if (host) R.string.friend_host_hint else R.string.friend_guest_hint), color = GameNightPalette.muted, fontSize = 13.sp)
        val invite: @Composable () -> Unit = { Surface(color = GameNightPalette.panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, GameNightPalette.raised)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(words(R.string.friend_code_label), color = GameNightPalette.muted, fontSize = 12.sp)
                Text(room.code.chunked(4).joinToString(" "), color = GameNightPalette.gold, fontWeight = FontWeight.Black, fontSize = 32.sp,
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
                    }, enabled = !sharing, modifier = Modifier.heightIn(min = 48.dp).testTag("friend-share")) {
                        Text(words(if (sharing) R.string.friend_preparing_link else R.string.friend_invite))
                    }
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(words(R.string.friend_code_label), room.code))
                        copied = true
                    }, modifier = Modifier.heightIn(min = 48.dp).testTag("friend-copy")) { Text(words(if (copied) R.string.friend_copied else R.string.friend_copy)) }
                }
            }
        } }
        val roster: @Composable () -> Unit = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(words(R.string.friend_people, room.members.size, room.options.capacity), fontWeight = FontWeight.Bold)
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
        if (host) Button(onClick = start, enabled = enabled && ready && seconds > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("friend-start"),
            colors = ButtonDefaults.buttonColors(containerColor = GameNightPalette.coral, contentColor = GameNightPalette.background)) {
            Text(words(if (ready) R.string.friend_start else R.string.friend_wait), fontWeight = FontWeight.Bold)
        }
        } }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 580.dp) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { invite() }
                Box(Modifier.weight(1f)) { roster() }
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { invite(); roster() }
        }
        Text(words(R.string.friend_expires, (seconds + 59) / 60), color = GameNightPalette.muted, fontSize = 12.sp)
    }
}
