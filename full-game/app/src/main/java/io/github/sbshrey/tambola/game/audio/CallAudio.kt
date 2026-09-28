package io.github.sbshrey.tambola.game.audio

import android.content.Context

/** An owner-scoped handle: leaving one table cannot cancel another table's announcement. */
class CallAudio(private val context: Context, private val onFailure: () -> Unit) {
    private val mixer = GameAudio.get(context)
    private val owner = Any()
    val isPlaying: Boolean get() = mixer.isAnnouncing(owner)
    fun play(number: Int, language: String, celebration: Boolean = false) =
        mixer.announce(owner, number, language, celebration, explicit = false, onFailure)
    fun repeat(number: Int, language: String) =
        mixer.announce(owner, number, language, celebration = false, explicit = true, onFailure)
    fun effect(cue: SoundCue) = mixer.effect(owner, cue)
    fun prizes(moment: io.github.sbshrey.tambola.game.presentation.WinMoment, language: String) {
        val configuration = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(if (language == "en") "en" else "hi"))
        }
        val words = io.github.sbshrey.tambola.game.ui.GameText(context.createConfigurationContext(configuration).resources)
        val text = moment.lines.joinToString(". ") { line ->
            words(io.github.sbshrey.tambola.game.R.string.play_won, line.players.joinToString { words.playerLabel(it) },
                line.prize?.let(words::prizeTitle) ?: line.title)
        }
        mixer.announcePrize(owner, text, language)
    }
    fun stop() = mixer.stop(owner)
}
