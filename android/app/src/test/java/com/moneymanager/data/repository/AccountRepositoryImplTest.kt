package com.moneymanager.data.repository

import com.moneymanager.data.local.entity.TransactionEntity
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AccountRepositoryImplTest {
    private lateinit var db: InMemoryDb
    private lateinit var repo: AccountRepositoryImpl

    @Before
    fun setUp() {
        db = InMemoryDb()
        repo = AccountRepositoryImpl(FakeAccountDao(db))
    }

    private fun <T> AppResult<T>.errorOrNull(): AppError? =
        (this as? AppResult.Failure)?.error

    private suspend fun createTopLevel(name: String): Long =
        (repo.create(name, parentId = null) as AppResult.Success).data.id

    @Test
    fun `rejects a duplicate top-level name with Conflict`() =
        runTest {
            createTopLevel("Cash")
            assertTrue(repo.create("Cash", parentId = null).errorOrNull() is AppError.Conflict)
        }

    @Test
    fun `rejects a third level with Validation`() =
        runTest {
            val bank = createTopLevel("Bank")
            val savings = (repo.create("Savings", parentId = bank) as AppResult.Success).data.id
            assertTrue(repo.create("Joint", parentId = savings).errorOrNull() is AppError.Validation)
        }

    @Test
    fun `delete is blocked when the account has children`() =
        runTest {
            val bank = createTopLevel("Bank")
            repo.create("Savings", parentId = bank)
            assertTrue(repo.delete(bank).errorOrNull() is AppError.Conflict)
        }

    @Test
    fun `delete is blocked when the account is used by a transaction`() =
        runTest {
            val cash = createTopLevel("Cash")
            db.transactions.value =
                listOf(
                    TransactionEntity(
                        id = 1,
                        type = TransactionType.EXPENSE,
                        date = LocalDate.of(2026, 8, 23),
                        amount = BigDecimal("5.00"),
                        categoryId = 1,
                        accountId = cash,
                        note = null,
                    ),
                )
            assertTrue(repo.delete(cash).errorOrNull() is AppError.Conflict)
        }

    @Test
    fun `delete succeeds for an unused leaf account`() =
        runTest {
            val cash = createTopLevel("Cash")
            assertTrue(repo.delete(cash) is AppResult.Success)
            assertTrue(db.accounts.value.isEmpty())
        }
}
