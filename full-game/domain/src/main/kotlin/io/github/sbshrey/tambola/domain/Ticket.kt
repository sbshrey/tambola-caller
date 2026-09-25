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
            (1..perPlayer).map { index ->
                var ticket: Ticket? = null
                repeat(100) {
                    if (ticket == null) {
                        val candidate = generate("${player.id}-$index", player.id)
                        if (fingerprints.add(candidate.fingerprint)) ticket = candidate
                    }
                }
                checkNotNull(ticket) { "Could not generate unique tickets" }
            }
        }
    }
}

internal fun <T> List<T>.shuffledWith(random: Random): List<T> = toMutableList().also { java.util.Collections.shuffle(it, random) }
