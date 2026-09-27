package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.COIN_STARTER_BALANCE
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CoinLedgerTest : PostgresTest() {
    private fun guest() = service.register(GuestRequest("Coin QA"), UUID.randomUUID().toString())
    private fun spend(player: String, key: String, amount: Long) = database.transaction {
        CoinLedger.change(it, player, key, -amount, now.get())
        CoinLedger.view(it, player)
    }
    private fun entries(player: String) = database.transaction { connection ->
        connection.query("SELECT entry_key, amount FROM coin_ledger WHERE player_id = ? ORDER BY entry_key", player) { it.getString(1) to it.getLong(2) }
    }
    private fun failure(code: String, block: () -> Unit) {
        assertEquals(code, assertThrows(ApiFailure::class.java, block).code)
    }

    @Test fun `registration and concurrent wallet reads grant starter coins once and old profiles initialize lazily`() {
        val actor = guest()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val wallets = executor.invokeAll(List(4) { Callable { service.wallet(actor.token) } }).map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(wallets.all { it.balance == COIN_STARTER_BALANCE && it.revision == 1L })
            assertEquals(listOf("starter" to COIN_STARTER_BALANCE), entries(actor.playerId))
            // Simulate a guest retained from schema v3; ordinary reads upgrade it.
            database.transaction { it.execute("DELETE FROM coin_wallets WHERE player_id = ?", actor.playerId) }
            assertEquals(COIN_STARTER_BALANCE, service.wallet(actor.token).balance)
            assertEquals(1, entries(actor.playerId).size)
        } finally { executor.shutdownNow() }
    }

    @Test fun `simultaneous debits serialize across rooms and cannot overspend`() {
        val actor = guest(); val executor = Executors.newFixedThreadPool(3)
        try {
            val outcomes = executor.invokeAll((1..3).map { index -> Callable {
                try { spend(actor.playerId, "room:$index:purchase", 600); "paid" }
                catch (error: ApiFailure) { error.code }
            } }).map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(2, outcomes.count { it == "paid" })
            assertEquals(1, outcomes.count { it == "coins_low" })
            assertEquals(300L, service.wallet(actor.token).balance)
            assertEquals(3, entries(actor.playerId).size)
        } finally { executor.shutdownNow() }
    }

    @Test fun `retried purchase has one debit and changed amount cannot reuse its identity`() {
        val actor = guest(); val executor = Executors.newFixedThreadPool(4)
        try {
            val replies = executor.invokeAll(List(4) { Callable { spend(actor.playerId, "purchase:one", 600) } }).map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(replies.all { it.balance == 900L && it.revision == 2L })
            failure("id_reused") { spend(actor.playerId, "purchase:one", 500) }
            assertEquals(900L, service.wallet(actor.token).balance)
        } finally { executor.shutdownNow() }
    }

    @Test fun `a debit waiting on another transaction checks the newly committed balance`() {
        val actor = guest()
        val held = CountDownLatch(1); val release = CountDownLatch(1)
        val waiterPid = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit(Callable {
                database.transaction {
                    CoinLedger.change(it, actor.playerId, "purchase:held", -1200, now.get())
                    held.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
            })
            assertTrue(held.await(5, TimeUnit.SECONDS))
            val waiting = executor.submit(Callable {
                try {
                    database.transaction {
                        waiterPid.set(it.query("SELECT pg_backend_pid()") { row -> row.getInt(1) }.single())
                        CoinLedger.change(it, actor.playerId, "purchase:waiting", -600, now.get())
                    }
                    "paid"
                } catch (error: ApiFailure) { error.code }
            })
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var blocked = false
            while (!blocked && System.nanoTime() < deadline) {
                if (waiterPid.get() != 0) blocked = database.transaction {
                    it.query("SELECT cardinality(pg_blocking_pids(?)) > 0", waiterPid.get()) { row -> row.getBoolean(1) }.single()
                }
                if (!blocked) Thread.sleep(10)
            }
            assertTrue("The second debit must actually wait on the held wallet", blocked)
            release.countDown(); first.get(5, TimeUnit.SECONDS)
            assertEquals("coins_low", waiting.get(5, TimeUnit.SECONDS))
            assertEquals(300L, service.wallet(actor.token).balance)
            assertEquals(2, entries(actor.playerId).size)
        } finally { release.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS) }
    }

    @Test fun `aborted room transaction rolls back its debit and the same request can safely retry`() {
        val actor = guest()
        assertThrows(IllegalStateException::class.java) {
            database.transaction {
                CoinLedger.change(it, actor.playerId, "purchase:rollback", -600, now.get())
                error("Simulated room write failure")
            }
        }
        assertEquals(COIN_STARTER_BALANCE, service.wallet(actor.token).balance)
        assertEquals(900L, spend(actor.playerId, "purchase:rollback", 600).balance)
    }

    @Test fun `settlement is immutable idempotent and remains spendable after service restart`() {
        val actor = guest()
        database.transaction {
            assertTrue(CoinLedger.credit(it, actor.playerId, "round:one:prize:TOP_LINE:ticket:one", 240, now.get()))
            assertFalse(CoinLedger.credit(it, actor.playerId, "round:one:prize:TOP_LINE:ticket:one", 240, now.get()))
        }
        assertThrows(IllegalStateException::class.java) { database.transaction {
            CoinLedger.credit(it, actor.playerId, "round:one:prize:TOP_LINE:ticket:one", 241, now.get())
        } }
        service = RoomService(database, now::get)
        assertEquals(1740L, service.wallet(actor.token).balance)
        assertEquals(2L, service.wallet(actor.token).revision)
    }

    @Test fun `broke refill is bounded and retryable without duplicate grants`() {
        val actor = guest()
        failure("refill_not_needed") { service.refill(actor.token, RefillRequest(UUID.randomUUID().toString())) }
        spend(actor.playerId, "purchase:spent", COIN_STARTER_BALANCE)
        val request = RefillRequest(UUID.randomUUID().toString())
        val first = service.refill(actor.token, request)
        assertEquals(COIN_REFILL, first.balance)
        assertEquals(now.get() + COIN_REFILL_INTERVAL, first.refillAfter)
        assertEquals(first, service.refill(actor.token, request))
        spend(actor.playerId, "purchase:again", COIN_REFILL)
        failure("refill_wait") { service.refill(actor.token, RefillRequest(UUID.randomUUID().toString())) }
        assertEquals(0L, service.refill(actor.token, request).balance)
        now.addAndGet(COIN_REFILL_INTERVAL)
        assertEquals(COIN_REFILL, service.refill(actor.token, RefillRequest(UUID.randomUUID().toString())).balance)
        assertEquals(2, entries(actor.playerId).count { it.first.startsWith("refill:") })
    }

    @Test fun `profile deletion removes wallet history and delayed credits cannot recreate it`() {
        val actor = guest()
        spend(actor.playerId, "purchase:one", 600)
        service.deleteProfile(actor.token, DeleteProfileRequest(UUID.randomUUID().toString()), "coin-qa")
        assertTrue(entries(actor.playerId).isEmpty())
        database.transaction {
            assertFalse(CoinLedger.credit(it, actor.playerId, "round:one:refund", 600, now.get()))
            assertTrue(it.query("SELECT 1 FROM coin_wallets WHERE player_id = ?", actor.playerId) { true }.isEmpty())
        }
        failure("unauthorized") { service.wallet(actor.token) }
    }

    @Test fun `two rooms can credit the same wallets in opposite order without locking each other`() {
        val first = guest(); val second = guest()
        val barrier = java.util.concurrent.CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(listOf(first to second, second to first).mapIndexed { index, pair -> Callable {
                database.transaction {
                    CoinLedger.credit(it, pair.first.playerId, "room:$index:award", 100, now.get())
                    barrier.await(5, TimeUnit.SECONDS)
                    CoinLedger.credit(it, pair.second.playerId, "room:$index:award", 100, now.get())
                }
            } })
            results.forEach { assertTrue(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1700L, service.wallet(first.token).balance)
            assertEquals(1700L, service.wallet(second.token).balance)
        } finally { executor.shutdownNow() }
    }
}
