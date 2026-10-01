package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.UUID

internal const val BINGO_INTERVAL = 8_000L
internal fun bingoCommitment(round: BingoRound, nonce: String) = digest("bingo-75-draw-v1\n${round.id}\n$nonce\n${round.draw.order.joinToString(",")}")

/** Persisted authoritative state. Its public projection is an explicit allow-list. */
@Serializable internal data class BingoRoomRecord(
    val id: String, val code: String, val hostId: String, val members: List<Member>,
    val purchases: Map<String, Int>, val createdAt: Long, val expiresAt: Long,
    val friendTable: Boolean = false, val startsAt: Long? = null, val nextDrawAt: Long? = null,
    val revision: Long = 0, val phase: RoomPhase = RoomPhase.LOBBY, val practiceSeats: Int = 0,
    val round: BingoRound? = null, val pool: BingoCoinPool? = null, val nonce: String? = null,
    val variant: GameVariant = GameVariant.BINGO_75,
    val realPlayersOnly: Boolean = false,
) {
    init {
        require(variant == GameVariant.BINGO_75 && code.matches(Regex("B-[A-Z2-9]{8}")))
        require(members.size <= 50 && members.map { it.id }.distinct().size == members.size)
        require(purchases.values.all { it in 1..6 } && practiceSeats in 0..49 && revision >= 0)
        require(createdAt >= 0 && expiresAt > createdAt)
        require(phase != RoomPhase.LOBBY || (round == null && pool == null && purchases.keys == members.map { it.id }.toSet()))
        require(phase !in setOf(RoomPhase.ACTIVE, RoomPhase.FINISHED) || (round != null && pool != null && nonce != null))
        require(round == null || (pool?.soldCards == round.cards.size && nonce != null))
    }
    fun population() = 30 + Math.floorMod(id.hashCode(), 21)
    private fun computerCounts(number: Int) = (1..number).associate { index ->
        practicePersona(id, index).id to 1 + (digest("$id:bingo-card-count:$index").take(8).toLong(16) % 6).toInt()
    }
    fun lobbyPlayers(): List<Player> {
        val count = if (friendTable || realPlayersOnly) 0 else (1 + practiceSeats.coerceAtMost(population() - 1) - members.size).coerceAtLeast(0)
        return members.map { Player(it.id, it.name, avatar = it.avatar) } + (1..count).map { practicePersona(id, it) }
    }
    fun lobbyCounts(): Map<String, Int> = purchases + computerCounts(lobbyPlayers().count { it.computer })
    fun start(now: Long): BingoRoomRecord {
        require(phase == RoomPhase.LOBBY && members.isNotEmpty() && (!friendTable && !realPlayersOnly || members.size >= 2))
        val filled = copy(practiceSeats = if (realPlayersOnly || friendTable) 0 else population() - 1)
        val players = filled.lobbyPlayers()
        val counts = filled.lobbyCounts()
        val generator = BingoCardGenerator(SecureRandom())
        val cards = players.flatMap { generator.deal(it.id, counts.getValue(it.id)) }
        val game = BingoRound(id = UUID.randomUUID().toString(), createdAt = now, players = players,
            cards = cards, draw = BingoDraw.shuffled(), winnersPerPattern = 2).start()
        return filled.copy(phase = RoomPhase.ACTIVE, startsAt = null, nextDrawAt = now + BINGO_INTERVAL,
            expiresAt = now + ROOM_LIFETIME, round = game, pool = BingoCoinPool(cards.size), nonce = secret())
    }
    fun tick(now: Long): BingoRoomRecord {
        if (phase == RoomPhase.CLOSED) return this
        if (expiresAt <= now) return copy(phase = RoomPhase.CLOSED, round = round?.cancel(), startsAt = null, nextDrawAt = null)
        if (phase == RoomPhase.LOBBY && !friendTable) {
            if (requireNotNull(startsAt) <= now) return if (now - startsAt > 30_000 || members.isEmpty() ||
                (realPlayersOnly && now - createdAt >= MATCH_WAIT_LIMIT))
                copy(phase = RoomPhase.CLOSED, startsAt = null) else if (!realPlayersOnly || members.size >= 2) start(now)
                else copy(startsAt = now + MATCH_COUNTDOWN)
            if (realPlayersOnly) return this
            val elapsed = (now - createdAt).coerceIn(0, 10_000)
            return copy(practiceSeats = ((population() - 1) * elapsed / 10_000).toInt())
        }
        if (phase == RoomPhase.ACTIVE && requireNotNull(nextDrawAt) <= now) {
            val game = requireNotNull(round).next().playComputers()
            return copy(round = game, phase = if (game.finished) RoomPhase.FINISHED else RoomPhase.ACTIVE,
                nextDrawAt = if (game.finished) null else now + BINGO_INTERVAL)
        }
        return this
    }
    fun view(actor: String, now: Long): BingoRoomView {
        val players = round?.players ?: lobbyPlayers()
        val counts = round?.cards?.groupingBy { it.playerId }?.eachCount() ?: lobbyCounts()
        val allocations = round?.let { requireNotNull(pool).allocations(it) }.orEmpty()
        return BingoRoomView(id, code, revision, phase, hostId, friendTable,
            members.map { MemberView(it.id, it.name, it.avatar, true, now - it.lastSeen < PRESENCE_TIMEOUT) },
            players, counts, startsAt, nextDrawAt, expiresAt, now, pool?.coins ?: counts.values.sum() * COIN_TICKET_PRICE,
            round?.let { game ->
                val own = game.cards.filter { it.playerId == actor }
                val ids = own.map { it.id }.toSet()
                PublicBingoRound(game.id, game.status, game.draw.called, own,
                    game.marks.filterKeys { it in ids }, game.claims, game.players, counts,
                    game.winnersPerPattern, requireNotNull(pool).prizes, bingoCommitment(game, requireNotNull(nonce)),
                    game.draw.order.takeIf { game.finished }, nonce.takeIf { game.finished },
                    if (game.finished) game.players.associate { player ->
                        val won = allocations.filter { it.playerId == player.id }
                        player.id to RoundWinnings(won.filter { it.pattern != null }.sumOf { it.coins }, 0,
                            won.filter { it.pattern == null }.sumOf { it.coins })
                    } else emptyMap())
            })
    }
    fun redact(player: String): BingoRoomRecord = copy(
        members = members.map { if (it.id == player) it.copy(name = "Deleted player", avatar = 0) else it },
        round = round?.let { game -> game.copy(players = game.players.map { if (it.id == player) it.copy(name = "Deleted player", avatar = 0) else it }) },
    )
}
