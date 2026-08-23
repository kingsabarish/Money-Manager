package com.moneymanager.domain.repository

import com.moneymanager.domain.model.AppSettings
import com.moneymanager.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Reads and writes user preferences. Implemented over DataStore. */
interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    /** Toggle Material You wallpaper matching (Android 12+). */
    suspend fun setDynamicColor(enabled: Boolean)

    /** Set the accent seed color (ARGB int) the palette is generated from. */
    suspend fun setSeedColor(argb: Int)

    suspend fun setLastBackupAt(epochMs: Long)
}
