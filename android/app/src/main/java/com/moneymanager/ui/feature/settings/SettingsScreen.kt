package com.moneymanager.ui.feature.settings

import android.app.Activity
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneymanager.domain.model.ThemeMode
import com.moneymanager.ui.theme.MoneyManagerTheme

/**
 * Settings: theme preference plus local JSON backup/restore. Backup writes a
 * portable snapshot to a file the user picks (Storage Access Framework); restore
 * reads one back. Google Drive auto-backup layers on top of the same snapshot.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(viewModel::importFrom) }

    val consentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onConsentGranted()
            } else {
                viewModel.onConsentCanceled()
            }
        }

    // When Drive needs consent, launch its intent; the callback above retries.
    LaunchedEffect(Unit) {
        viewModel.consentRequests.collect { intentSender ->
            consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    SettingsScreenContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onThemeModeChange = viewModel::onThemeModeChange,
        onBackUp = { exportLauncher.launch(viewModel.suggestedFileName) },
        onRestore = { importLauncher.launch(arrayOf("application/json")) },
        onDriveBackup = viewModel::backupToDrive,
        onDriveRestore = viewModel::restoreFromDrive,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    state: SettingsUiState,
    onNavigateBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ThemeSection(selected = state.themeMode, onChange = onThemeModeChange)

            HorizontalDivider()

            BackupSection(
                state = state,
                onBackUp = onBackUp,
                onRestore = onRestore,
            )

            HorizontalDivider()

            DriveSection(
                working = state.status is BackupStatus.Working,
                onDriveBackup = onDriveBackup,
                onDriveRestore = onDriveRestore,
            )
        }
    }
}

@Composable
private fun DriveSection(
    working: Boolean,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Google Drive")
        Text(
            text =
                "Back up to your Google account's private app storage. " +
                    "You'll be asked to sign in the first time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onDriveBackup,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back up to Drive")
        }
        OutlinedButton(
            onClick = onDriveRestore,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore from Drive")
        }
    }
}

@Composable
private fun ThemeSection(selected: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Appearance")
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == selected,
                    onClick = { onChange(mode) },
                    label = { Text(mode.label()) },
                )
            }
        }
    }
}

@Composable
private fun BackupSection(
    state: SettingsUiState,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
) {
    val working = state.status is BackupStatus.Working
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Backup")
        Text(
            text = "Save all your data to a JSON file, or restore it from one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = lastBackupLabel(state.lastBackupAtEpochMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onBackUp,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back up now")
        }
        OutlinedButton(
            onClick = onRestore,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore from file")
        }

        when (val status = state.status) {
            is BackupStatus.Working -> CircularProgressIndicator()
            is BackupStatus.Success ->
                Text(status.message, color = MaterialTheme.colorScheme.primary)
            is BackupStatus.Error ->
                Text(status.message, color = MaterialTheme.colorScheme.error)
            BackupStatus.Idle -> Unit
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun ThemeMode.label(): String =
    when (this) {
        ThemeMode.SYSTEM -> "System default"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

private fun lastBackupLabel(epochMs: Long?): String =
    if (epochMs == null) {
        "No backup yet"
    } else {
        "Last backup: " +
            DateUtils.getRelativeTimeSpanString(
                epochMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
    }

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MoneyManagerTheme {
        SettingsScreenContent(
            state =
                SettingsUiState(
                    themeMode = ThemeMode.SYSTEM,
                    lastBackupAtEpochMs = null,
                    status = BackupStatus.Idle,
                ),
            onNavigateBack = {},
            onThemeModeChange = {},
            onBackUp = {},
            onRestore = {},
            onDriveBackup = {},
            onDriveRestore = {},
        )
    }
}
