package io.github.sbshrey.tambola.game.audio

import android.content.Context

/** An owner-scoped handle: leaving one table cannot cancel another table's announcement. */
class CallAudio(context: Context, private val onFailure: () -> Unit) {
    private val mixer = GameAudio.get(context)
    private val owner = Any()
    val isPlaying: Boolean get() = mixer.isAnnouncing(owner)
    fun play(number: Int, language: String, celebration: Boolean = false) =
        mixer.announce(owner, number, language, celebration, explicit = false, onFailure)
    fun repeat(number: Int, language: String) =
        mixer.announce(owner, number, language, celebration = false, explicit = true, onFailure)
    fun effect(cue: SoundCue) = mixer.effect(owner, cue)
    fun stop() = mixer.stop(owner)
}
