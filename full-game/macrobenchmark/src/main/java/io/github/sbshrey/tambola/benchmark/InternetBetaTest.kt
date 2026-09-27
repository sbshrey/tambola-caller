package io.github.sbshrey.tambola.benchmark

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.sbshrey.tambola.client.PublicEndpointDirectory
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class InternetBetaTest {
    @Test fun friendsHistory() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaInternetBeta") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val target = "io.github.sbshrey.tambola.game.beta"
        assertEquals(0, instrumentation.context.packageManager.getApplicationInfo(target, 0).flags and ApplicationInfo.FLAG_DEBUGGABLE)
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        CoinJourney(instrumentation.context, device, target = target, friendTable = true, checkCallHistory = true,
            discoveryUrl = PublicEndpointDirectory.DIRECTORY_URL).use { journey -> journey.prepare(); journey.play() }
    }

    @Test fun friendsReconnect() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaInternetBeta") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        check(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1") { "Network toggling is restricted to the owned emulator" }
        val target = "io.github.sbshrey.tambola.game.beta"
        assertEquals(0, instrumentation.context.packageManager.getApplicationInfo(target, 0).flags and ApplicationInfo.FLAG_DEBUGGABLE)
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        CoinJourney(instrumentation.context, device, target = target, friendTable = true,
            discoveryUrl = PublicEndpointDirectory.DIRECTORY_URL).use { it.verifyFriendsReconnect() }
    }

    @Test fun friendInvitation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaInternetBeta") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val target = "io.github.sbshrey.tambola.game.beta"
        assertEquals(0, instrumentation.context.packageManager.getApplicationInfo(target, 0).flags and ApplicationInfo.FLAG_DEBUGGABLE)
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        CoinJourney(instrumentation.context, device, target = target, friendTable = true,
            discoveryUrl = PublicEndpointDirectory.DIRECTORY_URL).use { it.verifyFriendInvitation() }
    }
    @Test fun friendsRound() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaInternetBeta") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val target = "io.github.sbshrey.tambola.game.beta"
        assertEquals(0, instrumentation.context.packageManager.getApplicationInfo(target, 0).flags and ApplicationInfo.FLAG_DEBUGGABLE)
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        CoinJourney(instrumentation.context, device, target = target, friendTable = true,
            discoveryUrl = PublicEndpointDirectory.DIRECTORY_URL).use { journey -> journey.prepare(); journey.play() }
    }

    @Test fun purchaseRefund() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaInternetBeta") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val target = "io.github.sbshrey.tambola.game.beta"
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        CoinJourney(instrumentation.context, device, computerOpponents = true, target = target,
            discoveryUrl = PublicEndpointDirectory.DIRECTORY_URL).use { it.verifyPurchaseRefund() }
    }

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
