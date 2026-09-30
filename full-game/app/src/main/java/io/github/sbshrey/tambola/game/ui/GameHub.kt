package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.Screen
import androidx.compose.ui.Alignment

@Composable
fun GameHub(name: String?, coins: Long?, avatar: Int, lastGame: Screen?, profile: () -> Unit, tambola: () -> Unit, bingo: () -> Unit, settings: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)

            }
            TextButton(onClick = settings) { Text(stringResource(R.string.ui_settings)) }
        }
        TextButton(onClick = profile, modifier = Modifier.fillMaxWidth().testTag("hub-profile")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarBadge(avatar, size = 32.dp)
                Text(name ?: stringResource(R.string.ui_your_profile), Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (coins != null) Text(stringResource(R.string.hub_coins, coins), color = Saffron)
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            if (maxWidth > maxHeight) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HubGame("Tambola", stringResource(R.string.hub_tambola), "choose-tambola", Modifier.weight(1f), lastGame == Screen.ONLINE, tambola)
                HubGame("Bingo", stringResource(R.string.hub_bingo), "choose-bingo", Modifier.weight(1f), lastGame == Screen.BINGO, bingo)
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HubGame("Tambola", stringResource(R.string.hub_tambola), "choose-tambola", Modifier.weight(1f), lastGame == Screen.ONLINE, tambola)
                HubGame("Bingo", stringResource(R.string.hub_bingo), "choose-bingo", Modifier.weight(1f), lastGame == Screen.BINGO, bingo)
            }
        }
    }
}

@Composable
private fun HubGame(title: String, subtitle: String, tag: String, modifier: Modifier, recent: Boolean, action: () -> Unit) {
    Card(onClick = action, modifier = modifier.fillMaxSize().testTag(tag), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (recent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(subtitle, color = Muted)
        }
    }
}
