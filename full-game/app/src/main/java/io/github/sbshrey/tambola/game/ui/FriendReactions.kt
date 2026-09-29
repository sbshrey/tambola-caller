package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.protocol.*
import io.github.sbshrey.tambola.client.ServerTime

internal fun GameText.reaction(kind: FriendReaction): String = this(when (kind) {
    FriendReaction.GOOD_LUCK -> R.string.reaction_good_luck
    FriendReaction.NICE_WIN -> R.string.reaction_nice_win
    FriendReaction.WELL_PLAYED -> R.string.reaction_well_played
    FriendReaction.THANKS -> R.string.reaction_thanks
})

@Composable internal fun friendReactionCaption(snapshot: ReactionSnapshot?, room: RoomView, clock: ServerTime?): String? {
    if (clock == null) return null
    val words = gameText()
    val latest = snapshot?.takeIf { it.roomId == room.roomId && it.roundId == room.round?.id }
        ?.reactions?.maxWithOrNull(compareBy<RoomReaction> { it.at }.thenBy { it.playerId }) ?: return null
    val remaining by remainingServerTime(latest.at + REACTION_LIFETIME_MS, clock)
    val player = room.members.firstOrNull { it.playerId == latest.playerId } ?: return null
    return if (remaining > 0) words(R.string.reaction_from, player.displayName, words.reaction(latest.kind)) else null
}

@Composable internal fun FriendReactionMenu(snapshot: ReactionSnapshot, enabled: Boolean, sending: Boolean,
    clock: ServerTime, closeMenu: () -> Unit, send: (FriendReaction) -> Unit) {
    val words = gameText()
    var choosing by remember(snapshot.roomId, snapshot.roundId) { mutableStateOf(false) }
    val seconds by countdownSeconds(remainingServerTime(snapshot.nextAllowedAt, clock))
    DropdownMenuItem(text = { Text(words(R.string.reaction_title)) }, enabled = enabled && !sending,
        onClick = { choosing = true }, modifier = Modifier.testTag("friend-react"))
    if (choosing) AlertDialog(onDismissRequest = { choosing = false; closeMenu() }, title = { Text(words(R.string.reaction_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FriendReaction.entries.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { reaction -> OutlinedButton(onClick = { choosing = false; closeMenu(); send(reaction) },
                    enabled = enabled && !sending && seconds == 0L,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("send-reaction-${reaction.name}")) { Text(words.reaction(reaction)) } }
            } }
            if (seconds > 0) Text(words(R.string.reaction_wait, seconds), modifier = Modifier.testTag("reaction-cooldown"))
        } }, confirmButton = { TextButton(onClick = { choosing = false; closeMenu() }) { Text(words(R.string.ui_back_to_game)) } })
}
