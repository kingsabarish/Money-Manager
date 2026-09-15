package com.moneymanager.ui.feature.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneymanager.ui.components.PieChart
import com.moneymanager.ui.components.PieSlice
import com.moneymanager.ui.components.monthSwipe
import com.moneymanager.ui.theme.ChartPalette
import com.moneymanager.ui.theme.MoneyManagerTheme
import com.moneymanager.ui.util.formatAsCurrency
import com.moneymanager.ui.util.formatAsDay
import com.moneymanager.ui.util.formatAsMonth
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Color for a slice/legend row at [index], cycling through the palette. */
private fun paletteColor(index: Int): Color = ChartPalette[index % ChartPalette.size]

/**
 * Spending-by-category chart. Defaults to the current month, with prev/next
 * month navigation and a custom date-range picker. Subcategory spending is
 * rolled into its top-level category by the view model. Tapping a category opens
 * a drill-down listing of that category's transactions for the same period.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    val currentDetail = detail
    if (currentDetail != null) {
        val onBack = {
            if (detail?.selectedSubCategoryIds?.isNotEmpty() == true) {
                viewModel.clearSubCategory()
            } else {
                viewModel.clearSelection()
            }
        }
        BackHandler { onBack() }
        CategoryDetailContent(
            detail = currentDetail,
            onBack = onBack,
            onSelectSubCategory = viewModel::selectSubCategory,
            onClearSubCategory = viewModel::clearSubCategory,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onThisMonth = viewModel::thisMonth,
            onCustomRange = viewModel::setCustomRange,
        )
    } else {
        StatsScreenContent(
            state = state,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onThisMonth = viewModel::thisMonth,
            onCustomRange = viewModel::setCustomRange,
            onSelectCategory = viewModel::selectCategory,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreenContent(
    state: StatsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
    onSelectCategory: (Long) -> Unit,
) {
    Scaffold(
        contentWindowInsets =
            ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
        topBar = { TopAppBar(title = { Text("Stats") }) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .monthSwipe(onPrevious = onPreviousMonth, onNext = onNextMonth),
        ) {
            PeriodSelector(
                isMonth = state.isMonth,
                start = state.start,
                end = state.end,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onThisMonth = onThisMonth,
                onCustomRange = onCustomRange,
            )

            if (state.isEmpty) {
                EmptyPeriod()
            } else {
                ChartAndLegend(state, onSelectCategory)
            }
        }
    }
}

@Composable
private fun EmptyPeriod() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No expenses in this period.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/**
 * Period controls on a single line — prev/next month, the period label, and the
 * custom-range (plus "This month" reset when a custom range is active) — so the
 * chart and details below get as much vertical space as possible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    isMonth: Boolean,
    start: LocalDate,
    end: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
) {
    var showRangePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPreviousMonth) { Text("‹") }
        Text(
            text = if (isMonth) start.formatAsMonth() else rangeLabel(start, end),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onNextMonth) { Text("›") }
        if (!isMonth) {
            TextButton(onClick = onThisMonth) { Text("This month") }
        }
        TextButton(onClick = { showRangePicker = true }) { Text("Range") }
    }

    if (showRangePicker) {
        val rangeState =
            rememberDateRangePickerState(
                initialSelectedStartDateMillis = start.toEpochMillisUtc(),
                initialSelectedEndDateMillis = end.toEpochMillisUtc(),
            )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMs = rangeState.selectedStartDateMillis
                        val endMs = rangeState.selectedEndDateMillis
                        if (startMs != null && endMs != null) {
                            onCustomRange(startMs.toLocalDateUtc(), endMs.toLocalDateUtc())
                        }
                        showRangePicker = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(state = rangeState)
        }
    }
}

@Composable
private fun ChartAndLegend(state: StatsUiState, onSelectCategory: (Long) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "chart") {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                PieChart(
                    slices = state.slices.mapIndexed { i, s -> PieSlice(s.fraction, paletteColor(i)) },
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.total.formatAsCurrency(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        itemsIndexed(state.slices) { index, slice ->
            LegendRow(
                slice = slice,
                color = paletteColor(index),
                onClick = { onSelectCategory(slice.categoryId) },
            )
        }
    }
}

@Composable
private fun LegendRow(slice: CategorySlice, color: Color, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text = slice.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(slice.fraction * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = slice.amount.formatAsCurrency(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Drill-down: one top-level category's spending for the period — a total, a
 * subcategory percentage breakdown, and the transaction listing. Month
 * navigation stays available so the user can scan the category over time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDetailContent(
    detail: CategoryDetailUiState,
    onBack: () -> Unit,
    onSelectSubCategory: (Long) -> Unit,
    onClearSubCategory: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
) {
    Scaffold(
        contentWindowInsets =
            ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
        topBar = {
            TopAppBar(
                title = { Text(detail.categoryName) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .monthSwipe(onPrevious = onPreviousMonth, onNext = onNextMonth),
        ) {
            PeriodSelector(
                isMonth = detail.isMonth,
                start = detail.start,
                end = detail.end,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onThisMonth = onThisMonth,
                onCustomRange = onCustomRange,
            )

            if (detail.transactions.isEmpty()) {
                if (detail.selectedSubCategoryIds.isNotEmpty()) {
                    val subNames =
                        detail.breakdown
                            .filter { it.categoryId in detail.selectedSubCategoryIds }
                            .joinToString(", ") { it.name }
                    FilteredEmpty(subNames, onClearSubCategory)
                } else {
                    EmptyPeriod()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "total") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Total spent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = detail.total.formatAsCurrency(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (detail.breakdown.isNotEmpty()) {
                        item(key = "breakdown-header") {
                            SectionHeader("Breakdown")
                        }
                        items(detail.breakdown, key = { "b-${it.categoryId}" }) { sub ->
                            BreakdownRow(
                                sub = sub,
                                selected = sub.categoryId in detail.selectedSubCategoryIds,
                                onClick = { onSelectSubCategory(sub.categoryId) },
                            )
                        }
                    }

                    item(key = "txn-header") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val subNames =
                                detail.breakdown
                                    .filter { it.categoryId in detail.selectedSubCategoryIds }
                                    .joinToString(", ") { it.name }
                            Text(
                                text =
                                    if (subNames.isNotEmpty()) {
                                        "Transactions · $subNames"
                                    } else {
                                        "Transactions"
                                    },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp).weight(1f),
                            )
                            if (subNames.isNotEmpty()) {
                                TextButton(onClick = onClearSubCategory) { Text("Show all") }
                            }
                        }
                    }
                    items(detail.transactions, key = { it.id }) { row ->
                        DetailTransactionItem(row)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FilteredEmpty(
    name: String,
    onClear: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text =
                    if (name.isNotEmpty()) {
                        "No transactions for $name in this period."
                    } else {
                        "No transactions in this period."
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClear) { Text("Show all") }
        }
    }
}

@Composable
private fun BreakdownRow(
    sub: SubcategorySlice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = sub.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(sub.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sub.amount.formatAsCurrency(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        LinearProgressIndicator(
            progress = { sub.fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DetailTransactionItem(row: DetailTransactionRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.categoryName, style = MaterialTheme.typography.bodyLarge)
            val subtitle = row.note?.let { "${row.date.formatAsDay()} · $it" } ?: row.date.formatAsDay()
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = row.amount.formatAsCurrency(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun rangeLabel(start: LocalDate, end: LocalDate): String =
    "${start.formatAsDay()} – ${end.formatAsDay()}"

private fun LocalDate.toEpochMillisUtc(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Preview(showBackground = true)
@Composable
private fun StatsScreenPreview() {
    val slices =
        listOf(
            CategorySlice(1, "Food", BigDecimal("420.00"), 0.42f),
            CategorySlice(2, "Transport", BigDecimal("280.00"), 0.28f),
            CategorySlice(3, "Shopping", BigDecimal("180.00"), 0.18f),
            CategorySlice(4, "Bills", BigDecimal("120.00"), 0.12f),
        )
    MoneyManagerTheme {
        Surface {
            StatsScreenContent(
                state =
                    StatsUiState(
                        loading = false,
                        isMonth = true,
                        start = LocalDate.of(2026, 8, 1),
                        end = LocalDate.of(2026, 8, 31),
                        total = BigDecimal("1000.00"),
                        slices = slices,
                    ),
                onPreviousMonth = {},
                onNextMonth = {},
                onThisMonth = {},
                onCustomRange = { _, _ -> },
                onSelectCategory = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryDetailPreview() {
    MoneyManagerTheme {
        Surface {
            CategoryDetailContent(
                detail =
                    CategoryDetailUiState(
                        categoryId = 1,
                        categoryName = "Food",
                        isMonth = true,
                        start = LocalDate.of(2026, 8, 1),
                        end = LocalDate.of(2026, 8, 31),
                        total = BigDecimal("420.00"),
                        breakdown =
                            listOf(
                                SubcategorySlice(10, "Groceries", BigDecimal("300.00"), 0.71f),
                                SubcategorySlice(11, "Dining", BigDecimal("120.00"), 0.29f),
                            ),
                        transactions =
                            listOf(
                                DetailTransactionRow(1, LocalDate.of(2026, 8, 20), 10, "Groceries", "Weekly shop", BigDecimal("80.00")),
                                DetailTransactionRow(2, LocalDate.of(2026, 8, 18), 11, "Dining", null, BigDecimal("40.00")),
                            ),
                        selectedSubCategoryIds = emptySet(),
                    ),
                onBack = {},
                onSelectSubCategory = {},
                onClearSubCategory = {},
                onPreviousMonth = {},
                onNextMonth = {},
                onThisMonth = {},
                onCustomRange = { _, _ -> },
            )
        }
    }
}
