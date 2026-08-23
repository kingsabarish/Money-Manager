package com.moneymanager.domain.repository

import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.Category
import com.moneymanager.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to categories. Implementations enforce the two-level depth,
 * duplicate-name (per parent), and guarded-delete invariants — there is no
 * server to backstop them on-device.
 */
interface CategoryRepository {
    /** All categories (top-level and children), ordered by name. */
    fun observeAll(): Flow<List<Category>>

    suspend fun getById(id: Long): AppResult<Category>

    /**
     * Create a top-level category (when [parentId] is null) or a subcategory.
     * Fails with Validation if the parent is not top-level (would exceed two
     * levels) and Conflict if a sibling already has [name].
     */
    suspend fun create(
        name: String,
        parentId: Long?,
        type: TransactionType = TransactionType.EXPENSE,
    ): AppResult<Category>

    suspend fun rename(id: Long, name: String): AppResult<Category>

    /** Fails with Conflict if the category has children or is used by an entry. */
    suspend fun delete(id: Long): AppResult<Unit>
}
