package com.moneymanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.moneymanager.domain.model.AppSettings
import com.moneymanager.domain.model.ThemeMode
import com.moneymanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore-backed settings. Unknown/absent values fall back to [AppSettings]
 * defaults so a fresh install reads cleanly.
 */
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override fun observe(): Flow<AppSettings> =
            dataStore.data.map { prefs ->
                AppSettings(
                    themeMode =
                        prefs[KEY_THEME_MODE]
                            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                            ?: ThemeMode.SYSTEM,
                    dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
                    seedColorArgb = prefs[KEY_SEED_COLOR] ?: AppSettings.DEFAULT_SEED_COLOR,
                    lastBackupAtEpochMs = prefs[KEY_LAST_BACKUP_AT],
                )
            }

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { it[KEY_THEME_MODE] = mode.name }
        }

        override suspend fun setDynamicColor(enabled: Boolean) {
            dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
        }

        override suspend fun setSeedColor(argb: Int) {
            dataStore.edit { it[KEY_SEED_COLOR] = argb }
        }

        override suspend fun setLastBackupAt(epochMs: Long) {
            dataStore.edit { it[KEY_LAST_BACKUP_AT] = epochMs }
        }

        private companion object {
            val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
            val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val KEY_SEED_COLOR = intPreferencesKey("seed_color")
            val KEY_LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        }
    }
