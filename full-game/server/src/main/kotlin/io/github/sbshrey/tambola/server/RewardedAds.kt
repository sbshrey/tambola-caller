package io.github.sbshrey.tambola.server

import com.google.crypto.tink.apps.rewardedads.RewardedAdsVerifier
import io.github.sbshrey.tambola.protocol.*
import java.net.URI
import java.net.URL
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection
import org.slf4j.LoggerFactory

private const val DAY = 86_400_000L
private const val DELIVERY_GRACE = DAY

/** Account configuration is deliberately absent until a real rewarded unit is activated. */
class RewardedAds private constructor(val adUnit: String, private val verify: (String) -> Unit) {
    private val lastRejectionLog = AtomicLong(0)
    private val lastConsoleLog = AtomicLong(0)

    /** Only fixed reason labels are recorded, never callback values, identifiers or signatures. */
    private fun reject(reason: String, status: Int = 400, code: String = "invalid_ad"): Nothing {
        val now = System.currentTimeMillis()
        val previous = lastRejectionLog.get()
        if (now - previous >= 5_000 && lastRejectionLog.compareAndSet(previous, now)) {
            LoggerFactory.getLogger(RewardedAds::class.java).warn("AdMob callback rejected: {}", reason)
        }
        fail(status, code, "Ad verification failed: $reason.")
    }

    internal fun prepare(connection: Connection, player: String, now: Long): RewardAdIntent {
        val day = now / DAY
        val pending = connection.query("""SELECT i.id, i.ad_unit, i.expires_at FROM reward_ad_intents i
            LEFT JOIN reward_ad_receipts r ON r.intent_id = i.id
            WHERE i.player_id = ? AND i.reward_day = ? AND r.intent_id IS NULL""", player, day) {
            RewardAdIntent(it.getString(1), it.getString(2), AD_REWARD_COINS, it.getLong(3))
        }.singleOrNull()
        if (pending != null) return pending // Failed loads reuse their slot; no coins are consumed.
        val used = connection.query("SELECT count(*) FROM reward_ad_intents WHERE player_id = ? AND reward_day = ?", player, day) { it.getInt(1) }.single()
        demand(used < AD_REWARDS_PER_DAY, 429, "ad_daily_limit", "All five ad rewards collected. Come back tomorrow.")
        val intent = RewardAdIntent(UUID.randomUUID().toString(), adUnit, AD_REWARD_COINS, (day + 1) * DAY)
        connection.execute("INSERT INTO reward_ad_intents VALUES (?, ?, ?, ?, ?, ?, ?)", intent.id, player, day, used + 1, adUnit, now, intent.expiresAt)
        return intent
    }

    internal fun status(connection: Connection, player: String, id: String): RewardAdStatus {
        demand(id.matches(Regex("[a-f0-9-]{36}")), 400, "invalid_ad", "Invalid ad reward.")
        val confirmed = connection.query("""SELECT r.intent_id IS NOT NULL FROM reward_ad_intents i
            LEFT JOIN reward_ad_receipts r ON r.intent_id = i.id WHERE i.id = ? AND i.player_id = ?""", id, player) { it.getBoolean(1) }.singleOrNull()
        demand(confirmed != null, 404, "ad_missing", "Ad reward not found.")
        return RewardAdStatus(confirmed!!, CoinLedger.view(connection, player))
    }

    /** Verify before opening a transaction. Never trust the Android onUserEarnedReward callback. */
    internal fun verified(query: String, now: Long): VerifiedAd? {
        if (query.length !in 1..4096) reject("query_length")
        val url = "https://ssv.invalid/?$query"
        val fields = try {
            // Tink verifies URI.getQuery(); parse that same representation, without a second decode.
            val parts = URI(url).query.split('&').map { it.split('=', limit = 2).also { pair -> require(pair.size == 2) } }
            require(parts.map { it[0] }.distinct().size == parts.size)
            require(parts.takeLast(2).map { it[0] } == listOf("signature", "key_id"))
            parts.associate { it[0] to it[1] }
        } catch (_: Exception) { reject("query_format") }
        try { verify(url) } catch (_: Exception) { reject("signature_or_keys", 503, "ad_unverified") }
        val timestamp = fields["timestamp"]?.toLongOrNull()
        val transaction = fields["transaction_id"].orEmpty()
        val intent = fields["custom_data"].orEmpty()
        // AdMob's Verify URL tool signs these fixed sample IDs, not the configured ad unit.
        // A signed console probe proves delivery only: it must never create a reward receipt.
        val consoleProbe = fields["ad_unit"] == "1234567890" && transaction == "123456789"
        // Keep all mismatches in one safe diagnostic, so console setup does not require one retry per field.
        val mismatches = buildList {
            if (!consoleProbe && fields["ad_unit"] != adUnit.substringAfter('/')) add(if (fields["ad_unit"] == adUnit) "ad_unit_full_id" else "ad_unit")
            if (fields["reward_amount"] != AD_REWARD_COINS.toString()) add("reward_amount")
            if (fields["reward_item"] != "coins") add("reward_item")
            if (!consoleProbe && !transaction.matches(Regex("[a-fA-F0-9]{16,128}"))) add("transaction_id")
            if (!consoleProbe && !intent.matches(Regex("[a-f0-9-]{36}"))) add("custom_data")
            if (timestamp == null || timestamp !in (now - 2 * DAY)..(now + 60_000)) {
                add(if (timestamp != null && timestamp / 1000 in (now - 2 * DAY)..(now + 60_000)) "timestamp_microseconds" else "timestamp")
            }
        }
        if (mismatches.isNotEmpty()) reject(mismatches.joinToString(","))
        if (consoleProbe) {
            val loggedAt = System.currentTimeMillis()
            val previous = lastConsoleLog.get()
            if (loggedAt - previous >= 5_000 && lastConsoleLog.compareAndSet(previous, loggedAt)) {
                LoggerFactory.getLogger(RewardedAds::class.java).info("AdMob signed console verification accepted; no reward granted")
            }
            return null
        }
        return VerifiedAd(intent, transaction, timestamp!!)
    }

    internal fun credit(connection: Connection, ad: VerifiedAd, now: Long) {
        val intent = connection.query("SELECT player_id, ad_unit, created_at, expires_at FROM reward_ad_intents WHERE id = ?", ad.intent) {
            StoredAd(it.getString(1), it.getString(2), it.getLong(3), it.getLong(4))
        }.singleOrNull() ?: return // Deleted profiles stay deleted, including delayed callbacks.
        val player = intent.player
        if (connection.query("SELECT id FROM guests WHERE id = ? FOR UPDATE", player) { true }.isEmpty()) return
        if (intent.adUnit != adUnit || ad.timestamp !in (intent.createdAt - 60_000) until intent.expiresAt ||
            now >= intent.expiresAt + DELIVERY_GRACE) reject("intent_expired", 400, "ad_expired")
        val inserted = connection.execute("INSERT INTO reward_ad_receipts VALUES (?, ?, ?) ON CONFLICT DO NOTHING", ad.transaction, ad.intent, now)
        if (inserted == 1) check(CoinLedger.credit(connection, player, "ad:${ad.intent}", AD_REWARD_COINS, now))
    }

    companion object {
        fun configured(unit: String?): RewardedAds? = unit?.takeIf(String::isNotBlank)?.let {
            require(it.matches(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")) && !it.startsWith("ca-app-pub-3940256099942544/"))
            val keys = GoogleRewardKeys()
            RewardedAds(it) { url -> keys.verifier(URI(url).query.substringAfterLast("key_id=")).verify(url) }
        }
        internal fun forTest(unit: String, verifier: (String) -> Unit) = RewardedAds(unit, verifier)
    }
}

private data class StoredAd(val player: String, val adUnit: String, val createdAt: Long, val expiresAt: Long)

internal data class VerifiedAd(val intent: String, val transaction: String, val timestamp: Long)

/** Bounded HTTPS fetches, six-hour rotation, and a one-minute refresh throttle. No callback URLs are fetched. */
private class GoogleRewardKeys {
    private var loadedAt = 0L
    private var attemptedAt = 0L
    private var json = ""
    private var cached: RewardedAdsVerifier? = null
    @Synchronized fun verifier(keyId: String): RewardedAdsVerifier {
        val now = System.currentTimeMillis()
        val known = keyId.matches(Regex("[0-9]{1,20}")) && Regex("\"keyId\"\\s*:\\s*$keyId\\b").containsMatchIn(json)
        if (cached == null || now - loadedAt >= 6 * 60 * 60_000L || !known) {
            check(now - attemptedAt >= 60_000) { "Key refresh pending" }
            attemptedAt = now
            val connection = URL(RewardedAdsVerifier.PUBLIC_KEYS_URL_PROD).openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 5_000; connection.readTimeout = 5_000; connection.instanceFollowRedirects = false
                check(connection.responseCode == 200)
                val bytes = connection.inputStream.use { it.readNBytes(65_537) }
                check(bytes.size <= 65_536)
                val content = bytes.toString(Charsets.UTF_8)
                cached = RewardedAdsVerifier.Builder().setVerifyingPublicKeys(content).build()
                json = content; loadedAt = now
            } finally { connection.disconnect() }
        }
        return requireNotNull(cached)
    }
}
