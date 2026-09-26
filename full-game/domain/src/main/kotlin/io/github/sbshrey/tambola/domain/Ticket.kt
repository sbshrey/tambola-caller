package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Random

@Serializable
data class Ticket(val id: String, val playerId: String, val cells: List<Int>) {
    init {
        require(id.isNotBlank() && playerId.isNotBlank()) { "A ticket needs an owner and ID" }
        require(cells.size == 27) { "A ticket has 27 cells" }
        require(cells.all { it in 0..90 }) { "Invalid ticket number" }
        require(numbers.size == 15 && numbers.toSet().size == 15) { "A ticket needs 15 unique numbers" }
        require((0..2).all { row(it).size == 5 }) { "Each row needs five numbers" }
        (0..8).forEach { column ->
            val values = (0..2).map { cells[it * 9 + column] }.filter { it != 0 }
            require(values.size in 1..3 && values == values.sorted()) { "Invalid ticket column" }
            require(values.all { it in columnRange(column) }) { "Number in wrong column" }
        }
    }
    val numbers: List<Int> get() = cells.filter { it != 0 }
    fun row(index: Int): List<Int> = cells.subList(index * 9, index * 9 + 9).filter { it != 0 }
    val corners: List<Int> get() = listOf(row(0).first(), row(0).last(), row(2).first(), row(2).last())
    val fingerprint: String get() = cells.joinToString(",")
}

fun columnRange(column: Int): IntRange = when (column) {
    0 -> 1..9
    8 -> 80..90
    in 1..7 -> column * 10..column * 10 + 9
    else -> throw IllegalArgumentException("Column must be 0–8")
}

class TicketGenerator(private val random: Random = SecureRandom()) {
    fun generate(id: String, playerId: String): Ticket {
        // Cover all nine columns first, then fill each row to five. Each choice
        // has a bounded candidate set, so generation cannot spin in a retry loop.
        val occupied = Array(3) { BooleanArray(9) }
        val counts = IntArray(3)
        val columns = (0..8).toList().shuffledWith(random)
        columns.forEach { column ->
            val rows = (0..2).filter { counts[it] < 5 }
            val row = rows[random.nextInt(rows.size)]
            occupied[row][column] = true
            counts[row]++
        }
        (0..2).forEach { row ->
            (0..8).filter { !occupied[row][it] }.shuffledWith(random).take(5 - counts[row]).forEach { occupied[row][it] = true }
        }
        val cells = MutableList(27) { 0 }
        (0..8).forEach { column ->
            val rows = (0..2).filter { occupied[it][column] }
            val values = columnRange(column).toList().shuffledWith(random).take(rows.size).sorted()
            rows.forEachIndexed { index, row -> cells[row * 9 + column] = values[index] }
        }
        return Ticket(id, playerId, cells.toList())
    }

    fun deal(players: List<Player>, perPlayer: Int): List<Ticket> {
        require(players.isNotEmpty() && players.size <= 32 && perPlayer in 1..6)
        require(players.map { it.id }.distinct().size == players.size)
        val fingerprints = mutableSetOf<String>()
        return players.flatMap { player ->
            var tickets: List<Ticket>? = null
            repeat(100) {
                if (tickets == null) {
                    val candidate = strip(player.id).take(perPlayer)
                    if (candidate.none { it.fingerprint in fingerprints }) {
                        tickets = candidate
                        fingerprints.addAll(candidate.map { it.fingerprint })
                    }
                }
            }
            checkNotNull(tickets) { "Could not generate unique tickets" }
        }
    }

    /** A physical strip: six valid tickets partition 1..90, without shared numbers.
     * Smaller hands are a randomly ordered subset of a complete strip. Existing
     * saved tickets are deliberately not rewritten when the dealer changes.
     */
    private fun strip(playerId: String): List<Ticket> {
        // One number in every column of every ticket leaves 36 numbers to place.
        // A tiny integral flow assigns the extras (at most two per ticket/column).
        // Residual paths avoid rejection sampling and unlucky-seed dead ends.
        val source = 0
        val sink = 16
        val capacity = Array(17) { IntArray(17) }
        for (ticket in 0..5) {
            capacity[source][ticket + 1] = 6
            for (column in 0..8) capacity[ticket + 1][column + 7] = 2
        }
        for (column in 0..8) capacity[column + 7][sink] = columnRange(column).count() - 6
        val neighbors = Array(17) { from -> (0..sink).filter { to -> capacity[from][to] > 0 || capacity[to][from] > 0 }.shuffledWith(random) }
        var remaining = 36
        while (remaining > 0) {
            val parent = IntArray(17) { -1 }
            parent[source] = source
            val queue = ArrayDeque<Int>().apply { add(source) }
            while (queue.isNotEmpty() && parent[sink] == -1) {
                val from = queue.removeFirst()
                (if (from == source) neighbors[from].shuffledWith(random) else neighbors[from]).forEach { to ->
                    if (parent[to] == -1 && capacity[from][to] > 0) {
                        parent[to] = from
                        queue.add(to)
                    }
                }
            }
            check(parent[sink] != -1) { "Invalid strip allocation" }
            var to = sink
            while (to != source) {
                val from = parent[to]
                capacity[from][to]--
                capacity[to][from]++
                to = from
            }
            remaining--
        }
        val counts = Array(6) { ticket -> IntArray(9) { column -> 3 - capacity[ticket + 1][column + 7] } }
        val grids = Array(6) { MutableList(27) { 0 } }
        val occupied = Array(6) { Array(9) { emptyList<Int>() } }
        for (ticket in 0..5) {
            val room = IntArray(3) { 5 }
            // Largest columns first, filling rows with most space (random ties),
            // realizes the degree sequence without retrying row arrangements.
            (0..8).toList().shuffledWith(random).sortedByDescending { counts[ticket][it] }.forEach { column ->
                val rows = (0..2).toList().shuffledWith(random).sortedByDescending { room[it] }.take(counts[ticket][column]).sorted()
                rows.forEach { check(room[it] > 0); room[it]-- }
                occupied[ticket][column] = rows
            }
            check(room.all { it == 0 })
        }
        for (column in 0..8) {
            val values = columnRange(column).toList().shuffledWith(random)
            var offset = 0
            for (ticket in 0..5) {
                val rows = occupied[ticket][column]
                val numbers = values.subList(offset, offset + rows.size).sorted()
                rows.forEachIndexed { index, row -> grids[ticket][row * 9 + column] = numbers[index] }
                offset += rows.size
            }
        }
        return grids.toList().shuffledWith(random).mapIndexed { index, cells -> Ticket("$playerId-${index + 1}", playerId, cells.toList()) }
    }
}

internal fun <T> List<T>.shuffledWith(random: Random): List<T> = toMutableList().also { java.util.Collections.shuffle(it, random) }
