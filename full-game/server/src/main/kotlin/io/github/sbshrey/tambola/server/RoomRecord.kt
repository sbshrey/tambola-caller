@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

internal const val PRESENCE_TIMEOUT = 45_000L
internal const val TOUCH_INTERVAL = 15_000L
internal const val ROOM_LIFETIME = 24 * 60 * 60 * 1000L
internal const val SESSION_LIFETIME = 7 * ROOM_LIFETIME
internal const val EVENT_LIMIT = 1_000L
private val secureRandom = SecureRandom()
internal fun secret(): String = ByteArray(32).also(secureRandom::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
internal fun digest(value: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
internal fun roomCode(): String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".let { alphabet -> (1..8).map { alphabet[secureRandom.nextInt(alphabet.length)] }.joinToString("") }

/** Canonical commitment is SHA256 of UTF-8 `tambola-draw-v1\n<roundId>\n<nonce>\n<comma-separated order>`. */
internal fun commitment(round: Round, nonce: String): String = digest("tambola-draw-v1\n${round.id}\n$nonce\n${round.drawOrder.joinToString(",")}")

internal data class Guest(val id: String, val name: String, val avatar: Int)
internal data class Receipt(val hash: String, val response: String)
class ApiFailure(val status: Int, val code: String, override val message: String) : RuntimeException(message)
internal fun fail(status: Int, code: String, message: String): Nothing = throw ApiFailure(status, code, message)
internal fun demand(condition: Boolean, status: Int, code: String, message: String) { if (!condition) fail(status, code, message) }

@Serializable internal data class Member(
    val id: String, val name: String, val avatar: Int, val joinedAt: Long,
    val ready: Boolean = false, val connected: Boolean = true, val lastSeen: Long,
)

@Serializable internal data class RoomRecord(
    val id: String, val code: String, val hostId: String, val options: RoomOptions,
    val members: List<Member>, val expiresAt: Long, val revision: Long = 0,
    val phase: RoomPhase = RoomPhase.LOBBY, val locked: Boolean = false,
    val round: Round? = null, val nonce: String? = null, val drawCommitment: String? = null,
    val nextDrawAt: Long? = null,
    val computerClaimsAt: Map<String, Long> = emptyMap(),
    val purchases: Map<String, Int> = emptyMap(),
    val startsAt: Long? = null,
    val coinPool: CoinPool? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val friendTable: Boolean = false,
    // Internal only: old results and receipts stay immutable; a replay gets its own purchases.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val nextFriendCode: String? = null,
) {
    fun coinView(actor: String): CoinTableView? {
        if (!options.coinGame) return null
        val tickets = purchases.values.sum() + options.computerPlayers * COMPUTER_TICKETS
        if (coinPool == null && tickets < 2) return CoinTableView(tickets, tickets * COIN_TICKET_PRICE, emptyList(), purchases[actor] ?: 0, startsAt, friendTable = friendTable)
        val pool = coinPool ?: CoinPool(tickets)
        val allocations = round?.let(pool::allocations).orEmpty().filter { it.playerId == actor }
        return CoinTableView(pool.soldTickets, pool.coins, pool.prizes, purchases[actor] ?: 0, startsAt,
            allocations.filter { it.prize != null }.sumOf { it.coins }, allocations.filter { it.prize == null }.sumOf { it.coins }, friendTable)
    }
    fun view(actor: String, now: Long): RoomView = RoomView(
        code = code, roomId = id, revision = revision, phase = phase, hostId = hostId, locked = locked,
        options = options, members = members.map { MemberView(it.id, it.name, it.avatar, it.ready, it.connected && now - it.lastSeen < PRESENCE_TIMEOUT) },
        round = round?.let { game -> PublicRound(game.id, game.status, game.called,
            game.tickets.filter { it.playerId == actor }, game.awards, game.customAwards,
            game.players.associate { it.id to game.score(it.id) }, requireNotNull(drawCommitment),
            game.drawOrder.takeIf { game.finished }, nonce.takeIf { game.finished },
            game.players, game.players.flatMap { player ->
                val awarded = (game.awards.flatMap { it.ticketIds } + game.customAwards.flatMap { it.ticketIds }).toSet()
                game.tickets.filter { it.playerId == player.id }.mapIndexedNotNull { index, ticket ->
                    if (ticket.id in awarded) WinningTicket(ticket.id, player.id, index + 1) else null
                }
            }, game.ticketCounts) },
        nextDrawAt = nextDrawAt, expiresAt = expiresAt, serverTime = now,
        coins = coinView(actor),
    )
}
