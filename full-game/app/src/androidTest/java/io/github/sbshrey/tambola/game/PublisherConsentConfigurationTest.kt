package io.github.sbshrey.tambola.game

import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Opt-in account configuration check. Loads a form on an emulator; never requests or displays an ad. */
class PublisherConsentConfigurationTest {
    @Test fun publisherCanReturnAnEeaConsentForm() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaPublisherConsent") == "true")
        check(BuildConfig.DEBUG && isAndroidEmulator())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "io.github.sbshrey.tambola.game.beta")
        @Suppress("DEPRECATION")
        val app = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        check(app.metaData.getString("com.google.android.gms.ads.APPLICATION_ID") == "ca-app-pub-1312548197553464~7883782841")
        val outcome = AtomicReference<JSONObject>()
        fun failed(stage: String, error: FormError) = outcome.set(JSONObject()
            .put("stage", stage).put("formLoaded", false).put("errorCode", error.errorCode)
            .put("category", when {
                error.message.contains("misconfiguration", ignoreCase = true) -> "publisher_misconfiguration"
                error.message.contains("network", ignoreCase = true) -> "network"
                else -> "sdk_error"
            }))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val consent = UserMessagingPlatform.getConsentInformation(activity)
                consent.reset() // Test APK on the owned emulator only; never in the release app.
                val settings = ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA).build()
                val parameters = ConsentRequestParameters.Builder().setConsentDebugSettings(settings).build()
                consent.requestConsentInfoUpdate(activity, parameters, {
                    if (!consent.isConsentFormAvailable) {
                        outcome.set(JSONObject().put("stage", "consent_update").put("formLoaded", false)
                            .put("category", "form_unavailable").put("consentStatus", consent.consentStatus))
                    } else {
                        UserMessagingPlatform.loadConsentForm(activity, {
                            outcome.set(JSONObject().put("stage", "form_load").put("formLoaded", true)
                                .put("consentRequired", consent.consentStatus == ConsentInformation.ConsentStatus.REQUIRED))
                        }, { failed("form_load", it) })
                    }
                }, { failed("consent_update", it) })
            }
            val deadline = SystemClock.elapsedRealtime() + 60_000
            while (outcome.get() == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            val result = (outcome.get() ?: JSONObject().put("stage", "timeout").put("formLoaded", false))
                .put("emulatorOnly", true).put("forcedGeography", "EEA").put("adRequested", false)
            File(context.filesDir, "publisher-consent-check.json").writeText(result.toString(2))
            assertTrue("Publisher consent configuration did not return a form; see publisher-consent-check.json", result.getBoolean("formLoaded"))
        }
    }
}
