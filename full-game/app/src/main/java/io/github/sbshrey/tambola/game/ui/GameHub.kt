package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.Screen

@Composable
fun GameHub(name: String?, coins: Long?, avatar: Int, lastGame: Screen?, profile: () -> Unit,
    tambola: () -> Unit, bingo: () -> Unit, settings: () -> Unit, connected: Boolean = true) {
    MaterialTheme(colorScheme = GameNightPalette.colors) {
        Box(Modifier.fillMaxSize().background(GameNightPalette.background)) {
            LobbyBackdrop(Modifier.fillMaxSize())
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val landscape = maxWidth > maxHeight
                Column(Modifier.fillMaxSize().padding(horizontal = if (landscape) 28.dp else 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_name), color = GameNightPalette.cream,
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Text(stringResource(R.string.hub_live_games), color = GameNightPalette.muted,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = settings, modifier = Modifier.testTag("hub-settings")) {
                            Text(stringResource(R.string.ui_settings), color = GameNightPalette.cream)
                        }
                    }
                    Surface(onClick = profile, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("hub-profile"),
                        shape = RoundedCornerShape(18.dp), color = GameNightPalette.panel) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AvatarBadge(avatar, size = 38.dp)
                            Column(Modifier.weight(1f)) {
                                Text(name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ui_your_profile), color = GameNightPalette.cream,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(when {
                                    name.isNullOrBlank() -> R.string.hub_create_player
                                    connected -> R.string.hub_connected
                                    else -> R.string.hub_reconnecting
                                }),
                                    color = if (connected) GameNightPalette.mint else GameNightPalette.gold,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Text(coins?.let { stringResource(R.string.hub_coins, it) } ?: "…",
                                color = GameNightPalette.gold, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(stringResource(R.string.hub_choose), color = GameNightPalette.cream,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    if (landscape) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HubGame(stringResource(R.string.hub_tambola_title), stringResource(R.string.hub_tambola), "90",
                            GameNightPalette.coral, "choose-tambola", Modifier.weight(1f).fillMaxHeight(), lastGame == Screen.ONLINE, tambola)
                        HubGame(stringResource(R.string.bingo_title), stringResource(R.string.hub_bingo), "75",
                            GameNightPalette.mint, "choose-bingo", Modifier.weight(1f).fillMaxHeight(), lastGame == Screen.BINGO, bingo)
                    } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HubGame(stringResource(R.string.hub_tambola_title), stringResource(R.string.hub_tambola), "90",
                            GameNightPalette.coral, "choose-tambola", Modifier.weight(1f).fillMaxWidth(), lastGame == Screen.ONLINE, tambola)
                        HubGame(stringResource(R.string.bingo_title), stringResource(R.string.hub_bingo), "75",
                            GameNightPalette.mint, "choose-bingo", Modifier.weight(1f).fillMaxWidth(), lastGame == Screen.BINGO, bingo)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubGame(title: String, subtitle: String, ball: String, accent: Color, tag: String,
    modifier: Modifier, recent: Boolean, action: () -> Unit) {
    Surface(onClick = action, modifier = modifier.testTag(tag), shape = RoundedCornerShape(28.dp),
        color = GameNightPalette.panel, border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .55f))) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(GameNightPalette.panel, accent.copy(alpha = .23f))))) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * .34f
                drawCircle(accent.copy(alpha = .12f), radius * 1.5f, Offset(size.width * .91f, size.height * .04f))
                drawCircle(accent.copy(alpha = .13f), radius, Offset(size.width * .95f, size.height * .88f))
            }
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = accent.copy(alpha = .18f), shape = RoundedCornerShape(50)) {
                        Text(stringResource(R.string.hub_multiplayer), Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    if (recent) Text(stringResource(R.string.hub_recent), color = GameNightPalette.cream,
                        style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = CircleShape, color = accent, modifier = Modifier.size(70.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(ball, color = GameNightPalette.background, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, color = GameNightPalette.cream, style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black, maxLines = 1)
                        Text(subtitle, color = GameNightPalette.muted, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(stringResource(R.string.hub_join_table), color = accent,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
