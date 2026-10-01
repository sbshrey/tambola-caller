package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.domain.COIN_BETA_BALANCE
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
    val balance = state.wallet?.balance ?: if (state.name == null) COIN_BETA_BALANCE else null
    var chosen by rememberSaveable(code) { mutableIntStateOf(state.preferredTickets) }
    var submitted by rememberSaveable(code) { mutableStateOf(false) }
    val tickets = affordableTickets(chosen, balance)
    val cost = tickets * COIN_TICKET_PRICE
    LaunchedEffect(submitted, sameTable, state.busy, state.pending) {
        if (submitted && sameTable && !state.busy && !state.pending) close()
    }
    val recovery = sameTable || occupied || state.pending || state.storageFailure || state.sessionExpired
    MaterialTheme(colorScheme = GameNightPalette.colors) {
      Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().testTag("friend-invitation-window"), contentAlignment = Alignment.Center) {
          val wide = maxWidth >= 600.dp && maxWidth > maxHeight
          val details: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (!valid) Text(words(R.string.invite_invalid), fontSize = 14.sp, lineHeight = 18.sp)
              else {
                Text(code.chunked(4).joinToString(" "), fontSize = 20.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold, color = GameNightPalette.gold, modifier = Modifier.testTag("friend-invitation-code"))
                when {
                    state.pending -> Text(words(R.string.friend_invitation_pending), fontSize = 14.sp, lineHeight = 18.sp)
                    state.storageFailure || state.sessionExpired -> Text(words(R.string.invite_restore_profile), fontSize = 14.sp, lineHeight = 18.sp)
                    sameTable -> Text(words(R.string.invite_already_here), fontSize = 14.sp, lineHeight = 18.sp)
                    occupied -> Text(words(R.string.friend_invitation_occupied, state.room!!.code), fontSize = 14.sp, lineHeight = 18.sp)
                    else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columns = if (maxWidth >= 308.dp) 6 else 3
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..6).toList().chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    row.forEach { count ->
                                        FilterChip(selected = tickets == count, onClick = { chosen = count },
                                            enabled = !blocked && balance != null && balance >= count * COIN_TICKET_PRICE,
                                            label = { Text("$count", fontSize = 18.sp, lineHeight = 22.sp) },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("invite-tickets-$count"))
                                    }
                                }
                            }
                        }
                    }
                }
              }
            }
          }
          val actions: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (valid && !recovery) {
                Text(words(R.string.friend_entry_cost, pluralStringResource(R.plurals.ticket_count, tickets, tickets), cost), fontSize = 14.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.testTag("friend-invitation-cost"))
                Text(words(R.string.friend_refund_short), fontSize = 12.sp, lineHeight = 16.sp)
                if (balance != null && balance < COIN_TICKET_PRICE) Text(words(R.string.friend_invitation_refill), fontSize = 12.sp, lineHeight = 16.sp)
              }
              if (!state.available) Text(words(R.string.coin_unavailable), fontSize = 12.sp, lineHeight = 16.sp)
              state.error?.let { Text(words.message(it), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 16.sp) }
              if (valid && recovery) {
                Button(onClick = resume, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("friend-invitation-resume")) {
                    Text(words(R.string.friend_invitation_return), fontSize = 14.sp, lineHeight = 18.sp)
                }
              } else if (valid) Button(onClick = { submitted = true; join(tickets) },
                  enabled = !blocked && balance != null && balance >= cost,
                  modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("friend-invitation-join")) {
                  Text(words(R.string.friend_invitation_join, cost), fontSize = 14.sp, lineHeight = 18.sp)
              }
            }
          }
          Surface(Modifier.widthIn(max = 740.dp).fillMaxWidth(.94f).testTag("friend-invitation-content")
              .semantics { testTagsAsResourceId = true }, shape = RoundedCornerShape(24.dp), color = GameNightPalette.panel) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(words(R.string.lobby_friends_short), fontSize = 20.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp).testTag("friend-invitation-dismiss")) {
                    Text(words(R.string.invite_dismiss), fontSize = 14.sp, lineHeight = 18.sp)
                }
              }
              if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { details() }
                Box(Modifier.weight(1f)) { actions() }
              } else { details(); actions() }
            }
          }
        }
      }
    }
}
