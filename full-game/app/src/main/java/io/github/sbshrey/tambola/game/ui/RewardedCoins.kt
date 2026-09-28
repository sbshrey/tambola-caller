package io.github.sbshrey.tambola.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.sbshrey.tambola.game.BuildConfig
import io.github.sbshrey.tambola.game.R
import io.github.sbshrey.tambola.game.ads.LocalRewardedAds
import io.github.sbshrey.tambola.game.online.OnlineViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun RewardedCoins(enabled: Boolean, model: OnlineViewModel) {
    if (!BuildConfig.REWARDED_ADS_ENABLED) return
    val words = gameText()
    val controller = LocalRewardedAds.current ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableIntStateOf(0) }
    Column {
        OutlinedButton(enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().testTag("rewarded-coins"), onClick = {
            if (!model.beginAd()) return@OutlinedButton
            busy = true; message = R.string.ad_loading
            scope.launch {
                try {
                    val intent = if (BuildConfig.REWARDED_ADS_TEST) null else model.prepareAd()
                    val earned = controller.watch(intent?.id)
                    message = when {
                        !earned -> R.string.ad_not_completed
                        intent == null -> R.string.ad_test_complete
                        else -> {
                            message = R.string.ad_verifying
                            if (model.confirmAd(intent.id)) R.string.ad_confirmed else R.string.ad_pending
                        }
                    }
                } catch (error: CancellationException) { message = R.string.ad_unavailable; throw error }
                catch (_: Exception) { message = R.string.ad_unavailable }
                finally { busy = false; model.endAd() }
            }
        }) { Text(words(if (BuildConfig.REWARDED_ADS_TEST) R.string.ad_test_watch else R.string.ad_watch)) }
        if (message != 0) Text(words(message), style = MaterialTheme.typography.bodySmall)
        if (controller.privacyRequired) TextButton(onClick = controller::privacyOptions, enabled = !busy) { Text(words(R.string.ad_privacy)) }
    }
}
