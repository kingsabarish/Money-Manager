package com.moneymanager.ui.feature.unapproved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.categorization.CategorizationEngine
import com.moneymanager.domain.model.Account
import com.moneymanager.domain.model.Category
import com.moneymanager.domain.model.Transaction
import com.moneymanager.domain.repository.AccountRepository
import com.moneymanager.domain.repository.CategoryRepository
import com.moneymanager.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class UnapprovedItem(
    val id: Long,
    val amount: BigDecimal,
    val date: LocalDate,
    val categoryId: Long,
    val categoryName: String,
    val accountId: Long,
    val accountName: String,
    val note: String?,
    val merchant: String? = null,
)

data class UnapprovedUiState(
    val loading: Boolean = true,
    val items: List<UnapprovedItem> = emptyList(),
)

@HiltViewModel
class UnapprovedTransactionsViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val categorizationEngine: CategorizationEngine,
        categoryRepository: CategoryRepository,
        accountRepository: AccountRepository,
        private val notificationManager: com.moneymanager.data.ingestion.TransactionNotificationManager,
    ) : ViewModel() {
        val uiState: StateFlow<UnapprovedUiState> =
            combine(
                transactionRepository.observeUnapproved(),
                categoryRepository.observeAll(),
                accountRepository.observeAll(),
            ) { unapproved, categories, accounts ->
                val categoryMap = categories.associate { it.id to it }
                val accountMap = accounts.associate { it.id to it.name }

                val items =
                    unapproved.map { txn ->
                        val cat = categoryMap[txn.categoryId]
                        val catName =
                            when {
                                cat == null -> "Unknown"
                                cat.isTopLevel -> cat.name
                                else -> {
                                    val parentName = categoryMap[cat.parentId]?.name
                                    if (parentName != null) "$parentName · ${cat.name}" else cat.name
                                }
                            }

                        UnapprovedItem(
                            id = txn.id,
                            amount = txn.amount,
                            date = txn.date,
                            categoryId = txn.categoryId,
                            categoryName = catName,
                            accountId = txn.accountId,
                            accountName = accountMap[txn.accountId] ?: "Unknown",
                            note = txn.note,
                            merchant = txn.merchant,
                        )
                    }

                UnapprovedUiState(loading = false, items = items)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UnapprovedUiState(),
            )

        fun approve(item: UnapprovedItem) {
            viewModelScope.launch {
                transactionRepository.approve(item.id)
                notificationManager.cancelNotification(item.id.toInt())
                // Reinforce ML model
                categorizationEngine.train(
                    merchant = item.merchant ?: item.note ?: "",
                    note = item.note,
                    amount = item.amount,
                    assignedCategoryId = item.categoryId,
                )
            }
        }

        fun delete(id: Long) {
            viewModelScope.launch {
                transactionRepository.delete(id)
                notificationManager.cancelNotification(id.toInt())
            }
        }

        fun approveAll() {
            viewModelScope.launch {
                val currentItems = uiState.value.items
                transactionRepository.approveAll()
                for (item in currentItems) {
                    notificationManager.cancelNotification(item.id.toInt())
                    categorizationEngine.train(
                        merchant = item.merchant ?: item.note ?: "",
                        note = item.note,
                        amount = item.amount,
                        assignedCategoryId = item.categoryId,
                    )
                }
            }
        }
    }
