package io.github.sbshrey.tambola.game

import android.os.*
import android.util.AtomicFile
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/** Opt-in real-time offline session; ordinary smoke tests exclude this class. */
class LongSessionTest {
    @Test fun automaticGamesAndRematchesRetainProgressForAnHour() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Use the dedicated long-session driver", args.getString("tambolaLongSession") == "true")
        check(BuildConfig.DEBUG && isAndroidEmulator())
        val runId = checkNotNull(args.getString("tambolaRunId"))
        check(UUID.fromString(runId).toString() == runId)
        val rounds = (args.getString("tambolaRounds") ?: "9").toInt().also { require(it in 3..9) }
        val draws = (args.getString("tambolaDraws") ?: "90").toInt().also { require(it in 2..90) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceStore(context)
        val originalPreferences = preferences.values.first()
        val report = JSONObject().put("runId", runId).put("pid", android.os.Process.myPid())
            .put("configuredRounds", rounds).put("configuredDraws", draws).put("ticketsPerPlayer", 6)
            .put("computerPlayers", 2).put("intervalSeconds", 5).put("apiLevel", Build.VERSION.SDK_INT)
            .put("completed", false).put("fullSessionAtLeast60Minutes", false)
            .put("scope", "Debug emulator Activity, real app timers/storage/audio, ViewModel setup/rematch actions; not physical-device, release-build, cold-start or mobile-network acceptance")
        val checkpoints = JSONArray()
        val managedHeaps = mutableListOf<Long>()
        val pss = mutableListOf<Int>()
        val frames = SessionFrames()
        val started = SystemClock.elapsedRealtime()
        var activePlayMs = 0L
        var scenario: ActivityScenario<MainActivity>? = null
        var window: Window? = null
        var stage = "initialize"
        val handlerThread = HandlerThread("tambola-session-frames").apply { start() }
        fun persist() {
            report.put("stage", stage).put("elapsedMs", SystemClock.elapsedRealtime() - started)
                .put("activePlayMs", activePlayMs).put("rounds", checkpoints).put("frames", frames.snapshot())
            val file = AtomicFile(File(context.filesDir, "long-session.json"))
            val stream = file.startWrite()
            try { stream.write((report.toString(2) + "\n").toByteArray()); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
        }
        try {
            withTimeout(85 * 60_000L) {
                preferences.update(Preferences(voice = true, music = true, effects = true, haptics = false,
                    reducedMotion = false, interval = 5, tutorialCompleted = true, appearance = Appearance.DARK))
                val activity = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
                lateinit var model: GameViewModel
                activity.onActivity {
                    model = ViewModelProvider(it)[GameViewModel::class.java]
                    window = it.window
                    it.window.addOnFrameMetricsAvailableListener(frames, Handler(handlerThread.looper))
                    @Suppress("DEPRECATION")
                    report.put("displayRefreshRateHz", it.windowManager.defaultDisplay.refreshRate.toDouble())
                }
                suspend fun await(timeoutMs: Long = 20_000, condition: () -> Boolean) {
                    withTimeout(timeoutMs) {
                        while (!condition()) {
                            check(model.state.value.error == null) { "app_error" }
                            delay(100)
                        }
                    }
                    check(model.state.value.error == null) { "app_error" }
                }
                await { !model.state.value.loading && model.state.value.preferences.music }
                activity.onActivity { model.deleteHistory() }
                await { model.state.value.round == null && model.state.value.history.isEmpty() }
                activity.onActivity {
                    model.setup(GameMode.PRACTICE)
                    model.updateSetup(model.state.value.setupDraft.copy(names = "Session fixture", tickets = 6,
                        bots = 2, assisted = true, playAllNumbers = true))
                    model.create(automatic = true)
                }
                var previousId: String? = null
                var previousCards: List<List<Int>>? = null
                repeat(rounds) { index ->
                    val ordinal = index + 1
                    stage = "round-$ordinal-start"; persist()
                    await { model.state.value.round?.id != previousId && model.state.value.auto && !model.state.value.saving }
                    val initial = model.state.value.round!!
                    val cards = initial.tickets.filter { it.playerId == "p0" }.map { it.cells }
                    check(cards.size == 6 && cards.flatten().filter { it != 0 }.sorted() == (1..90).toList())
                    check(previousCards == null || cards != previousCards)
                    check(initial.tickets.size == 18 && initial.called.isEmpty())
                    await {
                        var keepsScreenOn = false
                        activity.onActivity { keepsScreenOn = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }
                        keepsScreenOn
                    }
                    val roundStarted = SystemClock.elapsedRealtime()
                    frames.active.set(true)
                    var reportedCalls = 0
                    await(12 * 60_000L) {
                        val state = model.state.value
                        val round = checkNotNull(state.round)
                        check(round.id == initial.id && state.screen == Screen.GAME)
                        check(round.status == RoundStatus.PLAYING || round.status == RoundStatus.COMPLETED)
                        if (round.called.size / 10 > reportedCalls) {
                            reportedCalls = round.called.size / 10
                            stage = "round-$ordinal-called-${round.called.size}"; persist()
                        }
                        round.called.size >= draws
                    }
                    if (draws < 90) activity.onActivity { model.finish() }
                    await { model.state.value.round!!.finished && !model.state.value.auto && model.state.value.history.size == ordinal }
                    activePlayMs += SystemClock.elapsedRealtime() - roundStarted
                    val finished = model.state.value.round!!
                    check(finished.called.size == draws && finished.called.distinct().size == draws)
                    check(finished.status == if (draws == 90) RoundStatus.COMPLETED else RoundStatus.CANCELLED)
                    check(finished.tickets == initial.tickets)
                    if (draws == 90) {
                        check(finished.called.sorted() == (1..90).toList())
                        check(finished.tickets.all { finished.marks[it.id].orEmpty().toSet() == it.numbers.toSet() })
                    }
                    delay(3_000) // Include the final award animation and voice tail.
                    frames.active.set(false)
                    activity.onActivity { model.pause(); model.dismissWin() }
                    delay(1_000)
                    // Requests are diagnostic: ART decides which collection to perform.
                    val gcBefore = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()
                    Runtime.getRuntime().gc(); System.runFinalization(); delay(500)
                    Runtime.getRuntime().gc(); delay(500)
                    val gcAfter = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()
                    val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                    val managed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                    managedHeaps += managed; pss += memory.totalPss
                    val sample = JSONObject().put("round", ordinal).put("calls", draws)
                        .put("appKeptScreenOnDuringPlay", true)
                        .put("roundElapsedMs", SystemClock.elapsedRealtime() - roundStarted)
                        .put("historyCount", model.state.value.history.size).put("managedHeapBytesAfterGcRequests", managed)
                        .put("nativeHeapAllocatedBytes", Debug.getNativeHeapAllocatedSize()).put("totalPssKiB", memory.totalPss)
                        .put("privateDirtyKiB", memory.totalPrivateDirty).put("gcCountBefore", gcBefore ?: JSONObject.NULL)
                        .put("gcCountAfter", gcAfter ?: JSONObject.NULL)
                    checkpoints.put(sample)
                    stage = "round-$ordinal-verified"; persist()
                    previousId = finished.id; previousCards = cards
                    if (ordinal < rounds) activity.onActivity { model.rematch(finished); model.create(automatic = true) }
                }
                val retained = managedHeaps.drop(1)
                val span = retained.max() - retained.min()
                report.put("managedHeapSpanAfterWarmupBytes", span)
                    .put("managedHeapSpanWithin16MiB", span <= 16 * 1024 * 1024)
                    .put("pssSpanAfterWarmupKiB", pss.drop(1).max() - pss.drop(1).min())
                    .put("fullSessionAtLeast60Minutes", rounds == 9 && draws == 90 && activePlayMs >= 3_600_000)
                    .put("completed", true)
                check(frames.count.get() > 0) { "no_frame_reports" }
                check(span <= 16 * 1024 * 1024) { "managed_heap_guard" }
                stage = "complete"
            }
        } catch (error: Throwable) {
            report.put("completed", false).put("failureType", error.javaClass.simpleName)
                .put("failureLocation", error.stackTrace.firstOrNull { it.className.startsWith(this@LongSessionTest.javaClass.name) }?.let { "${it.fileName}:${it.lineNumber}" })
            throw error
        } finally {
            frames.active.set(false)
            val cleanupErrors = JSONArray()
            fun clean(label: String, action: () -> Unit) { try { action() } catch (_: Exception) { cleanupErrors.put(label) } }
            clean("frame_listener") {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    window?.removeOnFrameMetricsAvailableListener(frames)
                }
            }
            clean("activity") { scenario?.close() }
            clean("frame_thread") { handlerThread.quitSafely(); handlerThread.join(5_000); check(!handlerThread.isAlive) }
            withContext(NonCancellable) {
                try { preferences.update(originalPreferences); check(preferences.values.first() == originalPreferences) }
                catch (_: Exception) { cleanupErrors.put("preferences") }
            }
            report.put("cleanupComplete", cleanupErrors.length() == 0).put("cleanupErrors", cleanupErrors)
            persist()
            check(cleanupErrors.length() == 0) { "session_cleanup_failed" }
        }
    }
}

/** Fixed-size counters avoid retaining one object per rendered frame during the soak. */
private class SessionFrames : Window.OnFrameMetricsAvailableListener {
    val active = AtomicBoolean(false)
    val count = AtomicLong()
    private val dropped = AtomicLong()
    private val unavailable = AtomicLong()
    private val firstDraw = AtomicLong()
    private val over60Hz = AtomicLong()
    private val maxNanos = AtomicLong()
    private val histogram = AtomicLongArray(2_002)
    override fun onFrameMetricsAvailable(window: Window, metrics: FrameMetrics, droppedReports: Int) {
        if (!active.get()) return
        dropped.addAndGet(droppedReports.toLong())
        val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        if (duration < 0) { unavailable.incrementAndGet(); return }
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) { firstDraw.incrementAndGet(); return }
        count.incrementAndGet()
        if (duration > 16_666_667) over60Hz.incrementAndGet()
        maxNanos.updateAndGet { maxOf(it, duration) }
        histogram.incrementAndGet(((duration + 999_999) / 1_000_000).coerceAtMost(2_001).toInt())
    }
    fun snapshot(): JSONObject {
        val total = count.get()
        var accumulated = 0L
        var p95: Int? = null
        for (bucket in 0 until histogram.length()) {
            accumulated += histogram.get(bucket)
            if (total > 0 && accumulated >= kotlin.math.ceil(total * .95).toLong()) { p95 = bucket; break }
        }
        return JSONObject().put("observedNonFirstDrawFrames", total).put("firstDrawFramesExcluded", firstDraw.get())
            .put("droppedReports", dropped.get()).put("unavailableDurations", unavailable.get())
            .put("over16666667Nanoseconds", over60Hz.get()).put("maximumMs", maxNanos.get() / 1e6)
            .put("p95BucketUpperMs", p95?.takeIf { it <= 2_000 } ?: JSONObject.NULL)
            .put("p95Beyond2000Ms", p95 == 2_001).put("reportsComplete", dropped.get() == 0L && unavailable.get() == 0L)
    }
}
