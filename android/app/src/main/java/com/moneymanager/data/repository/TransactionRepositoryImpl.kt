package com.moneymanager.data.repository

import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.TransactionEntity
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.Transaction
import com.moneymanager.domain.model.TransactionType
import com.moneymanager.domain.model.asSuccess
import com.moneymanager.domain.model.fail
import com.moneymanager.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** Room-backed [TransactionRepository]. Expenses only, for now. */
class TransactionRepositoryImpl
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
        private val categoryDao: CategoryDao,
        private val accountDao: AccountDao,
    ) : TransactionRepository {
        override fun observeAll(): Flow<List<Transaction>> =
            transactionDao.observeApproved().map { rows -> rows.map { it.toDomain() } }

        override fun observeApproved(): Flow<List<Transaction>> =
            transactionDao.observeApproved().map { rows -> rows.map { it.toDomain() } }

        override fun observeUnapproved(): Flow<List<Transaction>> =
            transactionDao.observeUnapproved().map { rows -> rows.map { it.toDomain() } }

        override fun observeUnapprovedCount(): Flow<Int> =
            transactionDao.observeUnapprovedCount()

        override suspend fun getById(id: Long): AppResult<Transaction> {
            val entity = transactionDao.getById(id) ?: return fail(AppError.NotFound)
            return entity.toDomain().asSuccess()
        }

        override suspend fun addExpense(
            amount: BigDecimal,
            date: LocalDate,
            categoryId: Long,
            accountId: Long,
            note: String?,
            merchant: String?,
        ): AppResult<Transaction> {
            validate(amount, categoryId, accountId)?.let { return fail(it) }

            val entity =
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    date = date,
                    amount = amount.setScale(2, java.math.RoundingMode.HALF_UP),
                    categoryId = categoryId,
                    accountId = accountId,
                    note = note?.takeIf { it.isNotBlank() },
                    isApproved = true,
                    merchant = merchant?.takeIf { it.isNotBlank() },
                )
            val id = transactionDao.insert(entity)
            return entity.copy(id = id).toDomain().asSuccess()
        }

        override suspend fun addAutoExpense(
            amount: BigDecimal,
            date: LocalDate,
            categoryId: Long,
            accountId: Long,
            note: String?,
            merchant: String?,
        ): AppResult<Transaction> {
            validate(amount, categoryId, accountId)?.let { return fail(it) }

            val entity =
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    date = date,
                    amount = amount.setScale(2, java.math.RoundingMode.HALF_UP),
                    categoryId = categoryId,
                    accountId = accountId,
                    note = note?.takeIf { it.isNotBlank() },
                    isApproved = false,
                    merchant = merchant?.takeIf { it.isNotBlank() },
                )
            val id = transactionDao.insert(entity)
            return entity.copy(id = id).toDomain().asSuccess()
        }

        override suspend fun updateExpense(
            id: Long,
            amount: BigDecimal,
            date: LocalDate,
            categoryId: Long,
            accountId: Long,
            note: String?,
            merchant: String?,
        ): AppResult<Transaction> {
            val existing = transactionDao.getById(id) ?: return fail(AppError.NotFound)
            validate(amount, categoryId, accountId)?.let { return fail(it) }

            val updated =
                existing.copy(
                    date = date,
                    amount = amount.setScale(2, java.math.RoundingMode.HALF_UP),
                    categoryId = categoryId,
                    accountId = accountId,
                    note = note?.takeIf { it.isNotBlank() },
                    isApproved = true,
                    merchant = merchant?.takeIf { it.isNotBlank() } ?: existing.merchant,
                )
            transactionDao.update(updated)
            return updated.toDomain().asSuccess()
        }

        override suspend fun approve(id: Long): AppResult<Unit> {
            val existing = transactionDao.getById(id) ?: return fail(AppError.NotFound)
            transactionDao.setApproved(existing.id, true)
            return Unit.asSuccess()
        }

        override suspend fun approveAll(): AppResult<Unit> {
            transactionDao.approveAll()
            return Unit.asSuccess()
        }

        /** Shared field validation for add/update; null means valid. */
        private suspend fun validate(
            amount: BigDecimal,
            categoryId: Long,
            accountId: Long,
        ): AppError? =
            when {
                amount.signum() <= 0 -> AppError.Validation("Amount must be greater than zero")
                categoryDao.getById(categoryId) == null ->
                    AppError.Validation("Category $categoryId does not exist")
                accountDao.getById(accountId) == null ->
                    AppError.Validation("Account $accountId does not exist")
                else -> null
            }

        override suspend fun delete(id: Long): AppResult<Unit> {
            val existing = transactionDao.getById(id) ?: return fail(AppError.NotFound)
            transactionDao.delete(existing)
            return Unit.asSuccess()
        }
    }
