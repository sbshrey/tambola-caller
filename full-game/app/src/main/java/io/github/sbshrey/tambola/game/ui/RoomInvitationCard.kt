package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.online.OnlineUiState

@Composable
fun RoomInvitationCard(code: String, state: OnlineUiState, join: () -> Unit, dismiss: () -> Unit) {
    val words = gameText()
    GameCard {
        Eyebrow(words(R.string.invite_title))
        if (code.isEmpty()) Text(words(R.string.invite_invalid))
        else {
            SelectionContainer { Text(code, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("invite-code")) }
            Text(when {
                !state.available -> words(R.string.invite_unavailable)
                state.room?.code == code -> words(R.string.invite_already_here)
                state.room != null -> words(R.string.invite_other_room, state.room.code)
                state.pending -> words(R.string.invite_pending)
                state.sessionExpired || state.storageFailure -> words(R.string.invite_restore_profile)
                state.name == null -> words(R.string.invite_create_profile)
                else -> words(R.string.invite_review)
            }, color = Muted)
            if (state.room?.code == code) PrimaryAction(words(R.string.invite_view_room)) { dismiss() }
            else if (state.name != null) PrimaryAction(words(R.string.invite_join), enabled = state.available && state.room == null &&
                !state.busy && !state.pending && !state.sessionExpired && !state.storageFailure) { join() }
        }
        TextButton(onClick = dismiss, modifier = Modifier.testTag("dismiss-invitation")) { Text(words(R.string.invite_dismiss)) }
    }
}
