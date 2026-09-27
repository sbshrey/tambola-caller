package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.domain.COIN_STARTER_BALANCE
import io.github.sbshrey.tambola.domain.COIN_TICKET_PRICE
import io.github.sbshrey.tambola.game.online.OnlineUiState
import io.github.sbshrey.tambola.game.presentation.affordableTickets
import io.github.sbshrey.tambola.protocol.*

/** Opening or restoring a link is read-only. Only the labelled purchase button joins. */
@Composable
internal fun FriendInviteDialog(code: String, state: OnlineUiState, join: (Int) -> Unit, close: () -> Unit, resume: () -> Unit) {
    val words = gameText()
    val valid = Regex("[A-HJ-NP-Z2-9]{8}").matches(code)
    val sameTable = state.room?.code == code && state.room.coins?.friendTable == true
    val occupied = state.room?.phase in setOf(RoomPhase.LOBBY, RoomPhase.ACTIVE)
    val blocked = state.loading || state.busy || state.pending || state.storageFailure || state.sessionExpired || !state.available || occupied
    val balance = state.wallet?.balance ?: if (state.name == null) COIN_STARTER_BALANCE else null
    var chosen by rememberSaveable(code) { mutableIntStateOf(state.preferredTickets) }
    var submitted by rememberSaveable(code) { mutableStateOf(false) }
    val tickets = affordableTickets(chosen, balance)
    val cost = tickets * COIN_TICKET_PRICE
    val fontScale = LocalDensity.current.fontScale
    LaunchedEffect(submitted, sameTable, state.busy, state.pending) {
        if (submitted && sameTable && !state.busy && !state.pending) close()
    }
    MaterialTheme(colorScheme = GameNightPalette.colors) {
        AlertDialog(onDismissRequest = close,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(.94f).semantics { testTagsAsResourceId = true },
            title = { Text(words(R.string.friend_invitation_title)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("friend-invitation-scroll"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!valid) Text(words(R.string.invite_invalid))
                else {
                    Text(words(R.string.friend_code_label) + ": " + code.chunked(4).joinToString(" "),
                        fontWeight = FontWeight.Bold, color = GameNightPalette.gold, modifier = Modifier.testTag("friend-invitation-code"))
                    when {
                        state.pending -> Text(words(R.string.friend_invitation_pending))
                        state.storageFailure || state.sessionExpired -> Text(words(R.string.invite_restore_profile))
                        sameTable -> Text(words(R.string.invite_already_here))
                        occupied -> Text(words(R.string.friend_invitation_occupied, state.room!!.code))
                        else -> {
                            Text(words(R.string.friend_invitation_review))
                            // A wide landscape dialog exposes all six choices without a hidden second row.
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                              val columns = if (maxWidth >= (360 * fontScale).dp) 6 else 3
                              Column {
                              (1..6).toList().chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { count ->
                                        FilterChip(selected = tickets == count, onClick = { chosen = count },
                                            enabled = !blocked && balance != null && balance >= count * COIN_TICKET_PRICE,
                                            label = { Text("$count") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("invite-tickets-$count"))
                                    }
                                }
                              }
                              }
                            }
                            Text(words(R.string.friend_entry_cost, tickets, cost), fontWeight = FontWeight.Bold, modifier = Modifier.testTag("friend-invitation-cost"))
                            Text(words(R.string.friend_refund), style = MaterialTheme.typography.bodySmall)
                            if (balance != null && balance < COIN_TICKET_PRICE) Text(words(R.string.friend_invitation_refill))
                        }
                    }
                    if (!state.available) Text(words(R.string.coin_unavailable))
                    state.error?.let { Text(words.message(it), color = MaterialTheme.colorScheme.error) }
                }
            }
        }, confirmButton = {
            if (valid && (sameTable || occupied || state.pending || state.storageFailure || state.sessionExpired)) {
                TextButton(onClick = resume, modifier = Modifier.heightIn(min = 48.dp).testTag("friend-invitation-resume")) {
                    Text(words(R.string.friend_invitation_return))
                }
            } else if (valid) Button(onClick = { submitted = true; join(tickets) },
                enabled = !blocked && balance != null && balance >= cost,
                modifier = Modifier.heightIn(min = 48.dp).testTag("friend-invitation-join")) {
                Text(words(R.string.friend_invitation_join, cost))
            }
        }, dismissButton = {
            TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp).testTag("friend-invitation-dismiss")) { Text(words(R.string.invite_dismiss)) }
        })
    }
}
