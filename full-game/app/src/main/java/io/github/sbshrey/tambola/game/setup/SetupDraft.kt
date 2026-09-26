package io.github.sbshrey.tambola.game.setup

import io.github.sbshrey.tambola.domain.*
import kotlinx.serialization.Serializable
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.presentation.*

@Serializable
data class SetupDraft(
    val mode: GameMode = GameMode.PRACTICE,
    val names: String = "You",
    val tickets: Int = 1,
    val bots: Int = 2,
    val assisted: Boolean = false,
    val prizes: List<Prize> = Prize.defaults.filterNot { it == Prize.FULL_HOUSE },
    val houses: Int = 1,
    val playAllNumbers: Boolean = false,
    val customPrizes: List<CustomPrize> = emptyList(),
    val avatars: List<Int> = emptyList(),
    val manualClaims: Boolean = true,
) {
    val playerNames get() = names.lines().map(String::trim).filter(String::isNotBlank)
    val playerCount get() = playerNames.size + if (mode == GameMode.PRACTICE) bots else 0
    fun avatar(index: Int) = avatars.getOrNull(index) ?: 0
    fun withAvatar(index: Int, value: Int): SetupDraft {
        val seats = playerNames.take(8).indices
        require(index in seats && value in 0 until AVATAR_COUNT)
        return copy(avatars = seats.map { if (it == index) value else avatar(it) })
    }
    val errors: List<UiMessage> get() = buildList {
        if (playerNames.any { it.length > 40 || it.any(Char::isISOControl) }) add(UiMessage(R.string.error_name_length))
        if (mode == GameMode.PRACTICE && playerNames.size != 1) add(UiMessage(R.string.error_enter_name))
        if (mode == GameMode.FAMILY && playerNames.size !in 2..8) add(UiMessage(R.string.error_family_count))
        if (playerNames.map(String::lowercase).distinct().size != playerNames.size) add(UiMessage(R.string.error_unique_names))
        if (tickets !in 1..6 || bots !in 0..5 || houses !in 1..3) add(UiMessage(R.string.error_supported_counts))
        if (avatars.size > 8 || avatars.any { it !in 0 until AVATAR_COUNT }) add(UiMessage(R.string.error_supported_avatar))
        if (mode != GameMode.ONLINE && houses > playerCount * tickets) add(UiMessage(R.string.error_house_tickets, listOf(houses)))
        customPrizes.filter { it.minimumTickets > tickets || it.ticketOrdinals.any { ordinal -> ordinal > tickets } }.forEach {
            add(UiMessage(R.string.error_prize_tickets, listOf(it.title)))
        }
    }
    fun settings(): RoundSettings {
        errors.firstOrNull()?.let { throw UiMessageException(it) }
        val housePrizes = if (houses == 1) listOf(Prize.FULL_HOUSE) else listOf(Prize.HOUSE_ONE, Prize.HOUSE_TWO, Prize.HOUSE_THREE).take(houses)
        return RoundSettings(mode, tickets, assisted, prizes + housePrizes, playAllNumbers, customPrizes, manualClaims)
    }
    companion object {
        fun fresh(mode: GameMode) = SetupDraft(mode = mode, names = if (mode == GameMode.FAMILY) "Asha\nBina" else "You", assisted = false,
            prizes = listOf(Prize.EARLY_FIVE, Prize.CORNERS, Prize.TOP_LINE, Prize.MIDDLE_LINE, Prize.BOTTOM_LINE))
        fun from(round: Round) = SetupDraft(round.settings.mode, round.players.filterNot { it.computer }.joinToString("\n") { it.name },
            round.settings.ticketsPerPlayer, round.players.count { it.computer }, round.settings.assistedMarking,
            round.settings.prizes.filterNot { it == Prize.FULL_HOUSE || it.isRankedHouse },
            round.settings.prizes.count { it.isRankedHouse }.coerceAtLeast(1), round.settings.playAllNumbers, round.settings.customPrizes,
            round.players.filterNot { it.computer }.map { it.avatar }, round.settings.manualClaims)
    }
}
