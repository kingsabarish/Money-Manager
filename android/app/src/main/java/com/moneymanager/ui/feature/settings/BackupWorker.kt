package com.moneymanager.ui.feature.settings

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneymanager.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Background worker that performs one Google Drive backup. It is enqueued on a
 * periodic schedule by [BackupScheduler] according to the user's chosen
 * frequency.
 *
 * Hilt's `@HiltWorker` annotation is unavailable in the pinned Hilt version, so
 * the worker is built by WorkManager's default factory and grabs its
 * dependencies through a Hilt entry point (see [BackupWorkerEntryPoint]).
 *
 * Drive consent must already have been granted through the manual "Back up to
 * Drive" action — the worker cannot show the consent UI, so if consent is still
 * required (or any error occurs) it simply skips this run and lets the next
 * scheduled run retry. A successful run records [SettingsRepository.setLastBackupAt].
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackupWorkerEntryPoint {
        fun driveBackup(): GoogleDriveBackup

        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        val entryPoint =
            EntryPointAccessors.fromApplication(applicationContext, BackupWorkerEntryPoint::class.java)
        return when (entryPoint.driveBackup().backup()) {
            is DriveResult.Success -> {
                entryPoint.settingsRepository().setLastBackupAt(System.currentTimeMillis())
                Result.success()
            }

            // ConsentRequired / Error / NoBackup: nothing the background worker
            // can resolve, so skip this run rather than retry in a tight loop.
            else -> {
                Log.i(TAG, "Periodic Drive backup skipped (consent not granted or error).")
                Result.success()
            }
        }
    }

    private companion object {
        const val TAG = "BackupWorker"
    }
}
