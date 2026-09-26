package io.github.sbshrey.tambola.server

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EventStreamsTest {
    @Test fun `credential and total limits reject excess connections and released slots can be reused`() {
        val streams = EventStreams(maximum = 3, perCredential = 2)
        val first = streams.acquire("a".repeat(43)); val second = streams.acquire("a".repeat(43))
        assertEquals(429, assertThrows(ApiFailure::class.java) { streams.acquire("a".repeat(43)) }.status)
        val third = streams.acquire("b".repeat(43))
        assertEquals(429, assertThrows(ApiFailure::class.java) { streams.acquire("c".repeat(43)) }.status)
        first.close(); first.close()
        streams.acquire("c".repeat(43)).use { }
        second.close(); third.close()
        repeat(1000) { streams.acquire(secret()).use { } }
        assertEquals(401, assertThrows(ApiFailure::class.java) { streams.acquire("invalid") }.status)
    }

    @Test fun `simultaneous acquisitions cannot exceed limits and concurrent release is idempotent`() {
        val streams = EventStreams(maximum = 4, perCredential = 4)
        val pool = Executors.newFixedThreadPool(12)
        try {
            val start = CountDownLatch(1)
            val results = (1..12).map { pool.submit(Callable {
                check(start.await(5, TimeUnit.SECONDS))
                try { streams.acquire("a".repeat(43)) } catch (failure: ApiFailure) { assertEquals(429, failure.status); null }
            }) }
            start.countDown()
            val admitted = results.mapNotNull { it.get(5, TimeUnit.SECONDS) }
            assertEquals(4, admitted.size)
            (admitted + admitted + admitted).map { value -> pool.submit { value.close() } }.forEach { it.get(5, TimeUnit.SECONDS) }
            val fresh = (1..4).map { streams.acquire("a".repeat(43)) }
            assertEquals(429, assertThrows(ApiFailure::class.java) { streams.acquire("a".repeat(43)) }.status)
            fresh.forEach { it.close() }
        } finally { pool.shutdownNow() }
    }
}
