package io.github.sbshrey.tambola.game

import android.app.Application
import io.github.sbshrey.tambola.game.audio.GameAudio
import io.github.sbshrey.tambola.game.data.GameDatabase
import io.github.sbshrey.tambola.game.data.PreferenceStore
import io.github.sbshrey.tambola.game.telemetry.BetaTelemetry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

class TambolaApplication : Application() {
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun onCreate() {
        super.onCreate()
        diagnosticsScope.launch {
            PreferenceStore(this@TambolaApplication).values.map { it.diagnostics }.distinctUntilChanged().catch { }.collect {
                BetaTelemetry.configure(this@TambolaApplication, it)
            }
        }
    }
    val audio: GameAudio by lazy { GameAudio(this) }
    // Room outlives Activities: an outgoing screen may still be releasing a cancelled save.
    internal val database: GameDatabase by lazy { GameDatabase.open(this) }
}
