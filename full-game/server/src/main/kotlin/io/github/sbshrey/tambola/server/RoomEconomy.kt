package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import java.sql.Connection

internal const val MATCH_COUNTDOWN = 12_000L
internal const val FRIEND_LOBBY_LIFETIME = 15 * 60_000L
internal const val COMPUTER_TICKETS = 3

/** Stable across restarts, unrelated to the draw order or anyone's chance of winning. */
internal fun RoomRecord.matchPopulation(): Int = 30 + Math.floorMod(id.hashCode(), 21)

/** Stable per-seat purchases, chosen independently of the hidden draw and player tickets. */
internal fun RoomRecord.computerTicketCounts(count: Int = options.computerPlayers): Map<String, Int> =
    (1..count).associate { index ->
        val tickets = if (options.largeMatch) 1 + (digest("$id:ticket-count:$index").take(8).toLong(16) % 6).toInt()
            else COMPUTER_TICKETS
        computerPlayer(id, index).id to tickets
    }

internal fun RoomRecord.progressiveSeats(now: Long): Int = if (options.largeMatch) {
    val elapsed = (now - (requireNotNull(startsAt) - MATCH_COUNTDOWN)).coerceIn(0L, 10_000L)
    ((matchPopulation() - 1) * elapsed / 10_000L).toInt()
} else ((now - (requireNotNull(startsAt) - MATCH_COUNTDOWN)) / 3_000L).toInt().coerceIn(0, 3)

// Fictional game handles; computer identity stays explicit in the public player record.
internal fun computerPlayer(roomId: String, index: Int): Player = practicePersona(roomId, index)

internal fun coinOptions(humans: Int = 1, rulesVersion: Int = 1) = RoomOptions(
    game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true,
        prizes = CoinPool(2, rulesVersion).prizes.map { it.prize }),
    capacity = if (rulesVersion == 2) 50 else 8, intervalSeconds = if (rulesVersion == 2) 10 else 5,
    computerPlayers = (4 - humans).coerceAtLeast(0), coinGame = true, coinRulesVersion = rulesVersion,
)

/** Lobby estimates may change as real players arrive. The started pool is immutable. */
internal fun RoomRecord.coinLobby(): RoomRecord {
    if (!options.coinGame || phase != RoomPhase.LOBBY) return this
    require(purchases.keys == members.map { it.id }.toSet() && purchases.values.all { it in 1..6 })
    val computers = when {
        friendTable -> 0
        options.largeMatch -> (1 + practiceSeats.coerceAtMost(matchPopulation() - 1) - members.size).coerceAtLeast(0)
        else -> (4 - members.size).coerceAtLeast(0).coerceAtMost(if (options.powersEnabled) practiceSeats else 3)
    }
    val tickets = purchases.values.sum() + computerTicketCounts(computers).values.sum()
    val prizes = CoinPool(tickets.coerceAtLeast(2), options.coinRulesVersion).prizes.map { it.prize }
    val players = (members.size + computers).coerceAtLeast(1)
    val winners = if (options.coinRulesVersion == 1) 1 else ((players + 9) / 10).coerceAtLeast(2).coerceAtMost(players)
    return copy(options = options.copy(computerPlayers = computers,
        game = options.game.copy(prizes = prizes, winnersPerPrize = winners)))
}

internal fun RoomRecord.startCoinRound(now: Long): RoomRecord {
    check(options.coinGame && phase == RoomPhase.LOBBY && members.isNotEmpty() && (!friendTable || members.size >= 2))
    val lobby = copy(practiceSeats = if (options.largeMatch) matchPopulation() - 1 else 3).coinLobby()
    val computers = (1..lobby.options.computerPlayers).map { index ->
        computerPlayer(id, index)
    }
    val counts = purchases + lobby.computerTicketCounts()
    val pool = CoinPool(counts.values.sum(), options.coinRulesVersion)
    val settings = lobby.options.game.copy(prizes = pool.prizes.map { it.prize })
    val game = Round.create(members.map { Player(it.id, it.name, avatar = it.avatar) } + computers,
        settings, now = now, ticketCounts = counts).start()
    val nonce = secret()
    return lobby.copy(options = lobby.options.copy(game = settings), phase = RoomPhase.ACTIVE, locked = true,
        expiresAt = now + ROOM_LIFETIME,
        startsAt = null, coinPool = pool, round = game, nonce = nonce, drawCommitment = commitment(game, nonce),
        nextDrawAt = now + options.intervalSeconds * 1_000L)
}

internal object RoomEconomy {
    /** Called under the room lock, in the same transaction as its snapshot/receipt. */
    fun reconcile(connection: Connection, before: RoomRecord, after: RoomRecord, now: Long) {
        if (!before.options.coinGame || before.phase != RoomPhase.LOBBY) return
        val bought = if (after.phase == RoomPhase.CLOSED) emptyMap() else after.purchases
        (before.purchases.keys + bought.keys).sorted().forEach { player ->
            val delta = ((before.purchases[player] ?: 0) - (bought[player] ?: 0)) * COIN_TICKET_PRICE
            val key = "room:${after.id}:purchase:${after.revision}:$player"
            if (delta < 0) CoinLedger.change(connection, player, key, delta, now)
            else if (delta > 0) CoinLedger.credit(connection, player, key, delta, now)
        }
    }

    fun settle(connection: Connection, room: RoomRecord, now: Long) {
        val pool = room.coinPool ?: return
        val round = requireNotNull(room.round)
        pool.allocations(round).forEach { allocation ->
            if (round.players.any { it.id == allocation.playerId && !it.computer }) {
                CoinLedger.credit(connection, allocation.playerId, "round:${round.id}:${allocation.key}", allocation.coins, now)
            }
        }
        room.powerUps.forEach { (player, powerUp) ->
            if (round.players.any { it.id == player && !it.computer }) {
                val bonus = pool.powerUpBonus(round, player, powerUp)
                if (bonus > 0) CoinLedger.credit(connection, player, "round:${round.id}:powerup", bonus, now)
            }
        }
        room.matchPowers.forEach { (player, powers) ->
            if (round.players.any { it.id == player && !it.computer }) {
                val bonus = matchPowerBonus(powers, round.awards, pool.prizes, round.status == RoundStatus.COMPLETED)
                if (bonus > 0) CoinLedger.credit(connection, player, "round:${round.id}:earned-power", bonus, now)
            }
        }
    }
}
