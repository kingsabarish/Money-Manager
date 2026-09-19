package com.moneymanager.domain.repository

import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

/** Read/write access to transactions (expenses only, for now). */
interface TransactionRepository {
    /** All approved entries, newest first. */
    fun observeAll(): Flow<List<Transaction>>

    /** All approved entries, newest first. */
    fun observeApproved(): Flow<List<Transaction>>

    /** All unapproved entries, newest first. */
    fun observeUnapproved(): Flow<List<Transaction>>

    /** Total count of unapproved entries. */
    fun observeUnapprovedCount(): Flow<Int>

    suspend fun getById(id: Long): AppResult<Transaction>

    /**
     * Add an expense entry (approved immediately). Fails with Validation if [amount] is not positive
     * or the referenced [categoryId] / [accountId] does not exist.
     */
    suspend fun addExpense(
        amount: BigDecimal,
        date: LocalDate,
        categoryId: Long,
        accountId: Long,
        note: String?,
    ): AppResult<Transaction>

    /**
     * Add an auto-detected expense entry with isApproved = false.
     */
    suspend fun addAutoExpense(
        amount: BigDecimal,
        date: LocalDate,
        categoryId: Long,
        accountId: Long,
        note: String?,
    ): AppResult<Transaction>

    /**
     * Update an existing expense. Sets isApproved = true. Fails with NotFound if [id] does not exist, or
     * Validation if [amount] is not positive or a referenced id does not exist.
     */
    suspend fun updateExpense(
        id: Long,
        amount: BigDecimal,
        date: LocalDate,
        categoryId: Long,
        accountId: Long,
        note: String?,
    ): AppResult<Transaction>

    suspend fun approve(id: Long): AppResult<Unit>

    suspend fun approveAll(): AppResult<Unit>

    suspend fun delete(id: Long): AppResult<Unit>
}
