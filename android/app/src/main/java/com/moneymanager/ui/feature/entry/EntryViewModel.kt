package com.moneymanager.ui.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.domain.model.Account
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.Category
import com.moneymanager.domain.repository.AccountRepository
import com.moneymanager.domain.repository.CategoryRepository
import com.moneymanager.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class EntryUiState(
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val selectedCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
) {
    val selectedCategory: Category? get() = categories.firstOrNull { it.id == selectedCategoryId }
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == selectedAccountId }
    val canSave: Boolean
        get() = !saving && amountText.isNotBlank() && selectedCategoryId != null && selectedAccountId != null
}

/** Backs the add-expense form. Loads the pickers and saves via the repository. */
@HiltViewModel
class EntryViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        categoryRepository: CategoryRepository,
        accountRepository: AccountRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(EntryUiState())
        val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                categoryRepository.observeAll().collect { categories ->
                    _uiState.update { state ->
                        state.copy(
                            categories = categories,
                            selectedCategoryId =
                                state.selectedCategoryId ?: categories.firstOrNull()?.id,
                        )
                    }
                }
            }
            viewModelScope.launch {
                accountRepository.observeAll().collect { accounts ->
                    _uiState.update { state ->
                        state.copy(
                            accounts = accounts,
                            selectedAccountId =
                                state.selectedAccountId ?: accounts.firstOrNull()?.id,
                        )
                    }
                }
            }
        }

        fun onAmountChange(value: String) {
            // Accept only digits and a single decimal separator.
            val filtered = value.filter { it.isDigit() || it == '.' }
            if (filtered.count { it == '.' } <= 1) {
                _uiState.update { it.copy(amountText = filtered, errorMessage = null) }
            }
        }

        fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }

        fun onNoteChange(note: String) = _uiState.update { it.copy(note = note) }

        fun onCategorySelected(id: Long) = _uiState.update { it.copy(selectedCategoryId = id) }

        fun onAccountSelected(id: Long) = _uiState.update { it.copy(selectedAccountId = id) }

        fun save() {
            val state = _uiState.value
            val amount = state.amountText.toBigDecimalOrNull()
            if (amount == null) {
                _uiState.update { it.copy(errorMessage = "Enter a valid amount") }
                return
            }
            val categoryId = state.selectedCategoryId
            val accountId = state.selectedAccountId
            if (categoryId == null || accountId == null) {
                _uiState.update { it.copy(errorMessage = "Pick a category and an account") }
                return
            }

            _uiState.update { it.copy(saving = true, errorMessage = null) }
            viewModelScope.launch {
                val result =
                    transactionRepository.addExpense(
                        amount = amount,
                        date = state.date,
                        categoryId = categoryId,
                        accountId = accountId,
                        note = state.note,
                    )
                when (result) {
                    is AppResult.Success -> _uiState.update { it.copy(saving = false, saved = true) }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(saving = false, errorMessage = result.error.toMessage())
                        }
                }
            }
        }

        private fun String.toBigDecimalOrNull(): BigDecimal? =
            try {
                if (isBlank()) null else BigDecimal(this)
            } catch (_: NumberFormatException) {
                null
            }

        private fun AppError.toMessage(): String =
            when (this) {
                is AppError.Validation -> message
                is AppError.Conflict -> message
                is AppError.Unknown -> message
                AppError.NotFound -> "Not found"
            }
    }
