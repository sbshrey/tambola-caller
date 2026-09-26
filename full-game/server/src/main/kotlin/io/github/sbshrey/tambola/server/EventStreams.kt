package io.github.sbshrey.tambola.server

import java.util.concurrent.atomic.AtomicBoolean

/** Per-process live limits complement the shared, persisted per-profile admission rate. */
internal class EventStreams(private val maximum: Int = 512, private val perCredential: Int = 4) {
    init { require(maximum > 0 && perCredential in 1..maximum) }
    private val active = mutableMapOf<String, Int>()
    private var total = 0

    @Synchronized fun acquire(token: String): AutoCloseable {
        demand(token.matches(Regex("[A-Za-z0-9_-]{43}")), 401, "unauthorized", "A valid guest session is required.")
        val key = digest(token)
        val count = active[key] ?: 0
        demand(total < maximum && count < perCredential, 429, "rate_limited", "Too many connections. Try again shortly.")
        active[key] = count + 1
        total++
        val released = AtomicBoolean(false)
        return AutoCloseable {
            if (released.compareAndSet(false, true)) release(key)
        }
    }

    @Synchronized private fun release(key: String) {
        val count = checkNotNull(active[key])
        if (count == 1) active.remove(key) else active[key] = count - 1
        total--
    }
}
