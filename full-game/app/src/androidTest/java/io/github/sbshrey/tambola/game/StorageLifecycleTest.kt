package io.github.sbshrey.tambola.game

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.GameMode
import io.github.sbshrey.tambola.game.data.PreferenceStore
import io.github.sbshrey.tambola.game.data.Preferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Dedicated lifecycle stress: closing the Activity triggers onStop and its asynchronous pause save. */
class StorageLifecycleTest {
    @Test fun repeatedActivityCloseAndReopenRetainsRoundsAndAllowsDeletion() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaStorageLifecycle") == "true")
        check(BuildConfig.DEBUG && isAndroidEmulator())
        PreferenceStore(instrumentation.targetContext).update(Preferences(voice = false, music = false, effects = false, tutorialCompleted = true))
        var previousRound: String? = null
        repeat(40) { iteration ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: GameViewModel
                scenario.onActivity { model = ViewModelProvider(it)[GameViewModel::class.java] }
                suspend fun await(phase: String, condition: () -> Boolean) {
                    try {
                        withTimeout(10_000) {
                            while (!condition()) {
                                check(model.state.value.error == null) { "Iteration $iteration $phase: resource ${model.state.value.error?.resource}" }
                                delay(10)
                            }
                        }
                        check(model.state.value.error == null) { "Iteration $iteration $phase: resource ${model.state.value.error?.resource}" }
                    } catch (error: Exception) {
                        runCatching { captureTestScreen("storage-lifecycle-failure") }
                        throw AssertionError("Iteration $iteration $phase: screen=${model.state.value.screen}, saving=${model.state.value.saving}", error)
                    }
                }
                await("restore") { !model.state.value.loading }
                previousRound?.let { assertEquals(it, model.state.value.round?.id) }
                scenario.onActivity { model.deleteHistory() }
                await("delete") { model.state.value.round == null && model.state.value.history.isEmpty() && model.state.value.screen == Screen.HOME }
                scenario.onActivity {
                    model.setup(GameMode.PRACTICE)
                    model.updateSetup(model.state.value.setupDraft.copy(names = "Lifecycle fixture", bots = 0, tickets = 1, avatars = listOf(0)))
                    model.create()
                }
                await("create") { !model.state.value.saving && model.state.value.screen == Screen.GAME }
                previousRound = model.state.value.round!!.id
            }
        }
    }
}
