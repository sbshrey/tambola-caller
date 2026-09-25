package io.github.sbshrey.tambola.game.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("preferences")
enum class Appearance(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
data class Preferences(val voice: Boolean = true, val language: String = "en", val haptics: Boolean = true, val reducedMotion: Boolean = false, val interval: Int = 10,
    val tutorialCompleted: Boolean = false, val tutorialDismissed: Boolean = false, val appearance: Appearance = Appearance.SYSTEM)

class PreferenceStore(private val context: Context) {
    private val voice = booleanPreferencesKey("voice")
    private val language = stringPreferencesKey("language")
    private val haptics = booleanPreferencesKey("haptics")
    private val reducedMotion = booleanPreferencesKey("reducedMotion")
    private val interval = intPreferencesKey("interval")
    private val tutorialCompleted = booleanPreferencesKey("tutorialCompleted")
    private val tutorialDismissed = booleanPreferencesKey("tutorialDismissed")
    private val appearance = stringPreferencesKey("appearance")
    val values = context.preferences.data.map { p ->
        Preferences(p[voice] ?: true, p[language]?.takeIf { it in setOf("en", "hi", "hinglish") } ?: "en", p[haptics] ?: true, p[reducedMotion] ?: false, (p[interval] ?: 10).coerceIn(5, 30),
            p[tutorialCompleted] ?: false, p[tutorialDismissed] ?: false,
            Appearance.entries.firstOrNull { it.name == p[appearance] } ?: Appearance.SYSTEM)
    }
    suspend fun update(value: Preferences) { context.preferences.edit { p ->
        p[voice] = value.voice; p[language] = value.language; p[haptics] = value.haptics; p[reducedMotion] = value.reducedMotion; p[interval] = value.interval
        p[tutorialCompleted] = value.tutorialCompleted; p[tutorialDismissed] = value.tutorialDismissed
        p[appearance] = value.appearance.name
    } }
    suspend fun finishTutorial(completed: Boolean) { context.preferences.edit { p ->
        p[tutorialDismissed] = true
        if (completed) p[tutorialCompleted] = true
    } }
}
