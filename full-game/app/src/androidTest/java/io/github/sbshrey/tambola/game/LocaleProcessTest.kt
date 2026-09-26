package io.github.sbshrey.tambola.game

import android.app.LocaleConfig
import android.app.LocaleManager
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.game.data.*
import io.github.sbshrey.tambola.game.ui.GameText
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Opt-in fixture: an external driver must kill the seed and verify a different process. */
class LocaleProcessTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val model get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private val directory get() = File(context.noBackupFilesDir, "locale-process-fixture").apply { mkdirs() }
    private val runId get() = checkNotNull(args.getString("tambolaRunId"))
    private val json = Json { encodeDefaults = true }
    private fun until(predicate: () -> Boolean) = compose.waitUntil(20_000, predicate)
    private fun words() = GameText(compose.activity.resources)
    private fun tap(id: Int) = compose.tapText(words()(id))
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun write(name: String, text: String) {
        val file = AtomicFile(File(directory, name))
        val stream = file.startWrite()
        try { stream.write(text.toByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    @Before fun guard() {
        assumeTrue("Use the dedicated cold-locale driver", args.getString("tambolaLocaleProcess") == "true")
        check(BuildConfig.DEBUG)
        check(isAndroidEmulator())
        check(UUID.fromString(runId).toString() == runId)
        until { !model.state.value.loading }
    }

    @Test fun seedAndWaitForProcessKill() = runBlocking<Unit> {
        val original = LocaleOriginal(runId, AppCompatDelegate.getApplicationLocales().toLanguageTags())
        write("original.json", json.encodeToString(original))
        PreferenceStore(context).update(Preferences(language = "hinglish", voice = false, effects = false, reducedMotion = true,
            tutorialCompleted = true, appearance = Appearance.LIGHT))
        until { model.state.value.preferences.language == "hinglish" }
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        until { compose.activity.resources.configuration.locales[0].language == "en" }
        compose.runOnIdle {
            model.setup(GameMode.PRACTICE)
            model.updateSetup(model.state.value.setupDraft.copy(names = "दीपा", bots = 0, assisted = false, playAllNumbers = true, avatars = listOf(7)))
            model.create()
        }
        until { !model.state.value.saving && model.state.value.screen == Screen.GAME }
        val ticket = model.state.value.round!!.tickets.single()
        do {
            val count = model.state.value.round!!.called.size
            if (count > 0) android.os.SystemClock.sleep(550)
            compose.tapTag("local-next")
            until { model.state.value.round!!.called.size == count + 1 }
        } while (model.state.value.round!!.called.none { it in ticket.numbers })
        val number = model.state.value.round!!.called.first { it in ticket.numbers }
        compose.tapTag("dab-called")
        until { number in model.state.value.round!!.marks[ticket.id].orEmpty() }
        compose.openArenaOption(words()(R.string.ui_settings))
        compose.tapTag("interface-language-hi")
        until { compose.activity.resources.configuration.locales[0].language == "hi" && model.state.value.round!!.status == RoundStatus.PAUSED }
        compose.onNodeWithTag("interface-language-hi").performScrollTo().assertIsSelected()
        compose.waitForIdle()
        val round = model.state.value.round!!
        write("witness.json", json.encodeToString(LocaleWitness(runId, Process.myPid(), round, "hinglish")))
        captureTestScreen("locale-process-seeded")
        write("ready.json", json.encodeToString(LocaleReady(runId, Process.myPid(), digest(RoundCodec.encode(round)))))
        awaitCancellation()
    }

    @Test fun verifyAfterColdStart() {
        val witness = json.decodeFromString<LocaleWitness>(File(directory, "witness.json").readText())
        val expected = checkNotNull(args.getString("tambolaExpectedLocale"))
        check(expected in setOf("hi", "en"))
        assertEquals(runId, witness.runId)
        assertNotEquals(witness.pid, Process.myPid())
        until { model.state.value.preferences.language == witness.callerLanguage }
        assertEquals(expected, compose.activity.resources.configuration.locales[0].language)
        assertEquals(expected, AppCompatDelegate.getApplicationLocales().toLanguageTags())
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals(expected, context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
            assertEquals(setOf("en", "hi"), LocaleConfig(context).supportedLocales!!.toLanguageTags().split(',').toSet())
        }
        assertEquals(witness.round, model.state.value.round)
        assertFalse(model.state.value.auto)
        assertNull(model.state.value.winMoment)
        compose.onNodeWithText(words()(R.string.ui_tambola_together)).assertIsDisplayed()
        tap(R.string.ui_resume_round)
        compose.onNodeWithText("दीपा").performScrollTo().assertExists()
        tap(R.string.ui_settings)
        compose.onNodeWithTag("interface-language-$expected").performScrollTo().assertIsSelected()
        captureTestScreen("locale-process-recovered-$expected")
        write("verified-$expected.json", json.encodeToString(LocaleReady(runId, Process.myPid(), digest(RoundCodec.encode(model.state.value.round!!)))))
    }

    @Test fun restoreOriginalLocale() {
        val file = File(directory, "original.json")
        if (!file.exists()) return
        val original = json.decodeFromString<LocaleOriginal>(file.readText())
        if (original.runId != runId) return
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(original.tags)) }
        until { AppCompatDelegate.getApplicationLocales().toLanguageTags() == original.tags }
        compose.waitForIdle()
    }
}

@Serializable private data class LocaleOriginal(val runId: String, val tags: String)
@Serializable private data class LocaleWitness(val runId: String, val pid: Int, val round: Round, val callerLanguage: String)
@Serializable private data class LocaleReady(val runId: String, val pid: Int, val roundSha256: String)
