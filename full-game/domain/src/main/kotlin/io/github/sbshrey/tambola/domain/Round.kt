package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Random
import java.util.UUID

@Serializable enum class GameMode { PRACTICE, FAMILY }
@Serializable enum class RoundStatus { READY, PLAYING, PAUSED, COMPLETED, CANCELLED }
@Serializable data class Player(val id: String, val name: String, val computer: Boolean = false) {
    init { require(id.isNotBlank() && name.isNotBlank() && name.length <= 40) }
}
@Serializable enum class Prize(val title: String, val points: Int, val explanation: String) {
    EARLY_FIVE("Early five", 10, "Any five called numbers on one ticket."),
    EARLY_TEN("Early ten", 20, "Any ten called numbers on one ticket."),
    TOP_LINE("Top line", 15, "All five numbers in the top row."),
    MIDDLE_LINE("Middle line", 15, "All five numbers in the middle row."),
    BOTTOM_LINE("Bottom line", 15, "All five numbers in the bottom row."),
    CORNERS("Four corners", 15, "Leftmost and rightmost numbers in the top and bottom rows."),
    FULL_HOUSE("Full house", 100, "All fifteen numbers on one ticket."),
    HOUSE_ONE("House one", 100, "The first group of tickets to complete all fifteen numbers."),
    HOUSE_TWO("House two", 75, "The next group of newly completed tickets after House one."),
    HOUSE_THREE("House three", 50, "The next group of newly completed tickets after House two.");

    fun matches(ticket: Ticket, called: Set<Int>): Boolean = when (this) {
        EARLY_FIVE -> ticket.numbers.count { it in called } >= 5
        EARLY_TEN -> ticket.numbers.count { it in called } >= 10
        TOP_LINE -> ticket.row(0).all { it in called }
        MIDDLE_LINE -> ticket.row(1).all { it in called }
        BOTTOM_LINE -> ticket.row(2).all { it in called }
        CORNERS -> ticket.corners.all { it in called }
        else -> ticket.numbers.all { it in called }
    }
    val isRankedHouse: Boolean get() = this in setOf(HOUSE_ONE, HOUSE_TWO, HOUSE_THREE)
    companion object {
        val defaults = listOf(EARLY_FIVE, TOP_LINE, MIDDLE_LINE, BOTTOM_LINE, FULL_HOUSE)
    }
}

@Serializable
data class RoundSettings(
    val mode: GameMode = GameMode.PRACTICE,
    val ticketsPerPlayer: Int = 1,
    val assistedMarking: Boolean = false,
    val prizes: List<Prize> = Prize.defaults,
    val playAllNumbers: Boolean = false,
) {
    init {
        require(ticketsPerPlayer in 1..6)
        require(prizes.isNotEmpty() && prizes.distinct().size == prizes.size)
        require(!(Prize.FULL_HOUSE in prizes && prizes.any { it.isRankedHouse }))
        require(Prize.HOUSE_TWO !in prizes || Prize.HOUSE_ONE in prizes)
        require(Prize.HOUSE_THREE !in prizes || Prize.HOUSE_TWO in prizes)
    }
}

@Serializable
data class Award(val prize: Prize, val drawIndex: Int, val ticketIds: List<String>, val playerIds: List<String>)

@Serializable
data class Round(
    val version: Int = 1,
    val id: String,
    val createdAt: Long,
    val settings: RoundSettings,
    val players: List<Player>,
    val tickets: List<Ticket>,
    val drawOrder: List<Int>,
    val called: List<Int> = emptyList(),
    val marks: Map<String, Set<Int>> = emptyMap(),
    val awards: List<Award> = emptyList(),
    val status: RoundStatus = RoundStatus.READY,
) {
    val latest: Int? get() = called.lastOrNull()
    val finished: Boolean get() = status == RoundStatus.COMPLETED || status == RoundStatus.CANCELLED
    fun score(playerId: String): Int = awards.filter { playerId in it.playerIds }.sumOf { it.prize.points }
    fun start(): Round = when (status) {
        RoundStatus.READY, RoundStatus.PAUSED -> copy(status = RoundStatus.PLAYING)
        else -> this
    }
    fun pause(): Round = if (status == RoundStatus.PLAYING) copy(status = RoundStatus.PAUSED) else this
    fun cancel(): Round = if (finished) this else copy(status = RoundStatus.CANCELLED)

    fun draw(): Round {
        if (status != RoundStatus.PLAYING || called.size == 90) return this
        val next = called + drawOrder[called.size]
        val nextAwards = awardsFor(next)
        val done = next.size == 90 || (!settings.playAllNumbers && terminalAward(nextAwards))
        val nextMarks = marks.toMutableMap()
        tickets.filter { settings.assistedMarking || players.first { p -> p.id == it.playerId }.computer }.forEach { ticket ->
            nextMarks[ticket.id] = ticket.numbers.filter { it in next }.toSet()
        }
        return copy(called = next, awards = nextAwards, marks = nextMarks.toMap(), status = if (done) RoundStatus.COMPLETED else status)
    }

    fun toggleMark(ticketId: String, number: Int): Round {
        require(status != RoundStatus.READY && !finished) { "This round is not active" }
        val ticket = tickets.firstOrNull { it.id == ticketId } ?: error("Unknown ticket")
        require(number in ticket.numbers && number in called) { "Only called numbers can be marked" }
        require(!settings.assistedMarking && !players.first { it.id == ticket.playerId }.computer) { "This ticket is automatically marked" }
        val marked = marks[ticketId].orEmpty()
        return copy(marks = marks + (ticketId to if (number in marked) marked - number else marked + number))
    }

    fun undo(): Round {
        require(settings.mode == GameMode.PRACTICE) { "Undo is available in practice only" }
        if (called.isEmpty() || status == RoundStatus.CANCELLED) return this
        val next = called.dropLast(1)
        return copy(called = next, awards = awards.filter { it.drawIndex <= next.size },
            marks = marks.mapValues { (_, values) -> values.intersect(next.toSet()) }, status = RoundStatus.PAUSED)
    }

    private fun terminalAward(results: List<Award>): Boolean {
        val finalPrize = settings.prizes.filter { it.isRankedHouse }.maxByOrNull { it.ordinal }
            ?: settings.prizes.firstOrNull { it == Prize.FULL_HOUSE }
        return if (finalPrize != null) results.any { it.prize == finalPrize } else results.size == settings.prizes.size
    }

    private fun awardsFor(next: List<Int>): List<Award> {
        val calledSet = next.toSet()
        val won = awards.map { it.prize }.toSet()
        val rankedWinners = awards.filter { it.prize.isRankedHouse }.flatMap { it.ticketIds }.toSet()
        val nextRank = settings.prizes.filter { it.isRankedHouse && it !in won }.minByOrNull { it.ordinal }
        return awards + settings.prizes.filter { it !in won }.mapNotNull { prize ->
            if (prize.isRankedHouse && prize != nextRank) return@mapNotNull null
            val matching = tickets.filter { prize.matches(it, calledSet) && (!prize.isRankedHouse || it.id !in rankedWinners) }
            if (matching.isEmpty()) null else Award(prize, next.size, matching.map { it.id }, matching.map { it.playerId }.distinct())
        }
    }

    /** Validate untrusted persistence at the boundary, including awards by replay. */
    fun validated(): Round {
        require(version == 1 && id.isNotBlank() && createdAt >= 0)
        require(players.size in 1..32 && players.map { it.id }.distinct().size == players.size)
        require(settings.mode != GameMode.FAMILY || (players.size in 2..8 && players.none { it.computer }))
        require(tickets.size == players.size * settings.ticketsPerPlayer)
        require(tickets.map { it.id }.distinct().size == tickets.size && tickets.map { it.fingerprint }.distinct().size == tickets.size)
        require(tickets.all { t -> players.any { it.id == t.playerId } })
        require(players.all { p -> tickets.count { it.playerId == p.id } == settings.ticketsPerPlayer })
        require(drawOrder.size == 90 && drawOrder.toSet() == (1..90).toSet())
        require(called.size <= 90 && called == drawOrder.take(called.size))
        require(marks.all { (id, values) -> tickets.any { it.id == id && it.numbers.containsAll(values) } && called.containsAll(values) })
        require(status != RoundStatus.READY || called.isEmpty())
        var replay = copy(called = emptyList(), awards = emptyList(), marks = emptyMap(), status = RoundStatus.PLAYING)
        repeat(called.size) {
            require(!replay.finished) { "Saved round contains draws after completion" }
            replay = replay.draw()
        }
        require(replay.awards == awards) { "Saved awards disagree with ticket rules" }
        require(status != RoundStatus.COMPLETED || replay.status == RoundStatus.COMPLETED)
        require(replay.status != RoundStatus.COMPLETED || finished)
        return this
    }

    companion object {
        fun create(players: List<Player>, settings: RoundSettings = RoundSettings(), random: Random = SecureRandom(), now: Long = System.currentTimeMillis()): Round =
            Round(id = UUID.randomUUID().toString(), createdAt = now, settings = settings, players = players.toList(),
                tickets = TicketGenerator(random).deal(players, settings.ticketsPerPlayer), drawOrder = (1..90).toList().shuffledWith(random)).validated()
    }
}

object RoundCodec {
    private val json = Json { encodeDefaults = true }
    fun encode(round: Round): String = json.encodeToString(round)
    fun decode(value: String): Round {
        require(value.length <= 1_000_000) { "Saved round is too large" }
        return json.decodeFromString<Round>(value).validated()
    }
}
