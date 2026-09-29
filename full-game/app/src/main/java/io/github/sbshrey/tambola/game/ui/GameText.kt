package io.github.sbshrey.tambola.game.ui

import android.content.res.Resources
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.Screen
import io.github.sbshrey.tambola.game.audio.SoundPause
import io.github.sbshrey.tambola.game.data.Appearance
import io.github.sbshrey.tambola.game.setup.SelectionKind
import io.github.sbshrey.tambola.game.presentation.UiMessage
import java.text.DateFormat
import java.util.Date
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources

/** Activity-local resources also work in click/semantics callbacks; never translate user content. */
class GameText(private val resources: Resources) {
    operator fun invoke(@StringRes id: Int, vararg args: Any?): String =
        if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
    fun message(value: UiMessage): String = invoke(value.resource,
        *value.arguments.map { if (it is Prize) prizeTitle(it) else it }.toTypedArray())
    val locale get() = resources.configuration.locales[0]
    fun date(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(time))
    fun screen(value: Screen): String = invoke(when (value) {
        Screen.HOME -> R.string.screen_home
        Screen.SETUP -> R.string.screen_setup
        Screen.GAME -> R.string.screen_game
        Screen.RESULTS -> R.string.screen_results
        Screen.HISTORY -> R.string.screen_history
        Screen.SETTINGS -> R.string.screen_settings
        Screen.ONLINE -> R.string.screen_online
        Screen.TUTORIAL -> R.string.screen_tutorial
        Screen.BADGES -> R.string.screen_badges
    })
    fun appearance(value: Appearance): String = invoke(when (value) {
        Appearance.SYSTEM -> R.string.appearance_system
        Appearance.LIGHT -> R.string.appearance_light
        Appearance.DARK -> R.string.appearance_dark
    })
    fun mode(value: GameMode): String = invoke(when (value) {
        GameMode.PRACTICE -> R.string.mode_practice
        GameMode.FAMILY -> R.string.mode_family
        GameMode.ONLINE -> R.string.mode_online
    })
    fun status(value: RoundStatus): String = invoke(when (value) {
        RoundStatus.READY -> R.string.status_ready
        RoundStatus.PLAYING -> R.string.status_playing
        RoundStatus.PAUSED -> R.string.status_paused
        RoundStatus.COMPLETED -> R.string.status_completed
        RoundStatus.CANCELLED -> R.string.status_cancelled
    })
    fun badgeTitle(value: Badge): String = invoke(when (value) {
        Badge.FIRST_ROUND -> R.string.badge_first_round
        Badge.FIRST_HOUSE -> R.string.badge_first_house
        Badge.FIVE_ROUNDS -> R.string.badge_five_rounds
    })
    fun badgeExplanation(value: Badge): String = invoke(when (value) {
        Badge.FIRST_ROUND -> R.string.badge_first_round_detail
        Badge.FIRST_HOUSE -> R.string.badge_first_house_detail
        Badge.FIVE_ROUNDS -> R.string.badge_five_rounds_detail
    })
    fun badgeModeTitle(value: BadgeMode): String = invoke(when (value) {
        BadgeMode.SOLO -> R.string.badge_mode_solo
        BadgeMode.COMPUTER -> R.string.badge_mode_computer
        BadgeMode.FAMILY -> R.string.badge_mode_family
        BadgeMode.ONLINE -> R.string.badge_mode_online
    })
    fun badgeModeDescription(value: BadgeMode): String = invoke(when (value) {
        BadgeMode.SOLO -> R.string.badge_mode_solo_detail
        BadgeMode.COMPUTER -> R.string.badge_mode_computer_detail
        BadgeMode.FAMILY -> R.string.badge_mode_family_detail
        BadgeMode.ONLINE -> R.string.badge_mode_online_detail
    })
    fun prizeTitle(value: Prize): String = invoke(when (value) {
        Prize.EARLY_FIVE -> R.string.prize_early_five
        Prize.EARLY_TEN -> R.string.prize_early_ten
        Prize.TOP_LINE -> R.string.prize_top_line
        Prize.MIDDLE_LINE -> R.string.prize_middle_line
        Prize.BOTTOM_LINE -> R.string.prize_bottom_line
        Prize.CORNERS -> R.string.prize_corners
        Prize.FULL_HOUSE -> R.string.prize_full_house
        Prize.HOUSE_ONE -> R.string.prize_house_one
        Prize.HOUSE_TWO -> R.string.prize_house_two
        Prize.HOUSE_THREE -> R.string.prize_house_three
    })
    fun prizeExplanation(value: Prize): String = invoke(when (value) {
        Prize.EARLY_FIVE -> R.string.prize_early_five_detail
        Prize.EARLY_TEN -> R.string.prize_early_ten_detail
        Prize.TOP_LINE -> R.string.prize_top_line_detail
        Prize.MIDDLE_LINE -> R.string.prize_middle_line_detail
        Prize.BOTTOM_LINE -> R.string.prize_bottom_line_detail
        Prize.CORNERS -> R.string.prize_corners_detail
        Prize.FULL_HOUSE -> R.string.prize_full_house_detail
        Prize.HOUSE_ONE -> R.string.prize_house_one_detail
        Prize.HOUSE_TWO -> R.string.prize_house_two_detail
        Prize.HOUSE_THREE -> R.string.prize_house_three_detail
    })
    fun selectionKind(value: SelectionKind): String = invoke(when (value) {
        SelectionKind.ALL -> R.string.selection_all
        SelectionKind.ROW -> R.string.selection_row
        SelectionKind.COLUMN -> R.string.selection_column
        SelectionKind.RANGE -> R.string.selection_range
        SelectionKind.POSITIONS -> R.string.selection_positions
    })
    fun soundPause(value: SoundPause): String = invoke(when (value) {
        SoundPause.WAITING -> R.string.sound_waiting
        SoundPause.INTERRUPTED -> R.string.sound_interrupted
        SoundPause.HEADPHONES -> R.string.sound_headphones
        SoundPause.UNAVAILABLE -> R.string.sound_unavailable
        SoundPause.ERROR -> R.string.sound_error
    })

    fun avatar(id: Int): String = invoke(when (GameAvatar.from(id)) {
        GameAvatar.SUN -> R.string.avatar_sun
        GameAvatar.MANGO -> R.string.avatar_mango
        GameAvatar.CHAI -> R.string.avatar_chai
        GameAvatar.PEACOCK -> R.string.avatar_peacock
        GameAvatar.LOTUS -> R.string.avatar_lotus
        GameAvatar.LADOO -> R.string.avatar_ladoo
        GameAvatar.KITE -> R.string.avatar_kite
        GameAvatar.MOON -> R.string.avatar_moon
    })
    fun row(index: Int): String = invoke(listOf(R.string.row_top, R.string.row_middle, R.string.row_bottom)[index])
    fun selection(value: NumberSelection): String = when (value) {
        NumberSelection.All -> invoke(R.string.selection_ticket)
        is NumberSelection.Row -> invoke(listOf(R.string.selection_top, R.string.selection_middle, R.string.selection_bottom)[value.index])
        is NumberSelection.Column -> invoke(R.string.selection_column_detail, value.index + 1, columnRange(value.index).first, columnRange(value.index).last)
        is NumberSelection.Range -> invoke(R.string.selection_range_detail, value.first, value.last)
        is NumberSelection.Positions -> if (value.positions.toSet() == setOf(0, 4, 10, 14)) invoke(R.string.selection_corners) else
            invoke(R.string.selection_positions_detail, value.positions.sorted().groupBy { it / 5 }.entries.joinToString("; ") { (row, positions) ->
                "${row(row).lowercase(locale)} ${positions.joinToString { (it % 5 + 1).toString() }}"
            })
    }
    fun condition(value: RuleCondition): String = if (value.minimumCalled == null) invoke(R.string.condition_all, selection(value.selection))
        else invoke(R.string.condition_at_least, value.minimumCalled, selection(value.selection))
    fun pattern(value: TicketPattern): String = value.alternatives.joinToString(invoke(R.string.pattern_or)) { group ->
        group.joinToString(invoke(R.string.pattern_and), "(", ")") { condition(it) }
    }
    fun customPrize(value: CustomPrize): String = invoke(if (value.minimumTickets == 1) R.string.custom_rule_one else R.string.custom_rule_many,
        pattern(value.pattern), value.minimumTickets, if (value.ticketOrdinals.isEmpty()) invoke(R.string.rule_period)
            else invoke(R.string.among_tickets, value.ticketOrdinals.sorted().joinToString()))
    fun endExplanation(value: RoundSettings): String = invoke(when {
        value.playAllNumbers -> R.string.end_all_numbers
        Prize.HOUSE_THREE in value.prizes -> R.string.end_house_three
        Prize.HOUSE_TWO in value.prizes -> R.string.end_house_two
        Prize.FULL_HOUSE in value.prizes || Prize.HOUSE_ONE in value.prizes -> R.string.end_first_house
        else -> R.string.end_all_prizes
    })
    fun playerLabel(player: Player): String = player.name
    fun ticketLabel(round: TableRound, id: String): String {
        val owner = round.ticketOwners.firstOrNull { it.id == id } ?: return invoke(R.string.winning_ticket)
        return invoke(R.string.owned_ticket_label, round.players.firstOrNull { it.id == owner.playerId }?.let(::playerLabel)
            ?: invoke(R.string.player_label), owner.ordinal)
    }
}

@Composable
fun gameText(): GameText {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { GameText(resources) }
}
