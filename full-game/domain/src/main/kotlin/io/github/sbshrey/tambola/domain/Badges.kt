package io.github.sbshrey.tambola.domain

import kotlinx.serialization.Serializable

enum class Badge(val title: String, val explanation: String, val symbol: String) {
    FIRST_ROUND("First round", "Finish a round, all the way to its agreed ending.", "1"),
    FIRST_HOUSE("First full house", "Win a full house or a ranked house in a completed round.", "15"),
    FIVE_ROUNDS("Five together", "Complete five different rounds in this mode.", "5");
}

/** Bounded milestone ledger, not a lifetime statistics counter. No names or tickets are stored. */
@Serializable data class BadgeProgress(
    val completedRoundIds: Set<String> = emptySet(),
    val houseRoundId: String? = null,
) {
    init {
        require(completedRoundIds.size <= 5 && completedRoundIds.all { it.isNotBlank() && it.length <= 128 })
        require(houseRoundId == null || (houseRoundId.isNotBlank() && houseRoundId.length <= 128))
    }
    fun earned(badge: Badge): Boolean = when (badge) {
        Badge.FIRST_ROUND -> completedRoundIds.isNotEmpty()
        Badge.FIRST_HOUSE -> houseRoundId != null
        Badge.FIVE_ROUNDS -> completedRoundIds.size == 5
    }
    fun record(roundId: String, status: RoundStatus, wonHouse: Boolean): BadgeProgress {
        if (status != RoundStatus.COMPLETED) return this
        require(roundId.isNotBlank() && roundId.length <= 128)
        return copy(completedRoundIds = if (completedRoundIds.size < 5) completedRoundIds + roundId else completedRoundIds,
            houseRoundId = houseRoundId ?: roundId.takeIf { wonHouse })
    }
}

enum class BadgeMode(val title: String, val description: String) {
    SOLO("Solo practice", "Your solo games on this device."),
    COMPUTER("Computer games", "Your games against computer players. Computer wins do not earn your house badge."),
    FAMILY("Family table", "Shared milestones for this device's family table. A house won by any human player counts."),
    ONLINE("Online profile", "Milestones for your current online profile on this device.");
}

fun Round.badgeMode(): BadgeMode = when (settings.mode) {
    GameMode.FAMILY -> BadgeMode.FAMILY
    GameMode.ONLINE -> BadgeMode.ONLINE
    GameMode.PRACTICE -> if (players.any { it.computer }) BadgeMode.COMPUTER else BadgeMode.SOLO
}

fun Iterable<Award>.hasHouseFor(playerIds: Set<String>): Boolean = any {
    (it.prize == Prize.FULL_HOUSE || it.prize.isRankedHouse) && it.playerIds.any { id -> id in playerIds }
}

fun BadgeProgress.record(round: Round): BadgeProgress = record(round.id, round.status,
    round.awards.hasHouseFor(round.players.filterNot { it.computer }.map { it.id }.toSet()))
