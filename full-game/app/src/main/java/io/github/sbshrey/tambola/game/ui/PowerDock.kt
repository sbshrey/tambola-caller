package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.R

internal fun GameText.matchPowerName(power: MatchPower): String = this(when (power) {
    MatchPower.SHIELD -> R.string.power_shield
    MatchPower.AUTO_DAB -> R.string.power_auto
    MatchPower.PRIZE_BONUS -> R.string.power_bonus
})

internal fun powerNoticeText(powers: MatchPowers, words: GameText): String? = powers.notice?.let { words(when (it) {
    PowerNotice.DROP -> R.string.power_drop
    PowerNotice.FULL -> R.string.power_full
    PowerNotice.SHIELD_SAVED -> R.string.power_shield_saved
    PowerNotice.TICKET_DISCARDED -> R.string.power_discarded
    PowerNotice.ACTIVATED -> R.string.power_active
}) }

@Composable internal fun PowerDock(table: TableRound, enabled: Boolean, activate: (String, MatchPower) -> Unit) {
    val powers = requireNotNull(table.powers)
    val words = gameText()
    var selected by remember(table.id) { mutableStateOf<MatchPower?>(null) }
    val haptic = LocalHapticFeedback.current
    var previousNotice by remember(table.id) { mutableIntStateOf(powers.noticeSequence) }
    LaunchedEffect(powers.noticeSequence) {
        if (powers.noticeSequence > previousNotice) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        previousNotice = powers.noticeSequence
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("power-dock"), verticalAlignment = Alignment.CenterVertically) {
        Text(words(R.string.power_progress, powers.correctMarks % 5), Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        MatchPower.entries.forEach { power ->
            val count = powers.inventory.count { it == power }
            TextButton(onClick = { selected = power }, modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 48.dp)
                .testTag("match-power-${power.name}").semantics { contentDescription = "${words.matchPowerName(power)}: $count" }) {
                Text("${when (power) { MatchPower.SHIELD -> "🛡"; MatchPower.AUTO_DAB -> "⚡"; MatchPower.PRIZE_BONUS -> "+25%" }} $count",
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
    selected?.let { power -> ArenaDialog(words.matchPowerName(power), { selected = null }) {
        Text(words(when (power) {
            MatchPower.SHIELD -> R.string.power_shield_detail
            MatchPower.AUTO_DAB -> R.string.power_auto_detail
            MatchPower.PRIZE_BONUS -> R.string.power_bonus_detail
        }))
        if (power != MatchPower.SHIELD) table.tickets.forEachIndexed { index, ticket ->
            val until = powers.autoUntil[ticket.id]
            val remaining by countdownSeconds(remainingCoinTime(until, table.serverTime, table.id))
            val caption = if (power == MatchPower.AUTO_DAB && remaining > 0) words(R.string.power_auto_remaining, index + 1, remaining)
                else words(R.string.power_use_ticket, index + 1)
            OutlinedButton(onClick = { activate(ticket.id, power); selected = null }, modifier = Modifier.fillMaxWidth().testTag("use-power-ticket-${index + 1}"),
                enabled = enabled && power in powers.inventory && ticket.id !in powers.discarded && power !in powers.used[ticket.id].orEmpty()) { Text(caption) }
        }
        Text(words(R.string.power_drop_rules), style = MaterialTheme.typography.bodySmall)
    } }
}
