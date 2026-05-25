package ru.fuezl.gymdiary.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.fuezl.gymdiary.core.model.ThemeMode
import ru.fuezl.gymdiary.core.model.UserSettings
import ru.fuezl.gymdiary.core.model.WeightUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("user_settings")

interface SettingsLocalDataSource {
    val settings: Flow<UserSettings>
    suspend fun updateTheme(themeMode: ThemeMode)
    suspend fun updateRestTimer(enabled: Boolean, seconds: Int)
    suspend fun updateHaptics(enabled: Boolean)
    suspend fun restore(settings: UserSettings)
}

@Singleton
class SettingsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SettingsLocalDataSource {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val defaultRestTimerKey = intPreferencesKey("default_rest_timer_seconds")
    private val restTimerEnabledKey = booleanPreferencesKey("rest_timer_enabled")
    private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
    private val weightUnitKey = stringPreferencesKey("weight_unit")

    override val settings: Flow<UserSettings> = context.settingsDataStore.data.map { preferences ->
        UserSettings(
            themeMode = preferences[themeModeKey]?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM,
            defaultRestTimerSeconds = preferences[defaultRestTimerKey] ?: 90,
            restTimerEnabled = preferences[restTimerEnabledKey] ?: true,
            hapticsEnabled = preferences[hapticsEnabledKey] ?: true,
            weightUnit = preferences[weightUnitKey]?.let(WeightUnit::valueOf) ?: WeightUnit.KG,
        )
    }

    override suspend fun updateTheme(themeMode: ThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = themeMode.name }
    }

    override suspend fun updateRestTimer(enabled: Boolean, seconds: Int) {
        context.settingsDataStore.edit {
            it[restTimerEnabledKey] = enabled
            it[defaultRestTimerKey] = seconds.coerceIn(15, 600)
        }
    }

    override suspend fun updateHaptics(enabled: Boolean) {
        context.settingsDataStore.edit { it[hapticsEnabledKey] = enabled }
    }

    override suspend fun restore(settings: UserSettings) {
        context.settingsDataStore.edit {
            it[themeModeKey] = settings.themeMode.name
            it[defaultRestTimerKey] = settings.defaultRestTimerSeconds
            it[restTimerEnabledKey] = settings.restTimerEnabled
            it[hapticsEnabledKey] = settings.hapticsEnabled
            it[weightUnitKey] = settings.weightUnit.name
        }
    }
}
