package io.github.sbshrey.tambola.game.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import io.github.sbshrey.tambola.client.ServerTime

/** The response anchor outlives menus, so opening a timer cannot restart its deadline. */
@Composable
internal fun remainingServerTime(deadline: Long, clock: ServerTime): State<Long> {
    val remaining = remember(deadline, clock) { mutableLongStateOf((deadline - clock.currentTimeMillis()).coerceAtLeast(0)) }
    LaunchedEffect(deadline, clock) {
        while (remaining.longValue > 0) {
            delay(minOf(200L, remaining.longValue))
            remaining.longValue = (deadline - clock.currentTimeMillis()).coerceAtLeast(0)
        }
    }
    return remaining
}

/** Keep clock reads inside the timer that needs them, and stop scheduling when its deadline ends. */
@Composable
internal fun remainingCoinTime(deadline: Long?, serverTime: Long?, identity: String?): State<Long> {
    val anchor = remember(deadline, serverTime, identity) {
        val reference = serverTime ?: System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val readRemaining: () -> Long = {
            ((deadline ?: reference) - reference - (SystemClock.elapsedRealtime() - elapsed)).coerceAtLeast(0)
        }
        readRemaining
    }
    val remaining = remember(anchor) { mutableLongStateOf(anchor()) }
    LaunchedEffect(anchor) {
        while (remaining.longValue > 0) {
            delay(minOf(200L, remaining.longValue))
            remaining.longValue = anchor()
        }
    }
    return remaining
}

@Composable
internal fun countdownSeconds(remaining: State<Long>): State<Long> =
    remember(remaining) { derivedStateOf { (remaining.value + 999) / 1000 } }
