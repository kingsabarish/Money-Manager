package com.moneymanager

import android.app.Application
import com.moneymanager.data.local.seed.DatabaseSeeder
import com.moneymanager.domain.repository.SettingsRepository
import com.moneymanager.ui.feature.settings.BackupScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Annotated for Hilt so it can host the DI graph.
 *
 * On startup it seeds default categories/accounts on first run (a no-op
 * afterwards) and re-arms the periodic Google Drive backup so the user's chosen
 * frequency survives a force-stop or reboot (WorkManager's alarms are cleared in
 * those cases until the app next runs).
 */
@HiltAndroidApp
class MoneyManagerApp : Application() {
    @Inject
    lateinit var seeder: DatabaseSeeder

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var backupScheduler: BackupScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seeder.seedIfEmpty() }
        appScope.launch {
            backupScheduler.arm(settingsRepository.observe().first().backupFrequency)
        }
    }
}
