package io.github.sbshrey.tambola.game.online

import io.github.sbshrey.tambola.client.RoomApiFailure
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.presentation.UiMessage

/** Wire codes are stable; server-supplied prose is neither localized nor trusted UI copy. */
internal fun roomFailureMessage(error: RoomApiFailure): UiMessage = UiMessage(when (error.code) {
    "room_missing" -> R.string.error_room_missing
    "room_closed" -> R.string.error_room_closed
    "room_locked" -> R.string.error_room_locked
    "friend_table_closed" -> R.string.friend_closed
    "friend_round_changed" -> R.string.friend_round_changed
    "room_full" -> R.string.error_room_full
    "room_limit" -> R.string.error_room_limit
    "host_only" -> R.string.error_host_only
    "not_lobby" -> R.string.error_not_lobby
    "not_active" -> R.string.error_not_active
    "invalid_avatar" -> R.string.error_supported_avatar
    "invalid_guest" -> R.string.error_name_length
    "capacity" -> R.string.error_capacity
    "not_ready" -> R.string.error_not_ready
    "insufficient_tickets" -> R.string.error_insufficient_tickets
    "paused" -> R.string.error_paused
    "automatic_calling" -> R.string.error_automatic_calling
    "not_paused" -> R.string.error_not_paused
    "not_finished" -> R.string.error_not_finished
    "invalid_member" -> R.string.error_invalid_member
    "round_in_progress" -> R.string.error_round_in_progress
    "not_member" -> R.string.error_not_member
    "id_reused" -> R.string.error_id_reused
    "rate_limited" -> R.string.error_rate_limited
    "database_unavailable", "internal_error" -> R.string.error_service_busy
    else -> when {
        error.status == 429 -> R.string.error_rate_limited
        error.status >= 500 -> R.string.error_service_busy
        else -> R.string.error_service_request
    }
})
