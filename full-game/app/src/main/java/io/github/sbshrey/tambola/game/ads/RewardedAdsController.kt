package io.github.sbshrey.tambola.game.ads

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.*
import com.google.android.ump.*
import io.github.sbshrey.tambola.game.BuildConfig
import io.github.sbshrey.tambola.protocol.AD_REWARD_COINS
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** Consent updates at launch; videos load only after an explicit watch action. All SDK calls use Main. */
class RewardedAdsController(private val activity: Activity) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private val consentUpdate = Mutex()
    private var consentUpdated = false
    var privacyRequired by mutableStateOf(false)
        private set

    suspend fun updateConsent(): Boolean = consentUpdate.withLock {
        if (!BuildConfig.REWARDED_ADS_ENABLED) return@withLock false
        if (consentUpdated) return@withLock true
        if (BuildConfig.REWARDED_ADS_TEST) { consentUpdated = true; return@withLock true }
        withTimeout(30_000) {
            suspendCancellableCoroutine { continuation ->
                consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                    consentUpdated = true
                    updatePrivacyRequirement()
                    if (continuation.isActive) continuation.resume(true)
                }, {
                    updatePrivacyRequirement()
                    if (continuation.isActive) continuation.resume(false)
                })
            }
        }
    }

    private fun updatePrivacyRequirement() {
        privacyRequired = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun privacyOptions() {
        if (privacyRequired && !activity.isFinishing) UserMessagingPlatform.showPrivacyOptionsForm(activity) { updatePrivacyRequirement() }
    }

    suspend fun watch(intentId: String?): Boolean {
        check(BuildConfig.REWARDED_ADS_ENABLED && !activity.isFinishing && !activity.isDestroyed)
        check(BuildConfig.REWARDED_ADS_TEST || intentId != null) // Every real reward must have an SSV destination.
        if (!consentUpdated && !updateConsent()) error("Consent unavailable")
        val allowed = BuildConfig.REWARDED_ADS_TEST || withTimeout(30_000) { suspendCancellableCoroutine { continuation ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                updatePrivacyRequirement()
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
        if (!BuildConfig.REWARDED_ADS_TEST) {
            check(ad.rewardItem.amount.toLong() == AD_REWARD_COINS && ad.rewardItem.type == "coins")
        }
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
