package com.moneymanager.ui.feature.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.moneymanager.domain.model.Account
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.Category
import com.moneymanager.domain.repository.AccountRepository
import com.moneymanager.domain.repository.CategoryRepository
import com.moneymanager.domain.repository.TransactionRepository
import com.moneymanager.ui.navigation.Entry
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
    val isEditing: Boolean = false,
    val noteSuggestions: List<String> = emptyList(),
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
        savedStateHandle: SavedStateHandle,
        private val transactionRepository: TransactionRepository,
        categoryRepository: CategoryRepository,
        accountRepository: AccountRepository,
    ) : ViewModel() {
        /** Non-null when editing an existing expense (from the Entry route arg). */
        private val editingId: Long? = savedStateHandle.toRoute<Entry>().transactionId

        private val _uiState = MutableStateFlow(EntryUiState(isEditing = editingId != null))
        val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

        init {
            if (editingId != null) {
                viewModelScope.launch {
                    val result = transactionRepository.getById(editingId)
                    if (result is AppResult.Success) {
                        val t = result.data
                        _uiState.update { state ->
                            state.copy(
                                amountText = t.amount.toPlainString(),
                                date = t.date,
                                note = t.note.orEmpty(),
                                selectedCategoryId = t.categoryId,
                                selectedAccountId = t.accountId,
                            )
                        }
                    }
                }
            }
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
            viewModelScope.launch {
                transactionRepository.observeAll().collect { transactions ->
                    val suggestions =
                        transactions
                            .map { it.note }
                            .filterNotNull()
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                    _uiState.update { it.copy(noteSuggestions = suggestions) }
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

        fun onNoteSuggestionSelected(suggestion: String) =
            _uiState.update { it.copy(note = suggestion) }

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
                    if (editingId != null) {
                        transactionRepository.updateExpense(
                            id = editingId,
                            amount = amount,
                            date = state.date,
                            categoryId = categoryId,
                            accountId = accountId,
                            note = state.note,
                        )
                    } else {
                        transactionRepository.addExpense(
                            amount = amount,
                            date = state.date,
                            categoryId = categoryId,
                            accountId = accountId,
                            note = state.note,
                        )
                    }
                when (result) {
                    is AppResult.Success -> _uiState.update { it.copy(saving = false, saved = true) }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(saving = false, errorMessage = result.error.toMessage())
                        }
                }
            }
        }

        /** Delete the expense being edited, then signal completion via [EntryUiState.saved]. */
        fun delete() {
            val id = editingId ?: return
            _uiState.update { it.copy(saving = true, errorMessage = null) }
            viewModelScope.launch {
                when (val result = transactionRepository.delete(id)) {
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
