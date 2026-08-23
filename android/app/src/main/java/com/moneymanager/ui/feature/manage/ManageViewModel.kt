package com.moneymanager.ui.feature.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.Account
import com.moneymanager.domain.model.Category
import com.moneymanager.domain.repository.AccountRepository
import com.moneymanager.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A top-level category/account together with its children (two-level tree). */
data class ManageGroup(
    val id: Long,
    val name: String,
    val children: List<ManageItem>,
)

data class ManageItem(val id: Long, val name: String)

data class ManageUiState(
    val categories: List<ManageGroup> = emptyList(),
    val accounts: List<ManageGroup> = emptyList(),
    /** Transient banner for a failed action (duplicate name, guarded delete, …). */
    val message: String? = null,
)

/**
 * Backs the "categories & accounts" management screen. Both trees are read from
 * their repository [observeAll] flows; create/rename/delete go back through the
 * repositories, which enforce the two-level, duplicate-name, and guarded-delete
 * invariants. A rejected action is surfaced as [ManageUiState.message].
 */
@HiltViewModel
class ManageViewModel
    @Inject
    constructor(
        private val categoryRepository: CategoryRepository,
        private val accountRepository: AccountRepository,
    ) : ViewModel() {
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<ManageUiState> =
            combine(
                categoryRepository.observeAll(),
                accountRepository.observeAll(),
                message,
            ) { categories, accounts, msg ->
                ManageUiState(
                    categories = groupCategories(categories),
                    accounts = groupAccounts(accounts),
                    message = msg,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ManageUiState(),
            )

        fun addCategory(name: String, parentId: Long?) =
            run { categoryRepository.create(name.trim(), parentId) }

        fun renameCategory(id: Long, name: String) =
            run { categoryRepository.rename(id, name.trim()) }

        fun deleteCategory(id: Long) = run { categoryRepository.delete(id) }

        fun addAccount(name: String, parentId: Long?) =
            run { accountRepository.create(name.trim(), parentId) }

        fun renameAccount(id: Long, name: String) =
            run { accountRepository.rename(id, name.trim()) }

        fun deleteAccount(id: Long) = run { accountRepository.delete(id) }

        /** Clear the banner once the UI has shown it. */
        fun onMessageShown() {
            message.value = null
        }

        private fun run(action: suspend () -> AppResult<*>) {
            viewModelScope.launch {
                when (val result = action()) {
                    is AppResult.Success -> message.value = null
                    is AppResult.Failure -> message.value = result.error.userMessage()
                }
            }
        }

        private fun groupCategories(list: List<Category>): List<ManageGroup> {
            val childrenByParent = list.filterNot { it.isTopLevel }.groupBy { it.parentId }
            return list.filter { it.isTopLevel }
                .sortedBy { it.name }
                .map { top ->
                    ManageGroup(
                        id = top.id,
                        name = top.name,
                        children =
                            childrenByParent[top.id].orEmpty()
                                .sortedBy { it.name }
                                .map { ManageItem(it.id, it.name) },
                    )
                }
        }

        private fun groupAccounts(list: List<Account>): List<ManageGroup> {
            val childrenByParent = list.filterNot { it.isTopLevel }.groupBy { it.parentId }
            return list.filter { it.isTopLevel }
                .sortedBy { it.name }
                .map { top ->
                    ManageGroup(
                        id = top.id,
                        name = top.name,
                        children =
                            childrenByParent[top.id].orEmpty()
                                .sortedBy { it.name }
                                .map { ManageItem(it.id, it.name) },
                    )
                }
        }

        private fun AppError.userMessage(): String =
            when (this) {
                is AppError.NotFound -> "Not found"
                is AppError.Conflict -> message
                is AppError.Validation -> message
                is AppError.Unknown -> message
            }
    }
