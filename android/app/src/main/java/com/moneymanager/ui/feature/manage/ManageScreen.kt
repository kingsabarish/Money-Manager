package com.moneymanager.ui.feature.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneymanager.ui.theme.MoneyManagerTheme

private enum class Kind(val label: String, val singular: String) {
    CATEGORY("Categories", "category"),
    ACCOUNT("Accounts", "account"),
}

/**
 * Add / rename / delete categories and accounts (two-level tree). Selecting a tab
 * switches between the two; each maps onto the matching [ManageViewModel] actions.
 * The repository rejects invalid edits (duplicate name, third level, guarded
 * delete); the reason is shown in a snackbar.
 */
@Composable
fun ManageScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ManageScreenContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onMessageShown = viewModel::onMessageShown,
        onAdd = { kind, parentId, name ->
            when (kind) {
                Kind.CATEGORY -> viewModel.addCategory(name, parentId)
                Kind.ACCOUNT -> viewModel.addAccount(name, parentId)
            }
        },
        onRename = { kind, id, name ->
            when (kind) {
                Kind.CATEGORY -> viewModel.renameCategory(id, name)
                Kind.ACCOUNT -> viewModel.renameAccount(id, name)
            }
        },
        onDelete = { kind, id ->
            when (kind) {
                Kind.CATEGORY -> viewModel.deleteCategory(id)
                Kind.ACCOUNT -> viewModel.deleteAccount(id)
            }
        },
    )
}

/** A pending text prompt (add or rename), owned by the screen. */
private data class TextPrompt(
    val title: String,
    val initial: String,
    val confirmLabel: String,
    val onConfirm: (String) -> Unit,
)

/** A pending destructive confirmation, owned by the screen. */
private data class ConfirmPrompt(val text: String, val onConfirm: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageScreenContent(
    state: ManageUiState,
    onNavigateBack: () -> Unit,
    onMessageShown: () -> Unit,
    onAdd: (Kind, Long?, String) -> Unit,
    onRename: (Kind, Long, String) -> Unit,
    onDelete: (Kind, Long) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val kind = Kind.entries[selectedTab]
    val groups = if (kind == Kind.CATEGORY) state.categories else state.accounts

    var prompt by remember { mutableStateOf<TextPrompt?>(null) }
    var confirm by remember { mutableStateOf<ConfirmPrompt?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories & accounts") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Kind.entries.forEachIndexed { index, entry ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        text = { Text(entry.label) },
                    )
                }
            }

            Button(
                onClick = {
                    prompt =
                        TextPrompt(
                            title = "New ${kind.singular}",
                            initial = "",
                            confirmLabel = "Add",
                            onConfirm = { name -> onAdd(kind, null, name) },
                        )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Add ${kind.singular}")
            }

            if (groups.isEmpty()) {
                Text(
                    text = "No ${kind.label.lowercase()} yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(groups, key = { it.id }) { group ->
                        GroupRow(
                            group = group,
                            onAddChild = {
                                prompt =
                                    TextPrompt(
                                        title = "New ${kind.singular} under ${group.name}",
                                        initial = "",
                                        confirmLabel = "Add",
                                        onConfirm = { name -> onAdd(kind, group.id, name) },
                                    )
                            },
                            onRename = {
                                prompt =
                                    TextPrompt(
                                        title = "Rename ${group.name}",
                                        initial = group.name,
                                        confirmLabel = "Rename",
                                        onConfirm = { name -> onRename(kind, group.id, name) },
                                    )
                            },
                            onDelete = {
                                confirm =
                                    ConfirmPrompt(
                                        text = "Delete \"${group.name}\"?",
                                        onConfirm = { onDelete(kind, group.id) },
                                    )
                            },
                            onRenameChild = { child ->
                                prompt =
                                    TextPrompt(
                                        title = "Rename ${child.name}",
                                        initial = child.name,
                                        confirmLabel = "Rename",
                                        onConfirm = { name -> onRename(kind, child.id, name) },
                                    )
                            },
                            onDeleteChild = { child ->
                                confirm =
                                    ConfirmPrompt(
                                        text = "Delete \"${child.name}\"?",
                                        onConfirm = { onDelete(kind, child.id) },
                                    )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    prompt?.let { active ->
        TextPromptDialog(
            prompt = active,
            onDismiss = { prompt = null },
        )
    }

    confirm?.let { active ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Confirm delete") },
            text = { Text(active.text) },
            confirmButton = {
                TextButton(onClick = {
                    active.onConfirm()
                    confirm = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GroupRow(
    group: ManageGroup,
    onAddChild: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRenameChild: (ManageItem) -> Unit,
    onDeleteChild: (ManageItem) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAddChild) { Text("+ Sub") }
            TextButton(onClick = onRename) { Text("Rename") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
        group.children.forEach { child ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = child.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onRenameChild(child) }) { Text("Rename") }
                TextButton(onClick = { onDeleteChild(child) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun TextPromptDialog(prompt: TextPrompt, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(prompt.initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    prompt.onConfirm(text)
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) { Text(prompt.confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ManageScreenPreview() {
    MoneyManagerTheme {
        ManageScreenContent(
            state =
                ManageUiState(
                    categories =
                        listOf(
                            ManageGroup(
                                id = 1,
                                name = "Food",
                                children = listOf(ManageItem(2, "Dining"), ManageItem(3, "Groceries")),
                            ),
                            ManageGroup(id = 4, name = "Transport", children = emptyList()),
                        ),
                    accounts = listOf(ManageGroup(id = 5, name = "Cash", children = emptyList())),
                ),
            onNavigateBack = {},
            onMessageShown = {},
            onAdd = { _, _, _ -> },
            onRename = { _, _, _ -> },
            onDelete = { _, _ -> },
        )
    }
}
