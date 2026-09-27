package io.github.sbshrey.tambola.benchmark

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.sbshrey.tambola.client.PublicEndpointDirectory
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class InternetBetaTest {
    @Test fun fullRound() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaInternetBeta") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val target = "io.github.sbshrey.tambola.game.beta"
        assertEquals(0, instrumentation.context.packageManager.getApplicationInfo(target, 0).flags and ApplicationInfo.FLAG_DEBUGGABLE)
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        CoinJourney(instrumentation.context, device, computerOpponents = true, target = target,
            discoveryUrl = PublicEndpointDirectory.DIRECTORY_URL).use { journey ->
            journey.prepare()
            journey.play()
        }
    }
}
