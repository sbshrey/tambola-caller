package io.github.sbshrey.tambola.game.audio

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.TambolaApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SoundCue(val file: String) { MARK("mark"), CALL("call"), DEAL("deal"), WIN("win") }
enum class SoundPause(val message: String) {
    WAITING("Sound is paused while another app has audio focus."),
    INTERRUPTED("Sound was interrupted. Resume when you're ready."),
    HEADPHONES("Your audio device disconnected. Sound is paused."),
    UNAVAILABLE("Sound could not start. Another app may be using audio."),
    ERROR("A sound could not play. You can keep playing with the numbers on screen."),
}

/** One main-thread mixer and focus owner for the entire app. All assets are packaged offline. */
class GameAudio internal constructor(private val context: Application) {
    companion object {
        fun get(context: Context): GameAudio = (context.applicationContext as TambolaApplication).audio
    }
    private val manager = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val musicAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val speechAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private data class Slot(val player: MediaPlayer, val owner: Any? = null, val cue: SoundCue? = null,
        val after: SoundCue? = null, val failure: (() -> Unit)? = null, var ready: Boolean = false)
    private var voice: Slot? = null
    private var music: Slot? = null
    private var effect: Slot? = null
    private var focus: AudioFocusRequest? = null
    private var hasFocus = false
    private var foreground = false
    private var musicEligible = true
    private var preferences = Preferences()
    private var appliedMusicGain = 0f
    private val mutablePause = MutableStateFlow<SoundPause?>(null)
    val pause = mutablePause.asStateFlow()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onRouteDisconnected()
        }
    }
    private val wantsMusic get() = foreground && musicEligible && preferences.music && preferences.musicVolume > 0
    private val effectsAudible get() = preferences.effects && preferences.effectsVolume > 0
    private fun assertMain() = check(Looper.myLooper() == Looper.getMainLooper())

    fun configure(value: Preferences) {
        assertMain()
        val old = preferences
        preferences = value.copy(voiceVolume = value.voiceVolume.coerceIn(0, 100),
            musicVolume = value.musicVolume.coerceIn(0, 100), effectsVolume = value.effectsVolume.coerceIn(0, 100))
        if ((!value.voice && old.voice) || preferences.voiceVolume == 0) releaseVoice()
        if (!effectsAudible) releaseEffect()
        updateGains(); reconcile()
    }
    fun setForeground(value: Boolean) {
        assertMain()
        if (foreground == value) return
        foreground = value
        if (value) {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED)
            else { @Suppress("DEPRECATION") context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) }
            if (mutablePause.value != SoundPause.HEADPHONES && mutablePause.value != SoundPause.ERROR) mutablePause.value = null
            reconcile()
        } else {
            context.unregisterReceiver(noisy)
            releaseAll(); abandonFocus()
        }
    }
    fun setMusicEligible(value: Boolean) { assertMain(); musicEligible = value; reconcile() }
    fun resumeSound() {
        assertMain()
        if (!foreground) return
        mutablePause.value = null
        reconcile()
    }
    internal fun onRouteDisconnected() {
        assertMain()
        if (!foreground) return
        mutablePause.value = SoundPause.HEADPHONES
        releaseAll(); abandonFocus()
    }
    internal fun isAnnouncing(owner: Any): Boolean { assertMain(); return voice?.owner === owner }

    internal fun announce(owner: Any, number: Int, language: String, celebration: Boolean, explicit: Boolean, onFailure: () -> Unit) {
        assertMain()
        require(number in 1..90 && language in setOf("en", "hi", "hinglish"))
        if (!foreground) return
        if (explicit) resumeSound()
        if (mutablePause.value != null) return
        releaseVoice(); releaseEffect()
        if ((preferences.voice || explicit) && preferences.voiceVolume > 0) {
            if (!ensureFocus()) return
            val slot = Slot(MediaPlayer(), owner, after = SoundCue.WIN.takeIf { celebration }, failure = onFailure)
            voice = slot
            prepare(slot, "voices/$language/numbers/%02d.mp3".format(java.util.Locale.ROOT, number), speech = true)
        } else if (!explicit) {
            effect(owner, if (celebration) SoundCue.WIN else SoundCue.CALL)
        }
        updateGains(); reconcile()
    }
    internal fun effect(owner: Any, cue: SoundCue) {
        assertMain()
        if (!foreground || !effectsAudible || mutablePause.value != null) return
        // Marks must not mask an announcement or replace a winning moment.
        if (voice != null || (effect?.cue == SoundCue.WIN && cue == SoundCue.MARK)) return
        releaseEffect()
        if (!ensureFocus()) return
        val slot = Slot(MediaPlayer(), owner, cue)
        effect = slot
        prepare(slot, "sound/${cue.file}.wav")
        updateGains()
    }
    internal fun stop(owner: Any) {
        assertMain()
        if (voice?.owner === owner) releaseVoice()
        if (effect?.owner === owner) releaseEffect()
        reconcile()
    }

    private fun ensureFocus(): Boolean {
        if (!foreground || mutablePause.value != null) return false
        val gain = if (wantsMusic) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        if (hasFocus && (focus?.focusGain == gain || focus?.focusGain == AudioManager.AUDIOFOCUS_GAIN)) return true
        abandonFocus()
        lateinit var request: AudioFocusRequest
        request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(if (wantsMusic) musicAttributes else speechAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change -> if (focus === request) onFocusChanged(change) }, main).build()
        focus = request
        hasFocus = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus) { mutablePause.value = SoundPause.UNAVAILABLE; releaseAll(); abandonFocus() }
        return hasFocus
    }
    private fun onFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                if (mutablePause.value == SoundPause.WAITING) mutablePause.value = null
                reconcile() // Only ambient music resumes. Discarded announcements never replay.
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasFocus = false
                releaseAll()
                mutablePause.value = if (wantsMusic) SoundPause.WAITING else SoundPause.INTERRUPTED
                if (!wantsMusic) abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                mutablePause.value = SoundPause.INTERRUPTED
                releaseAll(); abandonFocus()
            }
        }
    }
    private fun reconcile() {
        if (!wantsMusic || mutablePause.value != null) releaseMusic()
        else if (music == null && ensureFocus()) {
            val slot = Slot(MediaPlayer())
            music = slot
            prepare(slot, "sound/game-night.wav", loop = true)
        }
        updateGains()
        if (voice == null && music == null && effect == null && !(wantsMusic && mutablePause.value == SoundPause.WAITING)) {
            // Muting music during an interruption removes our focus request. No later gain
            // callback can arrive, so expose an explicit resume action instead of waiting forever.
            if (mutablePause.value == SoundPause.WAITING) mutablePause.value = SoundPause.INTERRUPTED
            abandonFocus()
        }
    }
    private fun current(slot: Slot) = voice === slot || music === slot || effect === slot
    private fun prepare(slot: Slot, path: String, speech: Boolean = false, loop: Boolean = false) {
        try {
            slot.player.setAudioAttributes(if (speech) speechAttributes else musicAttributes)
            context.assets.openFd(path).use { slot.player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            slot.player.isLooping = loop
            slot.player.setOnPreparedListener {
                if (current(slot) && foreground && hasFocus && mutablePause.value == null) {
                    slot.ready = true
                    updateGains()
                    try { it.start() } catch (_: IllegalStateException) { failed(slot) }
                }
            }
            slot.player.setOnCompletionListener {
                if (current(slot)) {
                    val next = slot.after
                    val owner = slot.owner
                    if (voice === slot) releaseVoice() else if (effect === slot) releaseEffect() else releaseMusic()
                    if (next != null && owner != null) effect(owner, next)
                    reconcile()
                }
            }
            slot.player.setOnErrorListener { _, _, _ -> if (current(slot)) failed(slot); true }
            slot.player.prepareAsync()
        } catch (_: Exception) { if (current(slot)) failed(slot) }
    }
    private fun failed(slot: Slot) {
        val notify = slot.failure
        mutablePause.value = SoundPause.ERROR
        releaseAll(); abandonFocus()
        notify?.invoke()
    }
    private fun updateGains() {
        appliedMusicGain = if (music == null) 0f else preferences.musicVolume / 100f * when {
            voice != null -> .15f
            effect?.cue == SoundCue.WIN -> .5f
            else -> 1f
        }
        fun Slot?.volume(gain: Float) { if (this?.ready == true) player.setVolume(gain, gain) }
        voice.volume(preferences.voiceVolume / 100f)
        effect.volume(preferences.effectsVolume / 100f)
        music.volume(appliedMusicGain)
    }
    private fun releaseVoice() { val old = voice; voice = null; old?.player?.release() }
    private fun releaseEffect() { val old = effect; effect = null; old?.player?.release() }
    private fun releaseMusic() { val old = music; music = null; old?.player?.release(); appliedMusicGain = 0f }
    private fun releaseAll() { releaseVoice(); releaseEffect(); releaseMusic() }
    private fun abandonFocus() {
        val old = focus
        focus = null; hasFocus = false
        old?.let { manager.abandonAudioFocusRequest(it) }
    }

    /** Observes actual player state for device tests; no test-only playback paths. */
    internal data class Snapshot(val voicePresent: Boolean, val voicePlaying: Boolean, val musicPresent: Boolean,
        val musicPlaying: Boolean, val effect: SoundCue?, val effectPlaying: Boolean, val musicGain: Float, val focusHeld: Boolean)
    internal fun snapshot(): Snapshot {
        assertMain()
        fun Slot?.playing() = this?.let { it.ready && it.player.isPlaying } ?: false
        return Snapshot(voice != null, voice.playing(), music != null, music.playing(), effect?.cue, effect.playing(), appliedMusicGain, hasFocus)
    }
}
