package com.moneymanager.domain.model

/**
 * User-facing app preferences. Persisted in DataStore; the backup timestamp is
 * updated by the backup flow, not the user directly.
 *
 * @param themeMode light / dark / follow-system preference.
 * @param dynamicColor when true, the palette matches the device wallpaper
 *   (Material You) on Android 12+; below that it falls back to [seedColorArgb].
 * @param seedColorArgb the accent seed color (ARGB int) the whole Material 3
 *   palette is generated from when [dynamicColor] is off.
 * @param lastBackupAtEpochMs epoch millis of the last successful backup, or null
 *   if none has run yet.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val seedColorArgb: Int = DEFAULT_SEED_COLOR,
    val lastBackupAtEpochMs: Long? = null,
) {
    companion object {
        /** Brand green — the default accent seed on a fresh install. */
        const val DEFAULT_SEED_COLOR: Int = 0xFF2E6B4F.toInt()
    }
}
