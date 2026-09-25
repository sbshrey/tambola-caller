package io.github.sbshrey.tambola.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer

/** Packaged recordings only. No network, microphone, or developer API key. */
class CallAudio(private val context: Context, private val onFailure: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    val isPlaying: Boolean get() = player != null

    fun play(number: Int, language: String) {
        stop()
        require(number in 1..90 && language in setOf("en", "hi", "hinglish"))
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
            }.build()
        focus = request
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { stop(); return }
        try {
            val media = MediaPlayer()
            player = media
            media.setAudioAttributes(attributes)
            context.assets.openFd("voices/$language/numbers/%02d.mp3".format(number)).use { media.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            media.setOnPreparedListener { if (player === it) it.start() }
            media.setOnCompletionListener { if (player === it) stop() }
            media.setOnErrorListener { instance, _, _ -> if (player === instance) { stop(); onFailure() }; true }
            media.prepareAsync()
        } catch (_: Exception) { stop(); onFailure() }
    }
    fun stop() {
        val old = player
        player = null
        old?.release()
        focus?.let { manager.abandonAudioFocusRequest(it) }
        focus = null
    }
}
