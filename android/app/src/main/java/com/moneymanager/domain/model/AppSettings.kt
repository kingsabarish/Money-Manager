package com.moneymanager.domain.model

/**
 * User-facing app preferences. Persisted in DataStore; the backup timestamp is
 * updated by the backup flow, not the user directly.
 *
 * @param lastBackupAtEpochMs epoch millis of the last successful backup, or null
 *   if none has run yet.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastBackupAtEpochMs: Long? = null,
)
