package com.moneymanager.domain.model

/**
 * How often the app should back up to Google Drive in the background.
 *
 * - [MANUAL] means no periodic work is scheduled; the user triggers backup
 *   from Settings ("Back up to Drive").
 * - [DAILY] / [WEEKLY] / [MONTHLY] schedule a WorkManager periodic job that
 *   uploads the snapshot to the Drive appDataFolder (it only succeeds once the
 *   user has granted Drive consent via the manual action at least once).
 */
enum class BackupFrequency {
    MANUAL,
    DAILY,
    WEEKLY,
    MONTHLY,
    ;

    fun label(): String =
        when (this) {
            MANUAL -> "Manual"
            DAILY -> "Daily"
            WEEKLY -> "Weekly"
            MONTHLY -> "Monthly"
        }
}
