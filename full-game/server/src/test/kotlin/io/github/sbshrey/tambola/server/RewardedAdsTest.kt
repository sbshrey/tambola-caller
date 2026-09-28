package io.github.sbshrey.tambola.server

import com.google.crypto.tink.apps.rewardedads.RewardedAdsVerifier
import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RewardedAdsTest : PostgresTest() {
    private val unit = "ca-app-pub-1234567890123456/1234567890"
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val verifier = RewardedAdsVerifier.Builder().addVerifyingPublicKey(42, Base64.getEncoder().encodeToString(keys.public.encoded)).build()
    private fun setup() { service = RoomService(database, now::get, ads = RewardedAds.forTest(unit, verifier::verify)) }
    private fun guest() = service.register(GuestRequest("Ad player"), UUID.randomUUID().toString())
    private fun callback(intent: String, tx: String = UUID.randomUUID().toString().replace("-", ""),
        amount: Long = AD_REWARD_COINS, timestamp: Long = now.get(), adUnit: String = unit.substringAfter('/')): String {
        val signed = "ad_network=5450213213286189855&ad_unit=$adUnit&custom_data=$intent&reward_amount=$amount&reward_item=coins&timestamp=$timestamp&transaction_id=$tx"
        val signature = Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(signed.toByteArray()); sign() }
        return "$signed&signature=${Base64.getUrlEncoder().withoutPadding().encodeToString(signature)}&key_id=42"
    }

    @Test fun `real signatures credit once across concurrent callbacks restart and wallet refresh`() {
        setup(); val actor = guest(); val intent = service.prepareAd(actor.token)
        assertEquals(intent, service.prepareAd(actor.token))
        assertFalse(service.adStatus(actor.token, intent.id).confirmed)
        val query = callback(intent.id)
        val workers = Executors.newFixedThreadPool(4)
        try { workers.invokeAll(List(4) { Callable { service.verifyAd(query) } }).forEach { it.get(10, TimeUnit.SECONDS) } }
        finally { workers.shutdownNow() }
        setup(); service.verifyAd(query)
        val status = service.adStatus(actor.token, intent.id)
        assertTrue(status.confirmed); assertEquals(2500L, status.wallet.balance)
        assertEquals(404, assertThrows(ApiFailure::class.java) { service.adStatus(guest().token, intent.id) }.status)
        assertEquals(1L, database.transaction { it.query("SELECT count(*) FROM reward_ad_receipts") { row -> row.getLong(1) }.single() })
    }

    @Test fun `bad signatures mismatched units amounts dates duplicates and unissued intents cannot grant`() {
        setup(); val actor = guest(); val intent = service.prepareAd(actor.token)
        val query = callback(intent.id)
        listOf(query.replace("reward_amount=1000", "reward_amount=9000"), callback(intent.id, amount = 9999),
            callback(intent.id, adUnit = "9999999999"), callback(intent.id, timestamp = now.get() + 120_000),
            callback(intent.id, timestamp = now.get() - 2 * 86_400_000L - 1),
            query.replace("&signature=", "&reward_item=coins&signature="), query + "&user_id=other").forEach {
            assertThrows(ApiFailure::class.java) { service.verifyAd(it) }
        }
        service.verifyAd(callback(UUID.randomUUID().toString()))
        assertEquals(1500L, service.wallet(actor.token).balance)
        val tx = "abcdef0123456789"
        service.verifyAd(callback(intent.id, tx))
        val next = service.prepareAd(actor.token)
        service.verifyAd(callback(next.id, tx))
        assertFalse(service.adStatus(actor.token, next.id).confirmed)
        assertEquals(2500L, service.wallet(actor.token).balance)
    }

    @Test fun `five daily rewards retry failed loads and reset on the next UTC date`() {
        setup(); val actor = guest()
        repeat(5) {
            val intent = service.prepareAd(actor.token)
            assertEquals(intent, service.prepareAd(actor.token))
            service.verifyAd(callback(intent.id))
        }
        assertEquals("ad_daily_limit", assertThrows(ApiFailure::class.java) { service.prepareAd(actor.token) }.code)
        assertEquals(6500L, service.wallet(actor.token).balance)
        now.set((now.get() / 86_400_000L + 1) * 86_400_000L)
        val intent = service.prepareAd(actor.token); service.verifyAd(callback(intent.id))
        assertEquals(7500L, service.wallet(actor.token).balance)
    }

    @Test fun `callback diagnostics identify all signed mismatches without echoing callback data`() {
        setup(); val actor = guest(); val intent = service.prepareAd(actor.token)
        val query = callback("private-marker", tx = "private-transaction", amount = 7, timestamp = now.get() * 1000, adUnit = unit)
        val error = assertThrows(ApiFailure::class.java) { service.verifyAd(query) }
        assertEquals(400, error.status)
        assertEquals("invalid_ad", error.code)
        assertEquals("Ad verification failed: ad_unit_full_id,reward_amount,transaction_id,custom_data,timestamp_microseconds.", error.message)
        val tampered = assertThrows(ApiFailure::class.java) { service.verifyAd(query.replace("reward_amount=7", "reward_amount=8")) }
        assertEquals("ad_unverified", tampered.code)
        assertEquals("Ad verification failed: signature_or_keys.", tampered.message)
        assertFalse(service.adStatus(actor.token, intent.id).confirmed)
        assertEquals(1500L, service.wallet(actor.token).balance)
    }

    @Test fun `signed console probes acknowledge delivery without crediting or consuming real intents`() {
        setup(); val actor = guest(); val intent = service.prepareAd(actor.token)
        val probe = callback(intent.id, tx = "123456789", adUnit = "1234567890")
        repeat(2) { service.verifyAd(probe) }
        // User ID and Custom data are optional in the console; neither may turn a probe into a reward.
        service.verifyAd(callback("", tx = "123456789", adUnit = "1234567890"))
        assertFalse(service.adStatus(actor.token, intent.id).confirmed)
        assertEquals(intent, service.prepareAd(actor.token))
        assertEquals(1500L, service.wallet(actor.token).balance)
        assertEquals(0L, database.transaction { it.query("SELECT count(*) FROM reward_ad_receipts") { row -> row.getLong(1) }.single() })
        service.verifyAd(callback(intent.id))
        assertTrue(service.adStatus(actor.token, intent.id).confirmed)
        assertEquals(2500L, service.wallet(actor.token).balance)
    }

    @Test fun `console probe handling cannot bypass signature reward timestamp or real unit checks`() {
        setup(); val actor = guest(); val intent = service.prepareAd(actor.token)
        val probe = callback(intent.id, tx = "123456789", adUnit = "1234567890")
        listOf(
            probe.replace("reward_amount=1000", "reward_amount=2000"),
            callback(intent.id, tx = "123456789", adUnit = "1234567890", amount = 7),
            callback(intent.id, tx = "123456789", adUnit = "1234567890", timestamp = now.get() + 120_000),
            callback(intent.id, tx = "123456789", adUnit = "9999999999"),
            callback(intent.id, adUnit = "9999999999"),
        ).forEach { assertThrows(ApiFailure::class.java) { service.verifyAd(it) } }
        assertFalse(service.adStatus(actor.token, intent.id).confirmed)
        assertEquals(1500L, service.wallet(actor.token).balance)
    }

    @Test fun `deleted profiles and default disabled deployment cannot receive rewards`() {
        assertEquals("ads_disabled", assertThrows(ApiFailure::class.java) { service.prepareAd(guest().token) }.code)
        setup(); val actor = guest(); val intent = service.prepareAd(actor.token); val query = callback(intent.id)
        service.deleteProfile(actor.token, DeleteProfileRequest(UUID.randomUUID().toString()), "test")
        service.verifyAd(query)
        assertEquals(0L, database.transaction { it.query("SELECT count(*) FROM reward_ad_intents") { row -> row.getLong(1) }.single() })
    }
}
