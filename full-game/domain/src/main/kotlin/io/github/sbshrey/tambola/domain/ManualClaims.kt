package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable

/** One compact, replayable proof per player and call. Hands in this mode are disjoint. */
@Serializable
data class ManualClaim(val playerId: String, val drawIndex: Int, val submissions: List<Set<Int>>,
    val selections: List<ClaimSelection?> = emptyList())

/** A human chooses exactly one ticket and one scheme; computer turns may check all. */
@Serializable
data class ClaimSelection(val ticketId: String, val prizeId: String) {
    init { require(ticketId.length in 1..100 && prizeId.length in 1..64) }
}

/** Stable 1.2–2.8 second reactions can be restored after a service restart.
 * Only computers eligible on the revealed prefix need a scheduled action.
 */
fun Round.computerClaimDelays(): Map<String, Long> {
    if (!settings.manualClaims || status != RoundStatus.PLAYING || called.isEmpty()) return emptyMap()
    return players.filter { it.computer }.mapNotNull { player ->
        val marked = tickets.filter { it.playerId == player.id }.flatMap { it.numbers }.filter { it in called }.toSet()
        if (claim(player.id, marked) == this) null
        else player.id to (1_200L + Math.floorMod("$id:${called.size}:${player.id}".hashCode(), 1_601))
    }.toMap()
}

fun Round.claimComputer(playerId: String): Round {
    require(players.any { it.id == playerId && it.computer }) { "Unknown computer player" }
    val marked = tickets.filter { it.playerId == playerId }.flatMap { it.numbers }.filter { it in called }.toSet()
    return claim(playerId, marked)
}

/** Check every owned ticket and every enabled scheme in one action. No match is a no-op.
 * A prize remains open to ties until the next call, including a terminal house or number 90.
 * Only revealed, manually marked numbers are accepted; the caller must authenticate playerId.
 */
fun Round.claim(playerId: String, markedNumbers: Set<Int> = tickets.filter { it.playerId == playerId }
    .flatMap { marks[it.id].orEmpty() }.filter { it in called }.toSet(), selection: ClaimSelection? = null): Round {
    require(settings.manualClaims) { "This round uses automatic awards" }
    require(status == RoundStatus.PLAYING || status == RoundStatus.PAUSED) { "This round is not active" }
    require(players.any { it.id == playerId }) { "Unknown player" }
    val owned = tickets.filter { it.playerId == playerId }
    if (selection != null) {
        require(owned.any { it.id == selection.ticketId }) { "Claim ticket is not owned by this player" }
        require(settings.prizes.any { it.name == selection.prizeId } || settings.customPrizes.any { it.id == selection.prizeId }) { "Unknown claim prize" }
    }
    require(markedNumbers.all { it in called && owned.any { ticket -> it in ticket.numbers } }) { "Only owned, called numbers can be claimed" }
    if (called.isEmpty()) return this

    // Every claimant on this call competes for the same ranked house. One person
    // cannot claim House one and House two by pressing twice before the next call.
    val closedRanks = awards.filter { it.prize.isRankedHouse && it.drawIndex < called.size }
    val nextRank = settings.prizes.filter { it.isRankedHouse && closedRanks.none { award -> award.prize == it } }.minByOrNull { it.ordinal }
    val earlierHouseTickets = closedRanks.flatMap { it.ticketIds }.toSet()
    val nextAwards = awards.toMutableList()
    settings.prizes.forEach { prize ->
        if (selection != null && selection.prizeId != prize.name) return@forEach
        val existing = nextAwards.firstOrNull { it.prize == prize }
        if (existing != null && existing.isClosed(settings, called.size)) return@forEach
        if (settings.winnersPerPrize > 1 && playerId in existing?.playerIds.orEmpty()) return@forEach
        if (prize.isRankedHouse && prize != nextRank) return@forEach
        val eligible = owned.filter { (selection == null || it.id == selection.ticketId) && prize.matches(it, markedNumbers) && (!prize.isRankedHouse || it.id !in earlierHouseTickets) }
        val matching = if (settings.winnersPerPrize > 1) eligible.take(1) else eligible
        if (matching.isEmpty()) return@forEach
        val ids = existing?.ticketIds.orEmpty().toSet() + matching.map { it.id }
        val winningTickets = tickets.filter { it.id in ids }
        nextAwards.remove(existing)
        nextAwards += Award(prize, called.size, winningTickets.map { it.id }, winningTickets.map { it.playerId }.distinct())
    }
    val nextCustom = customAwards.toMutableList()
    settings.customPrizes.forEach { prize ->
        if (selection != null && selection.prizeId != prize.id) return@forEach
        val existing = nextCustom.firstOrNull { it.prizeId == prize.id }
        if (existing != null && existing.drawIndex < called.size) return@forEach
        val eligible = prize.eligibleTickets(owned, markedNumbers)
        // Hand-wide custom schemes retain their declared ordinal/minimum-ticket rules.
        if (selection != null && eligible.none { it.id == selection.ticketId }) return@forEach
        val matching = if (selection == null || prize.minimumTickets > 1) eligible else eligible.filter { it.id == selection.ticketId }
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
    val selections = (previous?.selections?.takeIf { it.isNotEmpty() } ?: List(previous?.submissions?.size ?: 0) { null }) + selection
    val proof = ManualClaim(playerId, called.size, previous?.submissions.orEmpty() + listOf(markedNumbers.toSet()),
        selections.takeIf { it.any { choice -> choice != null } }.orEmpty())
    return copy(awards = orderedAwards, customAwards = orderedCustom,
        claims = (claims.filterNot { it == previous } + proof).sortedWith(compareBy<ManualClaim> { it.drawIndex }
            .thenBy { claim -> players.indexOfFirst { it.id == claim.playerId } }))
}

/** Called at the end of a call window. Computers get no future-number information. */
internal fun Round.drawManual(): Round {
    if (status != RoundStatus.PLAYING) return this
    var settled = this
    if (called.isNotEmpty()) players.filter { it.computer }.forEach { player ->
        settled = settled.claimComputer(player.id)
    }
    if (called.size == settings.maxCalls || (!settings.playAllNumbers && settled.terminalAward(settled.awards, settled.customAwards))) {
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
    require(claims.all { it.selections.isEmpty() || it.selections.size == it.submissions.size })
    var replay = copy(called = emptyList(), awards = emptyList(), customAwards = emptyList(), claims = emptyList(), marks = emptyMap(), status = RoundStatus.PLAYING)
    repeat(called.size) { index ->
        replay = replay.drawManual()
        require(replay.called.size == index + 1 && !replay.finished) { "Saved round contains draws after completion" }
        claims.filter { it.drawIndex == index + 1 }.forEach { proof ->
            proof.submissions.forEachIndexed { submission, marks ->
                val next = replay.claim(proof.playerId, marks, proof.selections.getOrNull(submission))
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
