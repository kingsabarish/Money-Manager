package com.moneymanager.data.repository

import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionRepositoryImplTest {
    private lateinit var db: InMemoryDb
    private lateinit var repo: TransactionRepositoryImpl

    @Before
    fun setUp() {
        db = InMemoryDb()
        repo =
            TransactionRepositoryImpl(
                transactionDao = FakeTransactionDao(db),
                categoryDao = FakeCategoryDao(db),
                accountDao = FakeAccountDao(db),
            )
        // Seed one category and one account so the FK checks pass.
        db.categories.value =
            listOf(CategoryEntity(id = 1, name = "Food", parentId = null, type = TransactionType.EXPENSE))
        db.accounts.value = listOf(AccountEntity(id = 1, name = "Cash", parentId = null))
    }

    private fun <T> AppResult<T>.errorOrNull(): AppError? =
        (this as? AppResult.Failure)?.error

    @Test
    fun `adds an expense and normalizes the amount to two decimals`() =
        runTest {
            val result =
                repo.addExpense(
                    amount = BigDecimal("10.5"),
                    date = LocalDate.of(2026, 8, 23),
                    categoryId = 1,
                    accountId = 1,
                    note = "lunch",
                )
            assertTrue(result is AppResult.Success)
            assertEquals(BigDecimal("10.50"), (result as AppResult.Success).data.amount)
            assertEquals(1, db.transactions.value.size)
        }

    @Test
    fun `rejects a non-positive amount`() =
        runTest {
            val result =
                repo.addExpense(BigDecimal.ZERO, LocalDate.of(2026, 8, 23), 1, 1, null)
            assertTrue(result.errorOrNull() is AppError.Validation)
        }

    @Test
    fun `rejects a non-existent category`() =
        runTest {
            val result =
                repo.addExpense(BigDecimal("1.00"), LocalDate.of(2026, 8, 23), 999, 1, null)
            assertTrue(result.errorOrNull() is AppError.Validation)
        }

    @Test
    fun `rejects a non-existent account`() =
        runTest {
            val result =
                repo.addExpense(BigDecimal("1.00"), LocalDate.of(2026, 8, 23), 1, 999, null)
            assertTrue(result.errorOrNull() is AppError.Validation)
        }

    @Test
    fun `blank note is stored as null`() =
        runTest {
            repo.addExpense(BigDecimal("1.00"), LocalDate.of(2026, 8, 23), 1, 1, "   ")
            assertEquals(null, db.transactions.value.single().note)
        }

    @Test
    fun `updates an existing expense in place`() =
        runTest {
            val id =
                (repo.addExpense(BigDecimal("1.00"), LocalDate.of(2026, 8, 23), 1, 1, "old")
                    as AppResult.Success).data.id

            val result =
                repo.updateExpense(
                    id = id,
                    amount = BigDecimal("2.5"),
                    date = LocalDate.of(2026, 8, 24),
                    categoryId = 1,
                    accountId = 1,
                    note = "new",
                )

            assertTrue(result is AppResult.Success)
            val stored = db.transactions.value.single()
            assertEquals(id, stored.id)
            assertEquals(BigDecimal("2.50"), stored.amount)
            assertEquals(LocalDate.of(2026, 8, 24), stored.date)
            assertEquals("new", stored.note)
            assertEquals(1, db.transactions.value.size)
        }

    @Test
    fun `updating a missing expense returns NotFound`() =
        runTest {
            val result =
                repo.updateExpense(999, BigDecimal("1.00"), LocalDate.of(2026, 8, 23), 1, 1, null)
            assertTrue(result.errorOrNull() is AppError.NotFound)
        }

    @Test
    fun `updating with a non-positive amount is rejected`() =
        runTest {
            val id =
                (repo.addExpense(BigDecimal("1.00"), LocalDate.of(2026, 8, 23), 1, 1, null)
                    as AppResult.Success).data.id
            val result =
                repo.updateExpense(id, BigDecimal.ZERO, LocalDate.of(2026, 8, 23), 1, 1, null)
            assertTrue(result.errorOrNull() is AppError.Validation)
        }

    @Test
    fun `deletes an expense`() =
        runTest {
            val id =
                (repo.addExpense(BigDecimal("1.00"), LocalDate.of(2026, 8, 23), 1, 1, null)
                    as AppResult.Success).data.id
            assertTrue(repo.delete(id) is AppResult.Success)
            assertTrue(db.transactions.value.isEmpty())
        }
}
