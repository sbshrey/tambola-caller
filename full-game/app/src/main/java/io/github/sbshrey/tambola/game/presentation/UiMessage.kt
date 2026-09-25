package io.github.sbshrey.tambola.game.presentation

import androidx.annotation.StringRes
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/** Resolve at display time so retained ViewModels never hold text in the previous locale. */
data class UiMessage(@param:StringRes @get:StringRes val resource: Int, val arguments: List<Any> = emptyList())

class UiMessageException(val uiMessage: UiMessage) : IllegalArgumentException("Invalid game settings")

@OptIn(ExperimentalContracts::class)
fun requireUi(value: Boolean, @StringRes resource: Int, vararg arguments: Any) {
    contract { returns() implies value }
    if (!value) throw UiMessageException(UiMessage(resource, arguments.toList()))
}

fun Throwable.uiMessage(@StringRes fallback: Int): UiMessage =
    (this as? UiMessageException)?.uiMessage ?: UiMessage(fallback)
