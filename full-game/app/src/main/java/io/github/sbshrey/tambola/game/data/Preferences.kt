package io.github.sbshrey.tambola.game.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("preferences")
data class Preferences(val voice: Boolean = true, val language: String = "en", val haptics: Boolean = true, val reducedMotion: Boolean = false, val interval: Int = 10)

class PreferenceStore(private val context: Context) {
    private val voice = booleanPreferencesKey("voice")
    private val language = stringPreferencesKey("language")
    private val haptics = booleanPreferencesKey("haptics")
    private val reducedMotion = booleanPreferencesKey("reducedMotion")
    private val interval = intPreferencesKey("interval")
    val values = context.preferences.data.map { p ->
        Preferences(p[voice] ?: true, p[language]?.takeIf { it in setOf("en", "hi", "hinglish") } ?: "en", p[haptics] ?: true, p[reducedMotion] ?: false, (p[interval] ?: 10).coerceIn(5, 30))
    }
    suspend fun update(value: Preferences) { context.preferences.edit { p ->
        p[voice] = value.voice; p[language] = value.language; p[haptics] = value.haptics; p[reducedMotion] = value.reducedMotion; p[interval] = value.interval
    } }
}
