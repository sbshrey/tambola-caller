package io.github.sbshrey.tambola.server

import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AllocationWindowTest : PostgresTest() {
    private fun windows(lines: List<String>) = lines.filter { it.startsWith("COIN_ALLOCATION_WINDOW|") }

    @Test fun `committed windows include elapsed SQL and intervening time without SQL text`() {
        val lines = mutableListOf<String>()
        val before = System.currentTimeMillis()
        database.transaction { raw ->
            val connection = ProfiledCoinServer.instrument(raw, true, lines::add)
            connection.query("SELECT pg_advisory_xact_lock(749023809)") { true }
            connection.query("SELECT pg_sleep(0.03), 'private-marker-not-for-export'") { true }
            Thread.sleep(20)
            connection.commit()
        }
        val fields = windows(lines).single().split('|')
        assertEquals(8, fields.size)
        val startMs = fields[1].toLong(); val endMs = fields[2].toLong()
        val heldNs = fields[4].toLong() - fields[3].toLong()
        assertTrue(startMs >= before && endMs in startMs..System.currentTimeMillis())
        assertTrue(heldNs >= 50_000_000 && fields[5].toLong() >= 0 && fields[6].toLong() >= 0)
        val queryNs = fields[7].split(':').last().toLong()
        assertTrue(fields[7].startsWith("other:1:") && queryNs >= 30_000_000)
        assertTrue(heldNs - queryNs - fields[6].toLong() >= 20_000_000)
        assertTrue(lines.none { "private-marker" in it || "SELECT" in it })
    }

    @Test fun `failed SQL and rollback do not leak a window into later commits`() {
        val lines = mutableListOf<String>()
        database.transaction { raw ->
            val connection = ProfiledCoinServer.instrument(raw, true, lines::add)
            connection.query("SELECT pg_advisory_xact_lock(749023809)") { true }
            assertThrows(SQLException::class.java) { connection.query("SELECT 1 / 0") { true } }
            connection.rollback()
            connection.commit()
            assertTrue(lines.isEmpty())
            connection.query("SELECT pg_advisory_xact_lock(749023809)") { true }
            connection.query("SELECT 1") { true }
            connection.commit()
            connection.commit()
        }
        assertEquals(1, windows(lines).size)
        assertEquals(1, lines.count { it.startsWith("COIN_SQL_TIMING|") })
    }

    @Test fun `default instrumentation retains the old format without new windows`() {
        val lines = mutableListOf<String>()
        database.transaction { raw ->
            val connection = ProfiledCoinServer.instrument(raw, emit = lines::add)
            connection.query("SELECT pg_advisory_xact_lock(749023809)") { true }
            connection.query("SELECT 1") { true }
            connection.commit()
        }
        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("COIN_SQL_TIMING|"))
        assertTrue(windows(lines).isEmpty())
    }

    @Test fun `confirmed advisory lock waiting stays outside the acquired window`() {
        val executor = Executors.newFixedThreadPool(2)
        val held = CountDownLatch(1); val release = CountDownLatch(1)
        val waiterPid = AtomicInteger()
        val lines = mutableListOf<String>()
        try {
            val blocker = executor.submit(Callable { database.transaction { connection ->
                connection.query("SELECT pg_advisory_xact_lock(749023809)") { true }
                held.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            } })
            assertTrue(held.await(5, TimeUnit.SECONDS))
            val waiter = executor.submit(Callable { database.transaction { raw ->
                waiterPid.set(raw.query("SELECT pg_backend_pid()") { it.getInt(1) }.single())
                val connection = ProfiledCoinServer.instrument(raw, true, lines::add)
                connection.query("SELECT pg_advisory_xact_lock(749023809)") { true }
                connection.query("SELECT 1") { true }
                connection.commit()
            } })
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var blocked = false
            while (!blocked && System.nanoTime() < deadline) {
                if (waiterPid.get() > 0) blocked = database.transaction { connection ->
                    connection.query("SELECT cardinality(pg_blocking_pids(?)) > 0", waiterPid.get()) { it.getBoolean(1) }.single()
                }
                if (!blocked) Thread.sleep(5)
            }
            assertTrue("PostgreSQL must confirm an actual blocked waiter", blocked)
            Thread.sleep(40)
            val releasedAt = System.currentTimeMillis()
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS); waiter.get(5, TimeUnit.SECONDS)
            val fields = windows(lines).single().split('|')
            assertTrue(fields[5].toLong() >= 40_000_000)
            assertTrue(fields[1].toLong() >= releasedAt)
        } finally { release.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS) }
    }
}
