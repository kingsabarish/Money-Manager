package com.moneymanager.domain.repository

import com.moneymanager.domain.model.AppSettings
import com.moneymanager.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Reads and writes user preferences. Implemented over DataStore. */
interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setLastBackupAt(epochMs: Long)
}
