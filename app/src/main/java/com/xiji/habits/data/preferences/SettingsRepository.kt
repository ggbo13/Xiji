package com.xiji.habits.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiji.habits.ui.theme.AccentTheme
import com.xiji.habits.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xiji_settings")

enum class WeekStartSetting(val key: String, val label: String) {
    MONDAY("monday", "周一"),
    SUNDAY("sunday", "周日");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: MONDAY
    }
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentKey: String = AccentTheme.FOREST.key,
    val dynamicColor: Boolean = false,
    val weekStart: WeekStartSetting = WeekStartSetting.MONDAY,
    val reduceAnimation: Boolean = false,
    val language: String = "zh",
    val firstDayHintShown: Boolean = false
) {
    val accent: AccentTheme get() = AccentTheme.of(accentKey)
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val WEEK_START = stringPreferencesKey("week_start")
        val REDUCE_ANIM = booleanPreferencesKey("reduce_animation")
        val LANGUAGE = stringPreferencesKey("language")
        val HINT = booleanPreferencesKey("first_day_hint")
    }

    val settings: Flow<UserSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserSettings(
                themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
                accentKey = p[Keys.ACCENT] ?: AccentTheme.FOREST.key,
                dynamicColor = p[Keys.DYNAMIC] ?: false,
                weekStart = WeekStartSetting.of(p[Keys.WEEK_START]),
                reduceAnimation = p[Keys.REDUCE_ANIM] ?: false,
                language = p[Keys.LANGUAGE] ?: "zh",
                firstDayHintShown = p[Keys.HINT] ?: false
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setAccent(key: String) = context.dataStore.edit { it[Keys.ACCENT] = key }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = enabled }
    suspend fun setWeekStart(start: WeekStartSetting) = context.dataStore.edit { it[Keys.WEEK_START] = start.key }
    suspend fun setReduceAnimation(enabled: Boolean) = context.dataStore.edit { it[Keys.REDUCE_ANIM] = enabled }
    suspend fun setLanguage(lang: String) = context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    suspend fun markHintShown() = context.dataStore.edit { it[Keys.HINT] = true }
}
