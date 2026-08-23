package com.moneymanager

import android.app.Application
import com.moneymanager.data.local.seed.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Annotated for Hilt so it can host the DI graph.
 *
 * On startup it seeds default categories/accounts on first run (a no-op
 * afterwards) on a background scope so the main thread is never blocked.
 */
@HiltAndroidApp
class MoneyManagerApp : Application() {
    @Inject
    lateinit var seeder: DatabaseSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seeder.seedIfEmpty() }
    }
}
