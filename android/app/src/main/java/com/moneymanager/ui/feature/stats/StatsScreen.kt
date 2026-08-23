package com.moneymanager.ui.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneymanager.ui.components.PieChart
import com.moneymanager.ui.components.PieSlice
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
 * rolled into its top-level category by the view model.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreenContent(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onThisMonth = viewModel::thisMonth,
        onCustomRange = viewModel::setCustomRange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreenContent(
    state: StatsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Stats") }) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            PeriodSelector(
                state = state,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onThisMonth = onThisMonth,
                onCustomRange = onCustomRange,
            )

            if (state.isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No expenses in this period.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                ChartAndLegend(state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    state: StatsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
) {
    var showRangePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPreviousMonth) { Text("‹") }
            Text(
                text = if (state.isMonth) state.start.formatAsMonth() else rangeLabel(state),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onNextMonth) { Text("›") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { showRangePicker = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("Custom range")
            }
            if (!state.isMonth) {
                OutlinedButton(
                    onClick = onThisMonth,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("This month")
                }
            }
        }
    }

    if (showRangePicker) {
        val rangeState =
            rememberDateRangePickerState(
                initialSelectedStartDateMillis = state.start.toEpochMillisUtc(),
                initialSelectedEndDateMillis = state.end.toEpochMillisUtc(),
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
private fun ChartAndLegend(state: StatsUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
        items(state.slices, key = { it.categoryId }) { slice ->
            val index = state.slices.indexOf(slice)
            LegendRow(slice = slice, color = paletteColor(index))
        }
    }
}

@Composable
private fun LegendRow(slice: CategorySlice, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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

private fun rangeLabel(state: StatsUiState): String =
    "${state.start.formatAsDay()} – ${state.end.formatAsDay()}"

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
            )
        }
    }
}
