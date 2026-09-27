package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.COIN_STARTER_BALANCE
import io.github.sbshrey.tambola.domain.COIN_TICKET_PRICE
import io.github.sbshrey.tambola.protocol.WalletView
import java.sql.Connection

internal const val COIN_REFILL = 500L
internal const val COIN_REFILL_INTERVAL = 5 * 60_000L

/** Caller owns the transaction. Lock order: guest, room (if any), wallet.
 * Negative changes/refills serialize on the wallet row. Positive settlements
 * need only the FK key-share lock, compatible with a purchase's NO KEY UPDATE
 * lock, so two rooms paying the same players cannot reverse wallet lock order.
 */
internal object CoinLedger {
    fun open(connection: Connection, player: String, now: Long): WalletView {
        ensure(connection, player, now)
        return view(connection, player)
    }

    fun ensure(connection: Connection, player: String, now: Long) {
        val inserted = connection.execute("INSERT INTO coin_wallets (player_id) SELECT id FROM guests WHERE id = ? ON CONFLICT DO NOTHING", player)
        if (inserted == 1) credit(connection, player, "starter", COIN_STARTER_BALANCE, now)
    }

    fun view(connection: Connection, player: String): WalletView = connection.query("""
        SELECT coalesce(sum(l.amount), 0), count(l.entry_key), w.refill_after
        FROM coin_wallets w LEFT JOIN coin_ledger l ON l.player_id = w.player_id
        WHERE w.player_id = ? GROUP BY w.player_id, w.refill_after
    """.trimIndent(), player) { WalletView(it.getLong(1), it.getLong(2), it.getLong(3)) }.singleOrNull()
        ?: fail(401, "wallet_missing", "This wallet is no longer available.")

    fun change(connection: Connection, player: String, key: String, amount: Long, now: Long) {
        validate(key, amount, now)
        lock(connection, player)
        previous(connection, player, key)?.let {
            demand(it == amount, 409, "id_reused", "This coin operation already has a different amount.")
            return
        }
        val before = view(connection, player)
        demand(amount > 0 || before.balance >= -amount, 409, "coins_low", "Choose fewer tickets or collect free coins.")
        connection.execute("INSERT INTO coin_ledger VALUES (?, ?, ?, ?)", player, key, amount, now)
        // The room response reads its wallet after all room effects are reconciled.
        // No caller needs an intermediate snapshot of this individual ledger write.
    }

    /** Credit a still-existing wallet once. A deleted profile is never recreated. */
    fun credit(connection: Connection, player: String, key: String, amount: Long, now: Long): Boolean {
        validate(key, amount, now); require(amount > 0)
        val inserted = connection.execute("""INSERT INTO coin_ledger
            SELECT player_id, ?, ?, ? FROM coin_wallets WHERE player_id = ?
            ON CONFLICT (player_id, entry_key) DO NOTHING""", key, amount, now, player)
        if (inserted == 0) previous(connection, player, key)?.let {
            check(it == amount) { "A settled coin allocation cannot change" }
        }
        return inserted == 1
    }

    fun refill(connection: Connection, player: String, operationId: String, now: Long): WalletView {
        val key = "refill:$operationId"
        validate(key, COIN_REFILL, now)
        lock(connection, player)
        if (previous(connection, player, key) != null) return view(connection, player)
        val wallet = view(connection, player)
        demand(wallet.balance < COIN_TICKET_PRICE, 409, "refill_not_needed", "You already have coins for a ticket.")
        demand(now >= wallet.refillAfter, 429, "refill_wait", "Your next free refill is not ready yet.")
        credit(connection, player, key, COIN_REFILL, now)
        connection.execute("UPDATE coin_wallets SET refill_after = ? WHERE player_id = ?", Math.addExact(now, COIN_REFILL_INTERVAL), player)
        return view(connection, player)
    }

    private fun lock(connection: Connection, player: String) {
        demand(connection.query("SELECT player_id FROM coin_wallets WHERE player_id = ? FOR NO KEY UPDATE", player) { true }.isNotEmpty(),
            401, "wallet_missing", "This wallet is no longer available.")
    }
    private fun previous(connection: Connection, player: String, key: String): Long? = connection.query(
        "SELECT amount FROM coin_ledger WHERE player_id = ? AND entry_key = ?", player, key) { it.getLong(1) }.singleOrNull()
    private fun validate(key: String, amount: Long, now: Long) {
        require(key.matches(Regex("[A-Za-z0-9:_-]{1,200}")))
        require(amount != 0L && amount in -19_200L..19_200L && now >= 0)
    }
}
