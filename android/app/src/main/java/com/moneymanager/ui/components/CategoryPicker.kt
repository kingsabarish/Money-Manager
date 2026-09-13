package com.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneymanager.domain.model.Category

@Composable
fun CategoryPickerField(
    label: String,
    categories: List<Category>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    val selected = categories.firstOrNull { it.id == selectedId }
    val displayLabel =
        when {
            selected == null -> "Select category"
            selected.isTopLevel -> selected.name
            else -> {
                val parentName = categories.firstOrNull { it.id == selected.parentId }?.name
                if (parentName != null) "$parentName · ${selected.name}" else selected.name
            }
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { showBottomSheet = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = displayLabel,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        }
    }

    if (showBottomSheet) {
        CategoryPickerBottomSheet(
            categories = categories,
            selectedId = selectedId,
            onDismiss = { showBottomSheet = false },
            onSelected = {
                onSelected(it)
                showBottomSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerBottomSheet(
    categories: List<Category>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
) {
    val topLevel = remember(categories) { categories.filter { it.isTopLevel }.sortedBy { it.name } }
    val childrenByParent =
        remember(categories) {
            categories
                .filter { !it.isTopLevel }
                .groupBy { it.parentId }
                .mapValues { (_, list) -> list.sortedBy { it.name } }
        }

    val initialParentId =
        remember(selectedId, categories) {
            when {
                selected(categories, selectedId)?.isTopLevel == true -> selectedId
                selected(categories, selectedId) != null -> selected(categories, selectedId)?.parentId
                else -> topLevel.firstOrNull()?.id
            }
        }
    var highlightedId by remember { mutableStateOf(initialParentId) }
    val highlighted = topLevel.firstOrNull { it.id == highlightedId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(topLevel, key = { it.id }) { cat ->
                    MasterRow(
                        name = cat.name,
                        highlighted = cat.id == highlightedId,
                        hasChildren = !childrenByParent[cat.id].isNullOrEmpty(),
                        onClick = { highlightedId = cat.id },
                    )
                }
            }
            VerticalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (highlighted != null) {
                    item(key = "parent-${highlighted.id}") {
                        DetailRow(
                            name = highlighted.name,
                            caption = "Main category",
                            selected = highlighted.id == selectedId,
                            onClick = { onSelected(highlighted.id) },
                        )
                        HorizontalDivider()
                    }
                    items(childrenByParent[highlighted.id].orEmpty(), key = { it.id }) { child ->
                        DetailRow(
                            name = child.name,
                            caption = null,
                            selected = child.id == selectedId,
                            onClick = { onSelected(child.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun selected(categories: List<Category>, id: Long?): Category? =
    categories.firstOrNull { it.id == id }

@Composable
private fun MasterRow(
    name: String,
    highlighted: Boolean,
    hasChildren: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (hasChildren) {
            Text(text = "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailRow(
    name: String,
    caption: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color =
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
