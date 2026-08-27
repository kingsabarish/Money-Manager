package com.moneymanager.ui.feature.settings

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.moneymanager.domain.model.BackupFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject

/**
 * Schedules / cancels the periodic Google Drive backup via WorkManager.
 *
 * When the user picks a frequency (via [schedule]) we enqueue two jobs:
 *   1. an immediate one-time backup, so the first snapshot is taken right away
 *      (once Drive consent has been granted), and
 *   2. a periodic backup whose first run is delayed by the interval, so
 *      subsequent backups land Daily / Weekly / Monthly after the initial one.
 *
 * [arm] is the same minus the immediate job — used on app startup to re-establish
 * the periodic schedule after a reboot / force-stop without triggering a backup
 * on every launch.
 *
 * WorkManager persists periodic work across reboots, so once scheduled it keeps
 * running until the user switches back to Manual (or uninstalls).
 */
class BackupScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun schedule(frequency: BackupFrequency) {
            val workManager = WorkManager.getInstance(context)
            if (frequency == BackupFrequency.MANUAL) {
                workManager.cancelUniqueWork(WORK_NAME)
                workManager.cancelUniqueWork(WORK_NAME_IMMEDIATE)
                return
            }
            // Immediate first backup (replaces any prior immediate request).
            workManager.enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest
                    .Builder(BackupWorker::class.java)
                    .setConstraints(BACKUP_CONSTRAINTS)
                    .build(),
            )
            // Then the recurring schedule, starting after one interval.
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest
                    .Builder(BackupWorker::class.java, interval(frequency))
                    .setInitialDelay(interval(frequency))
                    .setConstraints(BACKUP_CONSTRAINTS)
                    .build(),
            )
        }

        /** Re-arm only the periodic job (no immediate backup) — for app startup. */
        fun arm(frequency: BackupFrequency) {
            val workManager = WorkManager.getInstance(context)
            if (frequency == BackupFrequency.MANUAL) {
                workManager.cancelUniqueWork(WORK_NAME)
                workManager.cancelUniqueWork(WORK_NAME_IMMEDIATE)
                return
            }
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest
                    .Builder(BackupWorker::class.java, interval(frequency))
                    .setInitialDelay(interval(frequency))
                    .setConstraints(BACKUP_CONSTRAINTS)
                    .build(),
            )
        }

        private fun interval(frequency: BackupFrequency): Duration =
            when (frequency) {
                BackupFrequency.DAILY -> Duration.ofDays(1)
                BackupFrequency.WEEKLY -> Duration.ofDays(7)
                BackupFrequency.MONTHLY -> Duration.ofDays(30)
                BackupFrequency.MANUAL -> Duration.ofDays(1) // unreachable
            }

        private companion object {
            const val WORK_NAME = "periodic-drive-backup"
            const val WORK_NAME_IMMEDIATE = "periodic-drive-backup-initial"

            val BACKUP_CONSTRAINTS =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
        }
    }
