package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import java.sql.Connection

internal const val MATCH_COUNTDOWN = 12_000L
internal const val COMPUTER_TICKETS = 3

// Fictional game handles; computer identity stays explicit in the public player record.
private val computerHandles = listOf("ChaiChamp", "NeonNinja", "LuckyMango", "PixelRaja", "DiceDiva", "MoonMaverick", "TurboTikka", "LotusLegend")
internal fun computerPlayer(roomId: String, index: Int): Player = Player("computer-$roomId-$index",
    computerHandles[Math.floorMod(roomId.hashCode() + index - 1, computerHandles.size)], computer = true, avatar = index)

internal fun coinOptions(humans: Int = 1) = RoomOptions(
    game = RoundSettings(mode = GameMode.ONLINE, ticketsPerPlayer = 6, manualClaims = true,
        prizes = CoinPool(2).prizes.map { it.prize }),
    capacity = 8, intervalSeconds = 5, computerPlayers = (4 - humans).coerceAtLeast(0), coinGame = true,
)

/** Lobby estimates may change as real players arrive. The started pool is immutable. */
internal fun RoomRecord.coinLobby(): RoomRecord {
    if (!options.coinGame || phase != RoomPhase.LOBBY) return this
    require(purchases.keys == members.map { it.id }.toSet() && purchases.values.all { it in 1..6 })
    val computers = (4 - members.size).coerceAtLeast(0)
    val pool = CoinPool(purchases.values.sum() + computers * COMPUTER_TICKETS)
    return copy(options = options.copy(computerPlayers = computers,
        game = options.game.copy(prizes = pool.prizes.map { it.prize })))
}

internal fun RoomRecord.startCoinRound(now: Long): RoomRecord {
    check(options.coinGame && phase == RoomPhase.LOBBY && members.isNotEmpty())
    val lobby = coinLobby()
    val computers = (1..lobby.options.computerPlayers).map { index ->
        computerPlayer(id, index)
    }
    val counts = purchases + computers.associate { it.id to COMPUTER_TICKETS }
    val pool = CoinPool(counts.values.sum())
    val settings = lobby.options.game.copy(prizes = pool.prizes.map { it.prize })
    val game = Round.create(members.map { Player(it.id, it.name, avatar = it.avatar) } + computers,
        settings, now = now, ticketCounts = counts).start()
    val nonce = secret()
    return lobby.copy(options = lobby.options.copy(game = settings), phase = RoomPhase.ACTIVE, locked = true,
        startsAt = null, coinPool = pool, round = game, nonce = nonce, drawCommitment = commitment(game, nonce),
        nextDrawAt = now + 5_000L)
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
    }
}
