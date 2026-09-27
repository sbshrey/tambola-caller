package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sbshrey.tambola.game.R

/** Read-only catch-up view. Only revealed calls enter this component; no ticket or game callbacks. */
@Composable
internal fun CalledNumberDialog(called: List<Int>, initiallyHistory: Boolean = false, dismiss: () -> Unit) {
    val words = gameText()
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var history by rememberSaveable { mutableStateOf(initiallyHistory) }
    val historyScroll = rememberLazyListState()
    val boardScroll = rememberScrollState()
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth().heightIn(max = maxHeight)
                .testTag("number-board-dialog").semantics { testTagsAsResourceId = true },
                shape = RoundedCornerShape(24.dp), color = colors.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(words(R.string.ui_number_board), Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = dismiss, modifier = Modifier.size(48.dp).testTag("dismiss-number-board")
                            .semantics { contentDescription = words(R.string.ui_back_to_game) }) {
                            Text("×", fontSize = 28.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false to R.string.board_numbers, true to R.string.board_history).forEach { (value, label) ->
                            Tab(selected = history == value, onClick = { history = value },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .background(if (history == value) colors.secondaryContainer else colors.surfaceContainer, RoundedCornerShape(12.dp))
                                    .testTag(if (value) "number-board-history-tab" else "number-board-numbers-tab"),
                                selectedContentColor = colors.onSecondaryContainer, unselectedContentColor = colors.onSurfaceVariant,
                                text = { Text(words(label), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                    Text(words(R.string.ui_called_to_go, called.size, 90 - called.size), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().testTag("number-board-count"))
                    Text(called.lastOrNull()?.let { words(R.string.board_latest, it) } ?: words(R.string.play_waiting),
                        color = colors.primary, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().testTag("number-board-latest"))
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                        if (history) {
                            LazyColumn(state = historyScroll, modifier = Modifier.fillMaxWidth().testTag("number-board-history"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // A stable first item keeps the newest calls visible at the top. Indexed call keys
                                // preserve the visible older call when new calls arrive while someone reads back.
                                item(key = "order") { Text(words(R.string.board_latest_first), style = MaterialTheme.typography.bodySmall) }
                                items(called.withIndex().toList().asReversed(), key = { it.index }) { entry ->
                                    Row(Modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(12.dp))
                                        .testTag("call-history-${entry.index + 1}").semantics(mergeDescendants = true) {
                                            contentDescription = words(R.string.board_history_entry, entry.index + 1, entry.value)
                                        }.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(words(R.string.board_call_index, entry.index + 1), Modifier.weight(1f).clearAndSetSemantics {},
                                            style = MaterialTheme.typography.bodyMedium)
                                        Text(entry.value.toString(), Modifier.clearAndSetSemantics {},
                                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else Column(Modifier.fillMaxWidth().verticalScroll(boardScroll).testTag("number-board-scroll")) {
                            CalledNumberGrid(called, words)
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CalledNumberGrid(called: List<Int>, words: GameText) {
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val positions = called.withIndex().associate { it.value to it.index + 1 }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth.value + 6) / (40 * fontScale.coerceAtLeast(1f) + 6)).toInt().coerceIn(2, 10)
        val rows = (1..90).toList().chunked(columns)
        Column(Modifier.semantics { collectionInfo = CollectionInfo(rows.size, columns) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { columnIndex, number ->
                        val position = positions[number]
                        val foreground = if (position != null) colors.onSecondaryContainer else colors.onSurfaceVariant
                        val state = if (number == called.lastOrNull()) words(R.string.board_latest_at, position!!)
                            else position?.let { words(R.string.board_called_at, it) } ?: words(R.string.ui_not_called)
                        Box(Modifier.weight(1f).aspectRatio(1f)
                            .background(if (position != null) colors.secondaryContainer else colors.surfaceContainer, RoundedCornerShape(10.dp))
                            .border(if (number == called.lastOrNull()) 2.dp else 1.dp,
                                if (number == called.lastOrNull()) colors.primary else colors.outlineVariant, RoundedCornerShape(10.dp))
                            .testTag("board-number-$number").semantics(mergeDescendants = true) {
                                stateDescription = state
                                collectionItemInfo = CollectionItemInfo(rowIndex, 1, columnIndex, 1)
                            }, contentAlignment = Alignment.Center) {
                            Text(number.toString(), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = foreground)
                            if (position != null) Canvas(Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp)) {
                                drawLine(foreground, Offset(0f, size.height * .5f), Offset(size.width * .35f, size.height), 1.5.dp.toPx())
                                drawLine(foreground, Offset(size.width * .35f, size.height), Offset(size.width, 0f), 1.5.dp.toPx())
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
