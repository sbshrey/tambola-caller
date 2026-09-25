package io.github.sbshrey.tambola.game

import android.app.Application
import io.github.sbshrey.tambola.game.audio.GameAudio

class TambolaApplication : Application() {
    val audio: GameAudio by lazy { GameAudio(this) }
}
