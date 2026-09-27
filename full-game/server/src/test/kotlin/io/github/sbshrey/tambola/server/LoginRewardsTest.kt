package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LoginRewardsTest : PostgresTest() {
    private fun guest() = service.register(GuestRequest("Rewards player"), UUID.randomUUID().toString())

    @Test fun `existing spending is preserved and concurrent logins grant only once`() {
        val actor = guest()
        service.wallet(actor.token)
        database.transaction { CoinLedger.change(it, actor.playerId, "test:purchase", -600, now.get()) }
        val workers = Executors.newFixedThreadPool(4)
        try {
            val replies = workers.invokeAll(List(4) { Callable { service.loginRewards(actor.token) } }).map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(replies.all { it.wallet.balance == 49_900L && it.day == 1 && it.coins == 500L })
            assertEquals(1, replies.count { it.newlyCollected })
            assertEquals(1, replies.map { it.wallet.revision }.distinct().size)
            service = RoomService(database, now::get)
            assertEquals(replies.first().wallet, service.loginRewards(actor.token).wallet)
        } finally { workers.shutdownNow() }
    }

    @Test fun `seven rewards increase then repeat and a missed UTC day resets the streak`() {
        var actor = guest()
        val deviceKey = secret()
        service.enrollDevice(actor.token, EnrollDeviceRequest(deviceKey))
        var expected = COIN_BETA_BALANCE
        repeat(8) { index ->
            actor = service.renewSession(deviceKey, RenewSessionRequest(index.toLong(), secret()), "daily-test").credentials
            val day = index % 7 + 1
            val reply = service.loginRewards(actor.token)
            expected += DAILY_COIN_REWARDS[day - 1]
            assertEquals(day, reply.day)
            assertEquals(expected, reply.wallet.balance)
            assertFalse(service.loginRewards(actor.token).newlyCollected)
            now.set(reply.nextAt - 1)
            assertEquals(reply.wallet, service.loginRewards(actor.token).wallet)
            now.incrementAndGet()
        }
        now.addAndGet(86_400_000L)
        assertEquals(1, service.loginRewards(actor.token).day)
    }

    @Test fun `deleted or revoked sessions cannot mint login coins`() {
        val actor = guest()
        service.loginRewards(actor.token)
        service.deleteProfile(actor.token, DeleteProfileRequest(UUID.randomUUID().toString()), "test")
        assertEquals(401, assertThrows(ApiFailure::class.java) { service.loginRewards(actor.token) }.status)
        assertEquals(0L, database.transaction { it.query("SELECT count(*) FROM coin_ledger WHERE player_id = ?", actor.playerId) { row -> row.getLong(1) }.single() })
    }
}
