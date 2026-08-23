package com.moneymanager.ui.feature.settings

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.AppTheme
import com.moneymanager.domain.model.ThemeMode
import com.moneymanager.domain.repository.BackupRepository
import com.moneymanager.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Result of the last backup/restore action, surfaced as a one-shot-ish banner.
 * Kept in state (not an event channel) for simplicity; cleared on the next action.
 */
sealed interface BackupStatus {
    data object Idle : BackupStatus

    data object Working : BackupStatus

    data class Success(val message: String) : BackupStatus

    data class Error(val message: String) : BackupStatus
}

/** Which control a [BackupStatus] belongs to, so its message renders in the right section. */
enum class BackupSource {
    FILE,
    DRIVE,
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appTheme: AppTheme = AppTheme.GREEN,
    val lastBackupAtEpochMs: Long? = null,
    val status: BackupStatus = BackupStatus.Idle,
    val statusSource: BackupSource = BackupSource.FILE,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val backupRepository: BackupRepository,
        private val driveBackup: GoogleDriveBackup,
    ) : ViewModel() {
        private val status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
        private val statusSource = MutableStateFlow(BackupSource.FILE)

        private val consentRequestsFlow = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)

        /** Emits when Drive needs the consent UI launched; retry the last action on RESULT_OK. */
        val consentRequests: SharedFlow<IntentSender> = consentRequestsFlow.asSharedFlow()

        /** The Drive action awaiting consent, replayed after the user grants it. */
        private var pendingDriveAction: (suspend () -> DriveResult)? = null
        private var pendingSuccessMessage: String = ""

        val uiState: StateFlow<SettingsUiState> =
            combine(
                settingsRepository.observe(),
                status,
                statusSource,
            ) { settings, status, statusSource ->
                SettingsUiState(
                    themeMode = settings.themeMode,
                    appTheme = settings.appTheme,
                    lastBackupAtEpochMs = settings.lastBackupAtEpochMs,
                    status = status,
                    statusSource = statusSource,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

        /** Default file name suggested to the document picker on export. */
        val suggestedFileName: String
            get() = "money-manager-backup.json"

        fun onThemeModeChange(mode: ThemeMode) {
            viewModelScope.launch { settingsRepository.setThemeMode(mode) }
        }

        fun onAppThemeChange(theme: AppTheme) {
            viewModelScope.launch { settingsRepository.setAppTheme(theme) }
        }

        /** Export the database to the user-picked [uri] (from CreateDocument). */
        fun exportTo(uri: Uri) {
            viewModelScope.launch {
                statusSource.value = BackupSource.FILE
                status.value = BackupStatus.Working
                when (val result = backupRepository.exportToJson()) {
                    is AppResult.Success -> {
                        val written =
                            runCatching { writeText(uri, result.data) }
                        if (written.isSuccess) {
                            settingsRepository.setLastBackupAt(System.currentTimeMillis())
                            status.value = BackupStatus.Success("Backup saved")
                        } else {
                            status.value =
                                BackupStatus.Error(
                                    written.exceptionOrNull()?.message ?: "Could not write file",
                                )
                        }
                    }

                    is AppResult.Failure -> status.value = BackupStatus.Error(result.error.userMessage())
                }
            }
        }

        /** Restore the database from the user-picked [uri] (from OpenDocument). */
        fun importFrom(uri: Uri) {
            viewModelScope.launch {
                statusSource.value = BackupSource.FILE
                status.value = BackupStatus.Working
                val text = runCatching { readText(uri) }
                if (text.isFailure) {
                    status.value =
                        BackupStatus.Error(text.exceptionOrNull()?.message ?: "Could not read file")
                    return@launch
                }
                status.value =
                    when (val result = backupRepository.importFromJson(text.getOrThrow())) {
                        is AppResult.Success -> BackupStatus.Success("Backup restored")
                        is AppResult.Failure -> BackupStatus.Error(result.error.userMessage())
                    }
            }
        }

        fun backupToDrive() = runDriveAction("Backed up to Google Drive") { driveBackup.backup() }

        fun restoreFromDrive() = runDriveAction("Restored from Google Drive") { driveBackup.restore() }

        /** Call after the consent screen returns RESULT_OK to replay the pending action. */
        fun onConsentGranted() {
            val action = pendingDriveAction ?: return
            runDriveAction(pendingSuccessMessage, action)
        }

        fun onConsentCanceled() {
            pendingDriveAction = null
            statusSource.value = BackupSource.DRIVE
            status.value = BackupStatus.Error("Google sign-in was cancelled")
        }

        private fun runDriveAction(successMessage: String, action: suspend () -> DriveResult) {
            pendingDriveAction = action
            pendingSuccessMessage = successMessage
            viewModelScope.launch {
                statusSource.value = BackupSource.DRIVE
                status.value = BackupStatus.Working
                when (val result = action()) {
                    is DriveResult.Success -> {
                        settingsRepository.setLastBackupAt(System.currentTimeMillis())
                        pendingDriveAction = null
                        status.value = BackupStatus.Success(successMessage)
                    }

                    is DriveResult.NoBackup -> {
                        pendingDriveAction = null
                        status.value = BackupStatus.Error("No backup found in Google Drive yet")
                    }

                    is DriveResult.ConsentRequired -> {
                        // Keep the pending action; the UI launches consent and calls back.
                        status.value = BackupStatus.Working
                        consentRequestsFlow.emit(result.intentSender)
                    }

                    is DriveResult.Error -> {
                        pendingDriveAction = null
                        status.value = BackupStatus.Error(result.message)
                    }
                }
            }
        }

        private suspend fun writeText(uri: Uri, text: String) =
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(text.toByteArray())
                } ?: error("Could not open the selected file")
            }

        private suspend fun readText(uri: Uri): String =
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().decodeToString()
                } ?: error("Could not open the selected file")
            }
    }

private fun AppError.userMessage(): String =
    when (this) {
        is AppError.NotFound -> "Not found"
        is AppError.Conflict -> message
        is AppError.Validation -> message
        is AppError.Unknown -> message
    }
