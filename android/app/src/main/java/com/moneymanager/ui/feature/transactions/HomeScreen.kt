package com.moneymanager.ui.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneymanager.ui.theme.MoneyManagerTheme
import com.moneymanager.ui.util.formatAsCurrency
import com.moneymanager.ui.util.formatAsDay
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Landing screen: the expense list, grouped by day, with a FAB to add a new
 * expense. Reads its state from [TransactionsViewModel].
 */
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        state = uiState,
        onNavigateToSettings = onNavigateToSettings,
        onAddExpense = onAddExpense,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onNavigateToSettings: () -> Unit,
    onAddExpense: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Money Manager") },
                actions = {
                    TextButton(onClick = onNavigateToSettings) { Text("Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isEmpty ->
                    Text(
                        text = "No expenses yet.\nTap + to add your first one.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                else -> ExpenseList(state.sections)
            }
        }
    }
}

@Composable
private fun ExpenseList(sections: List<DaySection>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            item(key = "header-${section.date}") {
                DayHeader(section)
            }
            items(section.rows, key = { it.id }) { row ->
                ExpenseRow(row)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DayHeader(section: DaySection) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = section.date.formatAsDay(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = section.dayTotal.formatAsCurrency(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ExpenseRow(row: TransactionRow) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.categoryName, style = MaterialTheme.typography.bodyLarge)
            val subtitle = row.note?.let { "${row.accountName} · $it" } ?: row.accountName
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

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    val sample =
        listOf(
            DaySection(
                date = LocalDate.of(2026, 8, 23),
                dayTotal = BigDecimal("42.50"),
                rows =
                    listOf(
                        TransactionRow(1, BigDecimal("12.50"), "Food", "Cash", "Lunch"),
                        TransactionRow(2, BigDecimal("30.00"), "Transport", "Bank", null),
                    ),
            ),
        )
    MoneyManagerTheme {
        Surface {
            HomeScreenContent(
                state = HomeUiState(loading = false, sections = sample),
                onNavigateToSettings = {},
                onAddExpense = {},
            )
        }
    }
}
