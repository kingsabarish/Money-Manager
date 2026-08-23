package com.moneymanager.domain.model

/**
 * User-facing app preferences. Persisted in DataStore; the backup timestamp is
 * updated by the backup flow, not the user directly.
 *
 * @param themeMode light / dark / follow-system preference.
 * @param appTheme the color palette (accent scheme).
 * @param lastBackupAtEpochMs epoch millis of the last successful backup, or null
 *   if none has run yet.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appTheme: AppTheme = AppTheme.GREEN,
    val lastBackupAtEpochMs: Long? = null,
)
