package io.github.sbshrey.tambola.game.ads

import androidx.compose.runtime.staticCompositionLocalOf

/** One consent session per activity, shared by the lobby and the always-accessible data dialog. */
val LocalRewardedAds = staticCompositionLocalOf<RewardedAdsController?> { null }
