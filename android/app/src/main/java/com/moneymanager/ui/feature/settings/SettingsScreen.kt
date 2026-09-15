package com.moneymanager.ui.feature.settings

import android.app.Activity
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneymanager.domain.model.BackupFrequency
import java.text.DateFormat
import java.util.Date
import com.moneymanager.domain.model.ThemeMode
import com.moneymanager.ui.components.ColorPickerDialog
import com.moneymanager.ui.components.toOpaqueArgb
import com.moneymanager.ui.theme.ThemePresetSeeds
import com.moneymanager.ui.theme.MoneyManagerTheme

/**
 * Settings: theme preferences plus local JSON backup/restore. Backup writes a
 * portable snapshot to a file the user picks (Storage Access Framework); restore
 * reads one back. Google Drive backup layers on top of the same snapshot.
 */
@Composable
fun SettingsScreen(
    onNavigateToManage: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }

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
        onNavigateToManage = onNavigateToManage,
        onThemeModeChange = viewModel::onThemeModeChange,
        onDynamicColorChange = viewModel::onDynamicColorChange,
        onSeedColorChange = viewModel::onSeedColorChange,
        onBackUp = { exportLauncher.launch(viewModel.suggestedFileName) },
        onRestore = { importLauncher.launch(arrayOf("application/json")) },
        onDriveBackup = viewModel::backupToDrive,
        onDriveRestore = viewModel::restoreFromDrive,
        onDriveDelete = { showDeleteConfirm = true },
        selectedFrequency = state.backupFrequency,
        onFrequencyChange = viewModel::setBackupFrequency,
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Google Drive backup?") },
            text = {
                Text(
                    "This permanently removes your backup from Google Drive's private app " +
                        "storage. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteFromDrive()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (state.driveRestoreOptions != null) {
        RestorePickerDialog(
            entries = state.driveRestoreOptions!!,
            onRestore = viewModel::restoreFromDriveEntry,
            onDismiss = viewModel::dismissRestorePicker,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    state: SettingsUiState,
    onNavigateToManage: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSeedColorChange: (Int) -> Unit,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
    onDriveDelete: () -> Unit,
    selectedFrequency: BackupFrequency,
    onFrequencyChange: (BackupFrequency) -> Unit,
) {
    Scaffold(
        contentWindowInsets =
            ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ThemeSection(
                selectedMode = state.themeMode,
                dynamicColor = state.dynamicColor,
                seedColorArgb = state.seedColorArgb,
                onModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onSeedColorChange = onSeedColorChange,
            )

            HorizontalDivider()

            ManageSection(onManage = onNavigateToManage)

            HorizontalDivider()

            BackupSection(
                state = state,
                onBackUp = onBackUp,
                onRestore = onRestore,
            )

            HorizontalDivider()

            DriveSection(
                state = state,
                onDriveBackup = onDriveBackup,
                onDriveRestore = onDriveRestore,
                onDriveDelete = onDriveDelete,
                selectedFrequency = selectedFrequency,
                onFrequencyChange = onFrequencyChange,
            )
        }
    }
}

@Composable
private fun DriveSection(
    state: SettingsUiState,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
    onDriveDelete: () -> Unit,
    selectedFrequency: BackupFrequency,
    onFrequencyChange: (BackupFrequency) -> Unit,
) {
    val working = state.status is BackupStatus.Working
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Google Drive")
        Text(
            text =
                "Back up to your Google account's private app storage. " +
                    "You'll be asked to sign in the first time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = lastBackupLabel(state.lastBackupAtEpochMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
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
        OutlinedButton(
            onClick = onDriveDelete,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Delete from Drive")
        }

        Text(
            text = "Automatic backup",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                "Backs up in the background once consent is granted. " +
                    "Manual only backs up when you tap above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FrequencySelector(selected = selectedFrequency, onSelect = onFrequencyChange)

        if (state.statusSource == BackupSource.DRIVE) {
            StatusMessage(state.status)
        }
    }
}

/** Segmented choice of backup frequency, consistent with the theme chips. */
@Composable
private fun FrequencySelector(
    selected: BackupFrequency,
    onSelect: (BackupFrequency) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.selectableGroup(),
    ) {
        BackupFrequency.entries.forEach { freq ->
            FilterChip(
                selected = freq == selected,
                onClick = { onSelect(freq) },
                label = { Text(freq.label()) },
            )
        }
    }
}

@Composable
private fun ManageSection(onManage: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Categories & accounts")
        Text(
            text = "Add, rename, or remove the categories and accounts used when adding an expense.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onManage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Manage categories & accounts")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSection(
    selectedMode: ThemeMode,
    dynamicColor: Boolean,
    seedColorArgb: Int,
    onModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSeedColorChange: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Appearance")

        Text(
            text = "Light or dark",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == selectedMode,
                    onClick = { onModeChange(mode) },
                    label = { Text(mode.label()) },
                )
            }
        }

        // "Match wallpaper" (Material You) only exists on Android 12+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Match my wallpaper",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
            }
        }

        Text(
            text = "Accent color",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val pickerColor = Color(seedColorArgb)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemePresetSeeds.forEach { seed ->
                val argb = seed.toArgb()
                ColorDot(
                    color = seed,
                    selected = !dynamicColor && argb == seedColorArgb,
                    onClick = { onSeedColorChange(argb) },
                )
            }
            // Custom color: opens the full RGB picker, seeded with the current color.
            CustomColorDot(
                color = pickerColor,
                selected = !dynamicColor && ThemePresetSeeds.none { it.toArgb() == seedColorArgb },
                onClick = { showPicker = true },
            )
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = Color(seedColorArgb),
            onDismiss = { showPicker = false },
            onConfirm = { color ->
                onSeedColorChange(color.toOpaqueArgb())
                showPicker = false
            },
        )
    }
}

private val swatchSize = 40.dp

/** A selectable circular color swatch. */
@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(swatchSize)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
    )
}

/**
 * The "custom color" swatch: a rainbow ring signalling the full RGB picker, with
 * the currently-chosen custom color filling the center when one is active.
 */
@Composable
private fun CustomColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val rainbow =
        remember {
            Brush.sweepGradient(
                listOf(
                    Color.Red, Color.Yellow, Color.Green,
                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                ),
            )
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(swatchSize)
                .clip(CircleShape)
                .background(rainbow)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
    ) {
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, Color.White, CircleShape),
            )
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

        if (state.statusSource == BackupSource.FILE) {
            StatusMessage(state.status)
        }
    }
}

/** Renders the shared backup [status] as a spinner, success, or error line. */
@Composable
private fun StatusMessage(status: BackupStatus) {
    when (status) {
        is BackupStatus.Working -> CircularProgressIndicator()
        is BackupStatus.Success ->
            Text(status.message, color = MaterialTheme.colorScheme.primary)
        is BackupStatus.Error ->
            Text(status.message, color = MaterialTheme.colorScheme.error)
        BackupStatus.Idle -> Unit
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
        "No backup available"
    } else {
        "Last backup: " +
            DateUtils.getRelativeTimeSpanString(
                epochMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
    }

/** Formats a backup's timestamp for the restore picker (absolute + relative). */
private fun backupEntryLabel(epochMs: Long): String {
    val absolute = DateFormat.getDateTimeInstance().format(Date(epochMs))
    val relative =
        DateUtils.getRelativeTimeSpanString(
            epochMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
    return "$absolute ($relative)"
}

/**
 * Lets the user pick which timestamped Drive backup to restore. The newest entry
 * (index 0, since [GoogleDriveBackup.listBackups] returns them newest-first) is
 * selected by default.
 */
@Composable
private fun RestorePickerDialog(
    entries: List<BackupEntry>,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIndex by remember(entries) { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from Google Drive") },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(
                    "Choose a backup to restore. The newest is selected by default.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                entries.forEachIndexed { index, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selectedIndex,
                                onClick = { selectedIndex = index },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { selectedIndex = index },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(backupEntryLabel(entry.timestampEpochMs))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRestore(entries[selectedIndex].id) }) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
            onNavigateToManage = {},
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onSeedColorChange = {},
            onBackUp = {},
            onRestore = {},
            onDriveBackup = {},
            onDriveRestore = {},
            onDriveDelete = {},
            selectedFrequency = BackupFrequency.MANUAL,
            onFrequencyChange = {},
        )
    }
}
