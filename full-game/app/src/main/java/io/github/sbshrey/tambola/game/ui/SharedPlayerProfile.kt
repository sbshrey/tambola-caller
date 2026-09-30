package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.online.*

/** The same identity, wallet information and privacy controls from every game lobby. */
@Composable
internal fun SharedPlayerProfile(state: OnlineUiState, model: OnlineViewModel, dismiss: () -> Unit) {
    val words = gameText()
    val enabled = !state.adActive && !state.loading && !state.busy && !state.pending && !state.storageFailure && !state.sessionExpired && state.available
    var profileName by rememberSaveable { mutableStateOf("") }
    var profileAvatar by rememberSaveable { mutableIntStateOf(0) }
    var delete by remember { mutableStateOf(false) }
    var gameData by rememberSaveable { mutableStateOf(false) }
    MaterialTheme(colorScheme = GameNightPalette.colors) {
    if (!gameData && !delete && state.name == null) LobbyPlayerDialog(profileName, profileAvatar, enabled,
        changeName = { if (it.length <= 40) profileName = it }, chooseAvatar = { profileAvatar = it },
        save = { dismiss(); model.register(profileName, profileAvatar) }, close = { dismiss() }, openData = { gameData = true })
    else if (!gameData && !delete) AlertDialog(onDismissRequest = { dismiss() }, title = { Text(state.name ?: words(R.string.coin_profile)) }, text = {
        Column(Modifier.testTag("coin-profile-scroll").verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(words(R.string.beta_coins_grant, COIN_BETA_BALANCE), fontWeight = FontWeight.Bold)
            state.loginRewards?.let { reward ->
                Text(words(R.string.daily_coins_collected, reward.day, reward.coins))
            }
            Text(words(R.string.daily_coins_rules))
            DAILY_COIN_REWARDS.forEachIndexed { index, amount ->
                Text(words(R.string.daily_coins_day, index + 1, amount))
            }
            Text(words(R.string.coin_free))
            Text(words(R.string.coin_ties))
            if (state.name != null) RewardedCoins(enabled, model)
            TextButton(onClick = { gameData = true }) { Text(words(R.string.privacy_open)) }
            if (state.name != null) TextButton(onClick = { delete = true }, enabled = !state.busy && !state.pending && !state.storageFailure) { Text(words(R.string.ui_delete_online_profile)) }
        }
    }, confirmButton = { TextButton(onClick = { dismiss() }) { Text(words(R.string.ui_got_it)) } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(words(R.string.ui_delete_online_profile)) },
        text = { Text(words(R.string.ui_this_permanently_removes_your_service_profile_and_access)) },
        confirmButton = { TextButton(onClick = { delete = false; model.deleteProfile(); dismiss() }) { Text(words(R.string.ui_delete_online_profile)) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(words(R.string.ui_keep_playing)) } })
    if (gameData) GameDataDialog { gameData = false }
    }
}
