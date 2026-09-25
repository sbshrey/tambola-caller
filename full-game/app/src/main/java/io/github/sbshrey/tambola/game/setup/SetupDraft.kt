package io.github.sbshrey.tambola.game.setup

import io.github.sbshrey.tambola.domain.*
import kotlinx.serialization.Serializable

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
) {
    val playerNames get() = names.lines().map(String::trim).filter(String::isNotBlank)
    val playerCount get() = playerNames.size + if (mode == GameMode.PRACTICE) bots else 0
    val errors: List<String> get() = buildList {
        if (playerNames.any { it.length > 40 || it.any(Char::isISOControl) }) add("Each name needs 1–40 characters without control characters.")
        if (mode == GameMode.PRACTICE && playerNames.size != 1) add("Enter your name to begin.")
        if (mode == GameMode.FAMILY && playerNames.size !in 2..8) add("Family play needs 2–8 players, one name per line.")
        if (playerNames.map(String::lowercase).distinct().size != playerNames.size) add("Give each player a different name so everyone can recognise their tickets.")
        if (tickets !in 1..6 || bots !in 0..5 || houses !in 1..3) add("Choose supported ticket, player and house counts.")
        if (mode != GameMode.ONLINE && houses > playerCount * tickets) add("$houses houses need at least $houses tickets at the table.")
        customPrizes.filter { it.minimumTickets > tickets || it.ticketOrdinals.any { ordinal -> ordinal > tickets } }.forEach {
            add("${it.title} needs more tickets. Edit the prize or increase tickets per player.")
        }
    }
    fun settings(): RoundSettings {
        require(errors.isEmpty()) { errors.first() }
        val housePrizes = if (houses == 1) listOf(Prize.FULL_HOUSE) else listOf(Prize.HOUSE_ONE, Prize.HOUSE_TWO, Prize.HOUSE_THREE).take(houses)
        return RoundSettings(mode, tickets, assisted, prizes + housePrizes, playAllNumbers, customPrizes)
    }
    companion object {
        fun fresh(mode: GameMode) = SetupDraft(mode = mode, names = if (mode == GameMode.FAMILY) "Asha\nBina" else "You")
        fun from(round: Round) = SetupDraft(round.settings.mode, round.players.filterNot { it.computer }.joinToString("\n") { it.name },
            round.settings.ticketsPerPlayer, round.players.count { it.computer }, round.settings.assistedMarking,
            round.settings.prizes.filterNot { it == Prize.FULL_HOUSE || it.isRankedHouse },
            round.settings.prizes.count { it.isRankedHouse }.coerceAtLeast(1), round.settings.playAllNumbers, round.settings.customPrizes)
    }
}
