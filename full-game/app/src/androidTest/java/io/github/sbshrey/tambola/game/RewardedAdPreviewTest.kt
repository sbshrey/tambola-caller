package io.github.sbshrey.tambola.game

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import io.github.sbshrey.tambola.game.ads.RewardedAdsController
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Opt-in network check with Google's official test unit; never uses the game service or grants coins. */
class RewardedAdPreviewTest {
    @Test fun officialTestVideoLoadsAndDismisses() {
        assumeTrue(BuildConfig.REWARDED_ADS_TEST)
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaAdsPreview") == "true")
        check(isAndroidEmulator())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val outcome = AtomicReference<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity -> scope.launch {
                    try { outcome.set("completed:" + RewardedAdsController(activity).watch(null)) }
                    catch (_: Exception) { outcome.set("unavailable") }
                } }
                val deadline = SystemClock.elapsedRealtime() + 90_000
                var shown = false
                while (!shown && outcome.get() == null && SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {
                        shown = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .any { it.javaClass.name == "com.google.android.gms.ads.AdActivity" }
                    }
                    SystemClock.sleep(250)
                }
                assertTrue("Official Google test ad did not open", shown)
                device.takeScreenshot(File(instrumentation.targetContext.filesDir, "rewarded-test-video.png"))
                while (outcome.get() == null && SystemClock.elapsedRealtime() < deadline) {
                    val close = device.findObject(By.desc("Close ad")) ?: device.findObject(By.desc("Close"))
                        ?: device.findObject(By.text("Close"))
                    if (close != null && close.isEnabled) close.click() else device.pressBack()
                    SystemClock.sleep(1_000)
                }
                assertTrue("Ad completion did not return to the app", outcome.get()?.startsWith("completed:") == true)
            } finally { scope.cancel() }
        }
    }
}
