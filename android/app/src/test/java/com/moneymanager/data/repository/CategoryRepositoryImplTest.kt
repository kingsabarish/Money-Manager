package com.moneymanager.data.repository

import com.moneymanager.data.local.entity.TransactionEntity
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

class CategoryRepositoryImplTest {
    private lateinit var db: InMemoryDb
    private lateinit var repo: CategoryRepositoryImpl

    @Before
    fun setUp() {
        db = InMemoryDb()
        repo = CategoryRepositoryImpl(FakeCategoryDao(db))
    }

    private fun <T> AppResult<T>.errorOrNull(): AppError? =
        (this as? AppResult.Failure)?.error

    private suspend fun createTopLevel(name: String): Long {
        val result = repo.create(name, parentId = null)
        return (result as AppResult.Success).data.id
    }

    @Test
    fun `creates a top-level category`() =
        runTest {
            val result = repo.create("Food", parentId = null)
            assertTrue(result is AppResult.Success)
            assertEquals(1, db.categories.value.size)
        }

    @Test
    fun `rejects a duplicate top-level name with Conflict`() =
        runTest {
            createTopLevel("Food")
            val result = repo.create("Food", parentId = null)
            assertTrue(result.errorOrNull() is AppError.Conflict)
        }

    @Test
    fun `allows the same child name under different parents`() =
        runTest {
            val food = createTopLevel("Food")
            val travel = createTopLevel("Travel")
            assertTrue(repo.create("Misc", parentId = food) is AppResult.Success)
            assertTrue(repo.create("Misc", parentId = travel) is AppResult.Success)
        }

    @Test
    fun `rejects a duplicate child name under the same parent`() =
        runTest {
            val food = createTopLevel("Food")
            repo.create("Dining", parentId = food)
            val result = repo.create("Dining", parentId = food)
            assertTrue(result.errorOrNull() is AppError.Conflict)
        }

    @Test
    fun `rejects a third level with Validation`() =
        runTest {
            val food = createTopLevel("Food")
            val dining = (repo.create("Dining", parentId = food) as AppResult.Success).data.id
            val result = repo.create("Fast food", parentId = dining)
            assertTrue(result.errorOrNull() is AppError.Validation)
        }

    @Test
    fun `rejects a child under a non-existent parent`() =
        runTest {
            val result = repo.create("Orphan", parentId = 999L)
            assertTrue(result.errorOrNull() is AppError.Validation)
        }

    @Test
    fun `blank name is rejected`() =
        runTest {
            val result = repo.create("   ", parentId = null)
            assertTrue(result.errorOrNull() is AppError.Validation)
        }

    @Test
    fun `delete is blocked when the category has children`() =
        runTest {
            val food = createTopLevel("Food")
            repo.create("Dining", parentId = food)
            val result = repo.delete(food)
            assertTrue(result.errorOrNull() is AppError.Conflict)
        }

    @Test
    fun `delete is blocked when the category is used by a transaction`() =
        runTest {
            val food = createTopLevel("Food")
            db.transactions.value =
                listOf(
                    TransactionEntity(
                        id = 1,
                        type = TransactionType.EXPENSE,
                        date = LocalDate.of(2026, 8, 23),
                        amount = BigDecimal("10.00"),
                        categoryId = food,
                        accountId = 1,
                        note = null,
                    ),
                )
            val result = repo.delete(food)
            assertTrue(result.errorOrNull() is AppError.Conflict)
        }

    @Test
    fun `delete succeeds for an unused leaf category`() =
        runTest {
            val food = createTopLevel("Food")
            val result = repo.delete(food)
            assertTrue(result is AppResult.Success)
            assertTrue(db.categories.value.isEmpty())
        }

    @Test
    fun `delete of a missing category returns NotFound`() =
        runTest {
            val result = repo.delete(999L)
            assertTrue(result.errorOrNull() is AppError.NotFound)
        }
}
