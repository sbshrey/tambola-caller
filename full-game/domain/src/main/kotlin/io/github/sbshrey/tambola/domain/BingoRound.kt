package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Random
import java.util.UUID

@Serializable
data class BingoClaim(val playerId: String, val cardId: String, val pattern: BingoPattern, val drawCount: Int)

/** A category admits all ties on the draw that fills its quota. */
@Serializable
data class BingoRound(
    val version: Int = 1,
    val id: String,
    val createdAt: Long,
    val players: List<Player>,
    val cards: List<BingoCard>,
    val draw: BingoDraw,
    val status: RoundStatus = RoundStatus.READY,
    val marks: Map<String, Set<Int>> = emptyMap(),
    val claims: List<BingoClaim> = emptyList(),
    val winnersPerPattern: Int = 2,
) {
    init {
        require(version == 1 && id.isNotBlank() && createdAt >= 0)
        require(players.size in 1..50 && players.map { it.id }.distinct().size == players.size)
        require(cards.map { it.id }.distinct().size == cards.size)
        require(cards.all { card -> players.any { it.id == card.playerId } })
        require(players.all { p -> cards.count { it.playerId == p.id } in 1..6 })
        require(winnersPerPattern in 1..5)
        require(marks.all { (cardId, numbers) ->
            val card = cards.find { it.id == cardId }
            card != null && numbers.all { it in card.numbers && it in draw.called }
        })
        require(claims.map { it.playerId to it.pattern }.distinct().size == claims.size)
        require(claims.zipWithNext().all { (a, b) -> a.drawCount <= b.drawCount })
        claims.forEachIndexed { index, claim ->
            val card = cards.find { it.id == claim.cardId }
            require(card != null && card.playerId == claim.playerId && claim.drawCount in 1..draw.count)
            require(claim.pattern.isComplete(card, draw.order.take(claim.drawCount).toSet()))
            val preceding = claims.take(index).filter { it.pattern == claim.pattern }
            require(preceding.size < winnersPerPattern || preceding.last().drawCount == claim.drawCount)
        }
        require(status != RoundStatus.READY || (draw.count == 0 && marks.isEmpty() && claims.isEmpty()))
    }

    val finished: Boolean get() = status in setOf(RoundStatus.COMPLETED, RoundStatus.CANCELLED)
    fun start(): BingoRound = if (status in setOf(RoundStatus.READY, RoundStatus.PAUSED)) copy(status = RoundStatus.PLAYING) else this
    fun pause(): BingoRound = if (status == RoundStatus.PLAYING) copy(status = RoundStatus.PAUSED) else this
    fun cancel(): BingoRound = if (finished) this else copy(status = RoundStatus.CANCELLED)
    fun remaining(pattern: BingoPattern): Int = (winnersPerPattern - claims.count { it.pattern == pattern }).coerceAtLeast(0)
    fun closed(pattern: BingoPattern): Boolean {
        val awards = claims.filter { it.pattern == pattern }
        return finished || (awards.size >= winnersPerPattern && awards.last().drawCount < draw.count)
    }

    /** Called on the next timer tick, leaving a full final-call claim window. */
    fun next(): BingoRound {
        if (status != RoundStatus.PLAYING) return this
        if (draw.finished || BingoPattern.entries.all { remaining(it) == 0 }) return copy(status = RoundStatus.COMPLETED)
        return copy(draw = draw.next())
    }

    fun mark(playerId: String, cardId: String, number: Int): BingoRound {
        require(status == RoundStatus.PLAYING)
        val card = cards.single { it.id == cardId }
        require(card.playerId == playerId && number in card.numbers && number in draw.called)
        val current = marks[cardId].orEmpty()
        return copy(marks = marks + (cardId to if (number in current) current - number else current + number))
    }

    fun claim(playerId: String, cardId: String, pattern: BingoPattern): BingoRound {
        require(status == RoundStatus.PLAYING)
        val card = cards.single { it.id == cardId }
        require(card.playerId == playerId)
        if (claims.any { it.playerId == playerId && it.pattern == pattern }) return this
        require(!closed(pattern)) { "This prize is closed" }
        require(pattern.isComplete(card, draw.called.toSet(), marks[cardId].orEmpty())) { "Complete the pattern first" }
        return copy(claims = claims + BingoClaim(playerId, cardId, pattern, draw.count))
    }

    /** Practice points; online wallet credit is a separate server transaction. */
    fun points(playerId: String): Int = BingoPattern.entries.sumOf { pattern ->
        val winners = claims.filter { it.pattern == pattern }.map { it.playerId }.sorted()
        val position = winners.indexOf(playerId)
        val pool = if (pattern == BingoPattern.BLACKOUT) 40 else 20
        if (position < 0) 0 else pool / winners.size + if (position < pool % winners.size) 1 else 0
    }

    fun ranking(): List<Player> = players.sortedWith(compareByDescending<Player> { points(it.id) }.thenBy { it.name }.thenBy { it.id })

    /** Simulated opponents use the same mark and claim rules as a human. */
    fun playComputers(): BingoRound {
        if (status != RoundStatus.PLAYING) return this
        val called = draw.called.toSet()
        val computers = players.filter { it.computer }.map { it.id }.toSet()
        var next = copy(marks = marks + cards.filter { it.playerId in computers }
            .associate { it.id to it.numbers.filter { n -> n in called }.toSet() })
        for (player in players.filter { it.computer }) {
            for (card in cards.filter { it.playerId == player.id }) {
                for (pattern in BingoPattern.entries) {
                    if (!next.closed(pattern) && pattern.isComplete(card, called, next.marks[card.id].orEmpty()))
                        next = next.claim(player.id, card.id, pattern)
                }
            }
        }
        return next
    }

    companion object {
        fun practice(human: Player, cardCount: Int, now: Long, random: Random = SecureRandom()): BingoRound {
            require(!human.computer && cardCount in 1..6)
            val id = UUID.randomUUID().toString()
            val players = listOf(human) + (1 until (30 + random.nextInt(21))).map { practicePersona(id, it) }
            val generator = BingoCardGenerator(random)
            val cards = players.flatMap { generator.deal(it.id, if (it.id == human.id) cardCount else 1 + random.nextInt(6)) }
            return BingoRound(id = id, createdAt = now, players = players, cards = cards, draw = BingoDraw.shuffled(random))
        }
    }
}

object BingoRoundCodec {
    private val json = Json { encodeDefaults = true }
    fun encode(round: BingoRound): String = json.encodeToString(round)
    fun decode(payload: String): BingoRound {
        require(payload.length <= 1_000_000)
        return json.decodeFromString<BingoRound>(payload)
    }
}
