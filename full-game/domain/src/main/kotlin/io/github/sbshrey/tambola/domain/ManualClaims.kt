package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable

/** One compact, replayable proof per player and call. Hands in this mode are disjoint. */
@Serializable
data class ManualClaim(val playerId: String, val drawIndex: Int, val submissions: List<Set<Int>>)

/** Check every owned ticket and every enabled scheme in one action. No match is a no-op.
 * A prize remains open to ties until the next call, including a terminal house or number 90.
 * Only revealed, manually marked numbers are accepted; the caller must authenticate playerId.
 */
fun Round.claim(playerId: String, markedNumbers: Set<Int> = tickets.filter { it.playerId == playerId }
    .flatMap { marks[it.id].orEmpty() }.toSet()): Round {
    require(settings.manualClaims) { "This round uses automatic awards" }
    require(status == RoundStatus.PLAYING || status == RoundStatus.PAUSED) { "This round is not active" }
    require(players.any { it.id == playerId }) { "Unknown player" }
    val owned = tickets.filter { it.playerId == playerId }
    require(markedNumbers.all { it in called && owned.any { ticket -> it in ticket.numbers } }) { "Only owned, called numbers can be claimed" }
    if (called.isEmpty()) return this

    // Every claimant on this call competes for the same ranked house. One person
    // cannot claim House one and House two by pressing twice before the next call.
    val closedRanks = awards.filter { it.prize.isRankedHouse && it.drawIndex < called.size }
    val nextRank = settings.prizes.filter { it.isRankedHouse && closedRanks.none { award -> award.prize == it } }.minByOrNull { it.ordinal }
    val earlierHouseTickets = closedRanks.flatMap { it.ticketIds }.toSet()
    val nextAwards = awards.toMutableList()
    settings.prizes.forEach { prize ->
        val existing = nextAwards.firstOrNull { it.prize == prize }
        if (existing != null && existing.drawIndex < called.size) return@forEach
        if (prize.isRankedHouse && prize != nextRank) return@forEach
        val matching = owned.filter { prize.matches(it, markedNumbers) && (!prize.isRankedHouse || it.id !in earlierHouseTickets) }
        if (matching.isEmpty()) return@forEach
        val ids = existing?.ticketIds.orEmpty().toSet() + matching.map { it.id }
        val winningTickets = tickets.filter { it.id in ids }
        nextAwards.remove(existing)
        nextAwards += Award(prize, called.size, winningTickets.map { it.id }, winningTickets.map { it.playerId }.distinct())
    }
    val nextCustom = customAwards.toMutableList()
    settings.customPrizes.forEach { prize ->
        val existing = nextCustom.firstOrNull { it.prizeId == prize.id }
        if (existing != null && existing.drawIndex < called.size) return@forEach
        val matching = prize.eligibleTickets(owned, markedNumbers)
        if (matching.isEmpty()) return@forEach
        val ids = existing?.ticketIds.orEmpty().toSet() + matching.map { it.id }
        val winningTickets = tickets.filter { it.id in ids }
        nextCustom.remove(existing)
        nextCustom += CustomAward(prize.id, prize.version, called.size, winningTickets.map { it.id }, winningTickets.map { it.playerId }.distinct())
    }
    val orderedAwards = nextAwards.sortedWith(compareBy<Award> { it.drawIndex }.thenBy { settings.prizes.indexOf(it.prize) })
    val orderedCustom = nextCustom.sortedWith(compareBy<CustomAward> { it.drawIndex }.thenBy { award -> settings.customPrizes.indexOfFirst { it.id == award.prizeId } })
    if (orderedAwards == awards && orderedCustom == customAwards) return this
    val previous = claims.firstOrNull { it.playerId == playerId && it.drawIndex == called.size }
    // Keep separate successful submissions: merging their marks would manufacture
    // a pattern when the player unmarked a number between two presses.
    val proof = ManualClaim(playerId, called.size, previous?.submissions.orEmpty() + listOf(markedNumbers.toSet()))
    return copy(awards = orderedAwards, customAwards = orderedCustom,
        claims = (claims.filterNot { it == previous } + proof).sortedWith(compareBy<ManualClaim> { it.drawIndex }
            .thenBy { claim -> players.indexOfFirst { it.id == claim.playerId } }))
}

/** Called at the end of a call window. Computers get no future-number information. */
internal fun Round.drawManual(): Round {
    if (status != RoundStatus.PLAYING) return this
    var settled = this
    if (called.isNotEmpty()) players.filter { it.computer }.forEach { player ->
        val marked = tickets.filter { it.playerId == player.id }.flatMap { it.numbers }.filter { it in called }.toSet()
        settled = settled.claim(player.id, marked)
    }
    if (called.size == 90 || (!settings.playAllNumbers && settled.terminalAward(settled.awards, settled.customAwards))) {
        return settled.copy(status = RoundStatus.COMPLETED)
    }
    val next = called + drawOrder[called.size]
    val nextMarks = settled.marks.toMutableMap()
    tickets.filter { settings.assistedMarking || players.first { p -> p.id == it.playerId }.computer }.forEach { ticket ->
        nextMarks[ticket.id] = ticket.numbers.filter { it in next }.toSet()
    }
    return settled.copy(called = next, marks = nextMarks.toMap())
}

internal fun Round.validateManualClaims() {
    require(players.all { player ->
        val numbers = tickets.filter { it.playerId == player.id }.flatMap { it.numbers }
        numbers.size == numbers.distinct().size
    }) { "Manual-claim hands must not repeat numbers" }
    require(claims.size <= players.size * called.size)
    require(claims.map { it.playerId to it.drawIndex }.distinct().size == claims.size)
    val maxSubmissions = settings.ticketsPerPlayer * (settings.prizes.size + settings.customPrizes.size)
    require(claims.all { it.drawIndex in 1..called.size && it.submissions.size in 1..maxSubmissions && it.submissions.all { marks -> marks.size <= 90 } })
    var replay = copy(called = emptyList(), awards = emptyList(), customAwards = emptyList(), claims = emptyList(), marks = emptyMap(), status = RoundStatus.PLAYING)
    repeat(called.size) { index ->
        replay = replay.drawManual()
        require(replay.called.size == index + 1 && !replay.finished) { "Saved round contains draws after completion" }
        claims.filter { it.drawIndex == index + 1 }.forEach { proof ->
            proof.submissions.forEach { marks ->
                val next = replay.claim(proof.playerId, marks)
                require(next != replay) { "Saved claim does not win a prize" }
                replay = next
            }
        }
    }
    // Completion closes the final window without appending a fictitious 91st call.
    if (status == RoundStatus.COMPLETED) {
        replay = replay.drawManual()
        require(replay.finished && replay.called == called) { "Saved round is not complete" }
    }
    require(replay.awards == awards && replay.customAwards == customAwards && replay.claims == claims) { "Saved claims disagree with ticket rules" }
}
