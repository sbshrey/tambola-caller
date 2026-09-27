package io.github.sbshrey.tambola.game.ads

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.*
import com.google.android.ump.*
import io.github.sbshrey.tambola.game.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Invoked only from the lobby and only after the player asks to watch. All SDK calls use Main. */
class RewardedAdsController(private val activity: Activity) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private var consentUpdated = false
    var privacyRequired by mutableStateOf(false)
        private set

    suspend fun updateConsent(): Boolean = withTimeout(30_000) {
        if (!BuildConfig.REWARDED_ADS_ENABLED) return@withTimeout false
        if (BuildConfig.REWARDED_ADS_TEST) { consentUpdated = true; return@withTimeout true }
        suspendCancellableCoroutine { continuation ->
            consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                consentUpdated = true
                privacyRequired = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                if (continuation.isActive) continuation.resume(true)
            }, { if (continuation.isActive) continuation.resume(false) })
        }
    }

    fun privacyOptions() {
        if (privacyRequired && !activity.isFinishing) UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    suspend fun watch(intentId: String?): Boolean {
        check(BuildConfig.REWARDED_ADS_ENABLED && !activity.isFinishing && !activity.isDestroyed)
        if (!consentUpdated && !updateConsent()) error("Consent unavailable")
        val allowed = BuildConfig.REWARDED_ADS_TEST || withTimeout(30_000) { suspendCancellableCoroutine { continuation ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (continuation.isActive) continuation.resume(error == null && consent.canRequestAds())
            }
        } }
        check(allowed)
        withTimeout(30_000) { suspendCancellableCoroutine { continuation ->
            MobileAds.initialize(activity) { if (continuation.isActive) continuation.resume(Unit) }
        } }
        val ad = withTimeout(45_000) { suspendCancellableCoroutine<RewardedAd> { continuation ->
            RewardedAd.load(activity, BuildConfig.ADMOB_REWARD_UNIT, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { if (continuation.isActive) continuation.resume(ad) }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (continuation.isActive) continuation.cancel(IllegalStateException("Ad unavailable"))
                }
            })
        } }
        if (intentId != null) ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder().setCustomData(intentId).build())
        return suspendCancellableCoroutine { continuation ->
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { if (continuation.isActive) continuation.resume(earned) }
                override fun onAdFailedToShowFullScreenContent(error: AdError) { if (continuation.isActive) continuation.resume(false) }
            }
            ad.show(activity) { earned = true } // This signal never changes the coin balance.
        }
    }
}
