package com.moneymanager.ui.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.ThemeMode
import com.moneymanager.domain.repository.BackupRepository
import com.moneymanager.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastBackupAtEpochMs: Long? = null,
    val status: BackupStatus = BackupStatus.Idle,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val backupRepository: BackupRepository,
    ) : ViewModel() {
        private val status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)

        val uiState: StateFlow<SettingsUiState> =
            combine(settingsRepository.observe(), status) { settings, status ->
                SettingsUiState(
                    themeMode = settings.themeMode,
                    lastBackupAtEpochMs = settings.lastBackupAtEpochMs,
                    status = status,
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

        /** Export the database to the user-picked [uri] (from CreateDocument). */
        fun exportTo(uri: Uri) {
            viewModelScope.launch {
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
