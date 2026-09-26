package io.github.sbshrey.tambola.game

import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.tooling.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.ui.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** A real activity clock isolates dab animation work from server timers and UI automation polling. */
class CoinMarkTest {
    @OptIn(ExperimentalComposeRuntimeApi::class, ComposeToolingApi::class)
    @Test fun dabsAnimateWithoutRepeatedTicketComposition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaMarkGuard") == "true")
        check(isAndroidEmulator())
        val context = instrumentation.targetContext
        assertEquals(1f, Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE), 0f)
        var table by mutableStateOf(Round.create(listOf(Player("mark-fixture", "You")),
            RoundSettings(ticketsPerPlayer = 2, manualClaims = true), Random(53)).start().toTable()
            .copy(called = (1..90).toList()))
        val tickets = table.tickets
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val passes = AtomicLong()
        val registrations = AtomicLong()
        val handles = CopyOnWriteArrayList<CompositionObserverHandle>()
        val samples = JSONArray()
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    TambolaTheme {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            tickets.forEach { ticket -> CompactTicket(ticket, table,
                                Modifier.fillMaxWidth().height(160.dp), reducedMotion = false,
                                markNumber = { _, _ -> }, claimTicket = {}) }
                        }
                    }
                }
            }
            SystemClock.sleep(1000)
            val observer = object : CompositionObserver {
                override fun onBeginComposition(composition: ObservableComposition) { passes.incrementAndGet() }
                override fun onEndComposition(composition: ObservableComposition) = Unit
                override fun onScopeEnter(scope: RecomposeScope) = Unit
                override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
                override fun onScopeExit(scope: RecomposeScope) = Unit
                override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
                override fun onScopeDisposed(scope: RecomposeScope) = Unit
            }
            scenario.onActivity {
                Recomposer.runningRecomposers.value.forEach { recomposer ->
                    handles += recomposer.observe(object : CompositionRegistrationObserver {
                        override fun onCompositionRegistered(composition: ObservableComposition) {
                            registrations.incrementAndGet(); handles += composition.setObserver(observer)
                        }
                        override fun onCompositionUnregistered(composition: ObservableComposition) = Unit
                    })
                }
            }
            assertTrue(registrations.get() > 0)
            repeat(12) { index ->
                val ticket = tickets[index % tickets.size]
                val number = ticket.numbers[index / 4]
                val before = passes.get()
                val start = SystemClock.elapsedRealtime()
                scenario.onActivity {
                    val marks = table.marks[ticket.id].orEmpty()
                    table = table.copy(marks = table.marks + (ticket.id to
                        if (number in marks) marks - number else marks + number))
                }
                SystemClock.sleep(650)
                samples.put(JSONObject().put("index", index).put("compositionPasses", passes.get() - before)
                    .put("sampleMs", SystemClock.elapsedRealtime() - start))
            }
            File(context.filesDir, "coin-mark-compositions.json").writeText(JSONObject()
                .put("scope", "Two real CompactTicket grids; 12 state-driven mark/unmark changes; real activity clock; animations enabled")
                .put("observedCompositions", registrations.get()).put("samples", samples).toString(2) + "\n")
            for (index in 0 until samples.length()) assertTrue("Dab animation repeatedly recomposed the ticket: ${samples.getJSONObject(index)}",
                samples.getJSONObject(index).getLong("compositionPasses") <= 3)
        } finally {
            scenario.onActivity { handles.reversed().forEach { it.dispose() } }
            scenario.close()
        }
    }
}
