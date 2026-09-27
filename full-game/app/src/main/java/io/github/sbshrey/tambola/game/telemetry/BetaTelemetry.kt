package io.github.sbshrey.tambola.game.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import io.github.sbshrey.tambola.game.BuildConfig
import kotlinx.coroutines.CancellationException

enum class TelemetryOperation(val trace: String) { REQUEST("online_request"), MATCH("join_table"), CLAIM("claim_prize"), LOGIN("login_rewards") }

/** No player identity, tickets, room codes, URLs, tokens or exception messages in custom telemetry. */
object BetaTelemetry {
    @Volatile private var enabled = false

    fun configure(context: Context, consent: Boolean) {
        if (!BuildConfig.TELEMETRY_CONFIGURED) return
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty() && FirebaseApp.initializeApp(context) == null) return
            val crashes = FirebaseCrashlytics.getInstance()
            crashes.setCrashlyticsCollectionEnabled(consent)
            FirebasePerformance.getInstance().isPerformanceCollectionEnabled = consent
            if (!consent) crashes.deleteUnsentReports()
            enabled = consent
        }.onFailure { enabled = false }
    }

    suspend fun <T> measure(operation: TelemetryOperation, work: suspend () -> T): T {
        val trace = if (enabled) runCatching { FirebasePerformance.getInstance().newTrace(operation.trace).also { it.start() } }.getOrNull() else null
        var outcome = "success"
        try { return work() }
        catch (error: CancellationException) { outcome = "cancelled"; throw error }
        catch (error: Exception) { outcome = "failure"; throw error }
        finally { runCatching { trace?.putAttribute("outcome", outcome); trace?.stop() } }
    }
}
