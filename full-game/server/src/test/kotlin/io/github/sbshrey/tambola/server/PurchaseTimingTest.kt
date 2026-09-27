package io.github.sbshrey.tambola.server

import jdk.jfr.Recording
import jdk.jfr.consumer.RecordingFile
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PurchaseTimingTest {
    @Test fun `timing is disabled by default and does not change returned values`() {
        Recording().use { recording ->
            recording.start()
            assertNull(PurchaseTiming.start(false))
            assertEquals(42, PurchaseTiming.pool { PurchaseTiming.allocation { 42 } })
        }
    }

    @Test fun `recorded fields partition route time and identify nested waits without request data`() {
        val events = recorded {
            var now = 100L
            val timing = requireNotNull(PurchaseTiming.start(false) { now })
            now += 5; timing.admitted(); now += 7
            assertEquals("result", timing.service {
                now += 11
                PurchaseTiming.pool { now += 13 }
                PurchaseTiming.allocation { now += 17 }
                now += 19
                "result"
            })
            now += 23; timing.finish(true)
        }
        val event = events.single()
        assertEquals(5L, event.getLong("admissionNanos"))
        assertEquals(7L, event.getLong("dispatchNanos"))
        assertEquals(60L, event.getLong("serviceNanos"))
        assertEquals(13L, event.getLong("poolNanos"))
        assertEquals(17L, event.getLong("allocationNanos"))
        assertEquals(23L, event.getLong("remainderNanos"))
        assertEquals(95L, event.getLong("totalNanos"))
        assertEquals(1, event.getInt("poolAcquisitions"))
        assertTrue(event.getBoolean("success"))
        assertFalse(event.getBoolean("friendTable"))
        assertEquals(setOf("friendTable", "success", "admissionNanos", "dispatchNanos", "serviceNanos", "poolNanos", "poolAcquisitions", "allocationNanos", "remainderNanos", "totalNanos"),
            event.fields.map { it.name }.toSet() - setOf("startTime", "duration", "eventThread", "stackTrace"))
    }

    @Test fun `failed service removes thread context and cancelled admission is accounted for`() {
        val events = recorded {
            var now = 0L
            val failure = IllegalStateException("test exception must not be recorded")
            val timing = requireNotNull(PurchaseTiming.start(true) { now })
            timing.admitted()
            try {
                timing.service { PurchaseTiming.pool { now += 7; throw failure } }
                fail("Expected service failure")
            } catch (error: IllegalStateException) { assertSame(failure, error) }
            // Work on the reused thread after failure must not be added to that request.
            PurchaseTiming.pool { now += 11 }
            timing.finish(false)
            val cancelled = requireNotNull(PurchaseTiming.start(false) { now })
            now += 9; cancelled.finish(false)
        }
        val failed = events.single { it.getBoolean("friendTable") }
        assertFalse(failed.getBoolean("success"))
        assertEquals(7L, failed.getLong("poolNanos"))
        assertEquals(1, failed.getInt("poolAcquisitions"))
        assertEquals(7L, failed.getLong("serviceNanos"))
        val cancelled = events.single { !it.getBoolean("friendTable") }
        assertEquals(9L, cancelled.getLong("admissionNanos"))
        assertEquals(9L, cancelled.getLong("totalNanos"))
        assertEquals(0L, cancelled.getLong("dispatchNanos"))
        assertEquals(0L, cancelled.getLong("remainderNanos"))
    }

    private fun recorded(block: () -> Unit) = Recording().use { recording ->
        recording.enable(PurchaseTiming::class.java).withoutStackTrace()
        recording.start()
        block()
        recording.stop()
        val path = Files.createTempFile("tambola-purchase-timing-", ".jfr")
        try { recording.dump(path); RecordingFile.readAllEvents(path).filter { it.eventType.name == "tambola.PurchaseTiming" } }
        finally { Files.deleteIfExists(path) }
    }
}
