package com.moneymanager.ui.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.domain.repository.AccountRepository
import com.moneymanager.domain.repository.CategoryRepository
import com.moneymanager.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** A single expense row, with category/account names already resolved. */
data class TransactionRow(
    val id: Long,
    val amount: BigDecimal,
    val categoryName: String,
    val accountName: String,
    val note: String?,
)

/** Expenses for one calendar day, plus that day's total. */
data class DaySection(
    val date: LocalDate,
    val dayTotal: BigDecimal,
    val rows: List<TransactionRow>,
)

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val monthTotal: BigDecimal = BigDecimal.ZERO,
    val sections: List<DaySection> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && sections.isEmpty()
}

@HiltViewModel
class TransactionsViewModel
    @Inject
    constructor(
        transactionRepository: TransactionRepository,
        categoryRepository: CategoryRepository,
        accountRepository: AccountRepository,
    ) : ViewModel() {
        private val selectedMonth = MutableStateFlow(YearMonth.now())

        val uiState: StateFlow<HomeUiState> =
            combine(
                transactionRepository.observeAll(),
                categoryRepository.observeAll(),
                accountRepository.observeAll(),
                selectedMonth,
            ) { transactions, categories, accounts, month ->
                val categoryNames = categories.associate { it.id to it.name }
                val accountNames = accounts.associate { it.id to it.name }

                val monthTransactions =
                    transactions.filter { YearMonth.from(it.date) == month }

                val sections =
                    monthTransactions
                        .groupBy { it.date }
                        .toSortedMap(reverseOrder())
                        .map { (date, dayTransactions) ->
                            DaySection(
                                date = date,
                                dayTotal =
                                    dayTransactions.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount },
                                rows =
                                    dayTransactions.map { t ->
                                        TransactionRow(
                                            id = t.id,
                                            amount = t.amount,
                                            categoryName = categoryNames[t.categoryId] ?: "Unknown",
                                            accountName = accountNames[t.accountId] ?: "Unknown",
                                            note = t.note,
                                        )
                                    },
                            )
                        }

                HomeUiState(
                    loading = false,
                    month = month,
                    monthTotal =
                        monthTransactions.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount },
                    sections = sections,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState(),
            )

        fun previousMonth() = selectedMonth.update { it.minusMonths(1) }

        fun nextMonth() = selectedMonth.update { it.plusMonths(1) }
    }
