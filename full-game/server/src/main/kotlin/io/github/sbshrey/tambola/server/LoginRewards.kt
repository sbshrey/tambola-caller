package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.LoginRewards
import java.sql.Connection

private const val DAY = 86_400_000L

/** Server dates and fixed ledger keys make repeated logins and lost responses harmless. */
internal fun collectLoginRewards(connection: Connection, player: String, now: Long): LoginRewards {
    CoinLedger.ensure(connection, player, now)
    demand(connection.query("SELECT player_id FROM coin_wallets WHERE player_id = ? FOR NO KEY UPDATE", player) { true }.isNotEmpty(),
        401, "wallet_missing", "This wallet is no longer available.")
    // The original starter remains in history. Both old and new profiles receive the same
    // total beta starting grant; previous spending/winnings are never overwritten.
    val betaBonus = COIN_BETA_BALANCE - COIN_STARTER_BALANCE
    CoinLedger.credit(connection, player, "beta:2026:expanded", betaBonus, now)
    val today = now / DAY
    val prefix = "daily:$today:"
    val current = connection.query("SELECT entry_key, amount FROM coin_ledger WHERE player_id = ? AND entry_key LIKE ?", player, "$prefix%") {
        it.getString(1).substringAfterLast(':').toInt() to it.getLong(2)
    }.singleOrNull()
    val rewardDay = current?.first ?: run {
        val yesterday = connection.query("SELECT entry_key FROM coin_ledger WHERE player_id = ? AND entry_key LIKE ?", player, "daily:${today - 1}:%") {
            it.getString(1).substringAfterLast(':').toInt()
        }.singleOrNull()
        if (yesterday == null || yesterday == 7) 1 else yesterday + 1
    }
    val amount = DAILY_COIN_REWARDS[rewardDay - 1]
    check(current == null || current.second == amount)
    if (current == null) CoinLedger.credit(connection, player, "$prefix$rewardDay", amount, now)
    return LoginRewards(CoinLedger.view(connection, player), rewardDay, amount, Math.multiplyExact(today + 1, DAY), betaBonus, current == null)
}
