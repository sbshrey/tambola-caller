package io.github.sbshrey.tambola.game

import android.app.Application
import io.github.sbshrey.tambola.game.audio.GameAudio
import io.github.sbshrey.tambola.game.data.GameDatabase

class TambolaApplication : Application() {
    val audio: GameAudio by lazy { GameAudio(this) }
    // Room outlives Activities: an outgoing screen may still be releasing a cancelled save.
    internal val database: GameDatabase by lazy { GameDatabase.open(this) }
}
