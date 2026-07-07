package com.navidabbasian.kibord.core.settings

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kibord_settings")

/** حالت تم اپ */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.find { it.key == key } ?: SYSTEM
    }
}

/** تنظیمات سراسری اپ + کلمات سفارشی بازی دور — روی DataStore */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val MUSIC = booleanPreferencesKey("music_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DOR_CUSTOM_WORDS = stringPreferencesKey("dor_custom_words")
    }

    val soundEnabled: Flow<Boolean> = store.data.map { it[Keys.SOUND] ?: true }
    val musicEnabled: Flow<Boolean> = store.data.map { it[Keys.MUSIC] ?: true }
    val vibrationEnabled: Flow<Boolean> = store.data.map { it[Keys.VIBRATION] ?: true }
    val themeMode: Flow<ThemeMode> = store.data.map { ThemeMode.fromKey(it[Keys.THEME_MODE]) }

    /** کلمات سفارشی دور به‌صورت JSON (فهرست DorWord سریال‌شده) */
    val dorCustomWordsJson: Flow<String> = store.data.map { it[Keys.DOR_CUSTOM_WORDS] ?: "" }

    suspend fun setSoundEnabled(enabled: Boolean) = store.edit { it[Keys.SOUND] = enabled }
    suspend fun setMusicEnabled(enabled: Boolean) = store.edit { it[Keys.MUSIC] = enabled }
    suspend fun setVibrationEnabled(enabled: Boolean) = store.edit { it[Keys.VIBRATION] = enabled }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.THEME_MODE] = mode.key }
    suspend fun setDorCustomWordsJson(json: String) = store.edit { it[Keys.DOR_CUSTOM_WORDS] = json }
}

val LocalSettingsRepository = staticCompositionLocalOf<SettingsRepository?> { null }
