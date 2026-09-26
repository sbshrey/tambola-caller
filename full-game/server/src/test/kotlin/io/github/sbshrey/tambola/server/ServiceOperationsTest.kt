package io.github.sbshrey.tambola.server

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ServiceOperationsTest {
    @Test fun `worker readiness requires recent success and cleanup failures remain due`() {
        val clock = AtomicLong()
        val operations = ServiceOperations(true, clock = clock::get)
        assertFalse(operations.workerReady()); assertTrue(operations.cleanupDue())
        operations.step(WorkerStep.CLEANUP) {}; operations.workerSucceeded()
        assertTrue(operations.workerReady()); assertFalse(operations.cleanupDue())
        clock.set(31_000_000_000); assertFalse(operations.workerReady())
        operations.workerSucceeded(); assertTrue(operations.workerReady())
        operations.workerFailed(); assertFalse(operations.workerReady())
        clock.set(61_000_000_000); assertTrue(operations.cleanupDue())
        assertThrows(IllegalStateException::class.java) { operations.step(WorkerStep.CLEANUP) { error("fixture") } }
        assertTrue(operations.cleanupDue())
        operations.step(WorkerStep.CLEANUP) {}; operations.workerSucceeded()
        assertFalse(operations.cleanupDue()); assertTrue(operations.workerReady())
        assertTrue(ServiceOperations(false).workerReady())
    }

    @Test fun `metrics authorization needs a separate strong secret and never accepts guest-shaped missing credentials`() {
        val token = "m".repeat(43)
        assertThrows(IllegalArgumentException::class.java) { ServiceOperations(true, "short") }
        assertThrows(IllegalArgumentException::class.java) { ServiceOperations(true, " ".repeat(43)) }
        assertFalse(ServiceOperations(false).authorizes("Bearer $token"))
        val operations = ServiceOperations(false, token)
        assertFalse(operations.authorizes(null)); assertFalse(operations.authorizes(token))
        assertFalse(operations.authorizes("Bearer ${"n".repeat(43)}")); assertTrue(operations.authorizes("Bearer $token"))
    }

    @Test fun `histograms are cumulative concurrent and bounded independently of arbitrary paths`() {
        val clock = AtomicLong(250_000_000)
        val operations = ServiceOperations(false, clock = clock::get)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..4).map { pool.submit { repeat(1000) { operations.observe(metricRoute("/private-player-$it/token-$it")!!, 404, 0) } } }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
        val result = operations.render(PoolStats(1, 2, 0, 3), null)
        assertTrue(result.contains("tambola_http_duration_seconds_count{route=\"other\",status=\"4xx\"} 4000"))
        assertTrue(result.contains("tambola_http_duration_seconds_bucket{route=\"other\",status=\"4xx\",le=\"0.1\"} 0"))
        assertTrue(result.contains("tambola_http_duration_seconds_bucket{route=\"other\",status=\"4xx\",le=\"0.25\"} 4000"))
        assertTrue(result.contains("tambola_http_duration_seconds_sum{route=\"other\",status=\"4xx\"} 1000.0"))
        assertFalse(result.contains("private-player")); assertFalse(result.contains("token-"))
        assertEquals(MetricRoute.READ, metricRoute("/v1/rooms/SECRET"))
        assertEquals(MetricRoute.COMMAND, metricRoute("/v1/rooms/SECRET/commands"))
        assertNull(metricRoute("/v1/rooms/SECRET/events")); assertNull(metricRoute("/internal/metrics"))
        assertTrue(result.length < 110_000)
    }
}
