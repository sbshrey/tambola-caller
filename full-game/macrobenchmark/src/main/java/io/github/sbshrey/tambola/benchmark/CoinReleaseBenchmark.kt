package io.github.sbshrey.tambola.benchmark

import android.content.pm.ApplicationInfo
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** External UI driver: never links the app, reads its files or calls its ViewModels. */
@OptIn(ExperimentalMacrobenchmarkApi::class)
class CoinReleaseBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val target = "io.github.sbshrey.tambola.game"

    private fun requireCandidate() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaBenchmark") == "true")
        val context = InstrumentationRegistry.getInstrumentation().context
        val info = context.packageManager.getApplicationInfo(target, 0)
        assertEquals("Measure a non-debuggable app", 0, info.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        assertTrue("The candidate must allow shell profiling", info.isProfileableByShell)
    }

    @Test fun coldCoinEntry() {
        requireCandidate()
        benchmark.measureRepeated(target, listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Ignore(), startupMode = StartupMode.COLD, iterations = 3,
            setupBlock = { pressHome() }) {
            startActivityAndWait()
            assertTrue(device.wait(Until.hasObject(By.res("coin-play")), 10_000))
            assertTrue(device.findObject(By.res("coin-play")).isEnabled)
        }
    }

    @Test fun realCoinRound() {
        requireCandidate()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        CoinJourney(instrumentation.context, UiDevice.getInstance(instrumentation)).use { journey ->
            benchmark.measureRepeated(target, listOf(FrameTimingMetric()), compilationMode = CompilationMode.Ignore(),
                iterations = 1, setupBlock = { startActivityAndWait(); journey.prepare() }) {
                journey.play()
            }
        }
    }
}
