package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Random

/** Row-major 75-ball card. Zero is exclusively the free centre. */
@Serializable
data class BingoCard(val id: String, val playerId: String, val cells: List<Int>) {
    init {
        require(id.isNotBlank() && playerId.isNotBlank())
        require(cells.size == 25 && cells[12] == 0)
        require(cells.filterIndexed { index, _ -> index != 12 }.all { it in 1..75 })
        require(numbers.toSet().size == 24)
        cells.forEachIndexed { index, number ->
            require(index == 12 || number in bingoColumnRange(index % 5))
        }
    }
    val numbers: List<Int> get() = cells.filter { it != 0 }
    val fingerprint: String get() = cells.joinToString(",")
}

fun bingoColumnRange(column: Int): IntRange {
    require(column in 0..4)
    return (column * 15 + 1)..((column + 1) * 15)
}

fun bingoCallLabel(number: Int): String {
    require(number in 1..75)
    return "${"BINGO"[(number - 1) / 15]}-$number"
}

class BingoCardGenerator(private val random: Random = SecureRandom()) {
    fun generate(id: String, playerId: String): BingoCard {
        val cells = MutableList(25) { 0 }
        for (column in 0..4) {
            val values = bingoColumnRange(column).toList().shuffledWith(random)
            for (row in 0..4) if (row != 2 || column != 2) cells[row * 5 + column] = values[row]
        }
        return BingoCard(id, playerId, cells.toList())
    }

    fun deal(playerId: String, count: Int): List<BingoCard> {
        require(count in 1..6)
        val cards = mutableListOf<BingoCard>()
        repeat(count) { index ->
            var card = generate("$playerId-bingo-${index + 1}", playerId)
            // Bounded collision recovery; no unbounded RNG loop on a faulty source.
            repeat(16) {
                if (cards.any { it.fingerprint == card.fingerprint }) card = generate(card.id, playerId)
            }
            require(cards.none { it.fingerprint == card.fingerprint }) { "Could not deal distinct Bingo cards" }
            cards += card
        }
        return cards.toList()
    }
}

@Serializable
enum class BingoPattern { ANY_LINE, FOUR_CORNERS, X, BLACKOUT }

/** Sets of cell positions. Any one mask completes a pattern; a line includes diagonals. */
fun BingoPattern.masks(): List<Set<Int>> = when (this) {
    BingoPattern.ANY_LINE -> (0..4).map { row -> (0..4).map { row * 5 + it }.toSet() } +
        (0..4).map { column -> (0..4).map { it * 5 + column }.toSet() } +
        listOf(setOf(0, 6, 12, 18, 24), setOf(4, 8, 12, 16, 20))
    BingoPattern.FOUR_CORNERS -> listOf(setOf(0, 4, 20, 24))
    BingoPattern.X -> listOf(setOf(0, 4, 6, 8, 12, 16, 18, 20, 24))
    BingoPattern.BLACKOUT -> listOf((0..24).toSet())
}

/** Both called and marked are authoritative round inputs, never trusted claim payloads. */
fun BingoPattern.isComplete(card: BingoCard, called: Set<Int>, marked: Set<Int> = called): Boolean {
    require(called.all { it in 1..75 })
    require(marked.all { it in 1..75 })
    return masks().any { mask -> mask.all { index ->
        index == 12 || (card.cells[index] in called && card.cells[index] in marked)
    } }
}

@Serializable
data class BingoDraw(val order: List<Int>, val count: Int = 0) {
    init {
        require(order.size == 75 && order.toSet() == (1..75).toSet())
        require(count in 0..75)
    }
    val called: List<Int> get() = order.take(count)
    val latest: Int? get() = order.getOrNull(count - 1)
    val finished: Boolean get() = count == 75
    fun next(): BingoDraw = if (finished) this else copy(count = count + 1)

    companion object {
        fun shuffled(random: Random = SecureRandom()): BingoDraw = BingoDraw((1..75).toList().shuffledWith(random))
    }
}
