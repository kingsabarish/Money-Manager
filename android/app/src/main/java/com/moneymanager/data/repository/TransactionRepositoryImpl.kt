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
            transactionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

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
        ): AppResult<Transaction> {
            if (amount.signum() <= 0) {
                return fail(AppError.Validation("Amount must be greater than zero"))
            }
            if (categoryDao.getById(categoryId) == null) {
                return fail(AppError.Validation("Category $categoryId does not exist"))
            }
            if (accountDao.getById(accountId) == null) {
                return fail(AppError.Validation("Account $accountId does not exist"))
            }

            val normalized = amount.setScale(2, java.math.RoundingMode.HALF_UP)
            val entity =
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    date = date,
                    amount = normalized,
                    categoryId = categoryId,
                    accountId = accountId,
                    note = note?.takeIf { it.isNotBlank() },
                )
            val id = transactionDao.insert(entity)
            return entity.copy(id = id).toDomain().asSuccess()
        }

        override suspend fun delete(id: Long): AppResult<Unit> {
            val existing = transactionDao.getById(id) ?: return fail(AppError.NotFound)
            transactionDao.delete(existing)
            return Unit.asSuccess()
        }
    }
