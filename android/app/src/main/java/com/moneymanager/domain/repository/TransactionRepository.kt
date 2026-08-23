package com.moneymanager.domain.repository

import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

/** Read/write access to transactions (expenses only, for now). */
interface TransactionRepository {
    /** All entries, newest first. */
    fun observeAll(): Flow<List<Transaction>>

    suspend fun getById(id: Long): AppResult<Transaction>

    /**
     * Add an expense entry. Fails with Validation if [amount] is not positive
     * or the referenced [categoryId] / [accountId] does not exist.
     */
    suspend fun addExpense(
        amount: BigDecimal,
        date: LocalDate,
        categoryId: Long,
        accountId: Long,
        note: String?,
    ): AppResult<Transaction>

    suspend fun delete(id: Long): AppResult<Unit>
}
