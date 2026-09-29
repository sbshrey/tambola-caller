package io.github.sbshrey.tambola.benchmark

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Two explicit stages around adb install -r; never clears installed app data. */
class BetaUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val target = "io.github.sbshrey.tambola.game.beta"
    private val marker get() = File(instrumentation.context.filesDir, "owned-upgrade-fixture.txt")
    private fun node(tag: String) = checkNotNull(device.wait(Until.findObject(By.res(tag)), 15000)) { "Missing $tag" }
    private fun text(node: UiObject2): String = (listOfNotNull(node.text) + node.children.map(::text)).joinToString(" ").trim()
    private fun tap(tag: String) {
        val end = SystemClock.elapsedRealtime() + 15000
        while (!node(tag).isEnabled) { check(SystemClock.elapsedRealtime() < end); SystemClock.sleep(100) }
        node(tag).click()
    }
    private fun button(label: String) {
        val labels = checkNotNull(device.wait(Until.findObjects(By.text(label)), 10000))
        for (match in labels.asReversed()) {
            var n: UiObject2? = match
            while (n != null) {
                if (n.isClickable && n.isEnabled) { n.click(); return }
                n = n.parent
            }
        }
        error("No enabled button contains $label")
    }
    private fun wallet(expected: Long) {
        val end = SystemClock.elapsedRealtime() + 15000
        while (!text(node("coin-wallet")).contains("$expected coins")) {
            check(SystemClock.elapsedRealtime() < end) { "Unexpected wallet after upgrade action" }
            SystemClock.sleep(150)
        }
    }
    private fun start(version: Long) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaUpgrade") == "true")
        check(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
        assertEquals(version, instrumentation.context.packageManager.getPackageInfo(target, 0).longVersionCode)
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        node("coin-play")
    }

    @Test fun prepareV40Profile() {
        start(40)
        check(!marker.exists()) { "An upgrade fixture already needs verification or cleanup" }
        node("lobby-welcome-heading"); wallet(50000)
        tap("coin-wallet")
        assertTrue(device.wait(Until.hasObject(By.text("Choose your player")), 10000))
        assertFalse(device.hasObject(By.text("Delete online profile")))
        button("Keep playing")
        // Record ownership before registration so an interrupted fixture is recoverable.
        marker.writeText("preparing")
        tap("buy-tickets-6"); tap("coin-play"); node("cancel-match")
        wallet(49900)
        tap("cancel-match"); node("coin-play"); wallet(50500)
        assertTrue(node("buy-tickets-6").isChecked)
        marker.writeText("v40:50500:6")
    }

    @Test fun verifyV41ProfileAndCleanUp() {
        start(41)
        check(marker.readText() == "v40:50500:6") { "Only verify the owned v40 fixture" }
        wallet(50500)
        assertFalse(device.hasObject(By.res("lobby-welcome-heading")))
        assertTrue(node("buy-tickets-6").isChecked)
        device.takeScreenshot(File(instrumentation.context.filesDir, "beta-v41-upgrade.png"))
        // A paid request after upgrade proves the saved session still authenticates.
        tap("coin-play"); node("cancel-match"); wallet(49900)
        tap("cancel-match"); node("coin-play"); wallet(50500)
        tap("coin-wallet")
        repeat(8) {
            if (!device.hasObject(By.text("Delete online profile")))
                device.findObjects(By.scrollable(true)).single().scroll(Direction.DOWN, .7f)
        }
        button("Delete online profile")
        assertTrue(device.wait(Until.hasObject(By.text("Keep playing")), 10000))
        button("Delete online profile")
        node("coin-play"); wallet(50000); node("lobby-welcome-heading")
        marker.delete()
        File(instrumentation.context.filesDir, "beta-v41-upgrade.txt").writeText(
            "PASS: v40 to v41 install -r retained wallet, six-ticket preference and authenticated session; purchase refunded; owned profile deleted.\n")
    }
}
