package com.moneymanager.domain.repository

import com.moneymanager.domain.model.Account
import com.moneymanager.domain.model.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to accounts. Implementations enforce the two-level depth,
 * duplicate-name (per parent), and guarded-delete invariants.
 */
interface AccountRepository {
    /** All accounts (top-level and children), ordered by name. */
    fun observeAll(): Flow<List<Account>>

    suspend fun getById(id: Long): AppResult<Account>

    /**
     * Create a top-level account (when [parentId] is null) or a sub-account.
     * Fails with Validation if the parent is not top-level and Conflict if a
     * sibling already has [name].
     */
    suspend fun create(name: String, parentId: Long?): AppResult<Account>

    suspend fun rename(id: Long, name: String): AppResult<Account>

    /** Fails with Conflict if the account has children or is used by an entry. */
    suspend fun delete(id: Long): AppResult<Unit>
}
