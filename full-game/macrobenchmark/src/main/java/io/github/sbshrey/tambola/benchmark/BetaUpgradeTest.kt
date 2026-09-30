package io.github.sbshrey.tambola.benchmark

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/** Explicit stages around a published in-app update; never clears installed app data. */
class BetaUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation).also { Configurator.getInstance().waitForIdleTimeout = 100 }
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
    private fun leaveWaitingRoom() {
        repeat(3) {
            tap("cancel-match")
            val end = SystemClock.elapsedRealtime() + 8000
            while (SystemClock.elapsedRealtime() < end && !device.hasObject(By.res("coin-play")) && !device.hasObject(By.text("Got it"))) SystemClock.sleep(100)
            if (device.hasObject(By.res("coin-play"))) return
            // Progressive joins can change the revision between reading and cancelling.
            if (device.hasObject(By.text("The room changed. Review its latest state and try your action again."))) {
                button("Got it")
            } else error("Room cancellation did not return to the lobby")
        }
        error("Room cancellation kept conflicting")
    }
    private fun start(version: Long) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaUpgrade") == "true")
        check(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
        assertEquals(version, instrumentation.context.packageManager.getPackageInfo(target, 0).longVersionCode)
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        if (version >= 44) {
            val gameChoice = device.wait(Until.findObject(By.res("choose-tambola")), 15000)
            gameChoice?.click()
        }
        check(device.wait(Until.hasObject(By.res("coin-play")), 15000) ||
            device.hasObject(By.res("cancel-match")) || device.hasObject(By.res("resume-match")) || device.hasObject(By.text("Got it")))
    }

    @Test fun prepareV43Profile() {
        start(43)
        check(!marker.exists()) { "An upgrade fixture already needs verification or cleanup" }
        node("lobby-welcome-heading"); wallet(50000)
        tap("coin-wallet")
        assertTrue(device.wait(Until.hasObject(By.text("Choose your player")), 10000))
        assertFalse(device.hasObject(By.text("Delete online profile")))
        button("Keep playing")
        // Record ownership before registration so an interrupted fixture is recoverable.
        marker.writeText("preparing")
        tap("buy-tickets-6"); tap("play-friends"); button("Create table"); node("cancel-match")
        wallet(49900)
        leaveWaitingRoom(); wallet(50500)
        assertTrue(node("buy-tickets-6").isChecked)
        marker.writeText("v43:50500:6")
    }

    @Test fun cleanInterruptedOwnedFixture() {
        val version = instrumentation.context.packageManager.getPackageInfo(target, 0).longVersionCode
        check(version in setOf(43L, 44L))
        start(version)
        check(marker.readText() in setOf("preparing", "v43:50500:6"))
        if (device.hasObject(By.text("Got it"))) button("Got it")
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
    }

    @Test fun verifyV43AgainstMultiGameHost() {
        start(43)
        check(marker.readText() == "v43:50500:6")
        wallet(50500); assertTrue(node("buy-tickets-6").isChecked)
        tap("play-friends"); button("Create table"); node("cancel-match"); wallet(49900)
        leaveWaitingRoom(); wallet(50500)
    }

    @Test fun verifyV44ProfileAndCleanUp() {
        start(44)
        check(marker.readText() == "v43:50500:6") { "Only verify the owned v43 fixture" }
        if (device.hasObject(By.text("The room changed. Review its latest state and try your action again."))) button("Got it")
        if (device.hasObject(By.res("cancel-match"))) leaveWaitingRoom()
        wallet(50500)
        assertFalse(device.hasObject(By.res("lobby-welcome-heading")))
        assertTrue(node("buy-tickets-6").isChecked)
        device.takeScreenshot(File(instrumentation.context.filesDir, "beta-v44-upgrade.png"))
        // A paid request after upgrade proves the saved session still authenticates.
        tap("play-friends"); button("Create table"); node("cancel-match"); wallet(49900)
        leaveWaitingRoom(); wallet(50500)
        // Exercise the new serialized Bingo requests in the optimized installed APK.
        tap("games-home"); tap("choose-bingo"); tap("bingo-online-cards-6")
        button("Friends"); button("Create table"); node("bingo-online-leave")
        assertTrue(device.wait(Until.hasObject(By.text("49900 coins")), 15000))
        device.takeScreenshot(File(instrumentation.context.filesDir, "beta-v44-bingo-table.png"))
        tap("bingo-online-leave"); node("bingo-online-play")
        assertTrue(device.wait(Until.hasObject(By.text("50500 coins")), 15000))
        assertTrue(node("bingo-online-cards-6").isChecked)
        tap("bingo-practice-mode"); tap("bingo-cards-6"); tap("bingo-deal")
        node("bingo-card"); repeat(5) { tap("bingo-next") }
        assertFalse(node("bingo-next").isEnabled)
        device.takeScreenshot(File(instrumentation.context.filesDir, "beta-v44-bingo-six-cards.png"))
        tap("bingo-home"); tap("choose-tambola"); wallet(50500)
        assertTrue(node("buy-tickets-6").isChecked)
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
        File(instrumentation.context.filesDir, "beta-v44-upgrade.txt").writeText(
            "PASS: v43 to v44 retained wallet, six-ticket preference and authenticated session; Tambola and six-card Bingo purchases refunded; Bingo practice paging passed; owned profile deleted.\n")
    }

    @Test fun installPublishedV44ThroughUpdater() {
        start(43)
        check(marker.readText() == "v43:50500:6") { "Prepare the owned profile before checking the public updater" }
        assertTrue(device.wait(Until.hasObject(By.text("Download")), 60000))
        device.takeScreenshot(File(instrumentation.context.filesDir, "beta-v44-update-offer.png"))
        button("Download")
        assertTrue(device.wait(Until.hasObject(By.text("Install update")), 240000))
        assertEquals(43L, instrumentation.context.packageManager.getPackageInfo(target, 0).longVersionCode)
        device.takeScreenshot(File(instrumentation.context.filesDir, "beta-v44-update-ready.png"))
        button("Install update")
        val allow = device.wait(Until.findObject(By.clazz("android.widget.Switch")), 5000)
        if (allow != null) {
            assertEquals("com.android.settings", device.currentPackageName)
            if (!allow.isChecked) allow.click()
            device.pressBack()
            // Android may recreate the app after changing package-install permission.
            // Its update offer is intentionally in memory; restart the verified download.
            device.wait(Until.hasObject(By.text("Install update")), 3000)
            if (device.hasObject(By.text("Download"))) {
                button("Download")
                assertTrue(device.wait(Until.hasObject(By.text("Install update")), 240000))
            }
            button("Install update")
        }
        val install = checkNotNull(device.wait(Until.findObject(By.text(Pattern.compile("(?i)install|update"))), 15000))
        assertTrue(device.currentPackageName.endsWith("packageinstaller"))
        device.takeScreenshot(File(instrumentation.context.filesDir, "beta-v44-android-confirmation.png"))
        button(install.text)
        val end = SystemClock.elapsedRealtime() + 60000
        while (instrumentation.context.packageManager.getPackageInfo(target, 0).longVersionCode != 44L) {
            check(SystemClock.elapsedRealtime() < end) { "Android did not finish the update" }
            SystemClock.sleep(500)
        }
        // Package metadata changes before the installer completes its activity cleanup.
        assertTrue(device.wait(Until.hasObject(By.text(Pattern.compile("(?i)done"))), 60000))
        button(device.findObject(By.text(Pattern.compile("(?i)done"))).text)
        device.pressHome()
        device.waitForIdle()
        start(44)
        wallet(50500)
        File(instrumentation.context.filesDir, "beta-v44-public-updater.txt").writeText(
            "PASS: v43 detected the published GitHub update, downloaded and validated it, requested Android confirmation, and installed v44 through the app updater.\n")
    }
}
