package io.github.sbshrey.tambola.game

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.game.audio.*
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.setup.SetupDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GameAudioTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val mixer get() = GameAudio.get(context)
    private val model get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun snapshot(): GameAudio.Snapshot { lateinit var value: GameAudio.Snapshot; main { value = mixer.snapshot() }; return value }
    private fun until(condition: () -> Boolean) = compose.waitUntil(12_000, condition)
    private fun configure(value: Preferences) {
        runBlocking { PreferenceStore(context).update(value) }
        until { model.state.value.preferences == value && !model.state.value.loading }
        main { mixer.resumeSound() }
    }
    private fun sound() = CallAudio(context) { throw AssertionError("Packaged voice failed to play") }

    @After fun quiet() {
        configure(Preferences(voice = false, music = false, effects = false, tutorialCompleted = true))
        main { mixer.setForeground(false) }
    }

    @Test fun realPlayersDuckMusicQueueOneWinAndReleaseIdleFocus() {
        configure(Preferences(music = true, musicVolume = 50, effectsVolume = 35, tutorialCompleted = true))
        val caller = sound()
        until { snapshot().musicPlaying }
        main { caller.play(88, "en", celebration = true) }
        until { snapshot().voicePlaying }
        assertEquals(.075f, snapshot().musicGain, .001f)
        main { caller.effect(SoundCue.MARK) }
        assertNull(snapshot().effect)
        until { snapshot().effectPlaying && snapshot().effect == SoundCue.WIN }
        assertFalse(snapshot().voicePresent)
        assertEquals(.25f, snapshot().musicGain, .001f)
        until { snapshot().effect == null }
        assertTrue(snapshot().musicPlaying)
        assertEquals(.5f, snapshot().musicGain, .001f)
        configure(model.state.value.preferences.copy(music = false, voice = false, effects = false))
        val silent = snapshot()
        assertFalse(silent.musicPresent); assertFalse(silent.focusHeld)
        main { caller.play(90, "hi", celebration = true); caller.effect(SoundCue.DEAL) }
        assertFalse(snapshot().voicePresent); assertNull(snapshot().effect); assertFalse(snapshot().focusHeld)
    }

    @Test fun cancellingPreparationAndAnotherOwnerCannotLeakOrCancelNewVoice() {
        configure(Preferences(music = true, tutorialCompleted = true))
        val old = sound(); val current = sound()
        until { snapshot().musicPlaying }
        main {
            old.play(88, "en", celebration = true)
            old.stop() // Cancel while preparing; its late callbacks must not create a win.
            current.play(89, "hinglish")
            old.stop()
            assertTrue(current.isPlaying)
        }
        until { snapshot().voicePlaying }
        configure(model.state.value.preferences.copy(voiceVolume = 0, effectsVolume = 0))
        assertFalse(snapshot().voicePresent); assertNull(snapshot().effect)
        assertEquals(.45f, snapshot().musicGain, .001f)
        main { current.repeat(7, "en") }
        assertFalse(snapshot().voicePresent)
        configure(model.state.value.preferences.copy(voice = false, voiceVolume = 70))
        main { current.repeat(7, "en") }
        until { snapshot().voicePlaying } // Explicit hear-again works with automatic speech disabled.
        main { current.stop() }
        assertFalse(snapshot().voicePresent); assertNull(snapshot().effect)
    }

    @Test fun competingFocusAndBackgroundResumeOnlyMusicAndRouteLossRequiresAction() {
        configure(Preferences(music = true, tutorialCompleted = true))
        val caller = sound()
        until { snapshot().musicPlaying }
        main { caller.play(88, "en", celebration = true) }
        until { snapshot().voicePlaying }
        val manager = context.getSystemService(AudioManager::class.java)
        val competitor = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener({}, Handler(Looper.getMainLooper())).build()
        try {
            main { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(competitor)) }
            until { mixer.pause.value == SoundPause.WAITING }
            assertFalse(snapshot().musicPresent); assertFalse(snapshot().voicePresent); assertNull(snapshot().effect)
        } finally { main { manager.abandonAudioFocusRequest(competitor) } }
        until { snapshot().musicPlaying }
        assertFalse(snapshot().voicePresent); assertNull(snapshot().effect)
        try {
            main { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(competitor)) }
            until { mixer.pause.value == SoundPause.WAITING }
            // Muting during a transient loss abandons focus; it must not leave a permanent
            // Waiting notice with no focus callback and no Resume action.
            runBlocking { PreferenceStore(context).update(model.state.value.preferences.copy(music = false)) }
            until { !model.state.value.preferences.music && mixer.pause.value == SoundPause.INTERRUPTED }
            assertFalse(snapshot().focusHeld)
        } finally { main { manager.abandonAudioFocusRequest(competitor) } }
        runBlocking { PreferenceStore(context).update(model.state.value.preferences.copy(music = true)) }
        until { model.state.value.preferences.music }
        assertFalse(snapshot().musicPresent)
        main { mixer.resumeSound() }
        until { snapshot().musicPlaying }
        main { caller.play(90, "hi", celebration = true) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(snapshot().focusHeld); assertFalse(snapshot().musicPresent); assertFalse(snapshot().voicePresent)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        until { snapshot().musicPlaying }
        assertFalse(snapshot().voicePresent); assertNull(snapshot().effect)
        // Exercises the same callback as ACTION_AUDIO_BECOMING_NOISY, not a physical unplug claim.
        main { mixer.onRouteDisconnected(); caller.play(1, "en") }
        assertEquals(SoundPause.HEADPHONES, mixer.pause.value)
        assertFalse(snapshot().focusHeld); assertFalse(snapshot().voicePresent)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(SoundPause.HEADPHONES, mixer.pause.value)
        assertFalse(snapshot().musicPresent)
        main { mixer.resumeSound() }
        until { snapshot().musicPlaying }
        assertFalse(snapshot().voicePresent); assertNull(snapshot().effect)
    }

    @Test fun savedIndependentSoundControlsRemainUsableAfterRecreation() {
        configure(Preferences(voice = false, music = false, effects = false, tutorialCompleted = true,
            voiceVolume = 65, musicVolume = 25, effectsVolume = 40))
        compose.onNodeWithTag("lobby-settings").performClick()
        compose.onNodeWithTag("setting-music").performClick()
        until { model.state.value.preferences.music && snapshot().musicPlaying }
        captureTestScreen("audio-music-settings")
        compose.onNodeWithTag("setting-effects").performClick()
        until { model.state.value.preferences.effects }
        captureTestScreen("audio-effects-settings")
        compose.activityRule.scenario.recreate()
        until { snapshot().musicPlaying && !model.state.value.loading }
        val prefs = model.state.value.preferences
        assertFalse(prefs.voice); assertTrue(prefs.music); assertTrue(prefs.effects)
        assertEquals(65, prefs.voiceVolume); assertEquals(25, prefs.musicVolume); assertEquals(40, prefs.effectsVolume)
        assertEquals(.25f, snapshot().musicGain, .001f)
        compose.onNodeWithTag("setting-music").performClick()
        until { !snapshot().musicPresent }
        compose.onNodeWithTag("setting-effects").performClick()
        until { !model.state.value.preferences.effects }
        assertFalse(snapshot().focusHeld)
    }

    @Test fun savedGameEventsCueOnceAndRestoreUndoAndInvalidMarksStaySilent() {
        configure(Preferences(voice = false, music = false, effects = true, tutorialCompleted = true))
        val prize = CustomPrize("custom_first", "First matching number", 10,
            TicketPattern(listOf(listOf(RuleCondition(NumberSelection.All, 1)))))
        val before = model.state.value.round?.id
        main { model.updateSetup(SetupDraft(bots = 0, playAllNumbers = true, customPrizes = listOf(prize), manualClaims = false)); model.create() }
        until { model.state.value.round?.id != before && !model.state.value.saving && snapshot().effectPlaying }
        assertEquals(SoundCue.DEAL, snapshot().effect)
        until { snapshot().effect == null }
        while (model.state.value.round!!.customAwards.isEmpty()) {
            val count = model.state.value.round!!.called.size
            assertTrue("Automatic custom prize must be awarded before the draw is exhausted", count < 90)
            main { model.draw() }
            until { model.state.value.round!!.called.size == count + 1 && snapshot().effectPlaying }
            assertEquals(if (model.state.value.round!!.customAwards.isEmpty()) SoundCue.CALL else SoundCue.WIN, snapshot().effect)
            until { snapshot().effect == null }
            android.os.SystemClock.sleep(550)
        }
        val round = model.state.value.round!!
        val ticket = round.tickets.single()
        val number = round.called.first { it in ticket.numbers }
        main { model.toggleMark(ticket.id, number) }
        until { number in model.state.value.round!!.marks[ticket.id].orEmpty() && snapshot().effectPlaying }
        assertEquals(SoundCue.MARK, snapshot().effect)
        until { snapshot().effect == null }
        main { model.toggleMark("missing-ticket", number) }
        until { model.state.value.error != null }
        assertNull(snapshot().effect)
        main { model.clearError(); model.undo() }
        until { model.state.value.round!!.called.size == round.called.size - 1 }
        assertFalse(snapshot().voicePresent); assertNull(snapshot().effect)
        compose.activityRule.scenario.recreate()
        until { !model.state.value.loading }
        assertEquals(round.id, model.state.value.round!!.id)
        assertFalse(snapshot().voicePresent); assertNull(snapshot().effect); assertFalse(snapshot().focusHeld)
    }
}
