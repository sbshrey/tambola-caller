package io.github.sbshrey.tambola.game.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

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
