package com.moneymanager.ui.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.categorization.CategorizationEngine
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.Transaction
import com.moneymanager.domain.repository.AccountRepository
import com.moneymanager.domain.repository.CategoryRepository
import com.moneymanager.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Collections
import javax.inject.Inject

data class TransactionRow(
    val id: Long,
    val amount: BigDecimal,
    val categoryName: String,
    val accountName: String,
    val note: String?,
    val merchant: String? = null,
)

data class DaySection(
    val date: LocalDate,
    val dayTotal: BigDecimal,
    val rows: List<TransactionRow>,
)

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val monthTotal: BigDecimal = BigDecimal.ZERO,
    val unapprovedCount: Int = 0,
    val sections: List<DaySection> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && sections.isEmpty()
}

@HiltViewModel
class TransactionsViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
        accountRepository: AccountRepository,
        private val categorizationEngine: CategorizationEngine,
    ) : ViewModel() {
        private val selectedMonth = MutableStateFlow(YearMonth.now())
        private val processedTxIds = Collections.synchronizedSet(mutableSetOf<Long>())

        init {
            // Foreground Smart Categorization:
            // When user opens the home screen, check if any unapproved transactions need AI categorization
            // that was deferred while the app was backgrounded.
            viewModelScope.launch {
                if (!categorizationEngine.isAiCoreSupported()) return@launch
                transactionRepository.observeUnapproved().collect { unapprovedList ->
                    for (txn in unapprovedList) {
                        if (processedTxIds.contains(txn.id)) continue
                        val merchantOrNote = txn.note ?: txn.merchant ?: continue
                        if (merchantOrNote.isBlank()) continue
                        val rawMessage = txn.merchant?.takeIf { it.isNotBlank() } ?: txn.note?.takeIf { it.isNotBlank() } ?: continue

                        val catRes = categoryRepository.getById(txn.categoryId)
                        val catName = if (catRes is AppResult.Success) catRes.data.name else ""
                        val isOtherOrGeneral = catName.equals("Other", ignoreCase = true) || catName.equals("General", ignoreCase = true)

                        if (isOtherOrGeneral) {
                            processedTxIds.add(txn.id)
                            launch {
                                try {
                                    val parsed = com.moneymanager.data.ingestion.TransactionParser.parseSms(rawMessage) as? com.moneymanager.data.ingestion.ParsedTransaction.Expense
                                    val fallbackMerchant = parsed?.merchant ?: txn.merchant ?: ""
                                    val fallbackNote = parsed?.note ?: txn.note
                                    val catResult = categorizationEngine.predictCategory(
                                        rawMessage = rawMessage,
                                        source = com.moneymanager.data.ingestion.TransactionSource.SMS,
                                        amount = txn.amount,
                                        fallbackMerchant = fallbackMerchant,
                                        fallbackNote = fallbackNote,
                                        date = txn.date,
                                    )
                                    if (catResult.finalCategoryId != txn.categoryId || catResult.predictedMerchant != txn.merchant) {
                                        transactionRepository.updateUnapprovedPrediction(
                                            id = txn.id,
                                            categoryId = catResult.finalCategoryId,
                                            merchant = catResult.predictedMerchant,
                                            note = catResult.predictedNote.takeIf { it.isNotBlank() },
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("TransactionsVM", "Foreground categorization failed for ${txn.id}", e)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uiState: StateFlow<HomeUiState> =
            combine(
                transactionRepository.observeAll(),
                categoryRepository.observeAll(),
                accountRepository.observeAll(),
                selectedMonth,
                transactionRepository.observeUnapprovedCount(),
            ) { transactions, categories, accounts, month, unapprovedCount ->
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
                                            merchant = t.merchant,
                                        )
                                    },
                            )
                        }

                val monthTotal =
                    monthTransactions.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }

                HomeUiState(
                    loading = false,
                    month = month,
                    monthTotal = monthTotal,
                    unapprovedCount = unapprovedCount,
                    sections = sections,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState(),
            )

        fun selectMonth(month: YearMonth) {
            selectedMonth.value = month
        }

        fun previousMonth() {
            selectedMonth.value = selectedMonth.value.minusMonths(1)
        }

        fun nextMonth() {
            selectedMonth.value = selectedMonth.value.plusMonths(1)
        }
    }
