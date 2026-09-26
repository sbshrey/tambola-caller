package io.github.sbshrey.tambola.client

/** Fresh HTTP server time, advanced by a monotonic clock rather than the phone's wall clock. */
data class ServerTime(val epochMillis: Long, val receivedNanos: Long) {
    fun currentTimeMillis(nowNanos: Long = System.nanoTime()): Long =
        epochMillis + ((nowNanos - receivedNanos) / 1_000_000).coerceAtLeast(0)
}
