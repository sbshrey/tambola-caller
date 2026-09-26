package io.github.sbshrey.tambola.game

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.runtime.tooling.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.game.online.OnlineViewModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** Read-only idle-home diagnostic on the disposable emulator; never creates a guest or game. */
class CoinIdleTest {
    @OptIn(ExperimentalComposeRuntimeApi::class, ComposeToolingApi::class)
    @Test fun idleCoinEntryDoesNotNeedAClock() {
        check(isAndroidEmulator())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A real activity clock matters here: Compose test rules control the coroutine clock,
        // so sleeping in a rule-based test cannot measure the app's real timer work.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val compositions = AtomicLong()
        val registrations = AtomicLong()
        val handles = CopyOnWriteArrayList<CompositionObserverHandle>()
        val observer = object : CompositionObserver {
            override fun onBeginComposition(composition: ObservableComposition) { compositions.incrementAndGet() }
            override fun onEndComposition(composition: ObservableComposition) = Unit
            override fun onScopeEnter(scope: RecomposeScope) = Unit
            override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
            override fun onScopeExit(scope: RecomposeScope) = Unit
            override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
            override fun onScopeDisposed(scope: RecomposeScope) = Unit
        }
        try {
            lateinit var model: OnlineViewModel
            scenario.onActivity { model = ViewModelProvider(it)[OnlineViewModel::class.java] }
            val readyBy = SystemClock.elapsedRealtime() + 10_000
            while (model.state.value.loading && SystemClock.elapsedRealtime() < readyBy) SystemClock.sleep(50)
            assertFalse(model.state.value.loading)
            assertNull("Use an empty disposable emulator profile", model.state.value.name)
            SystemClock.sleep(1000)
            scenario.onActivity {
                Recomposer.runningRecomposers.value.forEach { recomposer ->
                    handles += recomposer.observe(object : CompositionRegistrationObserver {
                        override fun onCompositionRegistered(composition: ObservableComposition) {
                            registrations.incrementAndGet()
                            handles += composition.setObserver(observer)
                        }
                        override fun onCompositionUnregistered(composition: ObservableComposition) = Unit
                    })
                }
            }
            assertTrue("Observer must attach to a running composition", registrations.get() > 0)
            val before = compositions.get()
            val started = SystemClock.elapsedRealtime()
            SystemClock.sleep(3200)
            val delta = compositions.get() - before
            val report = JSONObject().put("sampleMs", SystemClock.elapsedRealtime() - started)
                .put("compositionPasses", delta).put("observedCompositions", registrations.get()).put("screen", "idle coin entry")
                .put("scope", "Dedicated Android emulator, no guest or room; onBeginComposition callbacks")
            File(context.filesDir, "coin-idle.json").writeText(report.toString(2) + "\n")
            if (InstrumentationRegistry.getArguments().getString("tambolaIdleGuard") == "true")
                assertTrue("An idle entry should not continuously recompose: $delta", delta <= 1)
        } finally {
            scenario.onActivity { handles.reversed().forEach { it.dispose() } }
            scenario.close()
        }
    }
}
