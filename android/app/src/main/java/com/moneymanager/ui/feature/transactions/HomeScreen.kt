package com.moneymanager.ui.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.moneymanager.ui.components.MonthHeader
import com.moneymanager.ui.components.monthSwipe
import com.moneymanager.ui.theme.MoneyManagerTheme
import com.moneymanager.ui.util.formatAsCurrency
import com.moneymanager.ui.util.formatAsDay
import com.moneymanager.ui.util.formatAsMonth
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Landing screen: the expense list, grouped by day, with a FAB to add a new
 * expense. Reads its state from [TransactionsViewModel].
 */
@Composable
fun HomeScreen(
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        state = uiState,
        onAddExpense = onAddExpense,
        onEditExpense = onEditExpense,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Scaffold(
        contentWindowInsets =
            ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
        topBar = {
            TopAppBar(
                title = { Text("Money Manager") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .monthSwipe(onPrevious = onPreviousMonth, onNext = onNextMonth),
        ) {
            MonthHeader(
                label = state.month.formatAsMonth(),
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                subtitle = state.monthTotal.formatAsCurrency(),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isEmpty ->
                        Text(
                            text = "No expenses in ${state.month.formatAsMonth()}.\nSwipe or tap ‹ › to change month.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    else -> ExpenseList(state.sections, onEditExpense)
                }
            }
        }
    }
}

@Composable
private fun ExpenseList(sections: List<DaySection>, onEditExpense: (Long) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        sections.forEach { section ->
            item(key = "header-${section.date}") {
                DayHeader(section)
            }
            items(section.rows, key = { it.id }) { row ->
                ExpenseRow(row, onClick = { onEditExpense(row.id) })
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
private fun ExpenseRow(row: TransactionRow, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
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
                state =
                    HomeUiState(
                        loading = false,
                        month = YearMonth.of(2026, 8),
                        monthTotal = BigDecimal("42.50"),
                        sections = sample,
                    ),
                onAddExpense = {},
                onEditExpense = {},
                onPreviousMonth = {},
                onNextMonth = {},
            )
        }
    }
}
