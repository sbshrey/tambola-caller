package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.Round
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal const val PRESENCE_TIMEOUT = 45_000L
internal const val TOUCH_INTERVAL = 15_000L
internal const val ROOM_LIFETIME = 24 * 60 * 60 * 1000L
internal const val SESSION_LIFETIME = 7 * ROOM_LIFETIME
internal const val EVENT_LIMIT = 1_000L
private val secureRandom = SecureRandom()
internal fun secret(): String = ByteArray(32).also(secureRandom::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
internal fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
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
) {
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
            }) },
        nextDrawAt = nextDrawAt, expiresAt = expiresAt, serverTime = now,
    )
}
